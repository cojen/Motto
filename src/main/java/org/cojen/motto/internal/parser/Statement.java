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

import java.util.List;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public sealed interface Statement extends Element permits
    AsStatement, CoordinateLoadStatement, DeclarationStatement, DefinitionStatement,
    EmptyStatement, FieldLoadStatement, InfixStatement, IsStatement, JumpStatement,
    LabeledStatement, LiteralStatement, NewArrayStatement, NewStatement, PathStatement,
    PostfixStatement, PrefixStatement, ReturnStatement, SequenceStatement, StoreStatement,
    ThrowStatement, TupleStatement, YieldStatement
{
    public <R, P> R accept(ParseVisitor<R, P> v, P param);

    /**
     * @see LabeledStatement
     */
    public default Statement delabel() {
        return this;
    }

    default VarType asVarType(Parser p) {
        p.error(this, "illegal type");
        // Return a bogus type.
        return new TupleVarType(start(), List.of(), end());
    }
}
