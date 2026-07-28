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

import org.cojen.motto.internal.model.BaseAction;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public sealed interface Action
    permits ArrayAction, BranchAction, CallAction, CastAction, ConvertAction,
            CopyAction, DeclarationAction, JumpAction, ThrowAction, TupleAction, YieldAction,
            BaseAction
{
    /**
     * Returns the source code line for this action, or 0 if not applicable.
     */
    public int line();

    /**
     * Returns the source code column for this action, or -1 if not applicable.
     */
    public int column();
}
