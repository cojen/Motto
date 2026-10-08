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

import org.cojen.maker.ClassMaker;

import org.cojen.motto.model.ClassTypeItem;
import org.cojen.motto.model.Item;
import org.cojen.motto.model.Type;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public abstract sealed class GeneratedType
    implements BaseType, EncodableType, ClassTypeItem
    permits BaseCompositeType, BaseTupleType, BaseFunctionType, BaseCodeType
{
    private static final BasePath PACKAGE_PATH = BasePath.from(EncodableType.GENERATED_PREFIX);

    // Is used by NewClass such that calling asMakerType calls back into NewClass.generateType,
    // no matter where asMakerType is being called from.
    static final ScopedValue<NewClass> FOR_NEW_CLASS = ScopedValue.newInstance();

    private volatile BasePath mNamePath;
    private volatile String mGeneratedName;
    private volatile org.cojen.maker.Type mMakerType;
    private volatile BaseClassTypeItem mClassType;

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

    String generatedName() {
        String name = mGeneratedName;

        if (name == null) {
            mGeneratedName = name = EncodableType.GENERATED_PREFIX +
                // Use a slash separator because that's what Java class files use.
                '/' + namePath().getFirst();
        }

        return name;
    }

    @Override
    public org.cojen.maker.Type asMakerType() {
        String name = null;
        org.cojen.maker.Type type = mMakerType;

        if (type == null) {
            name = generatedName();
            mMakerType = type = org.cojen.maker.Type.external(name.replace('/', '.'), this);
        }

        if (!isPseudo() && FOR_NEW_CLASS.isBound()) {
            if (name == null) {
                name = generatedName();
            }
            NewClass clazz = FOR_NEW_CLASS.get();
            clazz.generateType(name);
            generateTypeDependencies(clazz);
        }

        return type;
    }

    /**
     * @see BaseCodeType
     */
    boolean isPseudo() {
        return false;
    }

    /**
     * Calls generateType for all generated dependencies of this type.
     */
    abstract void generateTypeDependencies(NewClass clazz);

    public BaseClassTypeItem classType() {
        BaseClassTypeItem classType = mClassType;

        if (classType == null) {
            mClassType = classType = makeClassType();
        }

        return classType;
    }

    /**
     * The class defintion should match what TheTypeGenerator makes, although private members
     * and code can be excluded.
     */
    abstract BaseClassTypeItem makeClassType();
}
