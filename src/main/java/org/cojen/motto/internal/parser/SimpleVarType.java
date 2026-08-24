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
import java.util.Set;

import org.cojen.motto.internal.compiler.CompilationEnv;

import org.cojen.motto.internal.model.BaseClassTypeItem;
import org.cojen.motto.internal.model.BaseItem;
import org.cojen.motto.internal.model.BasePath;
import org.cojen.motto.internal.model.BasePrimitiveType;
import org.cojen.motto.internal.model.BaseType;
import org.cojen.motto.internal.model.BaseUnspecifiedType;

/**
 * Example: `int` or `int[]` or `int...` or `java.lang.String`, etc.
 *
 * @author Brian S. O'Neill
 */
public sealed interface SimpleVarType extends VarType permits LoadStatement {
    @Override
    public default boolean isUnspecified() {
        return "_".equals(simpleName());
    }

    @Override
    public default BaseType tryResolve(CompilationEnv env, BaseItem scope) {
        BaseType type = trySelectClass(env, scope);

        if (type == null) {
            // Try to select a primitive type as the last resort.
            String simpleName = simpleName();
            return "_".equals(simpleName) ? BaseUnspecifiedType.THE
                : BasePrimitiveType.trySelectByName(simpleName);
        }

        if (type == null) {
            env.error(this, "cannot resolve type");
        }

        return type;
    }

    /**
     * Returns null if the name has more than one element or if it's quoted.
     */
    private String simpleName() {
        List<Token.Identifier> name = typeName();
        Token.Identifier first;
        if (name.size() == 1 && !(first = name.getFirst()).quoted) {
            return first.text;
        }
        return null;
    }

    public List<Token.Identifier> typeName();

    /**
     * Tries to resolve a type. If unable, an error is reported and null is returned.
     */
    static BaseClassTypeItem trySelectClass(CompilationEnv env, BaseItem scope,
                                            List<Token.Identifier> name)
    {
        BaseClassTypeItem clazz = new LoadStatement(name).trySelectClass(env, scope);
        if (clazz == null) {
            env.error(name, "cannot resolve type");
        }
        return clazz;
    }

    default BaseClassTypeItem trySelectClass(CompilationEnv env, BaseItem scope) {
        List<Token.Identifier> name = typeName();
        Token.Identifier first = name.getFirst();
        String firstText = first.text;

        BaseClassTypeItem clazz = scope.nearestClass();

        while (clazz != null) {
            if (clazz.namePath().getLast().equals(firstText)) { 
                return clazz;
            }

            // FIXME: Call findInnerClass and act on a set. Use scope for via.
            BaseClassTypeItem inner = matchInnerClass(clazz.findInnerClassForImport(firstText));

            if (inner != null) {
                return inner;
            }

            clazz = clazz.outerType();
        }

        BaseClassTypeItem match = matchInnerClass(env.findImportedClass(first));

        // Try to find a class by its fully qualified name. It might match on an inner class,
        // so permute the path.

        int nameIndex = name.size();
        BasePath fullName = BasePath.from(name);

        while (true) {
            clazz = env.findClass(fullName);

            if (clazz != null) {
                clazz = matchInnerClass(clazz, nameIndex);
                if (clazz != null) {
                    if (match == null) {
                        match = clazz;
                    } else {
                        env.error(this, "ambiguous type name");
                    }
                }
            }

            if (--nameIndex <= 0) {
                break;
            }

            // Assume that the implementation of findClassItem will obtain a canonical path
            // when caching the item.
            fullName = fullName.trimLastNonCanonical();
        }

        return match;
    }

    /**
     * Tries to find an inner class which matches the full name path.
     *
     * @param outer optional
     */
    private BaseClassTypeItem matchInnerClass(BaseClassTypeItem outer) {
        return outer == null ? null : matchInnerClass(outer, 1);
    }

    /**
     * @param outer required
     */
    private BaseClassTypeItem matchInnerClass(BaseClassTypeItem outer, int nameIndex) {
        List<Token.Identifier> name = typeName();

        for (; nameIndex < name.size(); nameIndex++) {
            // FIXME: Call findInnerClass and act on a set. Use scope for via.
            outer = outer.findInnerClassForImport(name.get(nameIndex).text);
            if (outer == null) {
                return null;
            }
        }

        return outer;
    }
}
