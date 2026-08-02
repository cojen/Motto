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
 * Example: `int a` or `int a = 0`
 *
 * @author Brian S. O'Neill
 */
public final class DeclarationStatement implements Statement {
    public final List<Token.Identifier> modifiers;
    public final VarType type;
    public final Token.Identifier name;
    public final Statement source;

    /**
     * @param modifiers required; might be empty
     * @param type required
     * @param name name of the variable or field being declared
     * @param source optional source for immediate assignment
     */
    DeclarationStatement(List<Token.Identifier> modifiers,
                         VarType type, Token.Identifier name, Statement source)
    {
        this.modifiers = modifiers;
        this.type = type;
        this.name = name;
        this.source = source;
    }

    @Override
    public <R, P> R accept(ParseVisitor<R, P> v, P param) {
        return v.visit(this, param);
    }

    @Override
    public Token start() {
        if (!modifiers.isEmpty()) {
            return modifiers.getFirst();
        }
        return type.start();
    }

    @Override
    public Token end() {
        if (source != null) {
            return source.end();
        }
        return name;
    }
}
