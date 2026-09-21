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

import java.util.Objects;

import org.cojen.motto.model.Item;
import org.cojen.motto.model.Type;

/**
 * Defines a type which is initially unspecified, but it can be changed later. It can be used
 * for inferring a common return type from a lambda function.
 *
 * @author Brian S. O'Neill
 */
public final class BaseDeferredType implements BaseType {
    private BaseType mCurrent;

    public BaseDeferredType() {
        mCurrent = BaseUnspecifiedType.THE;
    }

    public BaseDeferredType(BaseType initialType) {
        mCurrent = Objects.requireNonNull(initialType);
    }

    /**
     * Returns the currently inferred type.
     */
    public BaseType current() {
        return mCurrent;
    }

    /**
     * Specialize the current type by inferring a common compatible type.
     *
     * @return null if successful, or else returns the conflicting type
     */
    public BaseType specialize(BaseType other) {
        if (other instanceof BaseDeferredType deferred) {
            other = deferred.mCurrent;
        }
        BaseType newType = mCurrent.inferredType(other);
        if (newType == null) {
            return mCurrent;
        }
        mCurrent = newType;
        return null;
    }

    @Override
    public StringBuilder appendDisplayNameTo(StringBuilder b) {
        return mCurrent.appendDisplayNameTo(b);
    }

    @Override
    public boolean isPrimitive() {
        return mCurrent.isPrimitive();
    }

    @Override
    public boolean isObject() {
        return mCurrent.isObject();
    }

    @Override
    public boolean isInterface() {
        return mCurrent.isInterface();
    }

    @Override
    public boolean isArray() {
        return mCurrent.isArray();
    }

    @Override
    public BaseType noFieldNames() {
        BaseType noFieldNames = mCurrent.noFieldNames();
        return noFieldNames.equals(mCurrent) ? this : new BaseDeferredType(noFieldNames);
    }

    @Override
    public boolean isEquivalentTo(Type other) {
        // Always return false to be symmetric. If mCurrent was called, then all other Type
        // implementations would need to be aware of BaseDeferredType to remain symmetric.
        return false;
    }

    @Override
    public boolean isAssignableFrom(Type other) {
        if (other instanceof BaseDeferredType deferred) {
            other = deferred.mCurrent;
        }
        return mCurrent.isAssignableFrom(other);
    }

    @Override
    public boolean isAccessibleVia(Item via) {
        return mCurrent.isAccessibleVia(via);
    }

    @Override
    public BaseType inferredType(BaseType other) {
        if (other instanceof BaseDeferredType deferred) {
            other = deferred.mCurrent;
        }
        return mCurrent.inferredType(other);
    }

    @Override
    public org.cojen.maker.Type asMakerType() {
        return mCurrent.asMakerType();
    }

    @Override
    public int canConvertTo(Type to) {
        if (to instanceof BaseDeferredType deferred) {
            to = deferred.mCurrent;
        }
        return mCurrent.canConvertTo(to);
    }

    @Override
    public int typeCode() {
        return mCurrent.typeCode();
    }

    @Override
    public void encode(TypeEncoder encoder) {
        mCurrent.encode(encoder);
    }

    @Override
    public int doCompare(EncodableType other) {
        return mCurrent.doCompare(((BaseDeferredType) other).mCurrent);
    }
}
