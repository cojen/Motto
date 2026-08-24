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

import org.cojen.motto.model.ClassTypeItem;
import org.cojen.motto.model.Item;
import org.cojen.motto.model.Path;
import org.cojen.motto.model.PrimitiveType;
import org.cojen.motto.model.Type;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public abstract sealed class BasePrimitiveType implements BaseType, PrimitiveType
    permits BaseVoidType, BaseBooleanType, BaseCharType,
        BaseByteType, BaseShortType, BaseIntType, BaseLongType, BaseFloatType, BaseDoubleType
{
    @Override
    public final boolean isPrimitive() {
        return true;
    }

    @Override
    public final boolean isObject() {
        return false;
    }

    @Override
    public final boolean isInterface() {
        return false;
    }

    @Override
    public final boolean isArray() {
        return false;
    }

    @Override
    public final BaseType noFieldNames() {
        return this;
    }

    @Override
    public abstract BaseClassTypeItem box();

    @Override
    public final boolean isEquivalentTo(Type other) {
        // BasePrimitiveType classes should all be singletons.
        return this == other;
    }

    @Override
    public final boolean isAccessibleVia(Item via) {
        return true;
    }

    @Override
    public final int canConvertTo(Type to) {
        int code = BaseType.super.canConvertTo(to);

        if (code != Integer.MAX_VALUE) {
            return code;
        }

        if (to instanceof PrimitiveType toPrim) {
            return convertCode(toPrim);
        }

        Type toUnboxed = to.unbox();

        if (toUnboxed != null) {
            code = canConvertTo(toUnboxed);
            if (code != Integer.MAX_VALUE) {
                // +7: Simple boxing, 8..13: Convert then box.
                code += 7;
            }
            return code;
        }

        if (to instanceof ClassTypeItem toObj && toObj.packagePath().equals(BasePath.JAVA_LANG)) {
            Path namePath = toObj.namePath();
            if (namePath.size() == 1) {
                String name = namePath.getFirst();
                if ("Object".equals(name) || isGenericBox(name)) {
                    return 7; // Simple boxing.
                }
            }
        }

        return Integer.MAX_VALUE;
    }

    boolean isGenericBox(String className) {
        return false;
    }

    int convertCode(PrimitiveType to) {
        return Integer.MAX_VALUE;
    }

    public static BasePrimitiveType trySelectByName(String name) {
        return switch (name) {
            default -> null;
            case "void"    -> BaseVoidType.THE;
            case "boolean" -> BaseBooleanType.THE;
            case "char"    -> BaseCharType.THE;
            case "byte"    -> BaseByteType.THE;
            case "short"   -> BaseShortType.THE;
            case "int"     -> BaseIntType.THE;
            case "long"    -> BaseLongType.THE;
            case "float"   -> BaseFloatType.THE;
            case "double"  -> BaseDoubleType.THE;
        };
    }

    public static BasePrimitiveType trySelectByDescriptor(String desc) {
        return switch (desc) {
            default -> null;
            case "V" -> BaseVoidType.THE;
            case "Z" -> BaseBooleanType.THE;
            case "C" -> BaseCharType.THE;
            case "B" -> BaseByteType.THE;
            case "S" -> BaseShortType.THE;
            case "I" -> BaseIntType.THE;
            case "J" -> BaseLongType.THE;
            case "F" -> BaseFloatType.THE;
            case "D" -> BaseDoubleType.THE;
        };
    }
}
