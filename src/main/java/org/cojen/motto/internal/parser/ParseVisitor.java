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

package org.cojen.motto.internal.parser;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public interface ParseVisitor<R, P> {
    public R visit(AsStatement st, P param);

    public R visit(ClassDefinitionStatement st, P param);

    public R visit(CodeScopeStatement st, P param);

    public R visit(ConstructorDefinitionStatement st, P param);

    public R visit(CoordinateLoadStatement st, P param);

    public R visit(DeclarationStatement st, P param);

    public R visit(EmptyStatement st, P param);

    public R visit(FieldLoadStatement st, P param);

    public R visit(InfixStatement st, P param);

    public R visit(IsStatement st, P param);

    public R visit(JumpStatement st, P param);

    public R visit(LabeledStatement st, P param);

    public R visit(LambdaStatement st, P param);

    public R visit(LiteralStatement st, P param);

    public R visit(LoadStatement st, P param);

    public R visit(MethodCallStatement st, P param);

    public R visit(MethodDefinitionStatement st, P param);

    public R visit(NewArrayStatement st, P param);

    public R visit(NewClassDefinitionStatement st, P param);

    public R visit(NewStatement st, P param);

    public R visit(PostfixStatement st, P param);

    public R visit(PrefixStatement st, P param);

    public R visit(ReturnStatement st, P param);

    public R visit(SequenceStatement st, P param);

    public R visit(StaticInitStatement st, P param);

    public R visit(StoreStatement st, P param);

    public R visit(ThrowStatement st, P param);

    public R visit(TupleStatement st, P param);

    public R visit(UpdateStatement st, P param);

    public R visit(YieldStatement st, P param);
}
