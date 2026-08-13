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
public abstract sealed class FunctionDefinitionStatement extends DefinitionStatement
    permits ConstructorDefinitionStatement, MethodDefinitionStatement
{
    public final VarType returnType;
    public final TupleVarType paramType;

    /**
     * @param modifiers required; might be empty
     * @param name required base name of function
     * @param clauses required; might be empty
     * @param code optional
     * @param returnType can be null for void
     */
    FunctionDefinitionStatement(List<Token.Identifier> modifiers, Token.Identifier name,
                                List<Clause> clauses, TupleStatement code,
                                VarType returnType, TupleVarType paramType)
    {
        super(modifiers, name, clauses, code);
        this.returnType = returnType;
        this.paramType = paramType;
    }
}
