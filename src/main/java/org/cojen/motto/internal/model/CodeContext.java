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

import org.cojen.motto.internal.compiler.CompilationEnv;

/**
 * A CodeContext is referenced by blocks and bindings.
 *
 * @author Brian S. O'Neill
 */
public final class CodeContext {
    private final CompilationEnv mEnv;
    private final BaseBlock mResultBlock;
    private final BaseBinding mResultBinding;

    public CodeContext(CompilationEnv env, BaseType resultType) {
        mEnv = env;
        mResultBinding = new BaseBinding.Anonymous(this, resultType);
        mResultBlock = new BaseBlock(this);
    }

    public CompilationEnv env() {
        return mEnv;
    }

    /**
     * Returns an anonymous binding which must be assigned before jumping to the result block.
     */
    public BaseBinding resultBinding() {
        return mResultBinding;
    }

    /**
     * Returns a block to jump to when the code is done.
     */
    public BaseBlock resultBlock() {
        return mResultBlock;
    }

    /**
     * Assigns the context result binding and jumps to the result block.
     *
     * @param block and a jump from this block to the context result block
     * @return null if successful, or else a conflicting type if the context result type is
     * inferred, and it couldn't be specialized
     */
    public BaseType assignResultAndJump(BaseBlock block, BaseBinding result) {
        BaseType conflict = mResultBinding.type().specialize(result.type());

        if (conflict == null) {
            block.copy(mResultBinding, result);
            block.jump(mResultBlock);
        }

        return conflict;
    }
}
