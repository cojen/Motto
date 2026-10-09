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

import java.util.HashSet;
import java.util.Set;

import org.cojen.motto.model.ObjectType;
import org.cojen.motto.model.PrimitiveType;
import org.cojen.motto.model.Type;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public sealed interface BaseObjectType extends BaseType, ObjectType
    permits BaseClassTypeItem, BaseNullType, BaseArrayType, BaseTupleType, BaseFunctionType,
            BaseCompositeType, BaseCodeType
{
    @Override
    public default boolean isPrimitive() {
        return false;
    }

    @Override
    public default boolean isObject() {
        return true;
    }

    @Override
    public default BaseObjectType box() {
        return this;
    }

    @Override
    public BaseClassTypeItem superType();

    @Override
    public Set<? extends BaseClassTypeItem> interfaces();

    @Override
    public default int canConvertTo(Type to) {
        int code = BaseType.super.canConvertTo(to);

        if (code != Integer.MAX_VALUE) {
            return code;
        }

        BaseClassTypeItem superType = superType();

        if (superType != null && to.isAssignableFrom(superType)) {
            return 0;
        }

        for (BaseClassTypeItem iface : interfaces()) {
            if (to.isAssignableFrom(iface)) {
                return 0;
            }
        }

        BasePrimitiveType thisUnboxed;
        PrimitiveType toUnboxed;

        if ((thisUnboxed = unbox()) == null || (toUnboxed = to.unbox()) == null) {
            return Integer.MAX_VALUE;
        }

        // This point is reached when converting boxed primitives.

        // Expect 0..6 or max
        code = thisUnboxed.canConvertTo(toUnboxed);

        if (code != Integer.MAX_VALUE) {
            code += to instanceof ObjectType ? 14 : 21;
        }

        return code;
    }

    @Override
    public default BaseType inferredType(BaseType other) {
        if (equals(other) || other == BaseUnspecifiedType.THE) {
            return this;
        }

        if (other.isVoid()) {
            return null;
        }

        other = other.box();

        if (!(other instanceof BaseObjectType otherObj)) {
            return null;
        }

        var set = new HashSet<BaseObjectType>();
        gatherSupers(set, this);

        var otherSet = new HashSet<BaseObjectType>();
        gatherSupers(otherSet, otherObj);

        set.retainAll(otherSet);

        if (set.size() == 1) {
            return set.iterator().next();
        }

        var reduced = new HashSet<>(set);

        for (BaseObjectType t1 : set) {
            for (BaseObjectType t2 : set) {
                if (t1 != t2 && t1.isAssignableFrom(t2)) {
                    // Remove the less specific type.
                    reduced.remove(t1);
                }
            }
        }

        if (reduced.size() == 1) {
            return reduced.iterator().next();
        }

        // As a last resort, select a class type. There should be at most one.

        BaseType classType = null;

        for (BaseType type : reduced) {
            if (!type.isInterface()) {
                if (classType == null) {
                    classType = type;
                } else {
                    classType = null;
                    break;
                }
            }
        }

        return classType != null ? classType : LoadedClass.forObject();
    }

    private static void gatherSupers(HashSet<BaseObjectType> set, BaseObjectType from) {
        do {
            if (set.add(from)) {
                for (BaseClassTypeItem iface : from.interfaces()) {
                    gatherSupers(set, iface);
                }
            }
        } while ((from = from.superType()) != null);
    }
}
