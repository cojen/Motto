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

import java.util.AbstractList;
import java.util.List;

/**
 * Example: `[0]` or `[1, 2]` or `[,]`
 *
 * @author Brian S. O'Neill
 */
public final class Coordinate extends EnclosedStatementList implements VarCoordinate {
    /**
     * Used for a simple dimension declaration: `a[]`
     */
    static final List<Statement> ONE_DIMENSION = new AbstractList<>() {
        @Override
        public int size() {
            return 1;
        }

        @Override
        public Statement get(int index) {
            if (index != 0) {
                throw new IndexOutOfBoundsException();
            }
            // Null indicates that this is a dimension declaration.
            return null;
        }
    };

    /**
     * @param items if any item is null, it's treated as a dimension declaration 
     */
    Coordinate(Token lbrack, List<Statement> items, Token rbrack) {
        super(lbrack, items, rbrack);
    }

    public int dimensions() {
        return items.size();
    }

    /**
     * Returns true if the coordinate is a simple one-dimensional declaration.
     */
    public boolean isSimpleDeclaration() {
        return items.size() == 1 && items.getFirst() == null;
    }
}
