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
public interface ParseVisitor<R> {
    public R visit(AsStatement st);

    public R visit(ClassDefinitionStatement st);

    public R visit(CodeScopeStatement st);

    public R visit(ConstructorDefinitionStatement st);

    public R visit(CoordinateLoadStatement st);

    public R visit(DeclarationStatement st);

    public R visit(EmptyStatement st);

    public R visit(FieldLoadStatement st);

    public R visit(InfixStatement st);

    public R visit(IsStatement st);

    public R visit(JumpStatement st);

    public R visit(LabeledStatement st);

    public R visit(LambdaStatement st);

    public R visit(LiteralStatement st);

    public R visit(LoadStatement st);

    public R visit(MethodCallStatement st);

    public R visit(MethodDefinitionStatement st);

    public R visit(NewArrayStatement st);

    public R visit(NewClassDefinitionStatement st);

    public R visit(NewStatement st);

    public R visit(PostfixStatement st);

    public R visit(PrefixStatement st);

    public R visit(ReturnStatement st);

    public R visit(SequenceStatement st);

    public R visit(StaticInitStatement st);

    public R visit(StoreStatement st);

    public R visit(ThrowStatement st);

    public R visit(TupleStatement st);

    public R visit(UpdateStatement st);

    public R visit(YieldStatement st);
}
