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

import java.util.NoSuchElementException;
import java.util.Set;

import java.util.stream.Stream;

import org.cojen.motto.model.CallSignature;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class NewClass extends BaseClassTypeItem {
    // 1: initially available, 2: supertype cycle detection has been performed (if necessary)
    private int mAvailable;

    NewClass(int modifierBits, BasePath packagePath, BasePath namePath) {
        super(modifierBits, packagePath, namePath);
    }

    @Override
    public org.cojen.maker.Type asMakerType() {
        // FIXME
        throw null;
    }

    @Override
    public BaseClassTypeItem superType() {
        try {
            if (waitUntilAvailable() < 2) {
                checkForInheritanceCycle();
            }
        } catch (InterruptedException e) {
            return null;
        }

        return super.superType();
    }

    @Override
    public Set<? extends BaseClassTypeItem> interfaces() {
        try {
            if (waitUntilAvailable() < 2) {
                checkForInheritanceCycle();
            }
        } catch (InterruptedException e) {
            return Set.of();
        }

        return super.interfaces();
    }

    @Override
    public int numFields() { 
        try {
            waitUntilAvailable();
        } catch (InterruptedException e) {
            return 0;
        }

        return super.numFields();
    }

    @Override
    public Stream<? extends TheFieldItem> fields() {
        try {
            waitUntilAvailable();
        } catch (InterruptedException e) {
            return Stream.empty();
        }

        return super.fields();
    }

    @Override
    public TheFieldItem field(String name) {
        try {
            waitUntilAvailable();
        } catch (InterruptedException e) {
            throw new NoSuchElementException();
        }

        return super.field(name);
    }

    @Override
    public int numMethods() {
        try {
            waitUntilAvailable();
        } catch (InterruptedException e) {
            return 0;
        }

        return super.numMethods();
    }

    @Override
    public Stream<? extends BaseCallableItem> methods() {
        try {
            waitUntilAvailable();
        } catch (InterruptedException e) {
            return Stream.empty();
        }

        return super.methods();
    }

    @Override
    public Stream<? extends BaseCallableItem> methods(String name) {
        try {
            waitUntilAvailable();
        } catch (InterruptedException e) {
            return Stream.empty();
        }

        return super.methods(name);
    }

    @Override
    public BaseCallableItem method(CallSignature sig) {
        try {
            waitUntilAvailable();
        } catch (InterruptedException e) {
            throw new NoSuchElementException();
        }

        return super.method(sig);
    }

    @Override
    public int numConstructors() {
        try {
            waitUntilAvailable();
        } catch (InterruptedException e) {
            return 0;
        }

        return super.numConstructors();
    }

    @Override
    public Stream<? extends BaseCallableItem> constructors() {
        try {
            waitUntilAvailable();
        } catch (InterruptedException e) {
            return Stream.empty();
        }

        return super.constructors();
    }

    @Override
    public BaseCallableItem constructor(CallSignature sig) {
        try {
            waitUntilAvailable();
        } catch (InterruptedException e) {
            throw new NoSuchElementException();
        }

        return super.constructor(sig);
    }

    /**
     * Call to indicate that this class is available for linkage from other classes being
     * compiled. The super types and all members should be provided before calling this method,
     * and no further changes are permitted other than filling in the code.
     */
    public synchronized void available() {
        /* FIXME
        // Won't need these anymore.
        mPreparedFields = null;
        mPreparedMethods = null;

        tryAddClassField();
        */

        mAvailable = Math.max(1, mAvailable);

        notifyAll();
    }

    private synchronized int waitUntilAvailable() throws InterruptedException {
        int available;
        while ((available = mAvailable) == 0) {
            wait();
        }
        return available;
    }

    /**
     * Check for a super type inheritance cycle if necessary, but don't report any errors. Save
     * them for later.
     */
    public void checkForInheritanceCycle() {
        // FIXME: checkForInheritanceCycle
        synchronized (this) {
            mAvailable = Math.max(2, mAvailable);
        }
    }
}
