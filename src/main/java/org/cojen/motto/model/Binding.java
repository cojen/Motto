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

/**
 * 
 *
 * @author Brian S. O'Neill
 */
// FIXME: sealed
public interface Binding {
    /**
     * Returns a binding whose type is void, and anything copied into it is dropped.
     */
    public static Binding void_() {
        // FIXME
        throw null;
    }

    /**
     * Returns a constant binding whose type is null, and it can be copied into any reference.
     */
    public static Binding null_() {
        // FIXME
        throw null;
    }

    public Type type();

    public boolean isVolatile();

    /**
     * Returns true if the binding value cannot be observed to be modified by another thread.
     * This is usually true except for non-final field bindings.
     */
    public boolean isStable();

    // FIXME: Binding needs to support restrictions, like "not null", or "known constant(s)".
}
