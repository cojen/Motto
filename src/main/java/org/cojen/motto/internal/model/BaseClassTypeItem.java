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

import java.lang.constant.ClassDesc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

import java.util.stream.Stream;

import org.cojen.maker.ClassMaker;
import org.cojen.maker.Maker;

import org.cojen.motto.model.CallSignature;
import org.cojen.motto.model.ClassTypeItem;
import org.cojen.motto.model.ObjectType;
import org.cojen.motto.model.PrimitiveType;
import org.cojen.motto.model.TupleType;
import org.cojen.motto.model.Type;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public abstract sealed class BaseClassTypeItem extends BaseItem
    implements BaseObjectType, ClassTypeItem, EncodableType.ClassT
    permits ExternalClass, LoadedClass, NewClass
{
    private final BasePath mPackagePath, mNamePath;

    private ClassDesc mClassDesc;

    private BaseClassTypeItem mSuperType;
    private Set<BaseClassTypeItem> mSuperInterfaces;

    private Map<String, TheFieldItem> mFieldMap;
    private Map<String, Map<TheCallSignature, BaseCallableItem>> mMethodMap;
    private Map<TheCallSignature, BaseCallableItem> mConstructorMap;
    //private Map<String, BaseClassTypeItem> mInnerClassesMap;

    BaseClassTypeItem(int modifierBits, BasePath packagePath, BasePath namePath) {
        super(modifierBits);

        mPackagePath = Objects.requireNonNull(packagePath);
        mNamePath = Objects.requireNonNull(namePath);

        mFieldMap = Map.of();
        mMethodMap = Map.of();
        mConstructorMap = Map.of();
        //mInnerClassesMap = Map.of();
    }

    @Override
    public final BaseClassTypeItem enclosingType() {
        return this;
    }

    @Override
    public final BaseClassTypeItem enclosingClass() {
        return this;
    }

    @Override
    public BaseClassTypeItem superType() {
        return mSuperType;
    }

    @Override
    public Set<? extends BaseClassTypeItem> interfaces() {
        return mSuperInterfaces;
    }

    @Override
    public final BasePath packagePath() {
        return mPackagePath;
    }

    @Override
    public final BasePath namePath() {
        return mNamePath;
    }

    /**
     * Returns a fully qualified class name, dot separated, with mangling of special
     * characters.
     */
    public final String fullMangledName() {
        BasePath packagePath = packagePath();
        BasePath namePath = namePath();

        if (packagePath.isEmpty() && namePath.size() == 1) {
            return Maker.mangle(namePath.getFirst());
        }

        var b = new StringBuilder();

        if (!packagePath.isEmpty()) {
            packagePath.appendMangledTo(b, '.').append('.');
        }

        namePath.appendMangledTo(b, '$');

        return b.toString();
    }

    @Override
    public final ClassDesc asClassDesc() {
        if (mClassDesc == null) {
            mClassDesc = EncodableType.ClassT.super.asClassDesc();
        }
        return mClassDesc;
    }

    @Override
    public final boolean isStringType() {
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
    public final BaseClassTypeItem nestType() {
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
    public final StringBuilder appendDisplayNameTo(StringBuilder b) {
        BasePath packagePath = packagePath();
        if (!packagePath.isEmpty()) {
            packagePath.appendTo(b).append('.');
        }
        return namePath().appendTo(b);
    }

    @Override
    public final boolean isInterface() {
        return (modifierBits() & Modifiers.INTERFACE) != 0;
    }

    @Override
    public int numFields() {
        return mFieldMap.size();
    }

    @Override
    public Stream<? extends TheFieldItem> fields() {
        return mFieldMap.values().stream();
    }

    @Override
    public TheFieldItem field(String name) {
        TheFieldItem field = mFieldMap.get(name);
        if (field == null) {
            throw new NoSuchElementException();
        }
        return field;
    }

    @Override
    public final TheFieldItem field(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final int fieldIndex(String name) {
        throw new UnsupportedOperationException();
    }

    /**
     * Attempt to add a field.
     *
     * @return null if a conflicting field definition already exists
     */
    public TheFieldItem tryAddField(int modifierBits, BaseType type, String name) {
        var field = new TheFieldItem(modifierBits, this, type, name);

        Map<String, TheFieldItem> map = mFieldMap;

        if (map.isEmpty()) {
            mFieldMap = map = new LinkedHashMap<>();
            map.put(name, field);
            return field;
        } else {
            return map.putIfAbsent(name, field) == null ? field : null;
        }
    }

    @Override
    public int numMethods() {
        return (int) methods().count();
    }

    @Override
    public Stream<? extends BaseCallableItem> methods() {
        return mMethodMap.values().stream().flatMap(byName -> byName.values().stream());
    }

    @Override
    public Stream<? extends BaseCallableItem> methods(String name) {
        Map<TheCallSignature, BaseCallableItem> byName = mMethodMap.get(name);
       return byName == null ? Stream.empty() : byName.values().stream();
    }

    @Override
    public BaseCallableItem method(CallSignature sig) {
        Map<TheCallSignature, BaseCallableItem> byName = mMethodMap.get(sig.name());

        if (byName == null) {
            throw new NoSuchElementException();
        }

        sig = sig.noFieldNames();

        BaseCallableItem item = byName.get(sig);

        if (item != null && item.signature().noFieldNames().equals(sig)) {
            return item;
        }

        // Try to find a matching instance method. The first input element must be "this", but
        // it doesn't appear in the map keys.

        TupleType inputType = sig.inputType();

        if (inputType.numFields() == 0 || !inputType.fieldType(0).equals(this) ||
            !(sig instanceof TheCallSignature baseSig))
        {
            throw new NoSuchElementException();
        }

        item = byName.get(baseSig.trimFirst());

        if (item != null && !item.isStatic() && item.signature().noFieldNames().equals(sig)) {
            return item;
        }

        throw new NoSuchElementException();
    }

    /**
     * Attempt to add a method, which initially doesn't have any code.
     *
     * @return null if a conflicting method definition already exists
     * @throws IllegalArgumentException if adding an instance method and the first
     * parameter isn't named "this"
     */
    public final BaseCallableItem tryAddMethod(int modifierBits, BaseType outputType,
                                               String name, BaseTupleType inputType)
    {
        var sig = TheCallSignature.from(outputType, name, inputType, true);

        return tryAddMethod(modifierBits, sig);
    }

    /**
     * Attempt to add a method, which initially doesn't have any code.
     *
     * @return null if a conflicting method definition already exists
     * @throws IllegalArgumentException if adding an instance method and the first
     * parameter isn't named "this"
     */
    public final BaseCallableItem tryAddMethod(int modifierBits, TheCallSignature sig) {
        TheCallSignature key = sig.noFieldNames();

        if ((modifierBits & Modifiers.STATIC) == 0) {
            validateThis(sig.inputType());
            key = key.trimFirst();
        }

        BaseCallableItem method = BaseCallableItem.from(modifierBits, this, sig);

        Map<String, Map<TheCallSignature, BaseCallableItem>> map = mMethodMap;

        if (map.isEmpty()) {
            mMethodMap = map = new LinkedHashMap<>();
        }

        String name = sig.name();
        Map<TheCallSignature, BaseCallableItem> byName = map.get(name);

        if (byName == null) {
            byName = new LinkedHashMap<>();
            map.put(name, byName);
        }

        return byName.putIfAbsent(key, method) == null ? method : null;
    }

    @Override
    public int numConstructors() {
        return mConstructorMap.size();
    }

    @Override
    public Stream<? extends BaseCallableItem> constructors() {
        return mConstructorMap.values().stream();
    }

    @Override
    public BaseCallableItem constructor(CallSignature sig) {
        BaseCallableItem ctor = mConstructorMap.get(sig);
        if (ctor == null) {
            throw new NoSuchElementException();
        }
        return ctor;
    }

    /**
     * Attempt to add a constructor, which initially doesn't have any code.
     *
     * @param inputType the first parameter must be named "this", with the correct type
     * @return null if a conflicting constructor definition already exists
     * @throws IllegalArgumentException the first parameter isn't named "this"
     */
    public final BaseCallableItem tryAddConstructor(int modifierBits, BaseTupleType inputType) {
        var sig = TheCallSignature.from(TheVoidType.THE, "", validateThis(inputType), true);

        var ctor = BaseCallableItem.from(modifierBits, this, sig);

        Map<TheCallSignature, BaseCallableItem> map = mConstructorMap;
        TheCallSignature key = sig.noFieldNames();

        if (map.isEmpty()) {
            mConstructorMap = map = new LinkedHashMap<>();
            map.put(key, ctor);
            return ctor;
        } else {
            return map.putIfAbsent(key, ctor) == null ? ctor : null;
        }
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

    void applyModifiers(ClassMaker cm) {
        super.applyModifiers(cm);

        int modifiers = modifierBits();

        if ((modifiers & Modifiers.INTERFACE) != 0) {
            cm.interface_();
        } else if ((modifiers & Modifiers.ABSTRACT) != 0) {
            cm.abstract_();
        }
    }

    /**
     * @param superType optional (only for java.lang.Object)
     * @param interfaces optional
     */
    public final void setSuperTypes(BaseClassTypeItem superType,
                                    Set<BaseClassTypeItem> interfaces)
    {
        mSuperType = superType;
        mSuperInterfaces = interfaces == null ? Set.of() : interfaces;

    }

    /**
     * Validates that the first input element is a "this" parameter.
     *
     * @return the given inputType
     * @throws IllegalArgumentException if the first input element isn't "this", of the same
     * type as the this
     */
    private final BaseTupleType validateThis(BaseTupleType inputType) {
        if (inputType.numFields() == 0 || !inputType.fieldType(0).equals(this) ||
            !"this".equals(inputType.fieldName(0)))
        {
            throw new IllegalArgumentException();
        }
        return inputType;
    }
}
