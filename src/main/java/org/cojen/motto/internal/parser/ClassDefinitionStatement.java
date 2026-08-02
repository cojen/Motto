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
public final class ClassDefinitionStatement extends DefinitionStatement {
    public final Token.Identifier type;

    /**
     * @param modifiers required; might be empty
     * @param type required (class or interface)
     * @param name required
     * @param clauses required; might be empty
     * @param code required; can pass null if the definition is broken
     */
    ClassDefinitionStatement(List<Token.Identifier> modifiers, Token.Identifier type,
                             Token.Identifier name, List<Clause> clauses, TupleStatement code)
    {
        super(modifiers, name, clauses, code);
        this.type = type;
    }

    @Override
    public <R, P> R accept(ParseVisitor<R, P> v, P param) {
        return v.visit(this, param);
    }

    @Override
    public Token start() {
        return modifiers.isEmpty() ? type : modifiers.getFirst();
    }

    @Override
    public Token end() {
        if (code != null) {
            return code.end();
        }
        if (!clauses.isEmpty()) {
            return clauses.getLast().end();
        }
        return name;
    }
}
