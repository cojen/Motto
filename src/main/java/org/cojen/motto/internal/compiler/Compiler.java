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

import java.io.File;

import java.util.Objects;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class Compiler implements ErrorListener {
    private final ErrorListener mErrorListener;

    private volatile int mNumErrors;

    public Compiler(ErrorListener el) {
        mErrorListener = Objects.requireNonNull(el);
    }

    /**
     * Returns the number of errors reported against this Compiler instance.
     */
    public int numErrors() {
        return mNumErrors;
    }

    public void error(String message) {
        error(null, message);
    }

    public void error(File sourceFile, String message) {
        error(sourceFile, new CompileError(message));
    }

    public void error(File sourceFile, CompileError error) {
        error(sourceFile, error.startLine(), error.startColumn(),
              error.endLine(), error.endColumn(), error.message());
    }

    /**
     * @param sourceFile is null if not applicable
     * @param startLine source code start line, one-based; is 0 if not applicable
     * @param startColumn source code start column, zero-based; is -1 if not applicable
     * @param endLine source code end line, inclusive; is 0 if not applicable
     * @param endColumn source code end column, inclusive; is -1 if not applicable
     */
    @Override
    public synchronized void error(File sourceFile, int startLine, int startColumn,
                                   int endLine, int endColumn, String message)
    {
        mNumErrors++;
        mErrorListener.error(sourceFile, startLine, startColumn, endLine, endColumn, message);
    }

    @Override
    public synchronized void uncaught(File sourceFile, Throwable ex) {
        mNumErrors++;
        mErrorListener.uncaught(sourceFile, ex);
    }
}
