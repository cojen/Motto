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
 * Represents a list of statements enclosed within paren or brace tokens.
 *
 * @author Brian S. O'Neill
 */
public abstract sealed class EnclosedStatementList extends StatementList
    permits CodeScopeStatement, Coordinate, TupleStatement
{
    public final Token open;
    public final Token close;

    /**
     * @param open left paren or brace token
     * @param close right paren or brace token
     */
    EnclosedStatementList(Token open, List<Statement> items, Token close) {
        super(items);
        this.open = open;
        this.close = close;
    }

    @Override
    public final Token start() {
        Token start = open;

        if (start == null) {
            if (!items.isEmpty()) {
                start = items.getFirst().start();
            } else {
                start = close;
            }
        }

        return start;
    }

    @Override
    public final Token end() {
        return close;
    }
}
