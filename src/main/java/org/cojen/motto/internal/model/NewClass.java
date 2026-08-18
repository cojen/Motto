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

import java.io.File;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.NoSuchElementException;
import java.util.Map;
import java.util.Set;

import java.util.stream.Stream;

import org.cojen.maker.ClassMaker;
import org.cojen.maker.FieldMaker;
import org.cojen.maker.Maker;
import org.cojen.maker.MethodMaker;

import org.cojen.motto.internal.compiler.CompilationEnv;

import org.cojen.motto.model.CallSignature;

import static org.cojen.motto.internal.model.Modifiers.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class NewClass extends BaseClassTypeItem {
    private final CompilationEnv mEnv;
    private final Object mOrigin;

    private Map<String, Integer> mPreparedFields;  // field name to modifierBits
    private Map<String, Integer> mPreparedMethods; // method name to modifierBits

    // 1: initially available, 2: supertype cycle detection has been performed (if necessary)
    private int mAvailable;

    private volatile ClassMaker mClassMaker;

    private ArrayList<CodeGenerator> mCodeGenerators;

    /**
     * @param origin optional object describing where the class came from (usually a File)
     */
    NewClass(CompilationEnv env,
             int modifierBits, BasePath packagePath, BasePath namePath, Object origin)
    {
        super(modifierBits, packagePath, namePath);
        mEnv = env;
        mOrigin = origin;
    }

    @Override
    public CompilationEnv env() {
        return mEnv;
    }

    /**
     * Finish making the class by adding in the code (if was requested), and return a map of
     * fully qualified class names to class definitions. The first map entry is the outermost
     * enclosing class.
     */
    public Map<String, byte[]> finish() {
        ClassMaker cm = classMaker();

        if (mCodeGenerators != null) {
            ScopedValue.where(GeneratedType.FOR_NEW_CLASS, this).run(() -> {
                for (CodeGenerator generator : mCodeGenerators) {
                    generator.finish();
                }
            });

            mCodeGenerators = null;
        }

        byte[] bytes = cm.finishBytes();

        return Map.of(cm.name(), bytes);

        /* FIXME: inner classes; each needs to to be a NewClass, passed to FOR_NEW_CLASS
        if (mInnerMakers == null) {
            return Map.of(cm.name(), bytes);
        }

        var map = LinkedHashMap.<String, byte[]>newLinkedHashMap(1 + mInnerMakers.size());

        map.put(cm.name(), bytes);

        for (ClassMaker inner : mInnerMakers.values()) {
            map.put(inner.name(), inner.finishBytes());
        }

        return map;
        */
    }

    private ClassMaker classMaker() {
        ClassMaker cm = mClassMaker;

        if (cm != null) {
            return cm;
        }

        try {
            waitUntilAvailable();
        } catch (InterruptedException e) {
        }

        synchronized (this) {
            cm = mClassMaker;

            if (cm != null) {
                return cm;
            }

            cm = ClassMaker.beginExternal(fullMangledName());

            final ClassMaker fcm = cm; // this is annoying
            ScopedValue.where(GeneratedType.FOR_NEW_CLASS, this).run(() -> beginMaking(fcm));

            mClassMaker = cm;
        }

        return cm;
    }

    @Override
    public org.cojen.maker.Type asMakerType() {
        return classMaker().type();
    }

    /**
     * Prepare a field such that it can be observed by an import directive. If the field isn't
     * static, it's ignored. If multiple fields with the same name are prepared, the more
     * accessible modifier is selected.
     */
    public void prepareFieldForImport(int modifierBits, String name) {
        if ((modifierBits & STATIC) != 0) {
            mPreparedFields = prepare(mPreparedFields, modifierBits, name);
        }
    }

    /**
     * Prepare a method such that it can be observed by an import directive. If the method isn't
     * static, it's ignored. If multiple methods with the same name are prepared, the more
     * accessible modifier is selected.
     */
    public void prepareMethodForImport(int modifierBits, String name) {
        if ((modifierBits & STATIC) != 0) {
            mPreparedMethods = prepare(mPreparedMethods, modifierBits, name);
        }
    }

    private Map<String, Integer> prepare(Map<String, Integer> prepared,
                                         int modifierBits, String name)
    {
        if (prepared == null) {
            prepared = new HashMap<>();
        }

        Integer existing = prepared.putIfAbsent(name, modifierBits);

        if (existing != null && isMoreAccessible(existing, modifierBits)) {
            prepared.put(name, modifierBits);
        }

        return prepared;
    }

    @Override
    public int findFieldForImport(String name) {
        Map<String, Integer> prepared = mPreparedFields;
        if (prepared != null) {
            Integer modifierBits = prepared.get(name);
            return modifierBits == null ? -1 : modifierBits;
        }
        return super.findFieldForImport(name);
    }

    @Override
    public int findMethodForImport(String name) {
        Map<String, Integer> prepared = mPreparedMethods;
        if (prepared != null) {
            Integer modifierBits = prepared.get(name);
            return modifierBits == null ? -1 : modifierBits;
        }
        return super.findMethodForImport(name);
    }

    /**
     * Call to indicate that this class is available for linkage from other classes being
     * compiled. The super types and all members should be provided before calling this method,
     * and no further changes are permitted other than filling in the code.
     */
    public synchronized void available() {
        // Won't need these anymore.
        mPreparedFields = null;
        mPreparedMethods = null;

        /* FIXME
        tryAddClassField();
        */

        mAvailable = Math.max(1, mAvailable);

        notifyAll();
    }

    private synchronized int waitUntilAvailable() throws InterruptedException {
        int available;
        while ((available = mAvailable) == 0) {
            wait();
        }
        return available;
    }

    /**
     * Check for a super type inheritance cycle if necessary, but don't report any errors. Save
     * them for later.
     */
    public void checkForInheritanceCycle() {
        // FIXME: checkForInheritanceCycle
        synchronized (this) {
            mAvailable = Math.max(2, mAvailable);
        }
    }

    private void beginMaking(ClassMaker cm) {
        if (mOrigin instanceof File file) {
            cm.sourceFile(file.getName());
        }

        applyModifiers(cm);

        BaseObjectType superType = superType();
        if (superType != null) {
            cm.extend(superType.asMakerType());
        }

        for (BaseObjectType iface : interfaces()) {
            cm.implement(iface.asMakerType());
        }

        fields().filter(f -> !f.isPseudo()).forEach(field -> {
            FieldMaker fm = cm.addField(field.type().asMakerType(), Maker.mangle(field.name()));
            field.applyModifiers(fm);

            // FIXME: check if the field has an initial value
        });

        methods().filter(m -> !m.isPseudo()).forEach(method -> {
            // FIXME: If any types are unspecified, use Object (as is currently done), but also
            // define an attribute which has a correct signature. Something special is needed
            // for void parameters too. Also use a signature for macros, or signatures which
            // are unevaluated. Attribute name: "motto.CallSignature"

            MethodMaker mm;

            if (!method.isMacro()) {
                BaseCallSignature flattened = method.signature().flatten();
                Object[] paramTypes = makerParamsFor(method, flattened);

                // FIXME: might have conflicts
                mm = cm.addMethod(flattened.outputType().asMakerType(),
                                  Maker.mangle(flattened.name()), paramTypes);

                method.applyModifiers(mm);
                applyParamNames(mm, method, flattened);
            } else {
                // FIXME: macro
                throw null;
            }

            addCodeGenerator(mm, method);
        });

        constructors().filter(c -> !c.isPseudo()).forEach(ctor -> {
            MethodMaker mm = cm.addConstructor(makerParamsFor(ctor));

            ctor.applyModifiers(mm);
            applyParamNames(mm, ctor);

            addCodeGenerator(mm, ctor);
        });

        // FIXME: clinit, inner classes (inner classes before defining mMacroMaker)
    }

    private void addCodeGenerator(MethodMaker mm, BaseCallableItem item) {
        if (item.code() != null) {
            ArrayList<CodeGenerator> generators = mCodeGenerators;
            if (generators == null) {
                mCodeGenerators = generators = new ArrayList<>();
            }
            generators.add(new CodeGenerator(mm, item));
        }
    }

    // Called by GeneratedType.
    void generateType(String typeName) {
        // FIXME: generateType; use a special clinit; call ConstantBootstraps.type(...)?
        throw null;
    }

    @Override // BaseClassTypeItem
    protected void init() throws InterruptedException {
        if (waitUntilAvailable() < 2) {
            checkForInheritanceCycle();
        }
    }

    @Override // BaseClassTypeItem
    protected void initFields() throws InterruptedException {
        waitUntilAvailable();
    }

    @Override // BaseClassTypeItem
    protected void initMethods() throws InterruptedException {
        waitUntilAvailable();
    }

    @Override // BaseClassTypeItem
    protected void initConstructors() throws InterruptedException {
        waitUntilAvailable();
    }
}
