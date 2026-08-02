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
 * Example: `extends a.B` or `throws B, C`
 *
 * @author Brian S. O'Neill
 */
public final class Clause implements Element {
    public final Token.Identifier kind;
    public final List<List<Token.Identifier>> items;

    /**
     * @param kind required
     * @param items required list of qualified identifiers
     */
    Clause(Token.Identifier kind, List<List<Token.Identifier>> items) {
        this.kind = kind;
        this.items = items;
    }

    @Override
    public Token start() {
        return kind;
    }

    @Override
    public Token end() {
        return items.isEmpty() ? kind : items.getLast().getLast();
    }
}
