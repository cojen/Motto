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

package org.cojen.motto.model;

import org.cojen.motto.internal.model.BaseItem;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public sealed interface Item permits BaseItem, ClassTypeItem, FieldItem, CallableItem {
    /**
     * Returns the enclosing type, which might be null.
     */
    public Type enclosingType();

    /**
     * Returns the nearest enclosing type, which is never null. If this Item is also a Type,
     * then the enclosing type is the Item itself.
     */
    public Type nearestType();

    /**
     * Returns the nearest enclosing class, which is null if the item isn't enclosed by a
     * class. If this Item is a ClassTypeItem, then the enclosing class is the Item itself.
     */
    public default ClassTypeItem nearestClass() {
        Type type = nearestType();
        return type instanceof ClassTypeItem c ? c : null;
    }

    public boolean isStatic();

    public boolean isFinal();

    public boolean isPrivate();

    /**
     * Returns true if this item is accessible via the other item.
     *
     * @param via can pass null to check if the item is publicly available
     */
    public boolean isAccessibleVia(Item via);
}
