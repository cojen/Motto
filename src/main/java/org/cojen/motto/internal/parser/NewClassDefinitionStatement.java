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
 * Example: `new A() { ... }` or `a.new A() { ... }`
 *
 * @author Brian S. O'Neill
 */
public final class NewClassDefinitionStatement extends NewStatement {
    public final TupleStatement code;

    /**
     * @param name required
     * @param params required
     * @param code required; can pass null if definition is broken
     */
    NewClassDefinitionStatement(List<Token.Identifier> name, TupleStatement params,
                                TupleStatement code)
    {
        super(name, params);
        this.code = code;
    }

    /**
     * @param source required
     * @param name required
     * @param params required
     * @param code required; can pass null if definition is broken
     */
    NewClassDefinitionStatement(Statement source, Token.Identifier name, TupleStatement params,
                                TupleStatement code)
    {
        super(source, name, params);
        this.code = code;
    }

    @Override
    public <R, P> R accept(ParseVisitor<R, P> v, P param) {
        return v.visit(this, param);
    }

    @Override
    public Token end() {
        return code != null ? code.end() : params.end();
    }
}
