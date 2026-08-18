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

import org.cojen.motto.internal.compiler.CompilationEnv;

import static org.cojen.motto.internal.model.Modifiers.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public abstract sealed interface Element
    permits CompilationUnit, ImportDirective, Statement, StatementList, Token,Clause, VarType
{
    public Token start();

    public Token end();

    /**
     * Note: reports errors if any modifiers are unknown, disallowed, or redundant.
     *
     * @param allowedBits allowed modifier bits
     * @return modifierBits
     * @see org.cojen.motto.internal.model.Modifiers
     */
    static int resolveModifiers(CompilationEnv env, int allowedBits,
                                List<Token.Identifier> modifiers)
    {
        // FIXME: reject incompatible combinations

        int modifierBits = 0;

        if (!modifiers.isEmpty()) {
            for (Token.Identifier token : modifiers) {
                int selectedBit = switch (token.text) {
                    default -> 0;
                    case "public" -> PUBLIC;
                    case "internal" -> INTERNAL;
                    case "protected" -> PROTECTED;
                    case "static" -> STATIC;
                    case "final" -> FINAL;
                    case "synchronized" -> SYNCHRONIZED;
                    case "volatile" -> VOLATILE;
                    case "transient" -> TRANSIENT;
                    case "native" -> NATIVE;
                    case "abstract" -> ABSTRACT;
                    case "macro" -> MACRO;
                };

                if ((selectedBit & allowedBits) == 0) {
                    env.error(token, "illegal modifier");
                } else if ((modifierBits & selectedBit) != 0) {
                    env.error(token, "redundant modifier");
                } else {
                    modifierBits |= selectedBit;
                }
            }
        }

        return modifierBits;
    }
}

