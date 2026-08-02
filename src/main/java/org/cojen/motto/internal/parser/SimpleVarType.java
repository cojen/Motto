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
 * Example: `int` or `int[]` or `int...` or `java.lang.String`, etc.
 *
 * @author Brian S. O'Neill
 */
public final class SimpleVarType extends VarType {
    public final List<Token.Identifier> name;

    /**
     * @param name qualified name of variable or field type
     * @param coordinates optional; the items of each coordinate must be VarTypes or null
     */
    SimpleVarType(List<Token.Identifier> name, List<Coordinate> coordinates) {
        super(coordinates);
        this.name = name;
    }

    @Override
    public Token start() {
        return name.getFirst();
    }

    @Override
    public Token end() {
        if (coordinates != null && !coordinates.isEmpty()) {
            return coordinates.getLast().end();
        }
        return name.getLast();
    }
}
