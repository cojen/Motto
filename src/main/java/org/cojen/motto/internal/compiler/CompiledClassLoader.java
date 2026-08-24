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

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Allows newly compiled classes (represented as byte arrays) to be loaded. Newly compiled
 * classes have priority over existing classes, which are found by the parent ClassLoader.
 *
 * @author Brian S. O'Neill
 */
public final class CompiledClassLoader extends ClassLoader {
    static {
        registerAsParallelCapable();
    }

    private final ConcurrentHashMap<String, byte[]> mRegistered;

    public CompiledClassLoader(ClassLoader parent) {
        super(Objects.requireNonNull(parent));
        mRegistered = new ConcurrentHashMap<>();
    }

    /**
     * Register newly compiled classes. This must be called before attempting to load any
     * classes.
     */
    public void register(Collection<Map<String, byte[]>> classes) {
        for (Map<String, byte[]> map : classes) {
            register(map);
        }
    }

    /**
     * Register newly compiled classes. This must be called before attempting to load any
     * classes.
     */
    public void register(Map<String, byte[]> classes) {
        for (Map.Entry<String, byte[]> e : classes.entrySet()) {
            register(e.getKey(), e.getValue());
        }
    }

    /**
     * Register a newly compiled class by its fully qualified name. This must be called before
     * attempting to load any classes.
     */
    public void register(String className, byte[] bytes) {
        mRegistered.put(className, bytes);
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> clazz = findLoadedClass(name);

            if (clazz != null) {
                return clazz;
            }

            byte[] bytes = mRegistered.get(name);

            if (bytes != null) {
                clazz = defineClass(name, bytes, 0, bytes.length);
                mRegistered.remove(name, bytes);
                return clazz;
            }
        }

        return getParent().loadClass(name);
    }
}
