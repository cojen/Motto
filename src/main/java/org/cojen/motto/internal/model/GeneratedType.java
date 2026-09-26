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

import org.cojen.motto.model.ClassTypeItem;
import org.cojen.motto.model.Item;
import org.cojen.motto.model.Type;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public abstract sealed class GeneratedType implements BaseType, EncodableType, ClassTypeItem
    permits BaseCompositeType, BaseTupleType, BaseFunctionType
{
    private static final BasePath PACKAGE_PATH = BasePath.from(EncodableType.GENERATED_PREFIX);

    // Is used by NewClass such that calling asMakerType calls back into NewClass.generateType,
    // no matter where asMakerType is being called from.
    static final ScopedValue<NewClass> FOR_NEW_CLASS = ScopedValue.newInstance();

    private volatile BasePath mNamePath;
    private volatile String mGeneratedName;
    private volatile LoadedClass mClassType;

    @Override
    public boolean isEquivalentTo(Type other) {
        return this == other || other instanceof ClassTypeItem otherClass
            && packagePath().equals(otherClass.packagePath())
            && namePath().equals(otherClass.namePath());
    }

    @Override
    public BaseType enclosingType() {
        return null;
    }

    @Override
    public BaseType nearestType() {
        return this;
    }

    @Override
    public boolean isStatic() {
        return true;
    }

    @Override
    public boolean isFinal() {
        return true;
    }

    @Override
    public boolean isPrivate() {
        return false;
    }

    @Override
    public boolean isAccessibleVia(Item via) {
        return true;
    }

    @Override
    public BasePath packagePath() {
        return PACKAGE_PATH;
    }

    @Override
    public BasePath namePath() {
        BasePath namePath = mNamePath;

        if (namePath == null) {
            mNamePath = namePath = BasePath.from(TypeEncoder.encodeBase64(this));
        }

        return namePath;
    }

    @Override
    public ClassTypeItem outerType() {
        return null;
    }

    @Override
    public ClassTypeItem nestType() {
        return this;
    }

    @Override
    public org.cojen.maker.Type asMakerType() {
        var type = classType().asMakerType();

        if (FOR_NEW_CLASS.isBound()) {
            FOR_NEW_CLASS.get().generateType(generatedName());
        }

        return type;
    }

    public LoadedClass classType() {
        LoadedClass classType = mClassType;

        if (classType == null) {
            Class<?> clazz = TheTypeGenerator.generateFromName(generatedName());
            mClassType = classType = LoadedClass.classFrom(clazz);
        }

        return classType;
    }

    String generatedName() {
        String name = mGeneratedName;

        if (name == null) {
            mGeneratedName = name = EncodableType.GENERATED_PREFIX +
                // Use a slash separator because that's what Java class files use.
                '/' + namePath().getFirst();
        }

        return name;
    }
}
