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

import org.cojen.motto.model.ArrayAction;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
// FIXME: sealed
public abstract non-sealed class BaseArrayAction extends FlowAction implements ArrayAction {
    BaseArrayAction(int position) {
        super(position);
    }

    /* FIXME: notes

       - Array length should be available from a pseudo field binding.

       - Array construction could be supported by a pseudo constructor, but... how can multi
         dimensional array construction work? It would require multiple constructor overloads,
         which is messy.

       - The get/set array actions cannot be supported by methods because the type would be
         unspecified. A macro won't work, because it needs to generate an action!

     */
}
