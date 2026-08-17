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

import org.cojen.motto.internal.model.BaseItem;
import org.cojen.motto.internal.model.BaseType;

/**
 * Example: `(int a, String b)`
 *
 * @author Brian S. O'Neill
 */
public final class TupleVarType implements VarType {
    private final Token mOpen;
    private final List<VarType> mFieldTypes;
    private final Token mClose;

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

    @Override
    public BaseType tryResolve(CompilationEnv env, BaseItem scope) {
        // FIXME: tryResolve
        throw null;
    }

    public List<VarType> fieldTypes() {
        return mFieldTypes;
    }
}
