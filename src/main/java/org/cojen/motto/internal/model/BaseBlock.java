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
import java.util.function.Supplier;

import org.cojen.motto.internal.util.SimpleSet;

import org.cojen.motto.model.Action;
import org.cojen.motto.model.ArrayType;
import org.cojen.motto.model.Binding;
import org.cojen.motto.model.Block;
import org.cojen.motto.model.CallableItem;
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
    private static final ScopedValue<Supplier<BaseBlock>> CONTEXT = ScopedValue.newInstance();

    /**
     * Call to allow newBlockFromScope to work within the given scope.
     */
    static <R> R inScope(Supplier<BaseBlock> factory, ScopedValue.CallableOp<R, Throwable> scope)
        throws Throwable
    {
        return ScopedValue.where(CONTEXT, factory).call(scope);
    }

    /**
     * @throws IllegalStateException if not called via inScope
     */
    public static BaseBlock newBlockFromScope() {
        try {
            return CONTEXT.get().get();
        } catch (NoSuchElementException e) {
            throw new IllegalStateException();
        }
    }

    /*
      Thread-safety notes:

      BaseBlock isn't designed to be acted upon by multiple threads, but to guard against a
      misbehaving macro, minimal synchronization is used to prevent data structure corruption.
      This only is required for methods that the macro has access to.

      This doesn't guard against observing apparent corruption, but this is temporary. When
      it's guaranteed that macros cannot modify the BaseBlock (or anything it depends on), then
      the BaseBlock can be safely used by the one thread which is allowed to interact with it.

     */

    private final BlockContext mContext;
    private final int mHashCode;

    private int mPosition;

    private BaseAction mFirstAction;
    private BaseAction mLastAction;

    // Can be null, a BaseBlock, or a SimpleSet<BaseBlock>.
    private Object mPredecessors;

    // Postorder ordinal, starting with 0.
    private int mOrdinal;

    // Immediate dominator.
    private BaseBlock mIdom;

    public BaseBlock(BlockContext context) {
        mContext = context;

        // This is a bit faster than the default identity hashcode.
        mHashCode = ThreadLocalRandom.current().nextInt();
    }

    @Override
    public int hashCode() {
        return mHashCode;
    }

    @Override
    public void forEach(Consumer<? super Action> consumer) {
        baseForEach(consumer);
    }

    void baseForEach(Consumer<? super BaseAction> consumer) {
        BaseAction action = mFirstAction;

        while (action != null) {
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

    Iterator<BaseAction> baseIterator() {
        BaseAction action = mFirstAction;

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
                    mAction = flow.next;
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
    public BaseBlock merge() {
        return merge(this, null, new HashSet<>());
    }

    private BaseBlock merge(BaseBlock block, BaseBlock common, HashSet<BaseBlock> visited) {
        // Note: The block being processed can match the common block when following a path
        // which was already merged.
        while (block != common && visited.add(block)) {
            BaseAction last = block.mLastAction;

            if ((last == null || !(last instanceof TerminalAction)) && !block.isReturnBlock()) {
                if (common == null) {
                    // Use the context of the block that initiated the merge, not the block
                    // participating in the merge.
                    common = new BaseBlock(this.mContext);
                }
                block.jump(common);
                break;
            }

            if (last instanceof BaseJumpAction jump) {
                block = jump.destination(); // tail call
            } else if (last instanceof BaseBranchAction branch) {
                common = merge(branch.whenTrue(), common, visited);
                block = branch.whenFalse(); // tail call
            } else {
                break;
            }
        }

        return common;
    }

    @Override
    public BaseBinding.Anonymous var(Type type) {
        return var((BaseType) type);
    }

    public BaseBinding.Anonymous var(BaseType type) {
        return new BaseBinding.Anonymous(mContext, type);
    }

    @Override
    public BaseBinding.Local var(Type type, String name) {
        return var((BaseType) type, name);
    }

    public BaseBinding.Local var(BaseType type, String name) {
        return name == null ? var(type) : new BaseBinding.Named(mContext, type, name);
    }

    @Override
    public void append(Action action) {
        append((BaseAction) action);
    }

    public void append(BaseAction action) {
        addAction(action.detach());
    }

    @Override
    public void copy(Binding target, Object source) {
        copy((BaseBinding) target, toBinding(source));
    }

    public void copy(BaseBinding target, BaseBinding source) {
        if (target != source && !target.type().isVoid()) {
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
    public BaseBinding callDirect(CallableItem callable, Object... inputs) {
        return callDirect((BaseCallableItem) callable, inputs);
    }

    public BaseBinding callDirect(BaseCallableItem callable, Object[] inputs) {
        // FIXME: verify target and input types

        BaseBinding target = targetVar(callable);

        addAction(new BaseCallAction.Direct(mPosition, callable, target, toBindings(inputs)));

        return target;
    }

    @Override
    public BaseBinding callNew(CallableItem callable, Object... inputs) {
        return callNew((BaseCallableItem) callable, inputs);
    }

    public BaseBinding callNew(BaseCallableItem callable, Object... inputs) {
        return callNew(var(callable.signature().inputType().fieldType(0)), callable, inputs);
    }

    public BaseBinding callNew(BaseBinding target, BaseCallableItem callable, Object... inputs) {
        // FIXME: verify target, "this" input, and other input types

        addAction(new BaseCallAction.New(mPosition, callable, target, toBindings(inputs)));

        return target;
    }

    @Override
    public BaseBinding callVirtual(CallableItem callable, Object... inputs) {
        return callVirtual((BaseCallableItem) callable, inputs);
    }

    public BaseBinding callVirtual(BaseCallableItem callable, Object... inputs) {
        // FIXME: verify "this" input, and other input types

        BaseBinding target = targetVar(callable);

        addAction(new BaseCallAction.Virtual(mPosition, callable, target, toBindings(inputs)));

        return target;
    }

    private BaseBinding targetVar(BaseCallableItem callable) {
        BaseType type = callable.signature().outputType();
        return type == BaseVoidType.THE ? BaseBinding.Void.THE : var(type);
    }

    @Override
    public void yield(Object result) {
        this.yield(toBinding(result));
    }

    public void yield(BaseBinding result) {
        BaseType conflict = mContext.assignResultAndJump(this, result);

        if (conflict != null) {
            String message = BaseInferredType.errorMessage("yield", result.type(), conflict);
            // FIXME: report an error
            throw new IllegalStateException(message);
        }
    }

    @Override
    public void jump(Block destination) {
        jump((BaseBlock) destination);
    }

    public void jump(BaseBlock destination) {
        addJumpAction(new BaseJumpAction(mPosition, destination.skipAhead()));
    }

    @SuppressWarnings("unchecked")
    private synchronized void addJumpAction(BaseJumpAction jump) {
        if (isTerminated()) {
            throw new TerminatedBlockException();
        }

        if (mFirstAction == null) {
            // If possible, update the predecessors to jump ahead to the given destination,
            // skipping this simple block. The jump is still added to this block, in case it's
            // reached later.

            // FIXME: Cannot skip if the exception handlers don't match.

            // Note: The following code is deadlock prone because BaseBlock synchronization
            // order can be inconsistent. I'm not too concerned with this the moment, because
            // synchronization is only intended to prevent corruption from misbehaving macros.
            // If the macro causes a deadlock, then that's treated as a macro bug.

            Object predecessors = mPredecessors;

            if (predecessors != null) {
                if (predecessors instanceof BaseBlock pred) {
                    pred.mLastAction.changeDestination(pred, this, jump.destination());
                } else {
                    for (BaseBlock pred : (SimpleSet<BaseBlock>) predecessors) {
                        pred.mLastAction.changeDestination(pred, this, jump.destination());
                    }
                }
            }
        }

        addAction(jump);
    }

    @Override
    public void branch(Binding condition, Block whenTrue, Block whenFalse) {
        branch((BaseBinding) condition, (BaseBlock) whenTrue, (BaseBlock) whenFalse);
    }

    public void branch(BaseBinding condition, BaseBlock whenTrue, BaseBlock whenFalse) {
        if (condition instanceof BaseBinding.Constant constant &&
            constant.value() instanceof Boolean b)
        {
            jump(b ? whenTrue : whenFalse);
            return;
        }

        whenTrue = whenTrue.skipAhead();
        whenFalse = whenFalse.skipAhead();

        if (condition instanceof BaseBinding.Branch b && b.mergeBlock() == this) {
            b.skip(whenTrue, whenFalse);
            return;
        }

        addAction(new BaseBranchAction(mPosition, condition, whenTrue, whenFalse));
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
    public BaseBinding arrayNew(ArrayType type, Object... dims) {
        return arrayNew((BaseArrayType) type, dims);
    }

    public BaseBinding arrayNew(BaseArrayType type, Object... dims) {
        // FIXME: verify target and dimension types

        BaseBinding target = var(type);

        addAction(new BaseArrayAction.New(mPosition, type, target, toBindings(dims)));

        return target;
    }

    @Override
    public BaseBinding arrayGet(Binding array, Object index) {
        return arrayGet((BaseBinding) array, index);
    }

    public BaseBinding arrayGet(BaseBinding array, Object index) {
        BaseBinding target = var(array.type().arrayElementType());

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
    public BaseBinding tupleNew(TupleType type, Object... inputs) {
        return tupleNew((BaseTupleType) type, inputs);
    }

    public BaseBinding tupleNew(BaseTupleType type, Object... inputs) {
        // FIXME: verify target and input types

        BaseBinding target = var(type);

        addAction(new BaseTupleAction.New(mPosition, type, target, toBindings(inputs)));

        return target;
    }

    @Override
    public BaseBinding tupleGet(Binding tuple, Binding index) {
        // FIXME: Check if binding is an int/long/String. Check if constant. Must define
        // methods in the tuple class for doing runtime lookup.
        throw null;
    }

    @Override
    public BaseBinding tupleGet(Binding tuple, int index) {
        // FIXME: throw IndexOutOfBoundsException if index is wrong.
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
    public BaseBinding add(Object input1, Object input2) {
        return mathOp("add", input1, input2);
    }

    @Override
    public BaseBinding sub(Object input1, Object input2) {
        return mathOp("sub", input1, input2);
    }

    @Override
    public BaseBinding mul(Object input1, Object input2) {
        return mathOp("mul", input1, input2);
    }

    @Override
    public BaseBinding div(Object input1, Object input2) {
        return mathOp("div", input1, input2);
    }

    @Override
    public BaseBinding rem(Object input1, Object input2) {
        return mathOp("rem", input1, input2);
    }

    @Override
    public BaseBinding shl(Object input1, Object input2) {
        return mathOp("shl", input1, input2);
    }

    @Override
    public BaseBinding shr(Object input1, Object input2) {
        return mathOp("shr", input1, input2);
    }

    @Override
    public BaseBinding ushr(Object input1, Object input2) {
        return mathOp("ushr", input1, input2);
    }

    @Override
    public BaseBinding and(Object input1, Object input2) {
        return mathOp("and", input1, input2);
    }

    @Override
    public BaseBinding or(Object input1, Object input2) {
        return mathOp("or", input1, input2);
    }

    @Override
    public BaseBinding xor(Object input1, Object input2) {
        return mathOp("xor", input1, input2);
    }

    @Override
    public BaseBinding eq(Object input1, Object input2) {
        return mathOp("eq", input1, input2);
    }

    @Override
    public BaseBinding ne(Object input1, Object input2) {
        return mathOp("ne", input1, input2);
    }

    @Override
    public BaseBinding lt(Object input1, Object input2) {
        return mathOp("lt", input1, input2);
    }

    @Override
    public BaseBinding ge(Object input1, Object input2) {
        return mathOp("ge", input1, input2);
    }

    @Override
    public BaseBinding gt(Object input1, Object input2) {
        return mathOp("gt", input1, input2);
    }

    @Override
    public BaseBinding le(Object input1, Object input2) {
        return mathOp("le", input1, input2);
    }

    @Override
    public BaseBinding neg(Object input) {
        return mathOp("neg", input);
    }

    @Override
    public BaseBinding com(Object input) {
        return mathOp("com", input);
    }

    @Override
    public BaseBinding not(Object input) {
        return mathOp("not", input);
    }

    private BaseBinding mathOp(String op, Object input1, Object input2) {
        // FIXME: Support BigDecimal and BigInteger.

        BaseBinding in1 = toBinding(input1);
        BaseBinding in2 = toBinding(input2);

        var mathType = BaseType.from(Math.class);
        var inputType = BaseTupleType.from(in1.type(), in1.type());

        Map<BaseCallSignature, Set<CallableItem>> methods =
            mathType.findMethod(op, inputType, m -> m.isStatic() && m.isAccessibleVia(null));

        CallableItem callable = oneCallable(methods);

        // FIXME: constant folding

        return callDirect(callable, in1, in2);
    }

    private BaseBinding mathOp(String op, Object input) {
        // FIXME: Support BigDecimal and BigInteger.

        BaseBinding in = toBinding(input);

        var mathType = BaseType.from(Math.class);
        var inputType = BaseTupleType.from(in.type());

        Map<BaseCallSignature, Set<CallableItem>> methods =
            mathType.findMethod(op, inputType, m -> m.isStatic() && m.isAccessibleVia(null));

        CallableItem callable = oneCallable(methods);

        // FIXME: constant folding

        return callDirect(callable, in);
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

    /**
     * Returns the non-null context the Block belongs to.
     */
    public BlockContext context() {
        return mContext;
    }

    /**
     * Returns true if this block is the result block of its context, and the result is
     * excepted to be returned from a callable.
     */
    public boolean isReturnBlock() {
        return mContext.isReturnBlock(this);
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

    int firstSourcePosition() {
        BaseAction first = mFirstAction;
        return first == null ? mPosition : first.position();
    }

    private synchronized void addAction(BaseAction action) {
        BaseAction last = mLastAction;

        if (last == null) {
            mFirstAction = action;
        } else if (last instanceof FlowAction flow) {
            flow.next = action;
        } else {
            throw new TerminatedBlockException();
        }

        action.addPredecessors(this);

        mLastAction = action;
    }

    /**
     * If this is a simple block which jumps to another, return the final destination.
     */
    BaseBlock skipAhead() {
        BaseBlock block = this;
        while (block.mFirstAction instanceof BaseJumpAction jump) {
            block = jump.destination();
        }
        return block;
    }

    /**
     * Move the next action of the given flow action (of this block) to another block. The next
     * action of the given flow action then becomes {@link UnreachableAction unreachable}.
     *
     * @throws TerminatedBlockException if the destination block is terminated
     */
    void transfer(FlowAction action, BaseBlock destination) {
        BaseAction next = action.next;

        if (next != null) {
            if (next instanceof BaseJumpAction jump) {
                destination.addJumpAction(jump);
            } else {
                destination.addAction(next);
            }

            next.addPredecessors(destination);
            next.removePredecessors(this);
        }

        action.next = UnreachableAction.THE;
    }

    BaseAction firstAction() {
        return mFirstAction;
    }

    BaseAction lastAction() {
        return mLastAction;
    }

    @SuppressWarnings("unchecked")
    synchronized void addPredecessor(BaseBlock predecessor) {
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

    synchronized void removePredecessor(BaseBlock predecessor) {
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
     * has been called from the entry block.
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
        // From: "A Simple, Fast Dominance Algorithm" by Keith D. Cooper, Timothy J. Harvey,
        // and Ken Kennedy.

        // "initialize the dominators array"
        var postorder = new BaseBlock[reset(this, 0)];
        postorder(postorder, 0);

        // "doms[start_node] ← start_node"
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
                    // "new_idom ← first (processed) predecessor of b (pick one)"
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
}
