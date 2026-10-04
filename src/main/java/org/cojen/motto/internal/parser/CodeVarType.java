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

import org.cojen.motto.internal.compiler.CompilationEnv;

import org.cojen.motto.internal.model.BaseItem;
import org.cojen.motto.internal.model.BaseType;

/**
 * Represents code statements which can be passed to a macro.
 *
 * @author Brian S. O'Neill
 */
public final class CodeVarType implements VarType {
    public final VarType resultType;

    /**
     * @param resultType the type yielded by the code body
     */
    CodeVarType(VarType resultType) {
        this.resultType = resultType;
    }

    @Override
    public Token start() {
        return resultType.start();
    }

    @Override
    public Token end() {
        return resultType.end();
    }

    @Override
    public boolean isUnspecified() {
        return false;
    }

    @Override
    public BaseType tryResolve(CompilationEnv env, BaseItem scope) {
        // FIXME: tryResolve
        throw null;
    }
}
