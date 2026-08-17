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
import java.io.PrintStream;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public interface ErrorListener {
    /**
     * Is called (by multiple compilation threads) to report an error.
     *
     * @param sourceFile is null if not applicable
     * @param startLine source code start line, one-based; is 0 if not applicable
     * @param startColumn source code start column, zero-based; is -1 if not applicable
     * @param endLine source code end line, inclusive; is 0 if not applicable
     * @param endColumn source code end column, inclusive; is -1 if not applicable
     */
    public void error(File sourceFile,
                      int startLine, int startColumn, int endLine, int endColumn,
                      String message);

    public void uncaught(File sourceFile, Throwable ex);

    public static final class Basic implements ErrorListener {
        private final PrintStream mOut;

        public Basic() {
            this(System.out);
        }

        Basic(PrintStream out) {
            mOut = out;
        }

        @Override
        public void error(File sourceFile,
                          int startLine, int startColumn, int endLine, int endColumn,
                          String message)
        {
            mOut.println(sourceFile + "@(line=" + startLine + ", " +
                         "column=" + startColumn + "): " + message);
        }

        @Override
        public void uncaught(File sourceFile, Throwable ex) {
            synchronized (mOut) {
                mOut.println(sourceFile + ": " + ex.toString());
                ex.printStackTrace(mOut);
            }
        }
    }
}
