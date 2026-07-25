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

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
abstract sealed class BasePath extends AbstractList<String> implements Path {
    /**
     * Returns an empty path.
     */
    public static BasePath from() {
        return Empty.THE;
    }

    /**
     * Returns a canonical path with one element.
     */
    public static BasePath from(String element) {
        return InternSet.apply(new Single(element));
    }

    /**
     * Returns a canonical path with the given elements.
     */
    public static BasePath from(String... elements) {
        if (elements == null || elements.length == 0) {
            return Empty.THE;
        } else if (elements.length == 1) {
            return from(elements[0]);
        } else {
            return InternSet.apply(new Multi(elements.clone()));
        }
    }

    /**
     * Returns a canonical path parsed from a string. If a separator isn't immediately followed
     * by a non-separator, then it's not parsed as a separator.
     */
    public static BasePath parse(String path, char separator) {
        var list = new ArrayList<String>();
        int s = 0;
        while (true) {
            int e = findSeparatorIndex(path, separator, s);
            if (e < 0) {
                list.add(path.substring(s));
                break;
            }
            list.add(path.substring(s, e));
            s = e + 1;
        }
        return from(list.toArray(String[]::new));
    }

    private static int findSeparatorIndex(String path, char separator, int fromIndex) {
        while (true) {
            int index = path.indexOf(separator, fromIndex);
            if (index < 0) {
                return index;
            }
            fromIndex = index + 1;
            if (fromIndex >= path.length()) {
                return -1;
            }
            if (path.charAt(fromIndex) != separator) {
                return index;
            }
            while (true) {
                if (++fromIndex >= path.length()) {
                    return -1;
                }
                if (path.charAt(fromIndex) != separator) {
                    break;
                }
            }
        }
    }

    private static final int EMPTY_HASHCODE = 571140017;

    private static int hashCode(String element) {
        return EMPTY_HASHCODE * 31 + Objects.hashCode(element);
    }

    private static int hashCode(String[] elements, int start, int end) {
        int hash = EMPTY_HASHCODE;
        while (start < end) {
            hash = hash * 31 + Objects.hashCode(elements[start++]);
        }
        return hash;
    }

    private final int mHashCode;

    BasePath(int hashCode) {
        mHashCode = hashCode;
    }

    @Override
    public abstract BasePath append(String element);

    @Override
    public BasePath trimLast() {
        return trimLastNonCanonical().canonical();
    }

    /**
     * Returns a Path over a range of elements, which references the element array of the
     * original Path. Call "canonical" to get an instance which references a trimmed array.
     */
    public abstract BasePath trimLastNonCanonical();

    @Override
    public BasePath slice(int start, int size) {
        return sliceNonCanonical(start, size).canonical();
    }

    /**
     * Returns a Path over a range of elements, which references the element array of the
     * original Path. Call "canonical" to get an instance which references a trimmed array.
     */
    public abstract BasePath sliceNonCanonical(int start, int size);

    /**
     * Returns a canonical Path instance which doesn't reference the array of another Path.
     */
    public BasePath canonical() {
        return this;
    }

    @Override
    public final int hashCode() {
        return mHashCode;
    }

    @Override
    public final boolean equals(Object obj) {
        int size;
        return obj == this || obj instanceof Path p
            && (size = size()) == p.size() && doEquals(size, p);
    }

    /**
     * @param other size is known to be equal
     */
    protected boolean doEquals(int size, Path other) {
        for (int i=0; i<size; i++) {
            if (!Objects.equals(get(i), other.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the path elements concatenated with '.' separators.
     */
    @Override
    public final String toString() {
        return toString('.');
    }

    private static final class Empty extends BasePath {
        static final Empty THE = new Empty();

        private Empty() {
            super(EMPTY_HASHCODE);
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public String get(int index) {
            throw new IndexOutOfBoundsException();
        }

        @Override
        public Single append(String element) {
            return InternSet.apply(new Single(element));
        }

        @Override
        public BasePath trimLast() {
            throw new NoSuchElementException();
        }

        @Override
        public BasePath trimLastNonCanonical() {
            throw new NoSuchElementException();
        }

        @Override
        public Empty slice(int start, int size) {
            Objects.checkFromIndexSize(start, size, 0);
            return this;
        }

        @Override
        public Empty sliceNonCanonical(int start, int size) {
            return slice(start, size);
        }

        @Override
        protected boolean doEquals(int size, Path other) {
            return other instanceof Empty || super.doEquals(size, other);
        }
    }

    private static final class Single extends BasePath {
        private final String mElement;

        private Single(String element) {
            super(BasePath.hashCode(element));
            mElement = element;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public String get(int index) {
            if (index != 0) {
                throw new IndexOutOfBoundsException();
            }
            return mElement;
        }

        @Override
        public Multi append(String element) {
            return InternSet.apply(new Multi(mElement, element));
        }

        @Override
        public Empty trimLast() {
            return Empty.THE;
        }

        @Override
        public Empty trimLastNonCanonical() {
            return trimLast();
        }

        @Override
        public BasePath slice(int start, int size) {
            Objects.checkFromIndexSize(start, size, 1);
            return size == 0 ? Empty.THE : this;
        }

        @Override
        public BasePath sliceNonCanonical(int start, int size) {
            return slice(start, size);
        }

        @Override
        protected boolean doEquals(int size, Path other) {
            return other instanceof Single s
                ? Objects.equals(mElement, s.mElement)
                : super.doEquals(size, other);
        }
    }

    private static final class Multi extends BasePath {
        private final String[] mElements;

        private Multi(String... elements) {
            super(BasePath.hashCode(elements, 0, elements.length));
            mElements = elements;
        }

        @Override
        public int size() {
            return mElements.length;
        }

        @Override
        public String get(int index) {
            return mElements[index];
        }

        @Override
        public Multi append(String element) {
            var newElements = new String[mElements.length + 1];
            System.arraycopy(mElements, 0, newElements, 0, mElements.length);
            newElements[newElements.length - 1] = element;
            return InternSet.apply(new Multi(newElements));
        }

        @Override
        public BasePath trimLastNonCanonical() {
            int length = mElements.length;
            return length == 2 ? new Single(mElements[0]) : new Slice(mElements, 0, length - 1);
        }

        @Override
        public BasePath sliceNonCanonical(int start, int size) {
            Objects.checkFromIndexSize(start, size, mElements.length);
            if (size >= mElements.length) {
                return this;
            } else if (size > 1) {
                return new Slice(mElements, start, size);
            } else {
                return size == 0 ? Empty.THE : new Single(mElements[start]);
            }
        }

        @Override
        protected boolean doEquals(int size, Path other) {
            return other instanceof Multi m
                ? Arrays.equals(mElements, m.mElements)
                : super.doEquals(size, other);
        }
    }

    private static final class Slice extends BasePath {
        private final String[] mElements;
        private final int mStart, mLength;

        private Slice(String[] elements, int start, int length) {
            super(BasePath.hashCode(elements, start, start + length));
            mElements = elements;
            mStart = start;
            mLength = length;
        }

        @Override
        public int size() {
            return mLength;
        }

        @Override
        public String get(int index) {
            return mElements[mStart + Objects.checkIndex(index, mLength)];
        }

        @Override
        public Multi append(String element) {
            var newElements = new String[mLength + 1];
            System.arraycopy(mElements, mStart, newElements, 0, mLength);
            newElements[newElements.length - 1] = element;
            return InternSet.apply(new Multi(newElements));
        }

        @Override
        public BasePath trimLastNonCanonical() {
            return mLength == 2 ? new Single(mElements[mStart])
                : new Slice(mElements, mStart, mLength - 1);
        }

        @Override
        public BasePath sliceNonCanonical(int start, int size) {
            Objects.checkFromIndexSize(start, size, mLength);
            if (size >= mLength) {
                return this;
            } else if (size > 1) {
                return new Slice(mElements, mStart + start, size);
            } else {
                return size == 0 ? Empty.THE : new Single(mElements[mStart + start]);
            }
        }

        @Override
        public Multi canonical() {
            return InternSet.apply
                (new Multi(Arrays.copyOfRange(mElements, mStart, mStart + mLength)));
        }

        @Override
        protected boolean doEquals(int size, Path other) {
            return other instanceof Slice s
                ? Arrays.equals(mElements, mStart, mStart + mLength,
                                s.mElements, s.mStart, s.mStart + s.mLength)
                : super.doEquals(size, other);
        }
    }
}
