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

import org.cojen.motto.internal.model.BaseArrayType;
import org.cojen.motto.internal.model.BaseItem;
import org.cojen.motto.internal.model.BaseType;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class ArrayVarType implements VarType {
    private final VarType mElementType;
    private final List<Coordinate> mCoordinates;

    /**
     * @param coordinates must contain at least one element
     */
    ArrayVarType(VarType elementType, List<Coordinate> coordinates) {
        mElementType = elementType;
        mCoordinates = coordinates;
    }

    @Override
    public Token start() {
        return mElementType.start();
    }

    @Override
    public Token end() {
        return mCoordinates.getLast().end();
    }

    @Override
    public boolean isUnspecified() {
        return false;
    }

    @Override
    public BaseType tryResolve(CompilationEnv env, BaseItem scope) {
        BaseType type = mElementType.tryResolve(env, scope);

        if (type != null) {
            for (Coordinate c : mCoordinates) {
                if (!c.isSimpleDeclaration()) {
                    env.error(c, "illegal array declaration");
                    return null;
                }
                type = BaseArrayType.from(type);
            }
        }

        return type;
    }

    public VarType elementType() {
        return mElementType;
    }

    public List<? extends VarCoordinate> coordinates() {
        return mCoordinates;
    }
}
