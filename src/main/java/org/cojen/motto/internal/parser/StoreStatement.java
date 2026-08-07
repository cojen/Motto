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

/**
 * Examples: `a = b`, `a.b = c`, or `a[0] = b`
 *
 * @author Brian S. O'Neill
 */
public final class StoreStatement implements Statement {
    public final Statement target;
    public final Statement source;

    StoreStatement(Statement target, Statement source) {
        this.target = target;
        this.source = source;
    }

    @Override
    public <R, P> R accept(ParseVisitor<R, P> v, P param) {
        return v.visit(this, param);
    }

    @Override
    public Token start() {
        return target.start();
    }

    @Override
    public Token end() {
        return source.end();
    }
}
