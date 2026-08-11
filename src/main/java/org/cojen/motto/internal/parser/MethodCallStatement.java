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
import java.util.Objects;

/**
 * Example: `a(0)` or `a.b(0)`
 *
 * Note that segment parsing is quite lenient. When matching calls to defined methods, the
 * segments need further processing. In particular, those that implement PathStatement. If the
 * path is simple (it has one element), then it might match to a segment name.
 *
 * @author Brian S. O'Neill
 */
public final class MethodCallStatement extends PathStatement {
    public final Statement source;
    public final TupleStatement params;
    public final List<Statement> segments;

    /**
     * @param path path to variable or field
     * @param segments required; can be empty
     */
    MethodCallStatement(List<Token.Identifier> path, TupleStatement params,
                        List<Statement> segments)
    {
        super(path);
        this.source = null;
        this.params = params;
        this.segments = segments;
    }

    /**
     * @param source required
     * @param name method name
     * @param segments required; can be empty
     */
    MethodCallStatement(Statement source, Token.Identifier name, TupleStatement params,
                        List<Statement> segments)
    {
        super(List.of(name));
        this.source = Objects.requireNonNull(source);
        this.params = params;
        this.segments = segments;
    }

    @Override
    public <R, P> R accept(ParseVisitor<R, P> v, P param) {
        return v.visit(this, param);
    }

    @Override
    public Token start() {
        return source == null ? path.getFirst() : source.start();
    }

    @Override
    public Token end() {
        if (!segments.isEmpty()) {
            return segments.getLast().end();
        }
        return params.end();
    }
}
