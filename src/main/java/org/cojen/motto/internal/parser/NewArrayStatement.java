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
 * Example: `new int[10]` or `new int[] (1, 2, 3)`
 *
 * @author Brian S. O'Neill
 */
public final class NewArrayStatement implements Statement {
    public final VarType elementType;
    public final List<Coordinate> coordinates;
    public final TupleStatement values;

    /**
     * @param elementType required
     * @param coordinates required
     * @param values optional
     */
    NewArrayStatement(VarType elementType, List<Coordinate> coordinates, TupleStatement values) {
        this.elementType = elementType;
        this.coordinates = coordinates;
        this.values = values;
    }

    @Override
    public <R, P> R accept(ParseVisitor<R, P> v, P param) {
        return v.visit(this, param);
    }

    @Override
    public Token start() {
        return elementType.start();
    }

    @Override
    public Token end() {
        return values != null ? values.end() : coordinates.getLast().end();
    }
}
