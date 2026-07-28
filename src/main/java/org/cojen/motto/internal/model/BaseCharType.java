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

import org.cojen.motto.model.CharType;
import org.cojen.motto.model.DoubleType;
import org.cojen.motto.model.FloatType;
import org.cojen.motto.model.IntType;
import org.cojen.motto.model.LongType;
import org.cojen.motto.model.PrimitiveType;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class BaseCharType extends BasePrimitiveType implements CharType {
    public static final BaseCharType THE = new BaseCharType();

    private BaseCharType() {
    }

    @Override
    public StringBuilder appendDisplayNameTo(StringBuilder b) {
        return b.append("char");
    }

    @Override
    public BaseClassTypeItem box() {
        // FIXME: box
        throw null;
    }

    @Override
    int convertCode(PrimitiveType to) {
        return switch (to) {
            case    IntType _ -> 2;
            case   LongType _ -> 3;
            case  FloatType _ -> 4;
            case DoubleType _ -> 5;
            default -> Integer.MAX_VALUE;
        };
    }

    @Override
    public org.cojen.maker.Type asMakerType() {
        return org.cojen.maker.Type.from(char.class);
    }

    @Override
    public ClassDesc asClassDesc() {
        return ConstantDescs.CD_char;
    }

    @Override
    public void encode(TypeEncoder encoder) {
        encoder.encodeByte(EncodableType.T_CHAR);
    }
}
