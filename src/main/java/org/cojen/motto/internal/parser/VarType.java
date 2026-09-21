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

import org.cojen.motto.internal.model.BaseClassTypeItem;
import org.cojen.motto.internal.model.BaseItem;
import org.cojen.motto.internal.model.BaseTupleType;
import org.cojen.motto.internal.model.BaseType;


/**
 * @author Brian S. O'Neill
 */
public sealed interface VarType extends Element
    permits ArrayVarType, LambdaVarType, NamedVarType, SimpleVarType, TupleVarType
{
    /**
     * Returns true if the type represents model.UnspecifiedType.
     */
    public boolean isUnspecified();

    /**
     * Tries to resolve the type. If unable, an error is reported and null is returned.
     *
     * @param env used for error reporting and finding classes
     * @param scope used for finding the nearest enclosing class; see SimpleVarType
     * @return null if cannot resolve and an error was reported
     */
    public BaseType tryResolve(CompilationEnv env, BaseItem scope);

    /**
     * Tries to resolve the type as a tuple and optionally insert a "this" element as the first
     * one. If the first element exists and is named "this", an error is reported if the type
     * doesn't match what was given, unless the existing type is unspecified.
     *
     * @param insertThis optional
     * @return null if cannot resolve and an error was reported
     */
    // Note: TupleVarType must override this method.
    public default BaseTupleType tryResolve(CompilationEnv env, BaseItem scope,
                                            BaseClassTypeItem insertThis)
    {
        return TupleVarType.tryResolve(env, scope, insertThis, List.of(this));
    }
}
