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
 * A CompilationEnv is associated with a single source file.
 *
 * @author Brian S. O'Neill
 */
public final class CompilationEnv {
    private final Compiler mCompiler;
    private final File mSourceFile;

    private volatile int mNumErrors;

    /**
     * @param sourceFile optional
     */
    CompilationEnv(Compiler compiler, File sourceFile) {
        mCompiler = Objects.requireNonNull(compiler);
        mSourceFile = sourceFile;
    }

    public Compiler compiler() {
        return mCompiler;
    }

    public File sourceFile() {
        return mSourceFile;
    }

    /**
     * Returns the number of errors reported against this CompilationEnv instance.
     */
    public int numErrors() {
        return mNumErrors;
    }

    public synchronized void error(CompileError error) {
        mNumErrors++;
        mCompiler.error(mSourceFile, error);
    }

    public synchronized void uncaught(Throwable ex) {
        mNumErrors++;
        mCompiler.uncaught(mSourceFile, ex);
    }
}
