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

import java.util.Map;
import java.util.Objects;

import org.cojen.motto.model.BranchAction;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class BaseBranchAction extends BaseTerminalAction implements BranchAction {
    private final BaseBinding mCondition;
    private BaseBlock mWhenTrue;
    private BaseBlock mWhenFalse;

    BaseBranchAction(int position, BaseBinding condition, BaseBlock whenTrue, BaseBlock whenFalse) {
        super(position);
        mCondition = Objects.requireNonNull(condition);
        Objects.requireNonNull(whenTrue);
        mWhenTrue = whenTrue;
        mWhenFalse = whenFalse;
    }

    @Override
    public <R> R accept(BaseBlock block, ActionVisitor<R> visitor) {
        return visitor.visit(block, this);
    }

    @Override
    public BaseBinding condition() {
        return mCondition;
    }

    @Override
    public BaseBlock whenTrue() {
        return mWhenTrue;
    }

    @Override
    public BaseBlock whenFalse() {
        return mWhenFalse;
    }

    /**
     * @param owner the block that this action resides in
     */
    private void setWhenTrue(BaseBlock owner, BaseBlock newWhenTrue) {
        if (newWhenTrue != mWhenTrue) {
            mWhenTrue.removePredecessor(owner);
            newWhenTrue.addPredecessor(owner);
            mWhenTrue = newWhenTrue;
        }
    }

    /**
     * @param owner the block that this action resides in
     */
    private void setWhenFalse(BaseBlock owner, BaseBlock newWhenFalse) {
        if (newWhenFalse != mWhenFalse) {
            newWhenFalse.addPredecessor(owner);
            mWhenFalse.removePredecessor(owner);
            mWhenFalse = newWhenFalse;
        }
    }

    @Override
    void addPredecessors(BaseBlock owner) {
        mWhenFalse.addPredecessor(owner);
        mWhenTrue.addPredecessor(owner);
    }

    @Override
    void removePredecessors(BaseBlock owner) {
        mWhenFalse.removePredecessor(owner);
        mWhenTrue.removePredecessor(owner);
    }

    @Override
    void changeDestination(BaseBlock owner, BaseBlock oldDest, BaseBlock newDest) {
        if (mWhenTrue == oldDest) {
            setWhenTrue(owner, newDest);
        }
        if (mWhenFalse == oldDest) {
            setWhenFalse(owner, newDest);
        }
    }

    @Override
    BaseBranchAction detach() {
        return new BaseBranchAction(position(), mCondition, mWhenTrue, mWhenFalse);
    }

    @Override
    void trackBlockLocalBindings(Map<BaseBinding.Anonymous, Boolean> map) {
        mCondition.trackBlockLocalSource(map);
    }
}
