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

import org.cojen.motto.model.Item;
import org.cojen.motto.model.Type;
import org.cojen.motto.model.UnspecifiedType;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class BaseUnspecifiedType implements BaseType, UnspecifiedType {
    public static final BaseUnspecifiedType THE = new BaseUnspecifiedType();

    private BaseUnspecifiedType() {
    }

    @Override
    public StringBuilder appendDisplayNameTo(StringBuilder b) {
        return b.append('_');
    }

    @Override
    public boolean isPrimitive() {
        return false;
    }

    @Override
    public boolean isObject() {
        return false;
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
    public BaseType noFieldNames() {
        return this;
    }

    @Override
    public BaseType box() {
        return this;
    }

    @Override
    public boolean isAssignableFrom(Type other) {
        return true;
    }

    @Override
    public boolean isAccessibleVia(Item via) {
        return true;
    }

    @Override
    public org.cojen.maker.Type asMakerType() {
        return org.cojen.maker.Type.from(Object.class);
    }

    @Override
    public void encode(TypeEncoder encoder) {
        encoder.encodeByte(EncodableType.T_UNSPECIFIED);
    }
}
