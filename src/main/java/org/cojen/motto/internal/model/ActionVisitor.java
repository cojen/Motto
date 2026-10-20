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
 * 
 *
 * @author Brian S. O'Neill
 */
public interface ActionVisitor<R> {
    public R visit(BaseBlock block, BaseArrayAction.New action);

    public R visit(BaseBlock block, BaseArrayAction.Get action);

    public R visit(BaseBlock block, BaseArrayAction.Set action);

    public R visit(BaseBlock block, BaseBranchAction action);

    public R visit(BaseBlock block, BaseCallAction.Direct action);

    public R visit(BaseBlock block, BaseCallAction.New action);

    public R visit(BaseBlock block, BaseCallAction.Virtual action);

    public R visit(BaseBlock block, BaseCastAction action);

    public R visit(BaseBlock block, BaseConvertAction action);

    public R visit(BaseBlock block, BaseCopyAction action);

    public R visit(BaseBlock block, BaseJumpAction action);

    public R visit(BaseBlock block, BaseThrowAction action);

    public R visit(BaseBlock block, BaseTupleAction.New action);

    public R visit(BaseBlock block, BaseTupleAction.Get action);

    public R visit(BaseBlock block, BaseTupleAction.Set action);

    public R visit(BaseBlock block, UnreachableAction action);
}
