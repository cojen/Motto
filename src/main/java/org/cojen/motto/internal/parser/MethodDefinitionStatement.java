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
public final class MethodDefinitionStatement extends FunctionDefinitionStatement {
    public final List<DefinitionSegment> segments;

    /**
     * @param modifiers required; might be empty
     * @param name required base name of method
     * @param clauses required; might be empty
     * @param code optional
     * @param segments required; can be empty
     */
    MethodDefinitionStatement(List<Token.Identifier> modifiers, Token.Identifier name,
                              List<Clause> clauses, TupleStatement code, VarType returnType,
                              TupleVarType paramType, List<DefinitionSegment> segments)
    {
        super(modifiers, name, clauses, code, returnType, paramType);
        this.segments = segments;
    }

    @Override
    public <R, P> R accept(ParseVisitor<R, P> v, P param) {
        return v.visit(this, param);
    }

    @Override
    public Token start() {
        return modifiers.isEmpty() ? returnType.start() : modifiers.getFirst();
    }

    @Override
    public Token end() {
        if (code != null) {
            return code.end();
        }
        if (!segments.isEmpty()) {
            return segments.getLast().params.end();
        }
        if (!clauses.isEmpty()) {
            return clauses.getLast().end();
        }
        return paramType.end();
    }
}
