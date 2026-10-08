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

import java.util.Objects;

import org.cojen.motto.model.Binding;
import org.cojen.motto.model.Block;
import org.cojen.motto.model.Code;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class BaseCode implements Code {
    private final BaseBlock mEntry, mExit;
    private final BaseBinding mResult;

    public BaseCode(BaseBlock entry, BaseBinding result) {
        mEntry = entry;
        mExit = entry.merge();
        mResult = Objects.requireNonNull(result);
    }

    @Override
    public BaseBlock entry() {
        return mEntry;
    }

    @Override
    public BaseBlock exit() {
        return mExit;
    }

    @Override
    public BaseBinding result() {
        return mResult;
    }
}
