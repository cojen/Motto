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

import org.cojen.motto.model.Action;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public abstract sealed class BaseAction implements Action
    permits FlowAction, TerminalAction, BaseYieldAction
{
    private final int mPosition;

    /**
     * @param position encodes the source code line and column corresponding to this action;
     * pass 0 if not applicable
     */
    BaseAction(int position) {
        mPosition = position;
    }

    public abstract <R> R accept(ActionVisitor<R> visitor);

    @Override
    public int line() {
        // FIXME
        throw null;
    }

    @Override
    public int column() {
        // FIXME
        throw null;
    }

    int position() {
        return mPosition;
    }

    /**
     * For each binding used by this action, calls BaseBinding#trackBlockLocalSource or
     * BaseBinding#trackBlockLocalTarget against the given map. True values indicate that the
     * binding value is dependent upon a prior block.
     *
     * <p>If the action has source and target bindings, all the source bindings should be
     * tracked first, ensuring that the true value has precedence. The target could the same as
     * the source, and so naturally, it must be accessed as a source first.
     *
     * @param map a map associated with one block
     * @see BaseBinding
     */
    abstract void trackBlockLocalBindings(Map<BaseBinding.Anonymous, Boolean> map);
}
