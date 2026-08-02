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
 * Represents a sequence of statements which were separated using a `;` character.
 *
 * Example: `String a = "hello"; IO.println(a)`
 *
 * @author Brian S. O'Neill
 */
public final class SequenceStatement extends StatementList implements Statement {
    /**
     * @throws IllegalArgumentException if items is empty
     */
    SequenceStatement(List<Statement> items) {
        if (items.isEmpty()) {
            throw new IllegalArgumentException();
        }
        super(items);
    }

    @Override
    public <R, P> R accept(ParseVisitor<R, P> v, P param) {
        return v.visit(this, param);
    }

    @Override
    public final Token start() {
        return items.getFirst().start();
    }

    @Override
    public final Token end() {
        return items.getLast().end();
    }
}
