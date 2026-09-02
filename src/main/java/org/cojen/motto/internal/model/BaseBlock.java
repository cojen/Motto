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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import java.util.concurrent.ThreadLocalRandom;

import java.util.function.Consumer;

import org.cojen.motto.internal.util.SimpleSet;

import org.cojen.motto.model.Action;
import org.cojen.motto.model.ArrayType;
import org.cojen.motto.model.Binding;
import org.cojen.motto.model.Block;
import org.cojen.motto.model.CallableItem;
import org.cojen.motto.model.SegmentArgument;
import org.cojen.motto.model.TerminalAction;
import org.cojen.motto.model.TerminatedBlockException;
import org.cojen.motto.model.Type;
import org.cojen.motto.model.TupleType;

import org.cojen.motto.runtime.Math;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class BaseBlock implements Block {
    // This is a bit faster than the default identity hashcode.
    private final int mHashCode = ThreadLocalRandom.current().nextInt();

    private int mPosition;

    private BaseAction mFirstAction;
    private BaseAction mLastAction;

    private boolean mReduced;

    // Can be null, a BaseBlock, or a SimpleSet<BaseBlock>.
    private Object mPredecessors;

    // Postorder ordinal, starting with 0.
    private int mOrdinal;

    // Immediate dominator.
    private BaseBlock mIdom;

    public BaseBlock() {
    }

    @Override
    public int hashCode() {
        return mHashCode;
    }

    // FIXME: Need stricter checks that objects passed into these methods belong to the same
    // callable or class being made. For blocks, compare the context instance, which should be
    // unique for each macro call.

    @Override
    public void forEach(Consumer<? super Action> consumer) {
        baseForEach(true, consumer);
    }

    void baseForEach(Consumer<? super BaseAction> consumer) {
        baseForEach(true, consumer);
    }

    /**
     * @param skipAny when true, skip any simple jumps (the block contains one action which
     * jumps to another block)
     */
    void baseForEach(boolean skipAny, Consumer<? super BaseAction> consumer) {
        BaseAction action = mFirstAction;

        while (true) {
            if (skipAny) {
                action = skipSimpleJumps(action);
                if (action == null) {
                    break;
                }
            }

            consumer.accept(action);

            if (action instanceof FlowAction flow) {
                action = flow.next;
            } else {
                break;
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Iterator iterator() {
        return baseIterator();
    }

    private static BaseAction skipSimpleJumps(BaseAction action) {
        while (action instanceof BaseJumpAction jump) {
            BaseBlock destination = jump.destination();
            if (destination.isReachedOnce()) {
                // Flow into the next block as if it was inlined.
                action = destination.mFirstAction;
            } else {
                break;
            }
        }
        return action;
    }

    Iterator<BaseAction> baseIterator() {
        BaseAction action = skipSimpleJumps(mFirstAction);

        return new Iterator<>() {
            BaseAction mAction = action;

            @Override
            public boolean hasNext() {
                return mAction != null;
            }

            @Override
            public BaseAction next() {
                BaseAction next = mAction;
                if (next == null) {
                    throw new NoSuchElementException();
                }
                if (next instanceof FlowAction flow) {
                    mAction = skipSimpleJumps(flow.next);
                } else {
                    mAction = null;
                }
                return next;
            }
        };
    }

    @Override
    public boolean isTerminated() {
        return mLastAction instanceof TerminalAction;
    }

    @Override
    public boolean isFullyTerminated() {
        return isFullyTerminated(this, new HashSet<>());
    }

    private static boolean isFullyTerminated(BaseBlock block, HashSet<BaseBlock> visited) {
        while (true) {
            if (visited.contains(block)) {
                return true;
            }

            visited.add(block);

            BaseAction last = block.mLastAction;

            if (last == null || !(last instanceof TerminalAction)) {
                return false;
            }

            if (last instanceof BaseJumpAction jump) {
                block = jump.destination(); // tail call
            } else if (last instanceof BaseBranchAction branch) {
                if (!isFullyTerminated(branch.whenTrue(), visited)) {
                    return false;
                }
                block = branch.whenFalse(); // tail call
            } else {
                return true;
            }
        }
    }

    @Override
    public BaseBinding.Anonymous var(Type type) {
        return var((BaseType) type);
    }

    public BaseBinding.Anonymous var(BaseType type) {
        return new BaseBinding.Anonymous(type);
    }

    @Override
    public BaseBinding.Local var(Type type, String name) {
        return var((BaseType) type, name);
    }

    public BaseBinding.Local var(BaseType type, String name) {
        return name == null ? var(type) : BaseBinding.Named.from(type, name);
    }

    public void declare(Binding binding) {
        declare((BaseBinding) binding);
    }

    public void declare(BaseBinding binding) {
        addAction(new BaseDeclarationAction(mPosition, binding));
    }

    @Override
    public void yield(Object result) {
        this.yield(toBinding(result));
    }

    public void yield(BaseBinding result) {
        BaseAction action;
        BaseAction last = mLastAction;

        if (last == null) {
            action = new BaseYieldAction(mPosition, null, result);
            mFirstAction = action;
        } else if (last instanceof FlowAction flow) {
            action = new BaseYieldAction(mPosition, flow, result);
            flow.next = action;
        } else if (last instanceof BaseYieldAction yield) {
            FlowAction prev = yield.previous();
            if (prev == null) {
                action = new BaseYieldAction(mPosition, null, result);
                mFirstAction = action;
            } else {
                action = new BaseYieldAction(mPosition, prev, result);
                prev.next = action;
            }
        } else {
            throw new TerminatedBlockException();
        }

        mLastAction = action;
    }

    @Override
    public BaseBinding result() {
        return mLastAction instanceof BaseYieldAction yield ? yield.result() : BaseBinding.Void.THE;
    }

    @Override
    public void copy(Binding target, Object source) {
        copy((BaseBinding) target, toBinding(source));
    }

    public void copy(BaseBinding target, BaseBinding source) {
        if (target != source && target != BaseBinding.Void.THE) {
            addAction(new BaseCopyAction(mPosition, target, source));
        }
    }

    @Override
    public void cast(Binding target, Object source) {
        cast((BaseBinding) target, toBinding(source));
    }

    public void cast(BaseBinding target, BaseBinding source) {
        if (target != source) {
            // FIXME: If cast isn't required (same type or can be widened), do a copy instead.

            // FIXME
            throw null;
        }
    }

    @Override
    public void convert(Binding target, Object source) {
        convert((BaseBinding) target, toBinding(source));
    }

    public void convert(BaseBinding target, BaseBinding source) {
        if (target != source) {
            // FIXME: If convert isn't required (same type or can be widened), do a copy instead.

            // FIXME
            throw null;
        }
    }

    @Override
    public BaseBinding callDirect(Binding target, CallableItem callable, Object... inputs) {
        return callDirect(target, callable, inputs, (SegmentArgument[]) null);
    }

    @Override
    public BaseBinding callDirect(Binding target, CallableItem callable, Object[] inputs,
                                  SegmentArgument... segments)
    {
        return callDirect((BaseBinding) target, (BaseCallableItem) callable, inputs, segments);
    }

    public BaseBinding callDirect(BaseBinding target, BaseCallableItem callable,
                                  Object[] inputs, SegmentArgument... segments)
    {
        // FIXME: verify target and input types

        if (target == null) {
            target = targetVar(callable);
        }

        BaseSegmentArgument[] extArray = null;

        if (segments != null && segments.length != 0) {
            if (segments instanceof BaseSegmentArgument[] a) {
                extArray = a;
            } else {
                extArray = new BaseSegmentArgument[segments.length];
                for (int i=0; i<segments.length; i++) {
                    extArray[i] = (BaseSegmentArgument) segments[i];
                }
            }
        }

        addAction(new BaseCallAction.Direct
                  (mPosition, callable, target, toBindings(inputs), extArray));

        return target;
    }

    @Override
    public BaseBinding callNew(Binding target, CallableItem callable, Object... inputs) {
        return callNew((BaseBinding) target, (BaseCallableItem) callable, inputs);
    }

    public BaseBinding callNew(BaseBinding target, BaseCallableItem callable,
                               Object... inputs)
    {
        // FIXME: verify target, "this" input, and other input types

        if (target == null) {
            target = var(callable.signature().inputType().fieldType(0));
        }

        addAction(new BaseCallAction.New(mPosition, callable, target, toBindings(inputs)));

        return target;
    }

    @Override
    public BaseBinding callVirtual(Binding target, CallableItem callable, Object... inputs) {
        return callVirtual((BaseBinding) target, (BaseCallableItem) callable, inputs);
    }

    public BaseBinding callVirtual(BaseBinding target, BaseCallableItem callable,
                                   Object... inputs)
    {
        // FIXME: verify target, "this" input, and other input types

        if (target == null) {
            target = targetVar(callable);
        }

        addAction(new BaseCallAction.Virtual(mPosition, callable, target, toBindings(inputs)));

        return target;
    }

    private BaseBinding targetVar(BaseCallableItem callable) {
        BaseType type = callable.signature().outputType();
        return type == BaseVoidType.THE ? BaseBinding.Void.THE : var(type);
    }

    @Override
    public void jump(Block destination) {
        jump((BaseBlock) destination);
    }

    public void jump(BaseBlock destination) {
        addAction(new BaseJumpAction(this, mPosition, destination));
    }

    @Override
    public void branch(Binding condition, Block whenTrue, Block whenFalse) {
        branch((BaseBinding) condition, (BaseBlock) whenTrue, (BaseBlock) whenFalse);
    }

    public void branch(BaseBinding condition, BaseBlock whenTrue, BaseBlock whenFalse) {
        if (condition instanceof BaseBinding.Constant constant &&
            constant.value() instanceof Boolean b)
        {
            addAction(new BaseJumpAction(this, mPosition, b ? whenTrue : whenFalse));
        } else {
            addAction(new BaseBranchAction(this, mPosition, condition, whenTrue, whenFalse));
        }
    }

    @Override
    public void throw_(Object exception) {
        throw_(toBinding(exception));
    }

    public void throw_(BaseBinding exception) {
        addAction(new BaseThrowAction(mPosition, exception));
    }

    @Override
    public BaseBinding catch_(Type exceptionType, String varName, Block handler) {
        return catch_((BaseType) exceptionType, varName, (BaseBlock) handler);
    }

    public BaseBinding catch_(BaseType exceptionType, String varName, BaseBlock handler) {
        // FIXME: catch; note that exception handling affects reachability
        throw null;
    }

    @Override
    public BaseBinding arrayNew(Binding target, ArrayType type, Object... dims) {
        return arrayNew((BaseBinding) target, (BaseArrayType) type, dims);
    }

    public BaseBinding arrayNew(BaseBinding target, BaseArrayType type, Object... dims) {
        // FIXME: verify target and dimension types

        if (target == null) {
            target = var(type);
        }

        addAction(new BaseArrayAction.New(mPosition, type, target, toBindings(dims)));

        return target;
    }

    @Override
    public BaseBinding arrayGet(Binding target, Binding array, Object index) {
        return arrayGet((BaseBinding) target, (BaseBinding) array, index);
    }

    public BaseBinding arrayGet(BaseBinding target, BaseBinding array, Object index) {
        if (target == null) {
            target = var(array.type().arrayElementType());
        }

        addAction(new BaseArrayAction.Get(mPosition, array, target, toBinding(index)));

        return target;
    }

    @Override
    public void arraySet(Binding array, Object index, Object value) {
        arraySet((BaseBinding) array, index, value);
    }

    public void arraySet(BaseBinding array, Object index, Object value) {
        addAction(new BaseArrayAction.Set(mPosition, array, toBinding(index), toBinding(value)));
    }

    @Override
    public BaseBinding tupleNew(Binding target, TupleType type, Object... inputs) {
        return tupleNew((BaseBinding) target, (BaseTupleType) type, inputs);
    }

    public BaseBinding tupleNew(BaseBinding target, BaseTupleType type, Object... inputs) {
        // FIXME: verify target and input types

        if (target == null) {
            target = var(type);
        }

        addAction(new BaseTupleAction.New(mPosition, type, target, toBindings(inputs)));

        return target;
    }

    @Override
    public BaseBinding tupleGet(Binding target, Binding tuple, Binding index) {
        // FIXME: Check if binding is an int/long/String. Check if constant. Must define
        // methods in the tuple class for doing runtime lookup.
        throw null;
    }

    @Override
    public BaseBinding tupleGet(Binding target, Binding tuple, int index) {
        // FIXME: throw IndexOutOfBoundsException if index is wrong.
        throw null;
    }

    @Override
    public BaseBinding tupleGet(Binding target, Binding tuple, String label) {
        // FIXME: Find index; throw IllegalArgumentException if not found.
        throw null;
    }

    @Override
    public void tupleSet(Binding tuple, Binding index, Object value) {
        // FIXME: Check if binding is an int/long/String...
        throw null;
    }

    @Override
    public void tupleSet(Binding tuple, int index, Object value) {
        // FIXME: throw IndexOutOfBoundsException if index is wrong.
        throw null;
    }

    @Override
    public void tupleSet(Binding tuple, String index, Object value) {
        // FIXME: Find index; throw IllegalArgumentException if not found.
        throw null;
    }

    @Override
    public BaseBinding add(Binding target, Object input1, Object input2) {
        return mathOp("add", target, input1, input2);
    }

    @Override
    public BaseBinding sub(Binding target, Object input1, Object input2) {
        return mathOp("sub", target, input1, input2);
    }

    @Override
    public BaseBinding mul(Binding target, Object input1, Object input2) {
        return mathOp("mul", target, input1, input2);
    }

    @Override
    public BaseBinding div(Binding target, Object input1, Object input2) {
        return mathOp("div", target, input1, input2);
    }

    @Override
    public BaseBinding rem(Binding target, Object input1, Object input2) {
        return mathOp("rem", target, input1, input2);
    }

    @Override
    public BaseBinding shl(Binding target, Object input1, Object input2) {
        return mathOp("shl", target, input1, input2);
    }

    @Override
    public BaseBinding shr(Binding target, Object input1, Object input2) {
        return mathOp("shr", target, input1, input2);
    }

    @Override
    public BaseBinding ushr(Binding target, Object input1, Object input2) {
        return mathOp("ushr", target, input1, input2);
    }

    @Override
    public BaseBinding and(Binding target, Object input1, Object input2) {
        return mathOp("and", target, input1, input2);
    }

    @Override
    public BaseBinding or(Binding target, Object input1, Object input2) {
        return mathOp("or", target, input1, input2);
    }

    @Override
    public BaseBinding xor(Binding target, Object input1, Object input2) {
        return mathOp("xor", target, input1, input2);
    }

    @Override
    public BaseBinding eq(Binding target, Object input1, Object input2) {
        return mathOp("eq", target, input1, input2);
    }

    @Override
    public BaseBinding ne(Binding target, Object input1, Object input2) {
        return mathOp("ne", target, input1, input2);
    }

    @Override
    public BaseBinding lt(Binding target, Object input1, Object input2) {
        return mathOp("lt", target, input1, input2);
    }

    @Override
    public BaseBinding ge(Binding target, Object input1, Object input2) {
        return mathOp("ge", target, input1, input2);
    }

    @Override
    public BaseBinding gt(Binding target, Object input1, Object input2) {
        return mathOp("gt", target, input1, input2);
    }

    @Override
    public BaseBinding le(Binding target, Object input1, Object input2) {
        return mathOp("le", target, input1, input2);
    }

    @Override
    public BaseBinding neg(Binding target, Object input) {
        return mathOp("neg", target, input);
    }

    @Override
    public BaseBinding com(Binding target, Object input) {
        return mathOp("com", target, input);
    }

    @Override
    public BaseBinding not(Binding target, Object input) {
        return mathOp("not", target, input);
    }

    private BaseBinding mathOp(String op, Binding target, Object input1, Object input2) {
        // FIXME: Support BigDecimal and BigInteger.

        BaseBinding in1 = toBinding(input1);
        BaseBinding in2 = toBinding(input2);

        var mathType = BaseType.from(Math.class);
        var inputType = BaseTupleType.from(in1.type(), in1.type());

        Map<BaseCallSignature, Set<CallableItem>> methods =
            mathType.findMethod(op, inputType, m -> m.isStatic() && m.isAccessibleVia(null));

        CallableItem callable = oneCallable(methods);

        // FIXME: constant folding

        return callDirect(target, callable, in1, in2);
    }

    private BaseBinding mathOp(String op, Binding target, Object input) {
        // FIXME: Support BigDecimal and BigInteger.

        BaseBinding in = toBinding(input);

        var mathType = BaseType.from(Math.class);
        var inputType = BaseTupleType.from(in.type());

        Map<BaseCallSignature, Set<CallableItem>> methods =
            mathType.findMethod(op, inputType, m -> m.isStatic() && m.isAccessibleVia(null));

        CallableItem callable = oneCallable(methods);

        // FIXME: constant folding

        return callDirect(target, callable, in);
    }

    private static CallableItem oneCallable(Map<BaseCallSignature, Set<CallableItem>> methods) {
        if (methods.size() != 1) {
            // FIXME: use a better exception
            throw new IllegalStateException("unsupported type for operation");
        }

        Set<CallableItem> set = methods.values().iterator().next();

        if (set.size() != 1) {
            // FIXME: not expected; use a better exception
            throw new IllegalStateException("unsupported type for operation");
        }

        return set.iterator().next();
    }

    private BaseBinding toBinding(Object object) {
        if (object instanceof BaseBinding b) {
            return b;
        }

        if (object == null) {
            return BaseBinding.Null.THE;
        }

        return BaseBinding.Constant.from(BaseType.from(object.getClass()), object);
    }

    private BaseBinding[] toBindings(Object... objects) {
        if (objects instanceof BaseBinding[] b) {
            return b;
        }

        if (objects.length == 0) {
            return BaseBinding.EMPTY;
        }

        var bindings = new BaseBinding[objects.length];

        for (int i=0; i<objects.length; i++) {
            bindings[i] = toBinding(objects[i]);
        }

        return bindings;
    }

    public boolean isEmpty() {
        return mFirstAction == null;
    }

    /**
     * Define a source code position to be associated with newly appended actions.
     */
    public void sourcePosition(int position) {
        mPosition = position;
    }

    /**
     * Define a source code position to be associated with newly appended actions.
     */
    public void sourcePosition(int line, int column) {
        sourcePosition(BaseAction.encodePosition(line, column));
    }

    /**
     * Adds all the actions from the given block to this one. The other block should be
     * discarded.
     *
     * @throws TerminatedBlockException if this block is terminated
     */
    public void addAll(BaseBlock other) {
        BaseAction first = other.mFirstAction;
        if (first != null) {
            addAction(first);
            mLastAction = other.mLastAction;
        }
    }

    void addAction(BaseAction action) {
        BaseAction last = mLastAction;

        if (last == null) {
            mFirstAction = action;
        } else if (last instanceof FlowAction flow) {
            flow.next = action;
        } else if (last instanceof BaseYieldAction yield) {
            FlowAction prev = yield.previous();
            if (prev == null) {
                mFirstAction = action;
            } else {
                prev.next = action;
            }
        } else {
            throw new TerminatedBlockException();
        }

        mLastAction = action;
    }

    BaseAction firstAction() {
        return mFirstAction;
    }

    @SuppressWarnings("unchecked")
    void addPredecessor(BaseBlock predecessor) {
        Object predecessors = mPredecessors;
        if (predecessors == null) {
            mPredecessors = predecessor;
        } else {
            SimpleSet<BaseBlock> set;
            if (predecessors instanceof SimpleSet) {
                set = (SimpleSet<BaseBlock>) predecessors;
            } else {
                set = new SimpleSet<>();
                set.add((BaseBlock) predecessors);
                mPredecessors = set;
            }
            set.add(predecessor);
        }
    }

    void removePredecessor(BaseBlock predecessor) {
        Object predecessors = mPredecessors;
        if (predecessors == predecessor) {
            mPredecessors = null;
        } else if (predecessors instanceof SimpleSet set) {
            set.remove(predecessor);
            if (set.isEmpty()) {
                mPredecessors = null;
            }
        }
    }

    int numPredecessors() {
        Object predecessors = mPredecessors;
        if (predecessors == null) {
            return 0;
        } else if (predecessors instanceof SimpleSet set) {
            return set.size();
        } else {
            return 1;
        }
    }

    /**
     * @throws AssertionError if the number of predecessors is less than 1
     */
    boolean isReachedOnce() {
        int num = numPredecessors();
        if (num > 1) {
            return false;
        }
        if (num < 1) {
            throw new AssertionError("" + num);
        }
        return true;
    }

    /**
     * Returns the immediate dominator for this block, which is only valid after computeIdom
     * has been called from the start block.
     */
    BaseBlock idom() {
        return mIdom;
    }

    /**
     * Starting with this block, compute the immediate dominators of all successor blocks.
     * Later, if any changes are made to the block graph, then the immediate dominators are
     * likely invalid. This method must be called again to recompute them.
     */
    @SuppressWarnings("unchecked")
    void computeIdom() {
        var postorder = new BaseBlock[reset(this, 0)];
        postorder(postorder, 0);

        mIdom = this;

        boolean changed;

        do {
            changed = false;

            // "for all nodes b, in reverse postorder..."
            for (int i=postorder.length; --i>=0; ) {
                BaseBlock b = postorder[i];

                // "...(except start_node)"
                if (b == this) {
                    continue;
                }

                BaseBlock newIdom;

                Object preds = b.mPredecessors;

                if (preds instanceof SimpleSet set) {
                    // "new idom ← first (processed) predecessor of b (pick one)"
                    Iterator<BaseBlock> it = set.iterator();
                    do {
                        newIdom = it.next();
                    } while (newIdom.mIdom == null);

                    // "for all other predecessors, p, of b"
                    while (it.hasNext()) {
                        BaseBlock p = it.next();

                        // "if doms[p] already calculated"...
                        if (p.mIdom != null) {
                            // "intersect(p, new_idom)"

                            BaseBlock f1 = p;
                            BaseBlock f2 = newIdom;

                            while (f1.mOrdinal != f2.mOrdinal) {
                                while (f1.mOrdinal < f2.mOrdinal) {
                                    f1 = f1.mIdom;
                                }
                                while (f2.mOrdinal < f1.mOrdinal) {
                                    f2 = f2.mIdom;
                                }
                            }

                            newIdom = f1;
                        }
                    }
                } else {
                    // There's only one predecessor, it must be processed, and there's no other
                    // predecessors to intersect with.
                    newIdom = (BaseBlock) preds;
                    if (newIdom == null) {
                        throw new AssertionError();
                    }
                }

                if (b.mIdom != newIdom) {
                    b.mIdom = newIdom;
                    changed = true;
                }
            }
        } while (changed);
    }

    /**
     * Recursively sets mOrdinal to undefined, and sets mIdom to null.
     *
     * @param num initially 0
     * @return the total number of blocks visited
     */
    private static int reset(BaseBlock b, int num) {
        while (b.mOrdinal >= 0) {
            num++;
            b.mIdom = null;
            b.mOrdinal = -1; // indicates undefined

            BaseAction last = b.mLastAction;

            if (last instanceof BaseJumpAction jump) {
                b = jump.destination(); // tail call
            } else if (last instanceof BaseBranchAction branch) {
                num = branch.whenTrue().reset(b, num);
                b = branch.whenFalse(); // tail call
            } else {
                break;
            }
        }

        return num;
    }

    /**
     * Performs a postorder traversal, storing blocks in the given array and setting mOrdinal
     * to match the array index.
     *
     * @param index initially 0
     * @return the updated index
     */
    private int postorder(BaseBlock[] blocks, int index) {
        if (mOrdinal < 0) {
            mOrdinal = Integer.MAX_VALUE; // temporary; indicates block has been visited

            BaseAction last = mLastAction;

            if (last instanceof BaseJumpAction jump) {
                index = jump.destination().postorder(blocks, index);
            } else if (last instanceof BaseBranchAction branch) {
                index = branch.whenTrue().postorder(blocks, index);
                index = branch.whenFalse().postorder(blocks, index);
            }

            mOrdinal = index;
            blocks[index++] = this;
        }

        return index;
    }

    /**
     * Reduces this block and detects blocks which are only reached once. When iterating over
     * the actions, simple jumps are skipped for blocks which are reached once.
     *
     * @return false if the block isn't fully terminated
     */
    boolean finish() {
        return reduce();
    }

    /**
     * Reduces this block by attempting to shorten jump/branch paths.
     *
     * @return false if the block isn't fully terminated
     */
    boolean reduce() {
        if (mReduced) {
            return true;
        }

        if (!isTerminated()) {
            return false;
        }

        // Mark early to handle loop paths.
        mReduced = true;

        BaseAction last = mLastAction;

        // FIXME: Cannot use a direct destination when the exception handlers don't match.

        if (last instanceof BaseJumpAction jump) {
            BaseBlock destination = jump.destination();

            if (!destination.reduce()) {
                return false;
            }

            while (true) {
                if (destination.mFirstAction instanceof BaseJumpAction jump2) {
                    // Use a more direct destination.
                    jump.setDestination(this, destination = jump2.destination());
                } else {
                    break;
                }
            }
        } else if (last instanceof BaseBranchAction branch) {
            BaseBlock whenTrue = branch.whenTrue();

            if (!whenTrue.reduce()) {
                return false;
            }

            while (true) {
                if (whenTrue.mFirstAction instanceof BaseJumpAction jump) {
                    // Use a more direct destination.
                    branch.setWhenTrue(this, whenTrue = jump.destination());
                } else if (whenTrue.mFirstAction instanceof BaseBranchAction branch2 &&
                           sameCondition(branch, branch2))
                {
                    // Use a more direct destination.
                    branch.setWhenTrue(this, whenTrue = branch2.whenTrue());
                } else {
                    break;
                }
            }

            BaseBlock whenFalse = branch.whenFalse();

            if (!whenFalse.reduce()) {
                return false;
            }

            while (true) {
                if (whenFalse.mFirstAction instanceof BaseJumpAction jump) {
                    // Use a more direct destination.
                    branch.setWhenFalse(this, whenFalse = jump.destination());
                } else if (whenFalse.mFirstAction instanceof BaseBranchAction branch2 &&
                           sameCondition(branch, branch2))
                {
                    // Use a more direct destination.
                    branch.setWhenFalse(this, whenFalse = branch2.whenFalse());
                } else {
                    break;
                }
            }
        }

        return true;
    }

    private static boolean sameCondition(BaseBranchAction a, BaseBranchAction b) {
        return sameCondition(a.condition(), b.condition());
    }

    private static boolean sameCondition(BaseBinding a, BaseBinding b) {
        return a == b && a.isStable();
    }
}
