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

import java.util.Set;

import org.cojen.motto.internal.model.BaseClassTypeItem;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public sealed interface ClassTypeItem extends ObjectType, Item permits BaseClassTypeItem {
    @Override
    public default Type enclosingType() {
        return this;
    }

    @Override
    public default ClassTypeItem enclosingClass() {
        return this;
    }

    /**
     * Returns the non-null set of interfaces that this class implements.
     */
    public Set<? extends ClassTypeItem> interfaces();

    /**
     * Returns the package path for this type.
     */
    public Path packagePath();

    /**
     * Returns the class names for this type, which has one element for an outer class.
     */
    public Path namePath();

    /**
     * Returns the immediate outer type, which is null if this type represents a top-level
     * class. The immediate outer type might itself be an inner class.
     */
    public ClassTypeItem outerType();

    /**
     * Returns the outermost non-null outer type, which might be this type.
     */
    public ClassTypeItem nestType();
}
