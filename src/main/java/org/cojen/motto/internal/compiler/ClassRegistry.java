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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InterruptedIOException;
import java.io.InputStream;
import java.io.IOException;

import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;

import java.net.URI;

import java.nio.file.Files;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.cojen.motto.internal.model.BaseClassTypeItem;
import org.cojen.motto.internal.model.BasePath;
import org.cojen.motto.internal.model.ClassFinder;
import org.cojen.motto.internal.model.ExternalClass;
import org.cojen.motto.internal.model.NewClass;

/**
 * Defines a cached registry of new and external classes. Names are assumed to be mangled if
 * necessary.
 *
 * @author Brian S. O'Neill
 */
public abstract sealed class ClassRegistry implements Closeable, ClassFinder {
    /**
     * Returns an instance which first looks for external classes in the Java runtime (JRT),
     * then the boot modules, and then looks in the given directories and jar files.
     *
     * @param files class path of directories and jar files
     */
    public static ClassRegistry from(File... files) {
        ClassRegistry jrt = Delegate.from(JRT::new);
        ClassRegistry modules = Delegate.from(() -> fromBootModules());
        ClassRegistry first = new Ordered(jrt, modules);
        return from(first, files);
    }

    /**
     * Returns an instance which first looks for external classes in the given first instance,
     * and then looks in the given directories and jar files.
     *
     * @param first optional
     * @param files class path of directories and jar files
     */
    public static ClassRegistry from(ClassRegistry first, File... files) {
        return new Cached(doFrom(first, files));
    }

    /**
     * Returns an instance which first looks for external classes in the Java runtime (JRT),
     * then the boot modules, and then looks in directories and jar files from the system
     * classpath.
     */
    public static ClassRegistry fromClasspath() {
        return fromClasspath(System.getenv().get("CLASSPATH"));
    }

    /**
     * Returns an instance which first looks for external classes in the Java runtime (JRT),
     * then the boot modules, and then looks in directories and jar files from the given
     * classpath.
     *
     * @param classpath entries must be separated using the system path separator character
     */
    public static ClassRegistry fromClasspath(String classpath) {
        return from(splitClasspath(classpath));
    }

    /**
     * Returns an instance which first looks for external classes in the given first instance,
     * and then looks in directories and jar files from the system classpath.
     *
     * @param first optional
     */
    public static ClassRegistry fromClasspath(ClassRegistry first) {
        return fromClasspath(first, System.getenv().get("CLASSPATH"));
    }

    /**
     * Returns an instance which first looks for external classes in the given first instance,
     * and then looks in directories and jar files from the given classpath.
     *
     * @param first optional
     * @param classpath entries must be separated using the system path separator character
     */
    public static ClassRegistry fromClasspath(ClassRegistry first, String classpath) {
        return from(first, splitClasspath(classpath));
    }

    private static File[] splitClasspath(String classpath) {
        if (classpath == null || classpath.isEmpty()) {
            return new File[0];
        }

        BasePath path = BasePath.parse(classpath, File.pathSeparatorChar);

        var files = new File[path.size()];

        for (int i=0; i<files.length; i++) {
            String name = path.get(i);
            if (name.endsWith("*")) {
                name = name.substring(0, name.length() - 1);
            }
            files[i] = new File(name);
        }

        return files;
    }

    private static ClassRegistry doFrom(ClassRegistry first, File... files) {
        if (files == null || files.length == 0) {
            return first != null ? first : new Empty();
        }

        ClassRegistry[] sources;
        int offset;

        if (first == null) {
            if (files.length == 1) {
                return Delegate.from(new FileSelector(files[0]));
            }
            sources = new ClassRegistry[files.length];
            offset = 0;
        } else {
            sources = new ClassRegistry[1 + files.length];
            sources[0] = first;
            offset = 1;
        }

        for (int i=0; i<files.length; i++) {
            sources[offset + i] = Delegate.from(new FileSelector(files[i]));
        }

        return new Ordered(sources);
    }

    private static ClassRegistry fromBootModules() {
        return fromModules(ModuleLayer.boot());
    }

    private static ClassRegistry fromModules(ModuleLayer layer) {
        // FIXME: Try to group by packages to reduce the number of parallel searches.

        Set<ResolvedModule> modules = layer.configuration().modules();

        var sources = new ArrayList<ClassRegistry>();

        for (ResolvedModule module : modules) {
            try {
                sources.add(new ModuleReg(module.reference()));
            } catch (IOException e) {
            }
        }

        return Parallel.from(sources);
    }

    /**
     * @param className optional
     */
    static void importError(ErrorListener el,
                            BasePath packagePath, String className, String message)
    {
        BasePath namePath = className == null ? null : BasePath.from(className);
        importError(el, packagePath, namePath, message);
    }

    /**
     * @param namePath optional
     */
    static void importError(ErrorListener el,
                            BasePath packagePath, BasePath namePath, String message)
    {
        var b = new StringBuilder().append("error importing ");
        concat(b, packagePath, namePath).append(": ").append(message);

        el.error(null, 0, -1, 0, -1, b.toString());
    }

    private static StringBuilder concat(StringBuilder b, BasePath packagePath, BasePath namePath) {
        if (packagePath != null) {
            packagePath.appendTo(b);
        }

        if (namePath != null) {
            if (!packagePath.isEmpty()) {
                b.append('.');
            }
            namePath.appendTo(b);
        }

        return b;
    }

    ClassRegistry() {
    }

    /**
     * Register a prepared NewClass which is being compiled. All classes being compiled must be
     * prepared and registered before imports are resolved.
     *
     * @return false if a matching NewClass already exists
     */
    public final boolean tryRegister(NewClass clazz) {
        return register(clazz.packagePath(), clazz.namePath(), clazz) == clazz;
    }

    /**
     * @return the given class if registered, or else an existing class
     */
    BaseClassTypeItem register(BasePath packagePath, BasePath namePath, BaseClassTypeItem clazz) {
        // Only expected to be implemented by the Cached class.
        throw new UnsupportedOperationException();
    }

    /**
     * Checks if a package is known to exist. Searches the class path, module path, and any
     * registered NewClass instances which are currently being compiled.
     */
    public abstract boolean packageExists(BasePath packagePath) throws IOException;

    /**
     * Tries to find an outer class by its package and name. Searches the class path, module
     * path, and any registered NewClass instances which are currently being compiled.
     *
     * @param packagePath required, but can be empty
     * @param className required (must not be an inner class name)
     * @return null if not found
     */
    public final BaseClassTypeItem findClass(ErrorListener el,
                                             BasePath packagePath, String className)
        throws IOException
    {
        // FIXME: support inner classes by splitting on '$'

        return findClass(this, el, null, packagePath, BasePath.from(className));
    }

    /**
     * @param root first instance in the call stack
     * @param outer is null for outer classes
     * @param packagePath required
     * @param namePath required (first element is the outer class name)
     * @return null if not found
     */
    abstract BaseClassTypeItem findClass(ClassRegistry root, ErrorListener el,
                                         BaseClassTypeItem outer,
                                         BasePath packagePath, BasePath namePath)
        throws IOException;

    /**
     * Returns a new ClassLoader which finds existing classes, but it never finds newly
     * registered NewClass instances.
     */
    public final ClassLoader newClassLoader() {
        ClassRegistry registry = forExternal();
        return (registry == null ? new Empty() : registry).new Loader();
    }

    /**
     * Returns an instance which is only suitable for loading existing classes, and it doesn't
     * have a cache. Any intermediate exceptions when building the registry are dropped.
     *
     * @return null if nothing is loaded
     */
    abstract ClassRegistry forExternal();

    /**
     * @param className outer or inner class name, no package name, no dots (usually '$' instead)
     * @return null if not found
     */
    @Override // ClassFinder
    public BaseClassTypeItem findClass(BasePath packagePath, String className) throws IOException {
        return findClass(null, packagePath, className);
    }

    /**
     * @param className outer or inner class name, no package name, no dots (usually '$' instead)
     * @return null if not found
     */
    @Override // ClassFinder
    public abstract byte[] loadClassBytes(BasePath packagePath, String className)
        throws IOException;

    /**
     * Returns a String like so: "Map.class"
     *
     * @param className outer or inner class name, no package name
     */
    private static String fileName(BasePath className) {
        return filePath(null, className);
    }

    /**
     * Returns a String like so: "java/util/Map.class" or "java/util/Map$Entry.class"
     *
     * @param packagePath optional (can also be empty)
     * @param className outer or inner class name, no package name
     */
    private static String filePath(BasePath packagePath, BasePath className) {
        StringBuilder b;

        if (packagePath == null || packagePath.isEmpty()) {
            if (className.size() == 1) {
                return className.getFirst() + ".class";
            }
            b = new StringBuilder();
        } else {
            b = packagePath.appendTo(new StringBuilder(), '/').append('/');
        }

        return className.appendTo(b, '$').append(".class").toString();
    }

    /**
     * Returns a String like so: "java/util/Map.class" or "java/util/Map$Entry.class"
     *
     * @param packagePath optional (can also be empty)
     * @param className outer or inner class name, no package name, no dots (usually '$' instead)
     */
    private static String filePath(BasePath packagePath, String className) {
        if (packagePath == null || packagePath.isEmpty()) {
            return className + ".class";
        }

        return packagePath.appendTo(new StringBuilder(), '/').append('/')
            .append(className).append(".class").toString();
    }

    private BaseClassTypeItem registerNewClass(BaseClassTypeItem outer,
                                               BasePath packagePath, BasePath namePath)
    {
        if (outer != null) {
            // FIXME: Do something with the outer param.
            throw null;
        }
        var clazz = new ExternalClass(packagePath, namePath, this);
        return register(packagePath, namePath, clazz);
    }

    @FunctionalInterface
    static interface Selector {
        ClassRegistry open() throws IOException;
    }

    private static final class FileSelector implements Selector {
        private final File mFile;

        FileSelector(File file) {
            mFile = file;
        }

        @Override
        public ClassRegistry open() throws IOException {
            File[] files = mFile.listFiles();

            if (files == null) {
                if (mFile.getName().endsWith(".jar")) {
                    return new Jar(new JarFile(mFile));
                }
                return new Empty();
            }

            if (files.length == 0) {
                return new Empty();
            }

            var sources = new ArrayList<ClassRegistry>(files.length);
            boolean nonJars = false;

            for (File file : files) {
                if (!file.getName().endsWith(".jar") || file.isDirectory()) {
                    nonJars = true;
                } else {
                    sources.add(new Jar(new JarFile(file)));
                }
            }

            if (nonJars) {
                sources.add(new Directory(mFile));
            }

            return Parallel.from(sources);
        }
    }

    private static final class Delegate extends ClassRegistry implements Runnable {
        static Delegate from(Selector selector) {
            var delegate = new Delegate(selector);
            Thread.startVirtualThread(delegate);
            return delegate;
        }

        private final Selector mSelector;

        private volatile ClassRegistry mSource;

        private Delegate(Selector selector) {
            mSelector = selector;
        }

        @Override
        public synchronized void run() {
            if (mSource == null) {
                try {
                    mSource = mSelector.open();
                } catch (Exception e) {
                    mSource = new Broken(e);
                }
                notifyAll();
            }
        }

        @Override
        public boolean packageExists(BasePath packagePath) throws IOException {
            return source().packageExists(packagePath);
        }

        @Override
        BaseClassTypeItem findClass(ClassRegistry root, ErrorListener el,
                                    BaseClassTypeItem outer,
                                    BasePath packagePath, BasePath namePath)
            throws IOException
        {
            return source().findClass(root, el, outer, packagePath, namePath);
        }

        @Override
        ClassRegistry forExternal() {
            try {
                return source().forExternal();
            } catch (InterruptedIOException e) {
                return null;
            }
        }

        @Override
        public byte[] loadClassBytes(BasePath packagePath, String className) throws IOException {
            return source().loadClassBytes(packagePath, className);
        }

        @Override
        public void close() throws IOException {
            source().close();
        }

        private ClassRegistry source() throws InterruptedIOException {
            ClassRegistry source = mSource;
            return source != null ? source : waitForSource();
        }

        private synchronized ClassRegistry waitForSource() throws InterruptedIOException {
            while (true) {
                ClassRegistry source = mSource;
                if (source != null) {
                    return source;
                }
                try {
                    wait();
                } catch (InterruptedException e) {
                    throw new InterruptedIOException();
                }
            }
        }
    }

    private static final class Empty extends ClassRegistry {
        private Empty() {
        }

        @Override
        public boolean packageExists(BasePath packagePath) {
            return false;
        }

        @Override
        BaseClassTypeItem findClass(ClassRegistry root, ErrorListener el,
                                    BaseClassTypeItem outer,
                                    BasePath packagePath, BasePath namePath)
        {
            return null;
        }

        @Override
        ClassRegistry forExternal() {
            return null;
        }

        @Override
        public byte[] loadClassBytes(BasePath packagePath, String className) {
            return null;
        }

        @Override
        public void close() {
        }
    }

    private static final class Broken extends ClassRegistry {
        private final Exception mFailure;

        Broken(Exception failure) {
            mFailure = failure;
        }

        @Override
        public boolean packageExists(BasePath packagePath) throws IOException {
            throw new IOException(mFailure);
        }

        @Override
        BaseClassTypeItem findClass(ClassRegistry root, ErrorListener el,
                                    BaseClassTypeItem outer,
                                    BasePath packagePath, BasePath namePath)
            throws IOException
        {
            throw new IOException(mFailure);
        }

        @Override
        ClassRegistry forExternal() {
            return null;
        }

        @Override
        public byte[] loadClassBytes(BasePath packagePath, String className) {
            return null;
        }

        @Override
        public void close() {
        }
    }

    private static final class Cached extends ClassRegistry {
        private final ClassRegistry mSource;

        private final ConcurrentHashMap<BasePath, Boolean> mKnownPackages;
        private final ConcurrentHashMap<BasePath,
            ConcurrentHashMap<BasePath, BaseClassTypeItem>> mMainCache;

        private Cached(ClassRegistry source) {
            mSource = source;
            mKnownPackages = new ConcurrentHashMap<>();
            mMainCache = new ConcurrentHashMap<>();
        }

        /**
         * @return the given class if registered, or else an existing class
         */
        @Override
        BaseClassTypeItem register(BasePath packagePath, BasePath namePath,
                                   BaseClassTypeItem clazz)
        {
            ConcurrentHashMap<BasePath, BaseClassTypeItem> byClass = byClass(packagePath);
            BaseClassTypeItem existing = byClass.putIfAbsent(namePath.canonical(), clazz);
            if (existing != null) {
                return existing;
            }
            if (!mKnownPackages.containsKey(packagePath)) {
                mKnownPackages.put(packagePath.canonical(), true);
            }
            return clazz;
        }

        @Override
        public boolean packageExists(BasePath packagePath) throws IOException {
            Boolean result = mKnownPackages.get(packagePath);
            if (result == null) {
                result = mSource.packageExists(packagePath);
                mKnownPackages.put(packagePath.canonical(), result);
            }
            return result;
        }

        @Override
        BaseClassTypeItem findClass(ClassRegistry root, ErrorListener el,
                                    BaseClassTypeItem outer,
                                    BasePath packagePath, BasePath namePath)
            throws IOException
        {
            BaseClassTypeItem item = byClass(packagePath).get(namePath);

            if (item == null) {
                // If found, the method should have called registerNewClass, which in turn will
                // call the register method of this class to cache the item.
                item = mSource.findClass(root, el, outer, packagePath, namePath);
            }

            return item;
        }

        @Override
        ClassRegistry forExternal() {
            return this;
        }

        @Override
        public byte[] loadClassBytes(BasePath packagePath, String className) throws IOException {
            return mSource.loadClassBytes(packagePath, className);
        }

        boolean isNewClass(BasePath packagePath, BasePath namePath) {
            ConcurrentHashMap<BasePath, BaseClassTypeItem> byClass = mMainCache.get(packagePath);
            return byClass != null && byClass.get(namePath) instanceof NewClass;
        }

        private ConcurrentHashMap<BasePath, BaseClassTypeItem> byClass(BasePath packagePath) {
            ConcurrentHashMap<BasePath, BaseClassTypeItem> byClass = mMainCache.get(packagePath);

            if (byClass == null) {
                byClass = new ConcurrentHashMap<BasePath, BaseClassTypeItem>();
                var existing = mMainCache.putIfAbsent(packagePath.canonical(), byClass);
                if (existing != null) {
                    byClass = existing;
                }
            }

            return byClass;
        }

        @Override
        public void close() throws IOException {
            try {
                mSource.close();
            } finally {
                mKnownPackages.clear();
                mMainCache.clear();
            }
        }
    }

    private static abstract sealed class Multi extends ClassRegistry {
        final ClassRegistry[] mSources;

        Multi(ClassRegistry... sources) {
            mSources = sources;
        }

        @Override
        ClassRegistry forExternal() {
            ClassRegistry[] sources = mSources;

            if (sources.length != 0) {
                if (sources.length == 1) {
                    return sources[0].forExternal();
                }

                var list = new ArrayList<ClassRegistry>(sources.length);

                for (ClassRegistry source : sources) {
                    source = source.forExternal();
                    if (source != null) {
                        list.add(source);
                    }
                }

                if (!list.isEmpty()) {
                    return new Ordered(list.toArray(ClassRegistry[]::new));
                }
            }

            return null;
        }

        @Override
        public final byte[] loadClassBytes(BasePath packagePath, String className)
            throws IOException
        {
            for (ClassRegistry source : mSources) {
                byte[] bytes = source.loadClassBytes(packagePath, className);
                if (bytes != null) {
                    return bytes;
                }
            }
            return null;
        }

        @Override
        public void close() throws IOException {
            IOException ex = null;

            for (ClassRegistry source : mSources) {
                try {
                    source.close();
                } catch (IOException e) {
                    if (ex == null) {
                        ex = e;
                    }
                }
            }

            if (ex != null) {
                throw ex;
            }
        }
    }

    private static final class Ordered extends Multi {
        Ordered(ClassRegistry... sources) {
            super(sources);
        }

        @Override
        public boolean packageExists(BasePath packagePath) throws IOException {
            for (ClassRegistry source : mSources) {
                if (source.packageExists(packagePath)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        BaseClassTypeItem findClass(ClassRegistry root, ErrorListener el,
                                    BaseClassTypeItem outer,
                                    BasePath packagePath, BasePath namePath)
            throws IOException
        {
            for (ClassRegistry source : mSources) {
                BaseClassTypeItem item = source.findClass(root, el, outer, packagePath, namePath);
                if (item != null) {
                    return item;
                }
            }
            return null;
        }
    }

    private static final class Parallel extends Multi {
        static ClassRegistry from(ArrayList<ClassRegistry> sources) {
            int size = sources.size();

            if (size <= 1) {
                return size == 0 ? new Empty() : sources.getFirst();
            }

            return new Parallel(sources.toArray(ClassRegistry[]::new));
        }

        private final ExecutorService mExecutor;

        private Parallel(ClassRegistry... sources) {
            super(sources);
            mExecutor = Executors.newVirtualThreadPerTaskExecutor();
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean packageExists(BasePath packagePath) throws IOException {
            Future<Boolean>[] tasks = new Future[mSources.length];

            for (int i=0; i<tasks.length; i++) {
                ClassRegistry source = mSources[i];
                tasks[i] = mExecutor.submit(() -> source.packageExists(packagePath));
            }

            boolean result = false;
            Throwable ex = null;

            for (Future<Boolean> task : tasks) {
                try {
                    result |= task.get();
                } catch (Throwable e) {
                    if (ex == null) {
                        ex = e;
                    }
                }
            }

            if (result) {
                return true;
            }

            checkException(ex);

            return false;
        }

        @Override
        @SuppressWarnings("unchecked")
        BaseClassTypeItem findClass(ClassRegistry root, ErrorListener el,
                                    BaseClassTypeItem outer,
                                    BasePath packagePath, BasePath namePath)
            throws IOException
        {
            Future<BaseClassTypeItem>[] tasks = new Future[mSources.length];

            for (int i=0; i<tasks.length; i++) {
                ClassRegistry source = mSources[i];
                tasks[i] = mExecutor.submit
                    (() -> source.findClass(root, el, outer, packagePath, namePath));
            }

            BaseClassTypeItem result = null;
            boolean duplicates = false;
            Throwable ex = null;

            for (Future<BaseClassTypeItem> task : tasks) {
                try {
                    BaseClassTypeItem item = task.get();
                    if (item == null) {
                        continue;
                    }

                    if (result == null) {
                        result = item;
                        continue;
                    }

                    if (!duplicates) {
                        // FIXME: provide the origin info too (jar file, etc.)
                        String message = "duplicate definitions";

                        if (el == null) {
                            var b = new StringBuilder().append("Cannot load ");
                            concat(b, packagePath, namePath).append(": ").append(message);
                            throw new IOException(b.toString());
                        }

                        importError(el, packagePath, namePath, message);
                    }

                    duplicates = true;
                } catch (Throwable e) {
                    if (ex == null) {
                        ex = e;
                    }
                }
            }

            if (result != null) {
                return result;
            }

            checkException(ex);

            return null;
        }

        @Override
        public void close() throws IOException {
            mExecutor.shutdown();
            super.close();
        }

        private static void checkException(Throwable ex) throws IOException {
            if (ex != null) {
                if (ex instanceof ExecutionException) {
                    ex = ex.getCause();
                }
                try {
                    throw ex;
                } catch (InterruptedException e) {
                    throw new InterruptedIOException();
                } catch (IOException | RuntimeException | Error e) {
                    throw e;
                } catch (Throwable e) {
                    throw new IOException(e);
                }
            }
        }
    }

    private static final class Directory extends ClassRegistry {
        private final File mDir;

        Directory(File dir) {
            mDir = dir;
        }

        @Override
        public boolean packageExists(BasePath packagePath) {
            return expandDir(packagePath).isDirectory();
        }

        @Override
        BaseClassTypeItem findClass(ClassRegistry root, ErrorListener el,
                                    BaseClassTypeItem outer,
                                    BasePath packagePath, BasePath namePath)
            throws IOException
        {
            File file = new File(expandDir(packagePath), fileName(namePath));

            if (file.exists()) {
                return root.registerNewClass(outer, packagePath, namePath);
            }

            return null;
        }

        @Override
        ClassRegistry forExternal() {
            return this;
        }

        @Override
        public byte[] loadClassBytes(BasePath packagePath, String className) throws IOException {
            File file = new File(expandDir(packagePath), className + ".class");

            if (file.exists()) {
                try (var in = new FileInputStream(file)) {
                    return in.readAllBytes();
                } catch (FileNotFoundException e) {
                }
            }

            return null;
        }

        private File expandDir(BasePath packagePath) {
            File dir = mDir;
            for (String sub : packagePath) {
                dir = new File(dir, sub);
            }
            return dir;
        }

        @Override
        public void close() {
        }
    }

    private static final class Jar extends ClassRegistry {
        private final JarFile mJar;

        Jar(JarFile jar) {
            mJar = jar;
        }

        @Override
        public boolean packageExists(BasePath packagePath) {
            JarEntry entry = mJar.getJarEntry(packagePath.toString('/'));
            return entry != null && entry.isDirectory();
        }

        @Override
        BaseClassTypeItem findClass(ClassRegistry root, ErrorListener el,
                                    BaseClassTypeItem outer,
                                    BasePath packagePath, BasePath namePath)
            throws IOException
        {
            JarEntry entry = mJar.getJarEntry(filePath(packagePath, namePath));

            if (entry == null || entry.isDirectory()) {
                return null;
            }

            return root.registerNewClass(outer, packagePath, namePath);
        }

        @Override
        ClassRegistry forExternal() {
            return this;
        }

        @Override
        public byte[] loadClassBytes(BasePath packagePath, String className) throws IOException {
            JarEntry entry = mJar.getJarEntry(filePath(packagePath, className));

            if (entry == null || entry.isDirectory()) {
                return null;
            }

            try (InputStream in = mJar.getInputStream(entry)) {
                return in.readAllBytes();
            }
        }

        @Override
        public void close() throws IOException {
            mJar.close();
        }
    }

    /**
     * Finds Java RunTime classes.
     */
    private static final class JRT extends ClassRegistry {
        private final FileSystem mFileSystem;

        JRT() {
            mFileSystem = FileSystems.getFileSystem(URI.create("jrt:/"));
        }

        @Override
        public boolean packageExists(BasePath packagePath) {
            return Files.isDirectory(fsPackagePath(packagePath));
        }

        @Override
        BaseClassTypeItem findClass(ClassRegistry root, ErrorListener el,
                                    BaseClassTypeItem outer,
                                    BasePath packagePath, BasePath namePath)
            throws IOException
        {
            var fsPath = fsPackagePath(packagePath);

            if (!Files.isDirectory(fsPath)) {
                return null;
            }

            String filePath = filePath(packagePath, namePath);

            // Search all the modules that this package is defined from.
            for (java.nio.file.Path p : Files.list(fsPath).toList()) {
                if (Files.isSymbolicLink(p) && Files.isDirectory(p)) {
                    java.nio.file.Path fullPath = p.resolve(filePath);
                    if (Files.exists(fullPath)) {
                        return root.registerNewClass(outer, packagePath, namePath);
                    }
                }
            }

            return null;
        }

        @Override
        ClassRegistry forExternal() {
            // No need to explicitly load classes because the platform ClassLoader does it.
            return null;
        }

        @Override
        public byte[] loadClassBytes(BasePath packagePath, String className) throws IOException {
            var fsPath = fsPackagePath(packagePath);

            if (!Files.isDirectory(fsPath)) {
                return null;
            }

            String filePath = filePath(packagePath, className);

            // Search all the modules that this package is defined from.
            for (java.nio.file.Path p : Files.list(fsPath).toList()) {
                if (Files.isSymbolicLink(p) && Files.isDirectory(p)) {
                    java.nio.file.Path fullPath = p.resolve(filePath);
                    if (Files.exists(fullPath)) {
                        return Files.readAllBytes(fullPath);
                    }
                }
            }

            return null;
        }

        @Override
        public void close() {
            // JrtFileSystem.close() is unsupported.
        }

        private java.nio.file.Path fsPackagePath(BasePath packagePath) {
            return mFileSystem.getPath("packages", packagePath.toString());            
        }
    }

    private static final class ModuleReg extends ClassRegistry {
        private final ModuleReference mRef;
        private final ModuleReader mReader;

        ModuleReg(ModuleReference ref) throws IOException {
            mRef = ref;
            mReader = ref.open();
        }

        @Override
        public boolean packageExists(BasePath packagePath) throws IOException {
            return !mReader.find(packagePath.toString('/') + '/').isEmpty();
        }

        @Override
        BaseClassTypeItem findClass(ClassRegistry root, ErrorListener el,
                                    BaseClassTypeItem outer,
                                    BasePath packagePath, BasePath namePath)
            throws IOException
        {
            String filePath = filePath(packagePath, namePath);

            if (mReader.find(filePath).isEmpty()) {
                return null;
            }

            return root.registerNewClass(outer, packagePath, namePath);
        }

        @Override
        ClassRegistry forExternal() {
            return this;
        }

        @Override
        public byte[] loadClassBytes(BasePath packagePath, String className) throws IOException {
            Optional<InputStream> inRef = mReader.open(filePath(packagePath, className));

            if (inRef.isEmpty()) {
                return null;
            }

            try (InputStream in = inRef.get()) {
                return in.readAllBytes();
            }
        }

        @Override
        public void close() throws IOException {
            mReader.close();
        }
    }

    final class Loader extends ClassLoader {
        static {
            registerAsParallelCapable();
        }

        Loader() {
            super(getPlatformClassLoader());
        }

        @Override
        public Class<?> loadClass(String fullName) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(fullName)) {
                Class<?> clazz = findLoadedClass(fullName);

                if (clazz != null) {
                    return clazz;
                }

                BasePath packagePath = null;
                String className = null;

                if (ClassRegistry.this instanceof Cached cached) {
                    BasePath fullPath = packagePath = BasePath.parse(fullName, '.');
                    packagePath = fullPath.trimLastNonCanonical();
                    className = fullPath.getLast();
                    BasePath namePath = BasePath.parse(className, '$');
                    if (cached.isNewClass(packagePath, namePath)) {
                        throw new ClassNotFoundException(fullName);
                    }
                }

                try {
                    return getParent().loadClass(fullName);
                } catch (ClassNotFoundException e) {
                    // Parent doesn't have it.
                }

                if (packagePath == null) {
                    BasePath fullPath = packagePath = BasePath.parse(fullName, '.');
                    packagePath = fullPath.trimLastNonCanonical();
                    className = fullPath.getLast();
                }

                byte[] bytes;
                try {
                    bytes = loadClassBytes(packagePath, className);
                } catch (IOException e) {
                    throw new ClassNotFoundException(fullName, e);
                }

                if (bytes == null) {
                    throw new ClassNotFoundException(fullName);
                }

                return defineClass(fullName, bytes, 0, bytes.length);
            }
        }

        // FIXME: protected URL findResource(String name)

        // FIXME: protected Enumeration<URL> findResources(String name) throws IOException
    }
}
