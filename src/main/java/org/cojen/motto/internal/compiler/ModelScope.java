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

package org.cojen.motto.internal.compiler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.cojen.motto.internal.model.BaseBinding;
import org.cojen.motto.internal.model.BaseBlock;
import org.cojen.motto.internal.model.BaseCallableItem;
import org.cojen.motto.internal.model.BaseCallSignature;
import org.cojen.motto.internal.model.BaseInferredType;
import org.cojen.motto.internal.model.BaseItem;
import org.cojen.motto.internal.model.BaseScopeItem;
import org.cojen.motto.internal.model.BaseTupleType;
import org.cojen.motto.internal.model.BaseType;
import org.cojen.motto.internal.model.BaseUnspecifiedType;
import org.cojen.motto.internal.model.BaseVoidType;
import org.cojen.motto.internal.model.NewClass;
import org.cojen.motto.internal.model.NewLocalClass;
import org.cojen.motto.internal.model.ResultContext;

import org.cojen.motto.internal.parser.ClassDefinitionStatement;
import org.cojen.motto.internal.parser.ConstructorDefinitionStatement;
import org.cojen.motto.internal.parser.DeclarationStatement;
import org.cojen.motto.internal.parser.Element;
import org.cojen.motto.internal.parser.LabeledStatement;
import org.cojen.motto.internal.parser.MethodDefinitionStatement;
import org.cojen.motto.internal.parser.ReturnStatement;
import org.cojen.motto.internal.parser.Statement;
import org.cojen.motto.internal.parser.Token;
import org.cojen.motto.internal.parser.YieldStatement;

import static org.cojen.motto.internal.model.Modifiers.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
abstract sealed class ModelScope {
    final ModelGenerator mModGen;
    final ModelScope mParent;
    final BaseItem mItem;

    private static final class LabelTarget {
        LabeledStatement statement;
        final BaseBlock block;

        LabelTarget(LabeledStatement statement, BaseBlock block) {
            this.statement = statement;
            this.block = block;
        }

        void reached() {
            statement = null;
        }
    }

    ModelScope(ModelGenerator modGen, ModelScope parent, BaseItem item) {
        mModGen = modGen;
        mParent = parent;
        mItem = item;
    }

    ModelScope parent() {
        return mParent;
    }

    /**
     * Returns the nearest callable parent.
     *
     * @throws IllegalStateException if no callable parent is found
     */
    Callable callableParent() {
        ModelScope parent = mParent;
        do {
            if (parent instanceof Callable c) {
                return c;
            }
        } while ((parent = parent.mParent) != null);

        throw new IllegalStateException();
    }

    BaseItem item() {
        return mItem;
    }

    CompilationEnv env() {
        return mModGen.env();
    }

    /**
     * @throws UnsupportedOperationException if not supported by this scope
     */
    ResultContext context() {
        throw new UnsupportedOperationException();
    }

    /**
     * Try to add a declared field or local variable. If the type is unspecified, then
     * tryFindLocalVariable returns null.
     *
     * @return false if an error was reported
     */
    abstract boolean addDeclaration(DeclarationStatement ds);

    /**
     * Replace a local variable which had an unspecified type.
     *
     * @return null if the variable doesn't exist
     */
    BaseBinding.Local tryReplaceLocalDeclaration(BaseType type, String name) {
        return null;
    }

    /**
     * Returns null if no method was added and an error was reported.
     */
    abstract BaseCallableItem addConstructor(ConstructorDefinitionStatement st);

    /**
     * Returns null if no method was added and an error was reported.
     */
    abstract BaseCallableItem addMethod(MethodDefinitionStatement st);

    /**
     * Adds a local inner class as defined by the given statement, and also recursively adds
     * all path-accessible inner classes within it. As a side-effect, the statement `clazz`
     * field will refer to the NewLocalClass object.
     *
     * Returns null if no class was added and an error was reported.
     */
    NewLocalClass addLocalInnerClass(ClassDefinitionStatement st) {
        CompilationEnv env = env();
        int modifierBits = st.modifierBits(env);

        if ((modifierBits & STATIC) != 0) {
            env.error(st, "local inner class cannot be static");
        }

        modifierBits = (modifierBits & ~(PUBLIC | PROTECTED | STATIC)) | PRIVATE;

        NewLocalClass local;
        try {
            local = mItem.tryAddLocalInnerClass(modifierBits, st.name.text);
        } catch (UnsupportedOperationException e) {
            // Not expected.
            env.error(st, "inner classes not supported in this scope");
            return null;
        }

        if (local == null) {
            env.error(st, "duplicate inner class definition");
            return null;
        }

        st.clazz = local;
        st.resolveClass(env);

        if (st.code != null) {
            for (Statement sub : st.code.items) {
                if (sub instanceof ClassDefinitionStatement inner) {
                    inner.prepareClass(env, local);
                    inner.resolveClass(env);
                }
            }
        }

        return local;
    }

    /**
     * Adds a local inner class for a lambda. The returned class won't have any super types or
     * members defined yet.
     *
     * Returns null if no class was added and an error was reported.
     *
     * @param st used for error reporting
     */
    NewLocalClass addLambdaClass(Statement st) {
        NewLocalClass clazz;

        try {
            clazz = mItem.tryAddLocalInnerClass(PRIVATE | FINAL | CLASS, null);
        } catch (UnsupportedOperationException e) {
            // Not expected.
            env().error(st, "lambda classes not supported in this scope");
            return null;
        }

        clazz.available();

        return clazz;
    }

    /**
     * @return false if label is a duplicate
     *
     * @throws UnsupportedOperationException if not supported by this scope
     */
    boolean addLabel(LabeledStatement st) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return false if the label wasn't found
     */
    boolean labelVisited(LabeledStatement st) {
        return false;
    }

    /**
     * Note: Calling this method has the side effect of indicating that the label (if found)
     * has been reached.
     *
     * @return null if the label isn't found
     */
    BaseBlock findBlockForJump(String label) {
        return null;
    }

    /**
     * Returns true if the active block isn't terminated. Unlike checkReachability, calling
     * this method doesn't alter the reachability check failure count.
     */
    boolean isReachable() {
        return true;
    }

    /**
     * Calls isReachable and accumulates a count of times it returns false. If 0 is returned,
     * then this block is reachable. If 1 is returned, then this is the first time
     * isReachable returned false.
     */
    int checkReachability() {
        return 0;
    }

    /**
     * If no reachability check failures have been detected yet, then checks that all labels
     * have been reached, returning the first one not reached. A reachability error should be
     * reported against it.
     */
    LabeledStatement checkLabelReachability() {
        return null;
    }

    /**
     * Tries to find a find a named local variable, parameter, or a captured variable from an
     * enclosing method.
     */
    public BaseBinding tryFindLocalVariable(String name) {
        return null;
    }

    /**
     * Try to add a return statement.
     *
     * @param st used for error reporting
     * @return false if an error was reported
     * @throws UnsupportedOperationException if not supported by this scope
     */
    boolean addReturn(Statement st, BaseBinding result) {
        throw new UnsupportedOperationException();
    }

    /**
     * Try to add a yield statement.
     *
     * @param st used for error reporting
     * @return false if an error was reported
     * @throws UnsupportedOperationException if not supported by this scope
     */
    boolean addYield(Statement st, BaseBinding result) {
        throw new UnsupportedOperationException();
    }

    /**
     * Called after code items have been visited, adding additional actions at the end if
     * necessary.
     *
     * @param st used for error reporting
     * @param items code items which were just visited
     * @param lastResult is non-null if at least one item exists and it didn't produce an error
     * @return false if an error was reported
     */
    boolean afterVisitCode(Statement st, List<Statement> items, BaseBinding lastResult) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns the block for adding new actions to.
     *
     * @param element provides the source code position
     * @throws UnsupportedOperationException if not supported by this scope
     */
    BaseBlock activeBlock(Element element) {
        return activeBlock(element.start());
    }

    /**
     * Returns the block for adding new actions to.
     *
     * @param start provides the source code position
     * @throws UnsupportedOperationException if not supported by this scope
     */
    BaseBlock activeBlock(Token start) {
        return activeBlock(start.position());
    }

    /**
     * Returns the block for adding new actions to.
     *
     * @param position source code position to be associated with newly appended actions
     * @throws NullPointerException if actions cannot be added to the current scope
     * @throws UnsupportedOperationException if not supported by this scope
     */
    BaseBlock activeBlock(int position) {
        throw new UnsupportedOperationException();
    }

    /**
     * @throws UnsupportedOperationException if not supported by this scope
     */
    void setActiveBlock(BaseBlock block) {
        throw new UnsupportedOperationException();
    }

    /**
     * Finishes this scope by assigning code to a BaseCallableItem, or else the code is added
     * to the parent block.
     *
     * @return the parent scope
     * @throws NullPointerException if code exists, the item isn't BaseCallableItem, and no
     * parent exists
     * @throws TerminatedBlockException if attempting to add code to the parent, but the active
     * parent block is terminated
     */
    ModelScope finish() {
        return mParent;
    }

    /**
     * Scope for a class definition.
     */
    static final class ClassDef extends ModelScope {
        ClassDef(ModelGenerator modGen, ModelScope parent, NewClass item) {
            super(modGen, parent, item);
        }

        @Override
        boolean addDeclaration(DeclarationStatement ds) {
            // If null is returned, an error should have been reported already.
            return ds.addToClass(env(), (NewClass) mItem) != null;
        }

        @Override
        BaseCallableItem addConstructor(ConstructorDefinitionStatement st) {
            return st.addToClass(env(), (NewClass) mItem);
        }

        @Override
        BaseCallableItem addMethod(MethodDefinitionStatement st) {
            BaseCallableItem callable = st.addToClass(env(), (NewClass) mItem);

            if (callable == null) {
                // An error should have been reported already.
                return null;
            }

            BaseTupleType inputType = callable.implSignature().inputType();
            int num = inputType.numFields();

            for (int i=0; i<num; i++) {
                // FIXME: check modifiers

                BaseType type = inputType.fieldType(i);

                if (type == BaseUnspecifiedType.THE && !callable.isMacro()) {
                    Element e = st.paramType.fieldTypes().get(i);
                    env().error(e, "parameter type cannot be unspecified");
                }
            }

            return callable;
        }
    }

    /**
     * Scope which has code blocks, local variables, and labels.
     */
    static abstract sealed class Code extends ModelScope {
        final ResultContext mContext;

        BaseBlock mEntryBlock;
        BaseBlock mActiveBlock;

        Map<String, BaseBinding.Local> mLocals;

        Map<String, LabelTarget> mLabels;

        int mReachabilityCheckFailures;

        /**
         * Constructor for callables.
         */
        Code(ModelGenerator modGen, ModelScope parent, BaseCallableItem item) {
            var context = new ResultContext.Return(item.implSignature().outputType());
            this(modGen, parent, item, new BaseBlock(context));
        }

        /**
         * Constructor for nested code scopes.
         */
        Code(ModelGenerator modGen, ModelScope.Code parent, BaseBlock entry) {
            this(modGen, parent, new BaseScopeItem(parent.mItem), entry);
        }

        private Code(ModelGenerator modGen, ModelScope parent, BaseItem item, BaseBlock entry) {
            super(modGen, parent, item);
            mContext = entry.context();
            mEntryBlock = mActiveBlock = entry;
            mLocals = Map.of();
            mLabels = Map.of();
        }

        @Override
        ResultContext context() {
            return mContext;
        }

        @Override
        boolean addDeclaration(DeclarationStatement ds) {
            CompilationEnv env = env();
            int modifierBits = ds.modifierBits(env);

            if ((modifierBits & STATIC) != 0) {
                env.error(ds, "local variable cannot be static");
            }

            String name = ds.name.text;

            if (mItem instanceof BaseCallableItem ci &&
                ci.implSignature().inputType().fieldExists(name))
            {
                env.error(ds.name, "a variable with the same name is declared as a parameter");
                return false;
            }

            {
                ModelScope.Code scope = this;

                while (true) {
                    if (scope.mLocals.containsKey(name)) {
                        String message = "a variable with the same name";

                        if (scope == this) {
                            message += " is already declared";
                        } else {
                            message += " is declared in a parent scope";
                        }

                        env.error(ds.name, message);

                        return false;
                    }

                    ModelScope parent = scope.mParent;

                    if (parent instanceof ModelScope.Code pc) {
                        scope = pc;
                    } else {
                        break;
                    }
                }
            }

            // FIXME: check modifiers

            BaseType type = ds.type().tryResolve(env, mItem);

            if (type == null) {
                // An error should have been reported already.
                return false;
            }

            if (mLocals.isEmpty()) {
                mLocals = new LinkedHashMap<>();
            }

            mLocals.put(name, new BaseBinding.Named(mContext, type, name));

            return true;
        }

        @Override
        BaseBinding.Local tryReplaceLocalDeclaration(BaseType type, String name) {
            if (mLocals.isEmpty()) {
                return null;
            }
            var local = new BaseBinding.Named(mContext, type, name);
            if (mLocals.replace(name, local) == null) {
                return null;
            }
            return local;
        }

        @Override
        BaseCallableItem addConstructor(ConstructorDefinitionStatement st) {
            env().error(st, "local constructor not supported");
            return null;
        }

        @Override
        BaseCallableItem addMethod(MethodDefinitionStatement st) {
            // FIXME: local method requires a special checks and transforms
            env().error(st, "local method not supported");
            return null;
        }

        @Override
        boolean addLabel(LabeledStatement st) {
            Map<String, LabelTarget> labels = mLabels;
            if (labels.isEmpty()) {
                mLabels = labels = new LinkedHashMap<>();
            }
            var block = new BaseBlock(mContext);
            block.sourcePosition(st.start().position());
            return labels.putIfAbsent(st.label.text, new LabelTarget(st, block)) == null;
        }

        @Override
        boolean labelVisited(LabeledStatement st) {
            LabelTarget target = mLabels.get(st.label.text);

            if (target == null) {
                return false;
            }

            BaseBlock block = target.block;

            if (!mActiveBlock.isTerminated()) {
                target.reached();
                activeBlock(st).jump(block);
            }

            mActiveBlock = block;

            return true;
        }

        @Override
        BaseBlock findBlockForJump(String label) {
            ModelScope.Code scope = this;

            while (true) {
                LabelTarget target = scope.mLabels.get(label);

                if (target != null) {
                    target.reached();
                    return target.block;
                }

                ModelScope parent = scope.mParent;

                if (parent instanceof ModelScope.Code pc) {
                    scope = pc;
                } else {
                    return null;
                }
            }
        }

        @Override
        boolean isReachable() {
            return !mActiveBlock.isTerminated();
        }

        @Override
        int checkReachability() {
            return isReachable() ? 0 : ++mReachabilityCheckFailures;
        }

        @Override
        LabeledStatement checkLabelReachability() {
            if (mReachabilityCheckFailures == 0) {
                for (LabelTarget target : mLabels.values()) {
                    LabeledStatement st = target.statement;
                    if (st != null) {
                        mReachabilityCheckFailures++;
                        return st;
                    }
                }
            }

            return null;
        }

        @Override
        public BaseBinding tryFindLocalVariable(String name) {
            ModelScope.Code scope = this;

            ModelScope parent;
            BaseItem item;

            while (true) {
                BaseBinding.Local local = scope.mLocals.get(name);

                if (local != null) {
                    return local.type() == BaseUnspecifiedType.THE ? null : local;
                }

                parent = scope.mParent;

                if (parent == null) {
                    return null;
                }

                item = scope.mItem;

                if (parent instanceof ModelScope.Code pc) {
                    scope = pc;
                } else {
                    break;
                }
            }

            // Try to capture a variable from an enclosing method.

            if (!(item instanceof BaseCallableItem callable) ||
                !(parent.mItem instanceof NewLocalClass localClass))
            {
                return null;
            }

            parent = parent.mParent;

            if (parent == null || !(parent.mItem instanceof BaseCallableItem enclosing)) {
                return null;
            }

            // Any accessible fields in the local inner class will shadow a variable declared
            // by the enclosing method. If a field is found, then return null. The caller will
            // look for the field if necessary, when it's the right time.

            if (!localClass.findField(name, localClass).isEmpty()) {
                return null;
            }

            BaseBinding captured = parent.tryFindLocalVariable(name);

            if (captured != null) {
                if (captured instanceof BaseBinding.Named n) {
                    // FIXME: CapturedSet should be tracked by parent ModelScope, or possibly
                    // be tracked by ResultContext.
                    throw null;
                    //return enclosing.capture(n.type(), name, localClass);
                } else if (captured instanceof BaseBinding.Captured c) {
                    return c;
                }
            }

            return null;
        }

        @Override
        BaseBlock activeBlock(int position) {
            BaseBlock block = mActiveBlock;
            block.sourcePosition(position);
            return block;
        }

        @Override
        void setActiveBlock(BaseBlock block) {
            mActiveBlock = Objects.requireNonNull(block);
        }

        /**
         * @param st used for error reporting
         * @param block and a jump from this block to the context result block
         * @return false if an error was reported
         */
        boolean doReturn(Statement st, BaseBlock block, BaseBinding result) {
            return doResult(st, "return", block, result);
        }

        /**
         * @param st used for error reporting
         * @param block and a jump from this block to the context result block
         * @return false if an error was reported
         */
        boolean doYield(Statement st, BaseBlock block, BaseBinding result) {
            return doResult(st, "yield", block, result);
        }

        /**
         * @param st used for error reporting
         * @param block and a jump from this block to the context result block
         * @param which used for error reporting
         * @return false if an error was reported
         */
        private boolean doResult(Statement st, String which, BaseBlock block, BaseBinding result) {
            BaseType conflict = mContext.assignResultAndJump(block, result);

            if (conflict == null) {
                return true;
            }

            String message = BaseInferredType.errorMessage(which, result.type(), conflict);

            env().error(st, message);

            return false;
        }

        /**
         * @param st used for error reporting
         * @param items code items which were just visited
         * @param lastResult is non-null if at least one item exists and it didn't produce an error
         * @return false if an error was reported
         */
        boolean doAutomaticYield(Statement st, List<Statement> items, BaseBinding lastResult) {
            if (isReachable()) {
                // Automatically yield.

                BaseBinding result;
                if (items.size() == 1 && lastResult != null) {
                    result = lastResult;
                } else {
                    result = BaseBinding.Void.THE;
                }
                
                addYield(items.getLast(), result);
            }

            return true;
        }
    }

    /**
     * Scope for plain nested code.
     */
    static final class Nested extends Code {
        Nested(ModelGenerator modGen, ModelScope parent) {
            var pc = ((Code) parent);
            // Uses the same context as the parent (from mActiveBlock).
            super(modGen, pc, pc.mActiveBlock);
        }

        @Override
        boolean addReturn(Statement st, BaseBinding result) {
            return ((Code) mParent).addReturn(st, result);
        }

        @Override
        boolean addYield(Statement st, BaseBinding result) {
            return ((Code) mParent).addYield(st, result);
        }

        @Override
        boolean afterVisitCode(Statement st, List<Statement> items, BaseBinding lastResult) {
            // Nothing to do.
            return true;
        }

        @Override
        ModelScope finish() {
            ((Code) mParent).mActiveBlock = mActiveBlock;
            return mParent;
        }
    }

    /**
     * Scope for a callable code body.
     */
    static sealed class Callable extends Code {
        Callable(ModelGenerator modGen, ModelScope parent, BaseCallableItem item) {
            if (!(parent instanceof ClassDef)) {
                throw new IllegalArgumentException();
            }

            // Uses a new context.
            super(modGen, parent, item);

            // Parameters must be added before named local variables.

            BaseTupleType inputType = item.implSignature().inputType();
            int num = inputType.numFields();

            for (int i=0; i<num; i++) {
                BaseType type = inputType.fieldType(i);
                String name = inputType.fieldName(i);
                var param = new BaseBinding.Parameter(mContext, type, name, i);
                if (mLocals.isEmpty()) {
                    mLocals = new LinkedHashMap<>();
                }
                // Duplicates should have been checked when the tuple was created. Also, no
                // named local variables should be defined yet.
                if (name != null && mLocals.putIfAbsent(name, param) != null) {
                    throw new AssertionError();
                }
            }
        }

        @Override
        boolean addReturn(Statement st, BaseBinding result) {
            return doReturn(st, activeBlock(st), result);
        }

        @Override
        boolean addYield(Statement st, BaseBinding result) {
            env().error(st, "yield isn't permitted here");
            return false;
        }

        @Override
        boolean afterVisitCode(Statement st, List<Statement> items, BaseBinding lastResult) {
            if (isReachable()) {
                // The scope must end with a return statement.
                var callable = (BaseCallableItem) mItem;
                if (callable.implSignature().outputType().isVoid()) {
                    // Automatically return.
                    return doReturn(st, mActiveBlock, BaseBinding.Void.THE);
                } else if (env().numErrors() == 0) {
                    env().error(st.end(), "missing return statement");
                    return false;
                }
            }

            return true;
        }

        @Override
        ModelScope finish() {
            if (!mEntryBlock.isEmpty()) {
                ((BaseCallableItem) mItem).assignCode(mEntryBlock);
            }

            return mParent;
        }
    }

    /**
     * Scope for a lambda function body.
     */
    static final class Lambda extends Callable {
        Lambda(ModelGenerator modGen, ModelScope parent, BaseCallableItem item) {
            // Uses a new context.
            super(modGen, parent, item);
        }

        @Override
        boolean addReturn(Statement st, BaseBinding result) {
            // FIXME: Support FarReturnAction.
            env().error(st, "far return from a lambda function isn't allowed");
            return false;
        }

        @Override
        boolean addYield(Statement st, BaseBinding result) {
            return doYield(st, activeBlock(st), result);
        }

        @Override
        boolean afterVisitCode(Statement st, List<Statement> items, BaseBinding lastResult) {
            return doAutomaticYield(st, items, lastResult);
        }
    }

    /**
     * Scope for a code tuple item.
     */
    static final class CodeItem extends Code {
        CodeItem(ModelGenerator modGen, ModelScope parent) {
            // Uses a new non-returning context.
            var context = new ResultContext.Yield(new BaseInferredType());
            super(modGen, (Code) parent, new BaseBlock(context));
        }

        // FIXME: Instead of calling prepare to re-use this object, allow locals and labels to
        //        be transferred.
        void prepare() {
            if (mEntryBlock == null) {
                throw null;
            }
        }

        // FIXME: Revise along with the prepare method.
        BaseBlock extractCode() {
            BaseBlock entry = mEntryBlock;
            mEntryBlock = null;
            return entry;
        }

        @Override
        boolean addReturn(Statement st, BaseBinding result) {
            return callableParent().doReturn(st, activeBlock(st), result);
        }

        @Override
        boolean addYield(Statement st, BaseBinding result) {
            return doYield(st, activeBlock(st), result);
        }

        @Override
        boolean afterVisitCode(Statement st, List<Statement> items, BaseBinding lastResult) {
            return doAutomaticYield(st, items, lastResult);
        }
    }
}
