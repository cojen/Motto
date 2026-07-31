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

import java.util.Set;

import java.util.stream.Stream;

import org.cojen.motto.internal.util.InternSet;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class LoadedClass extends BaseClassTypeItem {
    public static BaseType from(Class<?> clazz) {
        if (clazz.isPrimitive()) {
            return BasePrimitiveType.trySelectByDescriptor(clazz.descriptorString());
        } else if (clazz.isArray()) {
            return from(clazz.getComponentType()).asArray();
        } else {
            return classFrom(clazz);
        }
    }

    /**
     * @param clazz must not be primitive or an array type
     */
    public static LoadedClass classFrom(Class<?> clazz) {
        // FIXME: Add a feature to InternSet to find without constructing? Or perhaps define a
        // custom key? A different kind of cache perhaps? Or just a simple local cache?
        return InternSet.apply(new LoadedClass(clazz));
    }

    private final Class<?> mClass;

    private LoadedClass(Class<?> clazz) {
        int modifierBits = Modifiers.from(clazz);
        BasePath packagePath = BasePath.parse(clazz.getPackageName(), '.');
        BasePath namePath = namePath(clazz);

        super(modifierBits, packagePath, namePath);

        mClass = clazz;

        // FIXME: setSuperTypes, fields, methods, constructors, inner classes. Do on demand
        // using the inherited init methods.
    }

    private static BasePath namePath(Class<?> clazz) {
        Class<?> enclosing = clazz.getEnclosingClass();

        if (enclosing == null) {
            return BasePath.from(clazz.getSimpleName());
        }

        int levels = 2;
        while ((enclosing = enclosing.getEnclosingClass()) != null) {
            levels++;
        }

        var names = new String[levels];

        do {
            names[--levels] = clazz.getSimpleName();
        } while ((clazz = clazz.getEnclosingClass()) != null);

        return BasePath.from(names);
    }

    @Override
    public org.cojen.maker.Type asMakerType() {
        return org.cojen.maker.Type.from(mClass);
    }

    @Override
    public int hashCode() {
        return mClass.hashCode() ^ 233600240;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof LoadedClass other && mClass == other.mClass;
    }
}
