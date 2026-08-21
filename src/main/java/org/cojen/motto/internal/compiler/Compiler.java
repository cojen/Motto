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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

import org.cojen.motto.internal.model.BaseClassTypeItem;
import org.cojen.motto.internal.model.BasePath;
import org.cojen.motto.internal.model.NewClass;

import org.cojen.motto.internal.parser.ClassDefinitionStatement;
import org.cojen.motto.internal.parser.CompilationUnit;
import org.cojen.motto.internal.parser.DefinitionStatement;
import org.cojen.motto.internal.parser.Parser;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class Compiler implements ErrorListener, Closeable {
    private final ErrorListener mErrorListener;
    private final ClassRegistry mClassRegistry;

    private final ExecutorService mExecutor;
    private final Map<File, CompileTask> mCompileTasks;

    private final ConcurrentHashMap<File, Map<String, byte[]>> mCompleted;

    private volatile int mNumErrors;

    public Compiler(ErrorListener el, ClassRegistry registry) {
        mErrorListener = Objects.requireNonNull(el);
        mClassRegistry = registry == null ? ClassRegistry.from() : registry;
        mExecutor = Executors.newVirtualThreadPerTaskExecutor();
        mCompileTasks = new ConcurrentHashMap<>();
        mCompleted = new ConcurrentHashMap<>();
    }

    @Override
    public void close() throws IOException {
        mClassRegistry.close();
    }

    /**
     * Begins compilation tasks for one or more source files. Source files (by absolute path)
     * are compiled at most once.
     */
    public void compile(List<File> sourceFiles) {
        for (File file : sourceFiles) {
            mCompileTasks.computeIfAbsent(file.getAbsoluteFile(), sourceFile -> {
                var task = new CompileTask(sourceFile);
                mExecutor.execute(task);
                return task;
            });
        }
    }

    /**
     * Begins compilation tasks for all matching files found in the given directory and all sub
     * directories. Source files (by absolute path) are compiled at most once.
     *
     * @param fileExtension must start with a "."
     */
    public void compile(File directory, String fileExtension) {
        var list = new ArrayList<File>();
        gatherFiles(list, directory, fileExtension);
        compile(list);
    }

    private static void gatherFiles(ArrayList<File> list, File file, String fileExtension) {
        if (file.isFile()) {
            if (file.getName().endsWith(fileExtension)) {
                list.add(file);
            }
            return;
        }

        File[] files = file.listFiles();

        if (files != null) {
            for (File f : files) {
                gatherFiles(list, f, fileExtension);
            }
        }
    }

    /**
     * Waits for all active compilation tasks to finish. This method should be called exactly
     * once after all the sources have been added, in order for the compilation tasks to
     * advance past the prepared phase.
     *
     * <p>No files are written by this method. The caller must write them, but only if no
     * errors are reported.
     *
     * @return all the compiled classes, keyed by source file and fully qualified class name;
     * the first entry of each sub map is the outermost enclosing class
     */
    public Map<File, Map<String, byte[]>> waitForCompletion() throws InterruptedException {
        for (CompileTask task : mCompileTasks.values()) {
            task.waitUntilPrepared();
        }

        // At this point, all the source files have been prepared and registered. This means
        // that importable members can be mutually seen by all of the compiled classes. The
        // full definitions of these members might not be available yet, and so code generation
        // tasks might block waiting until they become available.

        // Allow the tasks to start generating code.

        for (CompileTask task : mCompileTasks.values()) {
            task.generating();
        }

        // Wait for all the tasks to fully finish.

        for (CompileTask task : mCompileTasks.values()) {
            task.waitUntilFinished();
        }

        return mCompleted;
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

    /**
     * Called by CompileTask.run.
     */
    private void compile(CompileTask task) throws IOException {
        var env = new CompilationEnv(this, task.mSourceFile);

        CompilationUnit cu;
        try (var parser = new Parser(env)) {
            cu = parser.parse();
        }

        if (env.numErrors() != 0) {
            return;
        }

        // FIXME: Check this: package <name> clashes with class of same name
        // FIXME: Also do this: class <name> clashes with package of same name
        BasePath packagePath = cu.packagePath();

        {
            int ix = packagePath.size();
            File dir = env.sourceFile();
            while (--ix >= 0) {
                dir = dir.getParentFile();
                if (dir == null || !packagePath.get(ix).equals(dir.getName())) {
                    env.error(cu.packageName, "file directory path must match the package name");
                    return;
                }
            }
        }

        ClassDefinitionStatement cds = null;

        for (DefinitionStatement st : cu.definitions) {
            if (st instanceof ClassDefinitionStatement) {
                if (cds != null) {
                    env.error(cds, "at most one class can be defined per file");
                } else {
                    cds = (ClassDefinitionStatement) st;
                }
            } else {
                env.error(st, "must be a class definition");
            }
        }

        if (cds == null) {
            return;
        }

        NewClass clazz = cds.prepareNewClass(env, packagePath);

        if (clazz == null) {
            return;
        }

        task.prepared();

        try {
            task.waitUntilGenerating();
        } catch (InterruptedException e) {
            error(env.sourceFile(), e.toString());
            return;
        }

        // Imports can be resolved now that all classes being compiled have been prepared. This
        // means that they are known to exist, and all members which can be imported by other
        // classes are known as well.

        env.resolveImports(cu);

        cds.resolveClass(env);

        if (env.numErrors() != 0) {
            return;
        }

        cds.accept(new ModelGenerator(env), null);

        if (env.numErrors() != 0) {
            return;
        }

        generateCode(clazz);
    }

    /**
     * @param clazz must be ready for code generation and have reported no errors
     */
    private void generateCode(NewClass clazz) {
        // FIXME: generateCode
    }

    private final class CompileTask implements Runnable {
        final File mSourceFile;

        // 1: prepared, 2: generating, 3: finished
        private int mPhase;

        CompileTask(File sourceFile) {
            mSourceFile = sourceFile;
        }

        @Override
        public void run() {
            try {
                compile(this);
            } catch (Throwable e) {
                uncaught(mSourceFile, e);
            } finally {
                // Finished.
                setPhase(0, 3);
            }
        }

        /**
         * Advances to the prepared phase (1).
         */
        void prepared() {
            setPhase(0, 1);
        }

        /**
         * Advances to the generating phase (2).
         *
         * @throws IllegalStateException if not reached the prepared phase (1)
         */
        void generating() {
            setPhase(1, 2);
        }

        private synchronized void setPhase(int minPhase, int targetPhase) {
            if (mPhase < minPhase) {
                throw new IllegalStateException();
            }
            mPhase = Math.max(mPhase, targetPhase);
            notifyAll();
        }

        /**
         * Wait until the prepared phase (1) is reached.
         */
        void waitUntilPrepared() throws InterruptedException {
            waitUntil(1);
        }

        /**
         * Wait until the generating phase (2) is reached.
         */
        void waitUntilGenerating() throws InterruptedException {
            waitUntil(2);
        }

        /**
         * Wait until the finished phase (3) is reached.
         */
        void waitUntilFinished() throws InterruptedException {
            waitUntil(3);
        }

        private synchronized void waitUntil(int targetPhase) throws InterruptedException {
            while (mPhase < targetPhase) {
                wait();
            }
        }
    }
}
