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

package org.cojen.motto.internal.util;

import java.util.AbstractSet;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Defines a simple set which has a smaller memory footprint than java.util.HashSet. Nulls
 * aren't permitted.
 *
 * @author Brian S. O'Neill
 */
public final class SimpleSet<E> extends AbstractSet<E> {
    private Entry<E>[] mEntries;
    private int mSize;

    @SuppressWarnings("unchecked")
    public SimpleSet() {
        mEntries = new Entry[2];
    }

    @Override
    public int size() {
        return mSize;
    }

    @Override
    public boolean contains(Object obj) {
        Entry<E>[] entries = mEntries;
        int slot = obj.hashCode() & (entries.length - 1);

        for (Entry<E> e = entries[slot]; e != null; e = e.mNext) {
            if (obj.equals(e.mObj)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public Iterator<E> iterator() {
        var it = new Iterator<E>() {
            private int mIndex;
            private Entry<E> mNext;

            @Override
            public boolean hasNext() {
                return mNext != null;
            }

            @Override
            public E next() {
                Entry<E> next = mNext;
                if (next == null) {
                    throw new NoSuchElementException();
                }
                E obj = next.mObj;
                next = next.mNext;
                if (next == null) {
                    next = advance(mIndex);
                }
                mNext = next;
                return obj;
            }

            private Entry<E> advance(int index) {
                Entry<E>[] entries = mEntries;
                while (true) {
                    if (++index >= entries.length) {
                        return null;
                    }
                    Entry<E> next = entries[index];
                    if (next != null) {
                        mIndex = index;
                        return next;
                    }
                }
            }
        };

        it.mNext = it.advance(-1);

        return it;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean add(E obj) {
        Entry<E>[] entries = mEntries;
        int slot = obj.hashCode() & (entries.length - 1);

        for (Entry<E> e = entries[slot]; e != null; e = e.mNext) {
            if (obj.equals(e.mObj)) {
                return false;
            }
        }

        int size = mSize;

        if (size >= entries.length && entries.length < (1 << 30)) {
            // Rehash.
            var newEntries = new Entry[entries.length << 1];
            for (int i=0; i<entries.length; i++) {
                for (var e = entries[i]; e != null; ) {
                    Entry next = e.mNext;
                    slot = e.mObj.hashCode() & (newEntries.length - 1);
                    e.mNext = newEntries[slot];
                    newEntries[slot] = e;
                    e = next;
                }
            }
            mEntries = entries = newEntries;
            slot = obj.hashCode() & (entries.length - 1);
        }

        var newEntry = new Entry<E>(obj);
        newEntry.mNext = entries[slot];
        entries[slot] = newEntry;
        mSize = size + 1;

        return true;
    }

    @Override
    public boolean remove(Object obj) {
        Entry<E>[] entries = mEntries;
        int slot = obj.hashCode() & (entries.length - 1);

        for (Entry<E> e = entries[slot], prev = null; e != null; e = e.mNext) {
            if (obj.equals(e.mObj)) {
                if (prev == null) {
                    entries[slot] = e.mNext;
                } else {
                    prev.mNext = e.mNext;
                }
                mSize--;
                return true;
            } else {
                prev = e;
            }
        }

        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void clear() {
        mEntries = new Entry[2];
        mSize = 0;
    }

    private static final class Entry<E> {
        final E mObj;
        Entry<E> mNext;

        Entry(E obj) {
            mObj = obj;
        }
    }
}
