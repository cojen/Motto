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

/**
 * Represents a plain scope of code.
 *
 * @author Brian S. O'Neill
 */
public final class BaseScopeItem extends BaseItem {
    private final BaseItem mEnclosing;

    public BaseScopeItem(BaseItem enclosing) {
        super(enclosing.modifierBits());
        mEnclosing = enclosing;
    }

    @Override
    public BaseType enclosingType() {
        return mEnclosing.enclosingType();
    }

    @Override
    public BaseType nearestType() {
        return mEnclosing.nearestType();
    }

    @Override
    public BaseClassTypeItem nearestClass() {
        return mEnclosing.nearestClass();
    }
}
