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

import java.util.Set;

import java.util.stream.Stream;

import org.cojen.motto.model.CallableItem;
import org.cojen.motto.model.CallSignature;
import org.cojen.motto.model.ClassTypeItem;
import org.cojen.motto.model.FieldItem;
import org.cojen.motto.model.ObjectType;
import org.cojen.motto.model.PrimitiveType;
import org.cojen.motto.model.Type;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public abstract sealed class BaseClassTypeItem extends BaseItem
    implements BaseObjectType, ClassTypeItem, EncodableType.ClassT
    permits ExternalClass, NewClass
{
    BaseClassTypeItem(int modifierBits) {
        super(modifierBits);
    }

    @Override
    public final BaseType enclosingType() {
        return this;
    }

    @Override
    public final BaseClassTypeItem enclosingClass() {
        return this;
    }

    @Override
    public BaseClassTypeItem superType() {
        // FIXME
        throw null;
    }

    @Override
    public Set<? extends BaseClassTypeItem> interfaces() {
        // FIXME
        throw null;
    }

    @Override
    public BasePath packagePath() {
        // FIXME
        throw null;
    }

    @Override
    public BasePath namePath() {
        // FIXME
        throw null;
    }

    @Override
    public boolean isStringType() {
        if (packagePath().equals(BasePath.JAVA_LANG)) {
            BasePath namePath = namePath();
            return namePath.size() == 1 && namePath.getFirst().equals("String");
        }
        return false;
    }

    @Override
    public BaseClassTypeItem outerType() {
        // FIXME
        throw null;
    }

    @Override
    public BaseClassTypeItem nestType() {
        BaseClassTypeItem thisType = this;
        while (true) {
            BaseClassTypeItem outerType = thisType.outerType();
            if (outerType == null) {
                return thisType;
            }
            thisType = outerType;
        }
    }

    @Override
    public StringBuilder appendDisplayNameTo(StringBuilder b) {
        BasePath packagePath = packagePath();
        if (!packagePath.isEmpty()) {
            packagePath.appendTo(b).append('.');
        }
        return namePath().appendTo(b);
    }

    @Override
    public boolean isInterface() {
        return (modifierBits() & Modifiers.INTERFACE) != 0;
    }

    @Override
    public int numFields() {
        // FIXME
        throw null;
    }

    @Override
    public Stream<? extends FieldItem> fields() {
        // FIXME
        throw null;
    }

    @Override
    public FieldItem field(String name) {
        // FIXME
        throw null;
    }

    @Override
    public FieldItem field(int index) {
        // FIXME
        throw null;
    }

    @Override
    public int fieldIndex(String name) {
        // FIXME
        throw null;
    }

    @Override
    public int numMethods() {
        // FIXME
        throw null;
    }

    @Override
    public Stream<? extends CallableItem> methods() {
        // FIXME
        throw null;
    }

    @Override
    public Stream<? extends CallableItem> methods(String name) {
        // FIXME
        throw null;
    }

    @Override
    public CallableItem method(CallSignature sig) {
        // FIXME
        throw null;
    }

    @Override
    public int numConstructors() {
        // FIXME
        throw null;
    }

    @Override
    public Stream<? extends CallableItem> constructors() {
        // FIXME
        throw null;
    }

    @Override
    public CallableItem constructor(CallSignature sig) {
        // FIXME
        throw null;
    }

    @Override
    public final boolean isArray() {
        return false;
    }

    @Override
    public final BaseType noFieldNames() {
        if (numFields() != 0) {
            throw new UnsupportedOperationException();
        }
        return this;
    }

    @Override
    public final BasePrimitiveType unbox() {
        if (packagePath().equals(BasePath.JAVA_LANG)) {
            BasePath namePath = namePath();
            if (namePath.size() == 1) {
                return switch (namePath.getFirst()) {
                    case "Void"      -> TheVoidType.THE;
                    case "Boolean"   -> TheBooleanType.THE;
                    case "Character" -> TheCharType.THE;
                    case "Byte"      -> TheByteType.THE;
                    case "Short"     -> TheShortType.THE;
                    case "Integer"   -> TheIntType.THE;
                    case "Long"      -> TheLongType.THE;
                    case "Float"     -> TheFloatType.THE;
                    case "Double"    -> TheDoubleType.THE;
                    default -> null;
                };
            }
        }

        return null;
    }

    @Override
    public final int canConvertTo(Type to) {
        int code = BaseObjectType.super.canConvertTo(to);

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
}
