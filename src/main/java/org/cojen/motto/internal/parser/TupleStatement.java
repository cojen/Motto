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

import java.util.ArrayList;
import java.util.List;

/**
 * Example: `(a, 0)` or `{a, 0}`
 *
 * @author Brian S. O'Neill
 */
public final class TupleStatement extends EnclosedStatementList implements Statement {
    /**
     * @param open T_LPAREN or T_LBRACE
     * @param close T_RPAREN or T_RBRACE
     */
    TupleStatement(Token open, List<Statement> items, Token close) {
        super(open, items, close);
    }

    @Override
    public <R, P> R accept(ParseVisitor<R, P> v, P param) {
        return v.visit(this, param);
    }

    /**
     * Returns true of the tuple starts with a `(`.
     */
    public boolean isEvaluated() {
        return open.type() == Token.T_LPAREN;
    }

    /**
     * Returns true of the tuple starts with a `{`, representing a code scope.
     */
    public boolean isUnevaluated() {
        return open.type() == Token.T_LBRACE;
    }

    @Override
    public VarType asVarType(Parser p) {
        List<Statement> items = this.items;
        int size = items.size();

        if (size == 0) {
            return new TupleVarType(open, List.of(), close);
        }

        VarType first = items.getFirst().asVarType(p);

        if (size == 1 && !(first instanceof NamedVarType)) {
            // Unwrap tuple types which consist of a single unnamed element.
            return first;
        }

        var fieldTypes = new ArrayList<VarType>(size);
        fieldTypes.add(first); 

        for (int i=1; i<size; i++) {
            fieldTypes.add(items.get(i).asVarType(p));
        }

        return new TupleVarType(open, fieldTypes, close);
    }
}
