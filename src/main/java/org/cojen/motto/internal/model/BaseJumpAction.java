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

import org.cojen.motto.model.JumpAction;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class BaseJumpAction extends BaseTerminalAction implements JumpAction {
    private BaseBlock mDestination;

    BaseJumpAction(int position, BaseBlock destination) {
        super(position);
        mDestination = destination;
    }

    @Override
    public <R> R accept(BaseBlock block, ActionVisitor<R> visitor) {
        return visitor.visit(block, this);
    }

    @Override
    public BaseBlock destination() {
        return mDestination;
    }

    /**
     * @param owner the block that this action resides in
     */
    void setDestination(BaseBlock owner, BaseBlock newDestination) {
        if (newDestination != mDestination) {
            newDestination.addPredecessor(owner);
            mDestination.removePredecessor(owner);
            mDestination = newDestination;
        }
    }

    @Override
    void addPredecessors(BaseBlock owner) {
        mDestination.addPredecessor(owner);
    }

    @Override
    void removePredecessors(BaseBlock owner) {
        mDestination.removePredecessor(owner);
    }

    @Override
    void changeDestination(BaseBlock owner, BaseBlock oldDest, BaseBlock newDest) {
        if (mDestination == oldDest) {
            setDestination(owner, newDest);
        }
    }

    @Override
    BaseJumpAction detach() {
        return new BaseJumpAction(position(), mDestination);
    }

    @Override
    void trackBlockLocalBindings(Map<BaseBinding.Anonymous, Boolean> map) {
        // Nothing to do.
    }
}
