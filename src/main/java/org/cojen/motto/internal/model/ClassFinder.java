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

package org.cojen.motto.internal.model;

import java.io.IOException;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public interface ClassFinder {
    /**
     * Tries to find a class by package name and class name.
     *
     * @param className outer or inner class name, no package name, no dots (usually '$' instead)
     * @return null if not found
     */
    public BaseClassTypeItem findClass(BasePath packagePath, String className) throws IOException;

    /**
     * Tries to loads class bytes by package name and class name.
     *
     * @param className outer or inner class name, no package name, no dots (usually '$' instead)
     * @return null if not found
     */
    public byte[] loadClassBytes(BasePath packagePath, String className) throws IOException;
}
