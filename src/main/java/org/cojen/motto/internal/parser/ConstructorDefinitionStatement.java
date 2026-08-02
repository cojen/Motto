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
public final class ConstructorDefinitionStatement extends FunctionDefinitionStatement {
    /**
     * @param modifiers required; might be empty
     * @param name required name of constructor
     * @param clauses required; might be empty
     * @param params items should only be DeclarationStatements (to be verifier later)
     * @param code required, unless the definition is broken
     */
    ConstructorDefinitionStatement(List<Token.Identifier> modifiers, Token.Identifier name,
                                   List<Clause> clauses, TupleStatement code, TupleStatement params)
    {
        super(modifiers, name, clauses, code, null, params);
    }

    @Override
    public <R, P> R accept(ParseVisitor<R, P> v, P param) {
        return v.visit(this, param);
    }

    @Override
    public Token start() {
        return modifiers.isEmpty() ? name : modifiers.getFirst();
    }

    @Override
    public Token end() {
        return code.end();
    }
}
