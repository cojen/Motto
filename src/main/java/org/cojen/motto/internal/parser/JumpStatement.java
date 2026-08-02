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
 * Example: `goto start`
 *
 * @author Brian S. O'Neill
 */
public final class JumpStatement implements Statement {
    public final Token.Identifier keyword;
    public final Token.Identifier target;

    /**
     * @param keyword goto, break, or continue
     * @param target required for goto
     */
    JumpStatement(Token.Identifier keyword, Token.Identifier target) {
        this.keyword = keyword;
        this.target = target;
    }

    @Override
    public <R, P> R accept(ParseVisitor<R, P> v, P param) {
        return v.visit(this, param);
    }

    @Override
    public Token start() {
        return keyword;
    }

    @Override
    public Token end() {
        return target;
    }
}
