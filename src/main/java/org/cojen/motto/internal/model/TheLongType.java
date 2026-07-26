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

import org.cojen.motto.model.LongType;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class TheLongType extends BasePrimitiveType implements LongType {
    public static final TheLongType THE = new TheLongType();

    private TheLongType() {
    }

    @Override
    public StringBuilder appendDisplayNameTo(StringBuilder b) {
        return b.append("long");
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
    public org.cojen.maker.Type asMakerType() {
        return org.cojen.maker.Type.from(long.class);
    }

    @Override
    public ClassDesc asClassDesc() {
        return ConstantDescs.CD_long;
    }

    @Override
    public void encode(TypeEncoder encoder) {
        encoder.encodeByte(EncodableType.T_LONG);
    }
}
