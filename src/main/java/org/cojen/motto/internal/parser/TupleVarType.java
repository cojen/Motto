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
 * Example: `(int a, String b)`
 *
 * @author Brian S. O'Neill
 */
public final class TupleVarType extends VarType {
    public final TupleStatement declarations;

    /**
     * @param declarations items should only be DeclarationStatements (to be verified later)
     * @param coordinates optional; items must be VarTypes or null
     */
    TupleVarType(TupleStatement declarations, List<Coordinate> coordinates) {
        super(coordinates);
        this.declarations = declarations;
    }

    @Override
    public Token start() {
        return declarations.start();
    }

    @Override
    public Token end() {
        if (coordinates != null && !coordinates.isEmpty()) {
            return coordinates.getLast().end();
        }
        return declarations.end();
    }
}
