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
import java.lang.constant.ConstantDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.DynamicConstantDesc;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

import java.util.function.Predicate;

import java.util.stream.Stream;

import org.cojen.motto.model.CallableItem;
import org.cojen.motto.model.CallSignature;
import org.cojen.motto.model.ClassTypeItem;
import org.cojen.motto.model.FieldItem;
import org.cojen.motto.model.Item;
import org.cojen.motto.model.TupleType;
import org.cojen.motto.model.Type;

import org.cojen.motto.runtime.ConstantBootstraps;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public sealed interface BaseType extends Type, EncodableType
    permits BaseObjectType, BasePrimitiveType, BaseUnspecifiedType, GeneratedType
{
    public static BaseType from(Class<?> clazz) {
        return LoadedClass.from(clazz);
    }

    @Override
    public default int numFields() {
        return 0;
    }

    @Override
    public default Stream<? extends FieldItem> fields() {
        return Stream.empty();
    }

    @Override
    public default boolean fieldExists(String name) {
        return false;
    }

    @Override
    public default FieldItem field(String name) {
        throw new NoSuchElementException();
    }

    @Override
    public default FieldItem field(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public default int fieldIndex(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public default Set<BaseFieldItem> findField(String name, Item via) {
        return findField(name, f -> f.isAccessibleVia(via));
    }

    @Override
    public default Set<BaseFieldItem> findField(String name, Predicate<FieldItem> filter) {
        return Set.of();
    }

    @Override
    public default int numMethods() {
        return 0;
    }

    @Override
    public default Stream<? extends BaseCallableItem> methods() {
        return Stream.empty();
    }

    @Override
    public default Stream<? extends BaseCallableItem> methods(String name) {
        return Stream.empty();
    }

    @Override
    public default BaseCallableItem method(CallSignature sig) {
        throw new NoSuchElementException();
    }

    @Override
    public default Map<BaseCallSignature, Set<CallableItem>> findMethod
        (String name, TupleType inputType, Item via)
    {
        return findMethod(name, (BaseTupleType) inputType, via);
    }

    @Override
    public default Map<BaseCallSignature, Set<CallableItem>> findMethod
        (String name, TupleType inputType, Predicate<CallableItem> filter)
    {
        return findMethod(name, (BaseTupleType) inputType, filter);
    }

    public default Map<BaseCallSignature, Set<CallableItem>> findMethod
        (String name, BaseTupleType inputType, Item via)
    {
        return findMethod(name, inputType, m -> m.isAccessibleVia(via));
    }

    public default Map<BaseCallSignature, Set<CallableItem>> findMethod
        (String name, BaseTupleType inputType, Predicate<CallableItem> filter)
    {
        return Map.of();
    }

    public default Map<BaseCallSignature, Set<CallableItem>> findMethod
        (CallSignature sig, Predicate<CallableItem> filter)
    {
        return findMethod((BaseCallSignature) sig, filter);
    }

    /**
     * Note: If the call has any segments, the repetition value should be -1, although the
     * value is ignored. Actual repetition should be specified using duplicate segments.
     */
    public default Map<BaseCallSignature, Set<CallableItem>> findMethod
        (BaseCallSignature sig, Predicate<CallableItem> filter)
    {
        return Map.of();
    }

    @Override
    public default int numConstructors() {
        return 0;
    }

    @Override
    public default Stream<? extends BaseCallableItem> constructors() {
        return Stream.empty();
    }

    @Override
    public default BaseCallableItem constructor(CallSignature sig) {
        throw new NoSuchElementException();
    }

    @Override
    public default Map<BaseCallSignature, BaseCallableItem> findConstructor
        (TupleType inputType, Item via)
    {
        return findConstructor((BaseTupleType) inputType, via);
    }

    @Override
    public default Map<BaseCallSignature, BaseCallableItem> findConstructor
        (TupleType inputType, Predicate<CallableItem> filter)
    {
        return findConstructor((BaseTupleType) inputType, filter);
    }

    public default Map<BaseCallSignature, BaseCallableItem> findConstructor
        (BaseTupleType inputType, Item via)
    {
        return findConstructor(inputType, c -> c.isAccessibleVia(via));
    }

    public default Map<BaseCallSignature, BaseCallableItem> findConstructor
        (BaseTupleType inputType, Predicate<CallableItem> filter)
    {
        return Map.of();
    }

    @Override
    public default Stream<? extends BaseClassTypeItem> innerClasses() {
        return Stream.empty();
    }

    @Override
    public default BaseClassTypeItem innerClass(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public default Set<BaseClassTypeItem> findInnerClass(String name,
                                                         Predicate<ClassTypeItem> filter)
    {
        return Set.of();
    }

    @Override
    public boolean isArray();

    @Override
    public default BaseType arrayElementType() {
        throw new UnsupportedOperationException();
    }

    @Override
    public default BaseArrayType asArray() {
        return BaseArrayType.from(this);
    }

    @Override
    public BaseType noFieldNames();

    @Override
    public default BaseType box() {
        return this;
    }

    @Override
    public default BasePrimitiveType unbox() {
        return null;
    }

    @Override
    public default boolean isAssignableFrom(Type other) {
        return other.isEquivalentTo(this)
            || ((other instanceof BaseType ot) && ot.canConvertTo(this) != Integer.MAX_VALUE)
            || (other instanceof TupleType tt
                && tt.numFields() == 1 && isAssignableFrom(tt.field(0).type()));
    }

    /**
     * Checks if a type can be converted without losing information. Lower codes have a cheaper
     * conversion cost.
     *
     * <p>Note: This method doesn't consider reference nullability.
     *
     *      0: Equal types.
     *   1..6: Primitive to wider primitive type (strict).
     *      7: Primitive to specific boxed instance.
     *  8..13: Primitive to converted boxed instance (wider type, Number, or Object).
     *      0: Specific instance to superclass or implemented interface (no-op cast)
     * 14..20: Reboxing to wider object type (NPE isn't possible).
     *     21: Unboxing to specific primitive type (NPE is possible).
     * 22..27: Unboxing to wider primitive type (NPE is possible).
     *    max: Disallowed.
     *
     * @return conversion code, which is max value if disallowed
     */
    public default int canConvertTo(Type to) {
        if (isEquivalentTo(to) || to == Type.unspecified()) {
            return 0;
        }
        if (to instanceof TupleType tt && tt.numFields() == 1) {
            return canConvertTo(tt.field(0).type());
        }
        return Integer.MAX_VALUE;
    }

    /**
     * Compares this argument type against the given parameter types, to select which one is a
     * better candidate to bind to for a method call.
     *
     * @return -1 if aParam is better, 1 if bParam is better, or 0 if neither is strictly better
     */
    public default int bindCompare(Type aParam, Type bParam) {
        int aCost = this.canConvertTo(aParam);
        int bCost = this.canConvertTo(bParam);

        if (aCost != bCost) {
            return aCost < bCost ? -1 : 1;
        }

        if (aCost != 0) {
            return 0;
        }

        if (this.isEquivalentTo(aParam)) {
            return this.isEquivalentTo(bParam) ? 0 : -1;
        } else if (this.isEquivalentTo(bParam)) {
            return 1;
        }

        // Favor the parameter which is more specialized. Note the compare order: A greater
        // depth is more specialized, which makes it better.
        return Integer.compare(depth(bParam), depth(aParam));
    }

    /**
     * Returns the superclass hierarchy depth of the given type.
     */
    private static int depth(Type type) {
        if (!(type instanceof BaseObjectType objType)) {
            return 0;
        }
        int depth = 0;
        while ((objType = objType.superType()) != null) depth++;
        return depth;
    }

    public default boolean isJavaLangObject() {
        return false;
    }

    public static boolean isJavaLangObject(Type type) {
        return type instanceof BaseType bt && bt.isJavaLangObject();
    }

    public org.cojen.maker.Type asMakerType();
}
