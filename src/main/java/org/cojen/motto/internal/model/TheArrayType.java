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

import java.util.Objects;

import org.cojen.motto.model.ArrayType;
import org.cojen.motto.model.Item;
import org.cojen.motto.model.Type;

import org.cojen.motto.internal.util.InternSet;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class TheArrayType implements BaseObjectType, ArrayType, EncodableType.ArrayT {
    public static TheArrayType from(BaseType elementType) {
        return InternSet.apply(new TheArrayType(Objects.requireNonNull(elementType)));
    }

    private final BaseType mElementType;

    private volatile TheArrayType mNoFieldNames;

    TheArrayType(BaseType elementType) {
        mElementType = elementType;
    }

    @Override
    public StringBuilder appendDisplayNameTo(StringBuilder b) {
        return mElementType.appendDisplayNameTo(b).append("[]");
    }

    @Override
    public boolean isInterface() {
        return false;
    }

    @Override
    public boolean isArray() {
        return true;
    }

    @Override
    public BaseType arrayElementType() {
        return mElementType;
    }

    @Override
    public TheArrayType noFieldNames() {
        TheArrayType noFieldNames = mNoFieldNames;

        if (noFieldNames == null) {
            BaseType noFieldNamesElement = mElementType.noFieldNames();

            if (noFieldNamesElement.equals(mElementType)) {
                noFieldNames = this;
            } else {
                noFieldNames = InternSet.apply(new TheArrayType(noFieldNamesElement));
            }

            mNoFieldNames = noFieldNames;
        }

        return noFieldNames;
    }

    @Override
    public boolean isAssignableFrom(Type other) {
        return BaseObjectType.super.isAssignableFrom(other)
            // This is stricter than Java, to help prevent ArrayStoreException.
            || arrayElementType().equals(other.arrayElementType());
    }

    @Override
    public boolean isAccessibleVia(Item via) {
        return mElementType.isAccessibleVia(via);
    }

    @Override
    public org.cojen.maker.Type asMakerType() {
        return mElementType.asMakerType().asArray();
    }

    @Override
    public void encodePrepare(TypeEncoder encoder) {
        ArrayT.super.encodePrepare(encoder);
    }

    @Override
    public void encode(TypeEncoder encoder) {
        ArrayT.super.encode(encoder);
    }

    @Override
    public void doEncode(TypeEncoder encoder) {
        ArrayT.super.doEncode(encoder);
    }

    @Override
    public int hashCode() {
        return mElementType.hashCode() ^ -1244306861;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof TheArrayType other
            && mElementType.equals(other.mElementType);
    }
}
