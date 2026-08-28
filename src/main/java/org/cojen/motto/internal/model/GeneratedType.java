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

import motto.TypeGenerator;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
abstract sealed class GeneratedType implements BaseType, EncodableType
    permits BaseTupleType, BaseFunctionType
{
    // Is used by NewClass such that calling asMakerType calls back into NewClass.generateType,
    // no matter where asMakerType is being called from.
    static final ScopedValue<NewClass> FOR_NEW_CLASS = ScopedValue.newInstance();

    private volatile String mGeneratedName;
    private volatile org.cojen.maker.Type mMakerType;

    @Override
    public org.cojen.maker.Type asMakerType() {
        var type = mMakerType;

        if (type == null) {
            Class<?> clazz = TypeGenerator.generateFromName(generatedName());
            mMakerType = type = org.cojen.maker.Type.from(clazz);
        }

        if (FOR_NEW_CLASS.isBound()) {
            FOR_NEW_CLASS.get().generateType(generatedName());
        }

        return type;
    }

    String generatedName() {
        String name = mGeneratedName;

        if (name == null) {
            mGeneratedName = name = EncodableType.GENERATED_PREFIX +
                // Use a slash separator because that's what Java class files use.
                '/' + TypeEncoder.encodeBase64(this);
        }

        return name;
    }
}
