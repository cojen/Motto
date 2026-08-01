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
 * Special exception to abort tokenizing or parsing when the call stack is deep or it's
 * inconvenient to report a special error state. Abort exceptions must never escape the
 * tokenizer or parser.
 *
 * @author Brian S. O'Neill
 */
final class Abort extends Exception {
    final Token token;

    Abort(Token token) {
        this.token = token;
    }

    @Override
    public Throwable fillInStackTrace() {
        return this;
    }
}
