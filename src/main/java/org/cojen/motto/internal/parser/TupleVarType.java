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

import java.util.HashSet;
import java.util.List;

import org.cojen.motto.internal.compiler.CompilationEnv;

import org.cojen.motto.internal.model.BaseClassTypeItem;
import org.cojen.motto.internal.model.BaseItem;
import org.cojen.motto.internal.model.BaseTupleType;
import org.cojen.motto.internal.model.BaseType;
import org.cojen.motto.internal.model.BaseUnspecifiedType;
import org.cojen.motto.internal.model.Modifiers;

/**
 * Example: `(int a, String b)`
 *
 * @author Brian S. O'Neill
 */
public final class TupleVarType implements VarType {
    private final Token mOpen;
    private final List<VarType> mFieldTypes;
    private final Token mClose;

    /**
     * @param open usually T_LPAREN or T_LBRACE; see TupleStatement.asVarType for the exception
     * @param close usually T_RPAREN or T_RBRACE; see TupleStatement.asVarType for the exception
     */
    TupleVarType(Token open, List<VarType> fieldTypes, Token close) {
        mOpen = open;
        mFieldTypes = fieldTypes;
        mClose = close;
    }

    @Override
    public Token start() {
        return mOpen;
    }

    @Override
    public Token end() {
        return mClose;
    }

    /**
     * Returns true of the tuple starts with a `(`.
     */
    public boolean isEvaluated() {
        return mOpen.type() == Token.T_LPAREN;
    }

    @Override
    public boolean isUnspecified() {
        return false;
    }

    @Override
    public BaseType tryResolve(CompilationEnv env, BaseItem scope) {
        return tryResolve(env, scope, null);
    }

    /**
     * Tries to resolve the type and optionally insert a "this" element as the first one. If
     * the first element exists and is named "this", an error is reported if the type doesn't
     * match what was given, unless the existing type is unspecified.
     *
     * @param insertThis optional
     * @return null if cannot resolve and an error was reported
     */
    public BaseTupleType tryResolve(CompilationEnv env, BaseItem scope,
                                    BaseClassTypeItem insertThis)
    {
        List<VarType> fieldTypes = mFieldTypes;

        // Check if a required "this" item is already defined.
        boolean hasThis = insertThis != null && !fieldTypes.isEmpty()
            && fieldTypes.getFirst() instanceof NamedVarType named
            && "this".equals(named.name().text);

        BaseType[] types;
        String[] names;
        int offset;

        if (insertThis == null || hasThis) {
            types = new BaseType[fieldTypes.size()];
            names = new String[types.length];
            offset = 0;
        } else {
            types = new BaseType[1 + fieldTypes.size()];
            names = new String[types.length];
            types[0] = insertThis;
            names[0] = "this";
            offset = 1;
        }

        HashSet<String> nameSet = names.length <= 1 ? null : HashSet.newHashSet(names.length);

        boolean error = false;

        for (VarType vtype : fieldTypes) {
            BaseType type = vtype.tryResolve(env, scope);
            String name;

            if (type == null) {
                // Assume an error was reported.
                error = true;
                name = null;
            } else if (vtype instanceof NamedVarType named) {
                Element.resolveModifiers(env, Modifiers.FINAL, named.modifiers());

                name = named.name().text;

                if (nameSet != null && !nameSet.add(name)) {
                    env.error(named.name(), "duplicate name");
                    name = null;
                }

                if (offset == 0 && hasThis && type != BaseUnspecifiedType.THE &&
                    !type.equals(insertThis))
                {
                    env.error(named.type(), "invalid `this` type");
                }
            } else {
                name = null;
            }

            types[offset] = type;
            names[offset++] = name;
        }

        return error ? null : BaseTupleType.from(types).withNames(names);
    }

    public List<VarType> fieldTypes() {
        return mFieldTypes;
    }
}
