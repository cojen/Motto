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

package org.cojen.motto.internal.compiler;

import java.util.SequencedCollection;

import org.cojen.motto.internal.parser.Element;
import org.cojen.motto.internal.parser.Token;

/**
 * @param startLine source code start line, one-based; is 0 if not applicable
 * @param startColumn source code start column, zero-based; is -1 if not applicable
 * @param endLine source code end line, inclusive
 * @param endColumn source code end column, exclusive
 */
public final record CompileError(int startLine, int startColumn,
                                 int endLine, int endColumn, String message)
{
    public CompileError(String message) {
        this(0, -1, 0, -1, message);
    }

    public CompileError(Token start, Token end, String message) {
        this(start.line(), start.column(), end.line(), end.column() + end.length(), message);
    }

    public CompileError(Element element, String message) {
        this(element.start(), element.end(), message);
    }

    /**
     * @param elements a range consecutive elements
     */
    public CompileError(SequencedCollection<? extends Element> elements, String message) {
        this(elements.getFirst().start(), elements.getLast().end(), message);
    }
}
