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

package org.cojen.motto.model;

import java.util.List;
import java.util.RandomAccess;

/**
 * Defines an immutable path of strings.
 *
 * @author Brian S. O'Neill
 */
public sealed interface Path extends List<String>, RandomAccess permits BasePath {
    /**
     * Returns a canonical empty path.
     */
    public static Path from() {
        return BasePath.from();
    }

    /**
     * Returns a canonical path with one element.
     */
    public static Path from(String element) {
        return BasePath.from(element);
    }

    /**
     * Returns a canonical path with the given elements.
     */
    public static Path from(String... elements) {
        return BasePath.from(elements);
    }

    /**
     * Returns a canonical path parsed from a string.
     */
    public static Path parse(String path, char separator) {
        return BasePath.parse(path, separator);
    }

    /**
     * Returns a canonical Path with a new element appended to the end.
     */
    public Path append(String element);

    /**
     * Returns a Path with the last element removed.
     *
     * @throws java.util.NoSuchElementException if the path is empty
     */
    public Path trimLast();

    /**
     * Returns a Path over a range of elements.
     */
    public Path slice(int start, int size);

    /**
     * Returns the path elements concatenated with '.' separators.
     */
    @Override
    public String toString();

    public default String toString(char separator) {
        int size = size();
        if (size <= 1) {
            return size == 0 ? "" : String.valueOf(get(0));
        }
        return appendTo(new StringBuilder(), separator).toString();
    }

    /**
     * Append the path elements with '.' separators.
     */
    public default StringBuilder appendTo(StringBuilder b) {
        return appendTo(b, '.');
    }

    public default StringBuilder appendTo(StringBuilder b, char separator) {
        int size = size();
        for (int i=0; i<size; i++) {
            if (i > 0) {
                b.append(separator);
            }
            b.append(get(i));
        }
        return b;
    }
}
