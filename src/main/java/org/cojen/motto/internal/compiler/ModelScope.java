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

import java.util.Map;

import org.cojen.motto.internal.model.BaseBinding;
import org.cojen.motto.internal.model.BaseBlock;
import org.cojen.motto.internal.model.BaseCallableItem;
import org.cojen.motto.internal.model.BaseFieldItem;
import org.cojen.motto.internal.model.BaseItem;
import org.cojen.motto.internal.model.NewClass;

import org.cojen.motto.internal.parser.Element;
import org.cojen.motto.internal.parser.DeclarationStatement;
import org.cojen.motto.internal.parser.LabeledStatement;
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
        mLocals = Map.of();
    }

    /**
     * @return the name of the field or variable, or else null if an error was reported
     */
    String addDeclaration(DeclarationStatement ds) {
        CompilationEnv env = mModGen.env();

        if (mItem instanceof NewClass clazz) {
            BaseFieldItem field = ds.addToClass(env, clazz);
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
    private BaseBlock activeBlock(Element element) {
        return activeBlock(element.start());
    }

    /**
     * Returns the block for adding new actions to.
     *
     * @param start provides the source code position
     */
    private BaseBlock activeBlock(Token start) {
        return activeBlock(start.position());
    }

    /**
     * Returns the block for adding new actions to.
     *
     * @param position source code position to be associated with newly appended actions
     * @throws NullPointerException if actions cannot be added to the current scope
     */
    private BaseBlock activeBlock(int position) {
        BaseBlock block = mActiveBlock;
        block.sourcePosition(position);
        return block;
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
