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

import java.util.Arrays;
import java.util.Set;

import org.cojen.motto.model.Item;
import org.cojen.motto.model.Type;

import org.cojen.motto.internal.util.InternSet;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class BaseCompositeType extends GeneratedType implements
    BaseObjectType, EncodableType.CompositeT
{
    /**
     * @param fieldTypes should be sorted to reduce the amount of generated composite types
     */
    public static BaseCompositeType from(BaseType... fieldTypes) {
        return InternSet.apply(new BaseCompositeType(fieldTypes));
    }

    private final BaseType[] mFieldTypes;

    private BaseCompositeType(BaseType[] fieldTypes) {
        mFieldTypes = fieldTypes;
    }

    @Override
    public StringBuilder appendDisplayNameTo(StringBuilder b) {
        b.append('(');

        int numFields = numFields();

        for (int i=0; i<numFields; i++) {
            if (i > 0) {
                b.append(", ");
            }
            fieldType(i).appendDisplayNameTo(b);
        }

        return b.append(')');
    }

    @Override
    public boolean isInterface() {
        return false;
    }

    @Override
    public boolean isArray() {
        return false;
    }

    @Override
    public BaseClassTypeItem superType() {
        return LoadedClass.forObject();
    }

    @Override
    public Set<BaseClassTypeItem> interfaces() {
        return Set.of();
    }

    @Override
    public BaseCompositeType noFieldNames() {
        return this;
    }

    @Override
    public boolean isEquivalentTo(Type other) {
        if (!(other instanceof BaseCompositeType ost)) {
            return false;
        }

        int numFields = numFields();

        if (numFields != ost.numFields()) {
            return false;
        }

        for (int i=0; i<numFields; i++) {
            if (!fieldType(i).isEquivalentTo(ost.fieldType(i))) {
                return false;
            }
        }

        return false;
    }

    @Override
    public boolean isAssignableFrom(Type other) {
        // Although field conversions could be checked, composite types are only intended to be
        // used for supporting captured variables.
        return isEquivalentTo(other);
    }

    @Override
    public boolean isAccessibleVia(Item via) {
        return true;
    }

    @Override
    public int numFields() {
        return mFieldTypes.length;
    }

    @Override
    public BaseType fieldType(int index) {
        return mFieldTypes[index];
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(mFieldTypes);
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof BaseCompositeType other
            && Arrays.equals(mFieldTypes, other.mFieldTypes);
    }
}
