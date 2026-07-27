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

import org.cojen.motto.internal.model.TheFieldItem;
import org.cojen.motto.internal.model.TupleFieldItem;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public sealed interface FieldItem extends Item permits TheFieldItem, TupleFieldItem {
    /**
     * Returns a non-null field type.
     */
    public Type type();

    /**
     * Returns a field name, which can be null if unnamed.
     */
    public String name();
}
