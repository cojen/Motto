/*
 *  Copyright 2026 Cojen.org
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.cojen.motto.internal.model;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Tracks the result for a block of code.
 *
 * @author Brian S. O'Neill
 */
public abstract sealed class BlockContext {
    private static final VarHandle cOwnerHandle;

    static {
        try {
            var lookup = MethodHandles.lookup();
            cOwnerHandle = lookup.findVarHandle(BlockContext.class, "mOwner", Thread.class);
        } catch (Throwable e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final BaseBlock mResultBlock;
    private final BaseBinding mResultBinding;

    private volatile Thread mOwner;

    public BlockContext(BaseType resultType) {
        mResultBinding = new BaseBinding.Anonymous(this, resultType);
        mResultBlock = new BaseBlock(this);
    }

    /**
     * Acquire ownership of the BlockContext, for the current thread. This is a simple kind of
     * reentrant lock which doesn't have a queue.
     *
     * @return true if acquired for the first time
     * @throws IllegalStateException if owned by another thread
     */
    public final boolean acquire() {
        Thread current = Thread.currentThread();
        Object result = cOwnerHandle.compareAndExchange(this, null, current);
        if (result == null) {
            return true;
        }
        if (result == current) {
            return false;
        }
        throw new IllegalStateException("access is owned by another thread");
    }

    /**
     * Release ownership of the BlockContext.
     *
     * @param wasAcquired pass true to actually release
     */
    public final void release(boolean wasAcquired) {
        if (wasAcquired) {
            mOwner = null;
        }
    }

    /**
     * Returns an anonymous binding which must be assigned before jumping to the result block.
     */
    public final BaseBinding resultBinding() {
        return mResultBinding;
    }

    /**
     * Returns a block to jump to when the code is done.
     */
    public final BaseBlock resultBlock() {
        return mResultBlock;
    }

    /**
     * Returns true if the given block is the result block of this context, and the result is
     * excepted to be returned from a callable.
     */
    public abstract boolean isReturnBlock(BaseBlock block);

    /**
     * Assigns the context result binding and jumps to the result block.
     *
     * @param block and a jump from this block to the context result block
     * @return null if successful, or else a conflicting type if the context result type is
     * inferred, and it couldn't be specialized
     */
    public final BaseType assignResultAndJump(BaseBlock block, BaseBinding result) {
        BaseType conflict = mResultBinding.type().specialize(result.type());

        if (conflict == null) {
            block.copy(mResultBinding, result);
            block.jump(mResultBlock);
        }

        return conflict;
    }

    /**
     * Defines a context which doesn't generate a return instruction.
     */
    public static final class Yield extends BlockContext {
        public Yield(BaseType resultType) {
            super(resultType);
        }
        
        @Override
        public boolean isReturnBlock(BaseBlock block) {
            return false;
        }
    }

    /**
     * Defines a context which should generate a return instruction.
     */
    public static final class Return extends BlockContext {
        public Return(BaseType resultType) {
            super(resultType);
        }
        
        @Override
        public boolean isReturnBlock(BaseBlock block) {
            return block == resultBlock();
        }
    }
}
