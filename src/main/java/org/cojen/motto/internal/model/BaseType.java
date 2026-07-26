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

import java.util.NoSuchElementException;
import java.util.Optional;

import java.util.stream.Stream;

import org.cojen.motto.model.ArrayType;
import org.cojen.motto.model.CallableItem;
import org.cojen.motto.model.CallSignature;
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
    permits BaseObjectType, BasePrimitiveType, TheUnspecifiedType, GeneratedType
{
    @Override
    public default Optional<? extends ConstantDesc> describeConstable() {
        DirectMethodHandleDesc bootstrap = ConstantDescs.ofConstantBootstrap
            (ConstantBootstraps.class.describeConstable().get(), "type",
             Type.class.describeConstable().get(), ConstantDescs.CD_String);

        String desc = asClassDesc().descriptorString();

        ClassDesc type = Type.class.describeConstable().get();

        return Optional.of(DynamicConstantDesc.ofNamed(bootstrap, "_", type, desc));
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
    public default int numMethods() {
        return 0;
    }

    @Override
    public default Stream<? extends CallableItem> methods() {
        return Stream.empty();
    }

    @Override
    public default Stream<? extends CallableItem> methods(String name) {
        return Stream.empty();
    }

    @Override
    public default CallableItem method(CallSignature sig) {
        throw new NoSuchElementException();
    }

    @Override
    public default int numConstructors() {
        return 0;
    }

    @Override
    public default Stream<? extends CallableItem> constructors() {
        return Stream.empty();
    }

    @Override
    public default CallableItem constructor(CallSignature sig) {
        throw new NoSuchElementException();
    }

    @Override
    public boolean isArray();

    @Override
    public default BaseType arrayElementType() {
        throw new UnsupportedOperationException();
    }

    @Override
    public default TheArrayType asArray() {
        return TheArrayType.from(this);
    }

    @Override
    public BaseType noFieldNames();

    @Override
    public default BaseType box() {
        return this;
    }

    @Override
    public default BasePrimitiveType unbox() {
        throw new UnsupportedOperationException();
    }

    @Override
    public default boolean isAssignableFrom(Type other) {
        return other.equals(this)
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
        if (equals(to) || to == Type.unspecified()) {
            return 0;
        }
        if (to instanceof TupleType tt && tt.numFields() == 1) {
            return canConvertTo(tt.field(0).type());
        }
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isAccessibleVia(Item via);

    public org.cojen.maker.Type asMakerType();
}
