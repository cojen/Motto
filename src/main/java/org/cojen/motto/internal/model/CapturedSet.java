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

import java.util.HashMap;

import org.cojen.motto.internal.util.SimpleSet;

/**
 * Tracks local variables which are captured by local inner classes.
 *
 * @author Brian S. O'Neill
 */
public final class CapturedSet {
    private final BaseCallableItem mCallable;

    // Maps variable names declared in the callable to bindings for captured variables.
    private final HashMap<String, BaseBinding.Captured> mCapturedMap;

    // Set of local inner classes which use the captured variables of this CapturedSet.
    private final SimpleSet<NewLocalClass> mUsedBy;

    public CapturedSet(BaseCallableItem callable) {
        mCallable = callable;
        mCapturedMap = new HashMap<>(4);
        mUsedBy = new SimpleSet<>();
    }

    /**
     * @throws IllegalStateException if already captured and the type doesn't match
     */
    public BaseBinding.Captured capture(BaseType type, String name, NewLocalClass usedBy) {
        var map = mCapturedMap;

        BaseBinding.Captured captured = map.get(name);

        if (captured == null) {
            captured = new BaseBinding.Captured(mCallable, type, name);
            map.put(name, captured);
        } else if (!type.equals(captured.type())) {
            throw new IllegalStateException();
        }

        mUsedBy.add(usedBy);

        return captured;
    }
}
