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
import java.util.Objects;

/**
 * Example: `new A()` or `a.new A()`
 *
 * @author Brian S. O'Neill
 */
public sealed class NewStatement implements Statement permits NewClassDefinitionStatement {
    public final Statement source;
    public final List<Token.Identifier> name;
    public final TupleStatement params;

    /**
     * @param name required
     * @param params required
     */
    NewStatement(List<Token.Identifier> name, TupleStatement params) {
        this.source = null;
        this.name = name;
        this.params = params;
    }

    /**
     * @param source required
     * @param name required
     * @param params required
     */
    NewStatement(Statement source, Token.Identifier name, TupleStatement params) {
        this.source = Objects.requireNonNull(source);
        this.name = List.of(name);
        this.params = params;
    }

    @Override
    public <R, P> R accept(ParseVisitor<R, P> v, P param) {
        return v.visit(this, param);
    }

    @Override
    public Token start() {
        return source == null ? name.getFirst() : source.start();
    }

    @Override
    public Token end() {
        return params.end();
    }
}
