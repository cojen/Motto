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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

import java.util.Objects;

import org.cojen.motto.internal.model.BaseClassTypeItem;
import org.cojen.motto.internal.model.BasePath;
import org.cojen.motto.internal.model.NewClass;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class Compiler implements ErrorListener, Closeable {
    private final ErrorListener mErrorListener;
    private final ClassRegistry mClassRegistry;

    private volatile int mNumErrors;

    public Compiler(ErrorListener el, ClassRegistry registry) {
        mErrorListener = Objects.requireNonNull(el);
        mClassRegistry = registry == null ? ClassRegistry.from() : registry;
    }

    @Override
    public void close() throws IOException {
        mClassRegistry.close();
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

    /**
     * Register a prepared NewClass which is being compiled. All classes being compiled must be
     * prepared and registered before imports are resolved.
     *
     * @return false if a matching NewClass already exists
     */
    boolean tryRegister(NewClass clazz) {
        return mClassRegistry.tryRegister(clazz);
    }

    /**
     * Checks if a package is known to exist. Searches the class path, module path, and any
     * registered NewClass instances which are currently being compiled.
     */
    public boolean packageExists(BasePath packagePath) {
        try {
            return mClassRegistry.packageExists(packagePath);
        } catch (IOException e) {
            ClassRegistry.importError(this, packagePath, (BasePath) null, e.toString());
            return false;
        }
    }

    /**
     * Tries to find an outer class by its fully qualified name. Searches the class path,
     * module path, and any registered NewClass instances which are currently being compiled.
     *
     * @return null if not found
     */
    public BaseClassTypeItem findClass(BasePath fullName) {
        return findClass(fullName.trimLast(), fullName.getLast());
    }

    /**
     * Tries to find an outer class by its package and name. Searches the class path, module
     * path, and any registered NewClass instances which are currently being compiled.
     *
     * @param packagePath required, but can be empty
     * @param className required (must not be an inner class name)
     * @return null if not found
     */
    public BaseClassTypeItem findClass(BasePath packagePath, String className) {
        try {
            return mClassRegistry.findClass(this, packagePath, className);
        } catch (IOException e) {
            ClassRegistry.importError(this, packagePath, className, e.toString());
            return null;
        }
    }

    /**
     * Tries to find an inner class by its package and class name. Searches the class path,
     * module path, and any registered NewClass instances which are currently being compiled.
     *
     * @param outer required
     * @param className loadable inner class name, not including the package name (typically
     * has '$' characters)
     * @param name simple inner class name
     * @return null if not found
     */
    public BaseClassTypeItem findClass(BaseClassTypeItem outer, String className, String name) {
        try {
            return mClassRegistry.findClass(this, outer, className, name);
        } catch (IOException e) {
            ClassRegistry.importError
                (this, outer.packagePath(), outer.namePath().append(name), e.toString());
            return null;
        }
    }

    /**
     * Follows the given path until a BaseClassTypeItem is found by its fully qualified name.
     * Call fullPathSize() to obtain the number of path elements consumed, which might not be
     * the full path size which was given.
     *
     * @return null if nothing matched
     */
    public BaseClassTypeItem matchClassItem(BasePath path) {
        int maxSize = path.size();
        for (int size = 0; size < maxSize; size++) {
            // Assume that the implementation of findClass will obtain a canonical path when
            // caching the clazz.
            BasePath packagePath = path.sliceNonCanonical(0, size);
            BaseClassTypeItem clazz = findClass(packagePath, path.get(size));
            if (clazz != null) {
                return clazz;
            }
        }
        return null;
    }
}
