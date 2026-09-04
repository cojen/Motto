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

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import java.util.HashSet;
import java.util.Set;

import java.util.concurrent.ConcurrentHashMap;

import org.cojen.maker.Maker;

/**
 * Access to a class which was loaded into the JVM as a Class object. Classes are strongly
 * referenced by an internal cache, and so they cannot be unloaded.
 *
 * @author Brian S. O'Neill
 * @see ExternalClass
 */
public final class LoadedClass extends BaseClassTypeItem {
    private static final ConcurrentHashMap<Class, LoadedClass> CACHE = new ConcurrentHashMap<>();

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
        return CACHE.computeIfAbsent(clazz, k -> new LoadedClass(clazz));
    }

    private final Class<?> mClass;

    // bit 0: init, bit 1: initFields, bit 2: initMethods, bit 3: initConstructors 
    private int mInitState;

    private LoadedClass(Class<?> clazz) {
        int modifierBits = Modifiers.from(clazz);
        BasePath packagePath = BasePath.parse(clazz.getPackageName(), '.');
        BasePath namePath = namePath(clazz);

        super(modifierBits, packagePath.demangle(), namePath.demangle());

        mClass = clazz;
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
    public LoadedClass outerType() {
        // FIXME: outerType
        throw null;
    }

    @Override
    public org.cojen.maker.Type asMakerType() {
        return org.cojen.maker.Type.from(mClass);
    }

    @Override // BaseClassTypeItem
    protected void init() throws InterruptedException {
        final int mask = 0b0001;

        if ((mInitState & mask) != 0) {
            return;
        }

        synchronized (this) {
            if ((mInitState & mask) != 0) {
                return;
            }

            BaseClassTypeItem superType;

            {
                Class<?> superclass = mClass.getSuperclass();
                if (superclass == null) {
                    superType = null;
                } else {
                    superType = classFrom(superclass);
                }
            }

            Set<BaseClassTypeItem> interfaces;

            {
                Class<?>[] ifaces = mClass.getInterfaces();

                if (ifaces.length == 0) {
                    interfaces = Set.of();
                } else {
                    interfaces = HashSet.newHashSet(ifaces.length);
                    for (int i=0; i<ifaces.length; i++) {
                        interfaces.add(classFrom(ifaces[i]));
                    }
                }
            }

            setSuperTypes(superType, interfaces);

            mInitState |= mask;
        }
    }

    @Override // BaseClassTypeItem
    protected void initFields() throws InterruptedException {
        final int mask = 0b0010;

        if ((mInitState & mask) != 0) {
            return;
        }

        synchronized (this) {
            if ((mInitState & mask) != 0) {
                return;
            }

            Field[] fields = mClass.getDeclaredFields();

            for (Field f : fields) {
                int modifierBits = Modifiers.from(f);

                if (Modifiers.isPrivate(modifierBits)) {
                    continue;
                }

                tryAddField(modifierBits, from(f.getType()), Maker.demangle(f.getName()));
            }

            mInitState |= mask;
        }
    }

    @Override // BaseClassTypeItem
    protected void initMethods() throws InterruptedException {
        final int mask = 0b0100;

        if ((mInitState & mask) != 0) {
            return;
        }

        synchronized (this) {
            if ((mInitState & mask) != 0) {
                return;
            }

            Method[] methods = mClass.getDeclaredMethods();

            for (Method m : methods) {
                int modifierBits = Modifiers.from(m);

                if (Modifiers.isPrivate(modifierBits)) {
                    continue;
                }

                BaseType outputType = from(m.getReturnType());
                String name = Maker.demangle(m.getName());
                BaseTupleType inputType = inputTypeFor(m);

                // FIXME: Must look for a special annotation.
                boolean evaluated = true;

                var sig = BaseCallSignature.from(outputType, name, inputType, evaluated);

                tryAddMethod(modifierBits, sig);
            }

            mInitState |= mask;
        }
    }

    @Override // BaseClassTypeItem
    protected void initConstructors() throws InterruptedException {
        final int mask = 0b1000;

        if ((mInitState & mask) != 0) {
            return;
        }

        synchronized (this) {
            if ((mInitState & mask) != 0) {
                return;
            }

            Constructor[] ctors = mClass.getDeclaredConstructors();

            for (Constructor c : ctors) {
                int modifierBits = Modifiers.from(c);

                if (Modifiers.isPrivate(modifierBits)) {
                    continue;
                }

                BaseTupleType inputType = inputTypeFor(c);

                // FIXME: Must look for a special annotation.
                boolean evaluated = true;

                tryAddConstructor(modifierBits, inputType, evaluated);
            }

            mInitState |= mask;
        }
    }

    private BaseTupleType inputTypeFor(Executable m) {
        boolean needsThis = !Modifier.isStatic(m.getModifiers());

        Class<?>[] classes = m.getParameterTypes();

        if (classes.length == 0 && !needsThis) {
            return BaseTupleType.EMPTY;
        }

        BaseType[] types;
        int offset;

        if (needsThis) {
            types = new BaseType[1 + classes.length];
            types[0] = from(m.getDeclaringClass());
            offset = 1;
        } else {
            types = new BaseType[classes.length];
            offset = 0;
        }

        for (int i=0; i<classes.length; i++) {
            types[offset + i] = from(classes[i]);
        }

        BaseTupleType inputType = BaseTupleType.from(types);

        if (needsThis) {
            inputType = inputType.withNames("this");
        }

        return inputType;
    }
}
