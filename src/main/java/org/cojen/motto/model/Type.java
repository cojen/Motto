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

import java.lang.constant.Constable;

import java.util.NoSuchElementException;

import java.util.stream.Stream;

import org.cojen.motto.internal.model.BaseType;
import org.cojen.motto.internal.model.TheBooleanType;
import org.cojen.motto.internal.model.TheByteType;
import org.cojen.motto.internal.model.TheCharType;
import org.cojen.motto.internal.model.TheDoubleType;
import org.cojen.motto.internal.model.TheFloatType;
import org.cojen.motto.internal.model.TheIntType;
import org.cojen.motto.internal.model.TheLongType;
import org.cojen.motto.internal.model.TheNullType;
import org.cojen.motto.internal.model.TheShortType;
import org.cojen.motto.internal.model.TheUnspecifiedType;
import org.cojen.motto.internal.model.TheVoidType;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public sealed interface Type extends Constable
    permits BaseType, UnspecifiedType, PrimitiveType, ObjectType, FunctionType
{
    public static UnspecifiedType unspecified() {
        return TheUnspecifiedType.THE;
    }

    public static VoidType void_() {
        return TheVoidType.THE;
    }

    public static BooleanType boolean_() {
        return TheBooleanType.THE;
    }

    public static CharType char_() {
        return TheCharType.THE;
    }

    public static ByteType byte_() {
        return TheByteType.THE;
    }

    public static ShortType short_() {
        return TheShortType.THE;
    }

    public static IntType int_() {
        return TheIntType.THE;
    }

    public static LongType long_() {
        return TheLongType.THE;
    }

    public static FloatType float_() {
        return TheFloatType.THE;
    }

    public static DoubleType double_() {
        return TheDoubleType.THE;
    }

    public static NullType null_() {
        return TheNullType.THE;
    }

    /**
     * Returns a type for a top-level class (not an inner class).
     *
     * @throws IllegalStateException if the current thread scope cannot load class types
     * @throws NullPointerException if the given path or name is null
     */
    public static ClassTypeItem class_(Path packagePath, String className) {
        // FIXME
        throw null;
    }

    /**
     * Returns a type for a top-level class or an inner class.
     *
     * @throws IllegalStateException if the current thread scope cannot load class types
     * @throws NullPointerException if any given paths are null
     */
    public static ClassTypeItem class_(Path packagePath, Path namePath) {
        // FIXME
        throw null;
    }

    /**
     * Returns a tuple type with unnamed fields
     *
     * @throws NullPointerException if any given types are null
     */
    public static TupleType tuple(Type... fieldTypes) {
        // FIXME
        throw null;
    }

    /**
     * @throws NullPointerException if any given types are null
     */
    public static FunctionType function(Type outputType, TupleType inputType) {
        // FIXME
        throw null;
    }

    /**
     * Returns the full display name for this type, which matches its source code name
     */
    public default String displayName() {
        return appendDisplayNameTo(new StringBuilder()).toString();
    }

    public StringBuilder appendDisplayNameTo(StringBuilder b);

    /**
     * Returns true if this type is an int, boolean, double, etc.
     */
    public boolean isPrimitive();

    /**
     * Returns true if this type is an array, an interface, or a class.
     */
    public boolean isObject();

    /**
     * Returns true if this type is definitely known to be an interface.
     */
    public boolean isInterface();

    /**
     * Returns the number of fields defined explicitly in this type.
     */
    public int numFields();

    /**
     * Returns all the fields defined explicitly in this type.
     */
    public Stream<? extends FieldItem> fields();

    /**
     * Finds a field by name, which might be inherited.
     *
     * @throws NoSuchElementException if the field doesn't exist
     */
    public FieldItem field(String name);

    /**
     * Finds a field by index, if the type supports ordered fields.
     *
     * @throws IndexOutOfBoundsException if the field doesn't exist
     * @throws UnsupportedOperationException if this type doesn't have ordered fields
     */
    public FieldItem field(int index);

    /**
     * Finds a field type by name, if the type supports ordered fields.
     *
     * @throws NoSuchElementException if the field doesn't exist
     */
    public default Type fieldType(String name) {
        return field(name).type();
    }

    /**
     * Finds a field type by index, if the type supports ordered fields.
     *
     * @throws IndexOutOfBoundsException if the field doesn't exist
     * @throws UnsupportedOperationException if this type doesn't have ordered fields
     */
    public default Type fieldType(int index) {
        return field(index).type();
    }

    /**
     * Finds a field name by index, if the type supports ordered fields.
     *
     * @throws IndexOutOfBoundsException if the field doesn't exist
     * @throws UnsupportedOperationException if this type doesn't have ordered fields
     */
    public default String fieldName(int index) {
        return field(index).name();
    }

    /**
     * Returns the index of a field, if the type supports ordered fields.
     *
     * @throws NoSuchElementException if the field doesn't exist
     * @throws UnsupportedOperationException if this type doesn't have ordered fields
     */
    public int fieldIndex(String name);

    /**
     * Returns the number of methods defined explicitly in this type.
     */
    public int numMethods();

    /**
     * Returns all the methods defined explicitly in this type.
     */
    public Stream<? extends CallableItem> methods();

    /**
     * Finds all methods defined explicitly in this type which have the given name.
     */
    public Stream<? extends CallableItem> methods(String name);

    /**
     * Finds a method by signature, which might be inherited.
     *
     * @throws NoSuchElementException if the method doesn't exist
     */
    public CallableItem method(CallSignature sig);

    /**
     * Returns the number of constructors defined explicitly in this type.
     */
    public int numConstructors();

    /**
     * Returns all the constructors defined explicitly in this type.
     */
    public Stream<? extends CallableItem> constructors();

    /**
     * Finds a constructor by signature.
     *
     * @throws NoSuchElementException if the constructor doesn't exist
     */
    public CallableItem constructor(CallSignature sig);

    /**
     * Returns true if this type is an array.
     */
    public boolean isArray();

    /**
     * Returns the element type of this array type.
     *
     * @throws UnsupportedOperationException if this type isn't an array
     */
    public Type arrayElementType();

    /**
     * Returns this type as an array type.
     */
    public ArrayType asArray();

    /**
     * Returns this type without any field names, recursively.
     */
    public Type noFieldNames();

    /**
     * If this is a primitive type, a wrapper class type is returned. Otherwise, the same type
     * returned.
     */
    public Type box();

    /**
     * Returns a primitive type for a wrapper class type.
     *
     * @throws UnsupportedOperationException if this type isn't a primitive wrapper
     */
    public PrimitiveType unbox();

    /**
     * Returns true if values of the other type can be assigned to values of this type,
     * possibly by performing a safe conversion. Conversions to/from tuples and non-tuples is
     * possible when the tuple has one field. If a primitive type is assigned from a boxed
     * primitive type, a NullPointerException can be thrown at runtime.
     */
    public boolean isAssignableFrom(Type other);

    /**
     * Returns true if this type is accessible via the given item.
     *
     * @param via can pass null to check if this type is publicly available
     */
    public boolean isAccessibleVia(Item via);
}
