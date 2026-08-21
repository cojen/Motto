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
import java.util.Map;

import org.cojen.motto.internal.model.BaseBinding;
import org.cojen.motto.internal.model.BaseBlock;
import org.cojen.motto.internal.model.BaseCallableItem;
import org.cojen.motto.internal.model.BaseFieldItem;
import org.cojen.motto.internal.model.BaseItem;
import org.cojen.motto.internal.model.BaseType;
import org.cojen.motto.internal.model.BaseVoidType;
import org.cojen.motto.internal.model.NewClass;

import org.cojen.motto.internal.parser.ConstructorDefinitionStatement;
import org.cojen.motto.internal.parser.DeclarationStatement;
import org.cojen.motto.internal.parser.Element;
import org.cojen.motto.internal.parser.LabeledStatement;
import org.cojen.motto.internal.parser.MethodDefinitionStatement;
import org.cojen.motto.internal.parser.Statement;
import org.cojen.motto.internal.parser.Token;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
final class ModelScope {
    private final ModelGenerator mModGen;
    private final ModelScope mParent;
    private final BaseItem mItem;

    private final BaseBlock mFirstBlock;
    private BaseBlock mActiveBlock;

    private Map<String, BaseBinding.Local> mLocals;

    private Map<String, LabelTarget> mLabels;

    private BaseBlock mReturnBlock;
    private BaseBinding mReturnVar;

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

    private int mReachabilityCheckFailures;

    /**
     * @param item expected to be a NewClass or a BaseCallableItem
     */
    ModelScope(ModelGenerator modGen, ModelScope parent, BaseItem item) {
        mModGen = modGen;
        mParent = parent;
        mItem = item;
        mFirstBlock = mActiveBlock = new BaseBlock();
        mLocals = Map.of();
        mLabels = Map.of();
    }

    ModelScope parent() {
        return mParent;
    }

    BaseItem item() {
        return mItem;
    }

    private CompilationEnv env() {
        return mModGen.env();
    }

    /**
     * @return the name of the field or variable, or else null if an error was reported
     */
    String addDeclaration(DeclarationStatement ds) {
        if (mItem instanceof NewClass clazz) {
            BaseFieldItem field = ds.addToClass(env(), clazz);
            // If null, an error should have been reported already.
            return field == null ? null : field.name();
        }

        // FIXME: declaration is a local variable; check modifiers; check conflicts; check
        // paramConflict; check parent paramConflict; stop if parent isn't a BaseCallableItem

        /* FIXME
        BaseFieldItem field = ds.addToScope(env, mItem);

        if (field == null || paramConflict(env, ds)) {
            // An error should have been reported already.
            return null;
        }

        String name = field.name();

        for (ModelScope parent = mParent; parent != null; parent = parent.mParent) {
            if (parent.paramConflict(env, ds)) {
                // An error should have been reported already.
                return null;
            }

            BaseScopeItem pitem = parent.mItem;

            if (!(pitem instanceof BaseClassItem) && pitem.findField(name) != null) {
                dupError(env, ds.name, "a variable with the same name");
                return null;
            }
        }

        return field;
        */
        throw null;
    }

    BaseCallableItem addConstructor(ConstructorDefinitionStatement st) {
        if (mItem instanceof NewClass clazz) {
            return st.addToClass(env(), clazz);
        }

        env().error(st, "local constructor not supported");
        return null;
    }

    BaseCallableItem addMethod(MethodDefinitionStatement st) {
        if (mItem instanceof NewClass clazz) {
            return st.addToClass(env(), clazz);
        }

        // FIXME: local method requires a special checks and transforms
        env().error(st, "local method not supported");
        return null;
    }

    private void dupError(CompilationEnv env, Token.Identifier name, String prefix) {
        env.error(name, prefix + " is declared in a parent scope");
    }

    /**
     * @return false if label is a duplicate
     */
    boolean addLabel(LabeledStatement st) {
        Map<String, LabelTarget> labels = mLabels;
        if (labels == null) {
            mLabels = labels = new LinkedHashMap<>();
        }
        var block = new BaseBlock();
        block.sourcePosition(st.start().position());
        return labels.putIfAbsent(st.label.text, new LabelTarget(st, block)) == null;
    }

    /**
     * @return false if the label wasn't found
     */
    boolean labelVisited(LabeledStatement st) {
        Map<String, LabelTarget> labels = mLabels;
        LabelTarget target;

        if (labels == null || (target = labels.get(st.label.text)) == null) {
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

    /**
     * Note: Calling this method has the side effect of indicating that the label (if found)
     * has been reached.
     *
     * @return null if the label isn't found
     */
    BaseBlock findBlockForJump(String label) {
        ModelScope scope = this;

        while (true) {
            Map<String, LabelTarget> labels = scope.mLabels;
            if (labels != null) {
                LabelTarget target = labels.get(label);
                if (target != null) {
                    target.reached();
                    return target.block;
                }
            }
            if ((scope = scope.mParent) == null) {
                return null;
            }
        }
    }

    /**
     * Returns a block to jump to when returning from a callable.
     *
     * @param st for error reporting
     */
    BaseBlock returnBlock(Statement st) {
        BaseBlock retBlock = mReturnBlock;
        if (retBlock == null) {
            initReturn(st);
            retBlock = mReturnBlock;
        }
        return retBlock;
    }

    /**
     * Returns a variable to use with the return block.
     *
     * @param st for error reporting
     */
    BaseBinding returnVar(Statement st) {
        BaseBinding retVar = mReturnVar;
        if (retVar == null) {
            initReturn(st);
            retVar = mReturnVar;
        }
        return retVar;
    }

    private void initReturn(Statement st) {
        ModelScope scope = this;
        BaseCallableItem callable;

        while (true) {
            if (scope.mItem instanceof BaseCallableItem c) {
                callable = c;
                break;
            }

            ModelScope parent = scope.mParent;

            if (parent == null) {
                env().error(st, "not in a returnable scope");
                mReturnBlock = new BaseBlock();
                mReturnVar = BaseBinding.Void.THE;
                return;
            }

            scope = parent;
        }

        BaseBlock retBlock = scope.mReturnBlock;
        BaseBinding retVar = scope.mReturnVar;

        if (retBlock == null) {
            scope.mReturnBlock = retBlock = new BaseBlock();

            BaseType outputType = callable.signature().outputType();
            scope.mReturnVar = retVar = retBlock.var(outputType);

            if (outputType != BaseVoidType.THE) {
                retBlock.yield(retVar);
            }
        }

        mReturnBlock = retBlock;
        mReturnVar = retVar;
    }

    /**
     * Returns true if the active block isn't terminated. Unlike checkReachability, calling
     * this method doesn't alter the reachability check failure count.
     */
    boolean isReachable() {
        return !mActiveBlock.isTerminated();
    }

    /**
     * Calls isReachable and accumulates a count of times it returns false. If 0 is returned,
     * then this block is reachable. If 1 is returned, then this is the first time
     * isReachable returned false.
     */
    int checkReachability() {
        return isReachable() ? 0 : ++mReachabilityCheckFailures;
    }

    /**
     * If no reachability check failures have been detected yet, then checks that all labels
     * have been reached, returning the first one not reached. A reachability error should be
     * reported against it.
     */
    LabeledStatement checkLabelReachability() {
        if (mReachabilityCheckFailures == 0) {
            Map<String, LabelTarget> labels = mLabels;
            if (labels != null) {
                for (LabelTarget target : mLabels.values()) {
                    LabeledStatement st = target.statement;
                    if (st != null) {
                        mReachabilityCheckFailures++;
                        return st;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Returns the block for adding new actions to.
     *
     * @param element provides the source code position
     */
    BaseBlock activeBlock(Element element) {
        return activeBlock(element.start());
    }

    /**
     * Returns the block for adding new actions to.
     *
     * @param start provides the source code position
     */
    BaseBlock activeBlock(Token start) {
        return activeBlock(start.position());
    }

    /**
     * Returns the block for adding new actions to.
     *
     * @param position source code position to be associated with newly appended actions
     * @throws NullPointerException if actions cannot be added to the current scope
     */
    BaseBlock activeBlock(int position) {
        BaseBlock block = mActiveBlock;
        block.sourcePosition(position);
        return block;
    }

    BaseBinding activeBlockResult() {
        return mActiveBlock.result();
    }

    /**
     * Finishes this scope by assigning code to a BaseCallableItem, or else the code is added
     * to the parent block.
     *
     * @return the parent scope
     * @throws NullPointerException if code exists, the item isn't BaseCallableItem, and no
     * parent exists
     * @throws tupl.model.TerminatedBlockException if attempting to add code to the parent, but
     * the active parent block is terminated
     */
    ModelScope finish() {
        if (!mFirstBlock.isEmpty()) {
            if (mItem instanceof BaseCallableItem callable) {
                callable.assignCode(mFirstBlock);
            } else {
                mParent.mActiveBlock.addAll(mFirstBlock);
            }
        }

        return mParent;
    }
}
