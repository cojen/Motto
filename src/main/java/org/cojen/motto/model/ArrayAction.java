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

import org.cojen.motto.internal.model.BaseArrayAction;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public sealed interface ArrayAction extends Action
    permits ArrayAction.New, ArrayAction.Get, ArrayAction.Set, BaseArrayAction
{
    // FIXME: sealed
    public static non-sealed interface New extends ArrayAction {
        public Binding output();

        public Type type();

        public int numDimensions();

        public Binding dimension(int index);
    }

    // FIXME: sealed
    public static non-sealed interface Get extends ArrayAction {
        public Binding output();

        public Binding array();

        public Binding index();
    }

    // FIXME: sealed
    public static non-sealed interface Set extends ArrayAction {
        public Binding array();

        public Binding index();

        public Binding value();
    }
}
