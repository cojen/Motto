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
 * 
 *
 * @author Brian S. O'Neill
 */
public final class IsStatement implements Statement {
    public final Statement source;
    public final Token.Basic not;
    public final VarType type;

    /**
     * @param source required
     * @param not optional
     * @param type required
     */
    IsStatement(Statement source, Token.Basic not, VarType type) {
        this.source = source;
        this.not = not;
        this.type = type;
    }

    @Override
    public <R, P> R accept(ParseVisitor<R, P> v, P param) {
        return v.visit(this, param);
    }

    @Override
    public Token start() {
        return not != null ? not : source.start();
    }

    @Override
    public Token end() {
        return type.end();
    }
}
