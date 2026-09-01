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

    /**
     * @param origin the block that this action resides in
     */
    BaseBranchAction(BaseBlock origin,
                     int position, BaseBinding condition, BaseBlock whenTrue, BaseBlock whenFalse)
    {
        super(position);
        mCondition = Objects.requireNonNull(condition);
        Objects.requireNonNull(whenTrue);
        whenFalse.addPredecessor(origin);
        whenTrue.addPredecessor(origin);
        mWhenTrue = whenTrue;
        mWhenFalse = whenFalse;
    }

    @Override
    public <R> R accept(ActionVisitor<R> visitor) {
        return visitor.visit(this);
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
     * @param origin the block that this action resides in
     */
    void setWhenTrue(BaseBlock origin, BaseBlock newWhenTrue) {
        newWhenTrue.addPredecessor(origin);
        mWhenTrue.removePredecessor(origin);
        mWhenTrue = newWhenTrue;
    }

    /**
     * @param origin the block that this action resides in
     */
    void setWhenFalse(BaseBlock origin, BaseBlock newWhenFalse) {
        newWhenFalse.addPredecessor(origin);
        mWhenFalse.removePredecessor(origin);
        mWhenFalse = newWhenFalse;
    }

    @Override
    void trackBlockLocalBindings(Map<BaseBinding.Anonymous, Boolean> map) {
        mCondition.trackBlockLocalSource(map);
    }
}
