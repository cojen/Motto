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
import java.lang.constant.ConstantDescs;

import org.cojen.motto.model.ByteType;
import org.cojen.motto.model.DoubleType;
import org.cojen.motto.model.FloatType;
import org.cojen.motto.model.IntType;
import org.cojen.motto.model.LongType;
import org.cojen.motto.model.PrimitiveType;
import org.cojen.motto.model.ShortType;

import org.cojen.motto.internal.tuple.EncodableType;
import org.cojen.motto.internal.tuple.TypeEncoder;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class TheByteType extends BasePrimitiveType implements ByteType {
    public static final TheByteType THE = new TheByteType();

    private TheByteType() {
    }

    @Override
    public StringBuilder appendDisplayNameTo(StringBuilder b) {
        return b.append("byte");
    }

    @Override
    public BaseClassTypeItem box() {
        // FIXME: box
        throw null;
    }

    @Override
    boolean isGenericBox(String className) {
        return "Number".equals(className);
    }

    @Override
    int convertCode(PrimitiveType to) {
        return switch (to) {
            case  ShortType _ -> 1;
            case    IntType _ -> 2;
            case   LongType _ -> 3;
            case  FloatType _ -> 4;
            case DoubleType _ -> 5;
            default -> Integer.MAX_VALUE;
        };
    }

    @Override
    public org.cojen.maker.Type asMakerType() {
        return org.cojen.maker.Type.from(byte.class);
    }

    @Override
    public ClassDesc asClassDesc() {
        return ConstantDescs.CD_byte;
    }

    @Override
    public void encode(TypeEncoder encoder) {
        encoder.encodeByte(EncodableType.T_BYTE);
    }
}
