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

package org.cojen.motto.model;

import org.cojen.motto.internal.model.BaseBlock;

/**
 * Defines an executable block of code within a larger code body.
 *
 * @author Brian S. O'Neill
 */
public sealed interface Block extends Iterable<Action> permits BaseBlock {
    /**
     * Returns a new block which isn't attached to anything until it becomes a destination for
     * a jump or branch action.
     */
    public static Block newBlock() {
        return new BaseBlock();
    }

    /**
     * Returns true if this block is terminated, and so no actions can be appended to it.
     */
    public boolean isTerminated();

    /**
     * Returns true if all execution paths originating from this block are terminated.
     */
    public boolean isFullyTerminated();

    /**
     * Terminates all non-terminated execution paths reachable from this block by jumping to a
     * new common block. If all paths are terminated, then no merge is possible, and instead
     * null is returned.
     *
     * @return a new block if the merge succeeded
     */
    // FIXME: special yield handling?
    //public Block merge();

    /**
     * Return a new anonymous local variable binding which is visible to all actions within the
     * code body.
     */
    public Binding var(Type type);

    /**
     * Return a named or new anonymous local variable binding which is visible to all actions
     * within the code body.
     *
     * @param name can pass null to create an anonymous variable
     */
    public Binding var(Type type, String name);

    /**
     * Append an action which specifies a computed {@link #result result}. If any actions are
     * added afterwards, then the yielded result is discarded, effectively becoming void.
     *
     * @param result a Binding or a constant
     * @throws TerminatedBlockException if this block is terminated
     */
    public void yield(Object result);

    /**
     * Returns the current result binding, as specified by the {@link #yield yield} method. If
     * not specified, then the result is {@link Binding#void_ void}.
     */
    public Binding result();

    /**
     * Append a copy action to the end of this block. If necessary, a widening conversion is
     * automatically applied. If the target and source are the same, then no copy is appended.
     *
     * @param source a Binding or a constant
     * @throws TerminatedBlockException if this block is terminated
     */
    public void copy(Binding target, Object source);

    /**
     * Append an action to the end of this block which performs a type cast and copy. If the
     * cast isn't necessary, then a copy action is appended instead.
     *
     * @param source a Binding or a constant
     * @throws TerminatedBlockException if this block is terminated
     */
    public void cast(Binding target, Object source);

    /**
     * Append an action to the end of this block which performs a type conversion and copy. If
     * the conversion isn't necessary, then a copy action is appended instead.
     *
     * @param source a Binding or a constant
     * @throws TerminatedBlockException if this block is terminated
     */
    public void convert(Binding target, Object source);

    /**
     * @param inputs Bindings or constants
     * @return the result binding
     * @throws TerminatedBlockException if this block is terminated
     */
    public Binding callDirect(CallableItem callable, Object... inputs);

    /**
     * @param inputs Bindings or constants
     * @return the result binding
     * @throws TerminatedBlockException if this block is terminated
     */
    public Binding callDirect(CallableItem callable, Object[] inputs, SegmentArgument... segments);

    /**
     * @param inputs Bindings or constants
     * @return the result binding
     * @throws TerminatedBlockException if this block is terminated
     */
    public Binding callNew(CallableItem callable, Object... inputs);

    /**
     * @param inputs Bindings or constants
     * @return the result binding
     * @throws TerminatedBlockException if this block is terminated
     */
    public Binding callVirtual(CallableItem callable, Object... inputs);

    /**
     * Append a jump action to the end of this block, and then terminate it.
     *
     * @throws TerminatedBlockException if this block is already terminated
     */
    public void jump(Block destination);

    /**
     * Append a conditional branch action to the end of this block, and then terminate it.
     *
     * @throws TerminatedBlockException if this block is already terminated
     */
    public void branch(Binding condition, Block whenTrue, Block whenFalse);

    /**
     * Append a throw action to the end of this block, and then terminate it.
     *
     * @param exception a Binding or a constant
     * @throws TerminatedBlockException if this block is already terminated
     */
    public void throw_(Object exception);

    /**
     * Provide an exception handler for this block. Handlers are selected by order of most
     * specialized to least specialized.
     *
     * @param exceptionType type of exception to catch; pass null to catch all
     * @param varName optional variable name
     * @param handler required exception handler entry block
     * @return a local variable binding which will reference the caught exception
     * @throws IllegalStateException if the caught type is duplicated
     */
    public Binding catch_(Type exceptionType, String varName, Block handler);

    /**
     * @param dims Bindings or constants
     * @return the result binding
     * @throws TerminatedBlockException if this block is terminated
     */
    public Binding arrayNew(ArrayType type, Object... dims);

    /**
     * @param index a Binding or a constant
     * @return the result binding
     * @throws TerminatedBlockException if this block is terminated
     */
    public Binding arrayGet(Binding array, Object index);

    /**
     * @param index a Binding or a constant
     * @param value a Binding or a constant
     * @throws TerminatedBlockException if this block is terminated
     */
    public void arraySet(Binding array, Object index, Object value);

    /**
     * @param inputs Bindings or constants
     * @return the result binding
     * @throws TerminatedBlockException if this block is terminated
     */
    public Binding tupleNew(TupleType type, Object... inputs);

    /**
     * @return the result binding
     * @throws TerminatedBlockException if this block is terminated
     */
    public Binding tupleGet(Binding tuple, Binding index);

    /**
     * @return the result binding
     * @throws TerminatedBlockException if this block is terminated
     */
    public Binding tupleGet(Binding tuple, int index);

    /**
     * @return the result binding
     * @throws TerminatedBlockException if this block is terminated
     */
    public Binding tupleGet(Binding tuple, String label);

    /**
     * @param value a Binding or a constant
     * @throws TerminatedBlockException if this block is terminated
     */
    public void tupleSet(Binding tuple, Binding index, Object value);

    /**
     * @param value a Binding or a constant
     * @throws TerminatedBlockException if this block is terminated
     */
    public void tupleSet(Binding tuple, int index, Object value);

    /**
     * @param value a Binding or a constant
     * @throws TerminatedBlockException if this block is terminated
     */
    public void tupleSet(Binding tuple, String index, Object value);

    /**
     * @param input1 a Binding or a constant
     * @param input2 a Binding or a constant
     * @return the result binding
     * @throws TerminatedBlockException if this block is terminated
     */
    public Binding add(Object input1, Object input2);

    /**
     * @param input1 a Binding or a constant
     * @param input2 a Binding or a constant
     * @return the result binding
     * @throws TerminatedBlockException if this block is terminated
     */
    public Binding sub(Object input1, Object input2);

    /**
     * @param input1 a Binding or a constant
     * @param input2 a Binding or a constant
     * @return the result binding
     * @throws TerminatedBlockException if this block is terminated
     */
    public Binding mul(Object input1, Object input2);

    /**
     * @param input1 a Binding or a constant
     * @param input2 a Binding or a constant
     * @return the result binding
     * @throws TerminatedBlockException if this block is terminated
     */
    public Binding div(Object input1, Object input2);

    /**
     * @param input1 a Binding or a constant
     * @param input2 a Binding or a constant
     * @return the result binding
     * @throws TerminatedBlockException if this block is terminated
     */
    public Binding rem(Object input1, Object input2);

    /**
     * @param input1 a Binding or a constant
     * @param input2 a Binding or a constant
     * @return the result binding
     * @throws TerminatedBlockException if this block is terminated
     */
    public Binding shl(Object input1, Object input2);

    /**

     * @param input1 a Binding or a constant
     * @param input2 a Binding or a constant
     * @return the result binding
     * @throws TerminatedBlockException if this block is terminated
     */
    public Binding shr(Object input1, Object input2);

    /**
     * @param input1 a Binding or a constant
     * @param input2 a Binding or a constant
     * @return the result binding
     * @throws TerminatedBlockException if this block is terminated
     */
    public Binding ushr(Object input1, Object input2);

    /**
     * @param input1 a Binding or a constant
     * @param input2 a Binding or a constant
     * @return the result binding
     * @throws TerminatedBlockException if this block is terminated
     */
    public Binding and(Object input1, Object input2);

    /**
     * @param input1 a Binding or a constant
     * @param input2 a Binding or a constant
     * @return the result binding
     * @throws TerminatedBlockException if this block is terminated
     */
    public Binding or(Object input1, Object input2);

    /**
     * @param input1 a Binding or a constant
     * @param input2 a Binding or a constant
     * @return the result binding
     * @throws TerminatedBlockException if this block is terminated
     */
    public Binding xor(Object input1, Object input2);

    /**
     * @param input1 a Binding or a constant
     * @param input2 a Binding or a constant
     * @return the result binding
     * @throws TerminatedBlockException if this block is terminated
     */
    public Binding eq(Object input1, Object input2);

    /**
     * @param input1 a Binding or a constant
     * @param input2 a Binding or a constant
     * @return the result binding
     * @throws TerminatedBlockException if this block is terminated
     */
    public Binding ne(Object input1, Object input2);

    /**
     * @param input1 a Binding or a constant
     * @param input2 a Binding or a constant
     * @return the result binding
     * @throws TerminatedBlockException if this block is terminated
     */
    public Binding lt(Object input1, Object input2);

    /**
     * @param input1 a Binding or a constant
     * @param input2 a Binding or a constant
     * @return the result binding
     * @throws TerminatedBlockException if this block is terminated
     */
    public Binding ge(Object input1, Object input2);

    /**
     * @param input1 a Binding or a constant
     * @param input2 a Binding or a constant
     * @return the result binding
     * @throws TerminatedBlockException if this block is terminated
     */
    public Binding gt(Object input1, Object input2);

    /**
     * @param input1 a Binding or a constant
     * @param input2 a Binding or a constant
     * @return the result binding
     * @throws TerminatedBlockException if this block is terminated
     */
    public Binding le(Object input1, Object input2);

    /**
     * @param input a Binding or a constant
     * @return the result binding
     * @throws TerminatedBlockException if this block is terminated
     */
    public Binding neg(Object input);

    /**
     * @param input a Binding or a constant
     * @return the result binding
     * @throws TerminatedBlockException if this block is terminated
     */
    public Binding com(Object input);

    /**
     * @param input a Binding or a constant
     * @return the result binding
     * @throws TerminatedBlockException if this block is terminated
     */
    public Binding not(Object input);
}
