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

import org.cojen.motto.model.CopyAction;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class BaseCopyAction extends FlowAction implements CopyAction {
    private final BaseBinding mTarget, mSource;

    BaseCopyAction(int position, BaseBinding target, BaseBinding source) {
        super(position);
        mTarget = Objects.requireNonNull(target);
        mSource = Objects.requireNonNull(source);
    }

    @Override
    public <R> R accept(ActionVisitor<R> visitor) {
        return visitor.visit(this);
    }

    @Override
    public BaseBinding target() {
        return mTarget;
    }

    @Override
    public BaseBinding source() {
        return mSource;
    }

    @Override
    void trackBlockLocalBindings(Map<BaseBinding.Anonymous, Boolean> map) {
        mSource.trackBlockLocalSource(map);
        mTarget.trackBlockLocalTarget(map);
    }
}
