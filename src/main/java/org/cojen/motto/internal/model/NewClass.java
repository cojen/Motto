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
import java.util.HashSet;
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

import motto.TypeGenerator;

import static org.cojen.motto.internal.model.Modifiers.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class NewClass extends BaseClassTypeItem {
    private final CompilationEnv mEnv;
    private final NewClass mOuterClass;
    private final Object mOrigin;

    private ArrayList<BaseCallableItem> mClinits;

    private Map<String, Integer> mPreparedFields;  // field name to modifierBits
    private Map<String, Integer> mPreparedMethods; // method name to modifierBits

    // 1: initially available, 2: supertype cycle detection has been performed (if necessary)
    private int mAvailable;

    private volatile ClassMaker mClassMaker;

    private ArrayList<CodeGenerator> mCodeGenerators;

    private Set<String> mGeneratedTypeNames;

    /**
     * @param outerClass is null for top-level classes
     * @param origin optional object describing where the class came from (usually a File)
     * @throws IllegalArgumentException if namePath has more than one element and outerClass is
     * null
     */
    public NewClass(CompilationEnv env, NewClass outerClass,
                    int modifierBits, BasePath packagePath, BasePath namePath, Object origin)
    {
        super(modifierBits, packagePath, namePath);

        if (outerClass == null && namePath.size() > 1) {
            throw new IllegalArgumentException();
        }

        mEnv = env;
        mOuterClass = outerClass;
        mOrigin = origin;
    }

    @Override
    public CompilationEnv env() {
        return mEnv;
    }

    @Override
    public NewClass outerType() {
        return mOuterClass;
    }

    /**
     * Add code for a static initializer.
     */
    public void addClinit(BaseCallableItem clinit) {
        ArrayList<BaseCallableItem> clinits = mClinits;
        if (clinits == null) {
            mClinits = clinits = new ArrayList<>();
        }
        clinits.add(clinit);
    }

    /**
     * Finish making the class by adding in the code (if was requested), and return a map of
     * fully qualified class names to class definitions ((with '$' separators for inner
     * classes). The first map entry is the outermost enclosing class.
     */
    public Map<String, byte[]> finish() {
        return finish(null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, byte[]> finish(Map<String, byte[]> finished) {
        ClassMaker cm = classMaker();

        if (mCodeGenerators != null) {
            ScopedValue.where(GeneratedType.FOR_NEW_CLASS, this).run(() -> {
                for (CodeGenerator generator : mCodeGenerators) {
                    generator.finish();
                }
            });

            mCodeGenerators = null;
        }

        if (mGeneratedTypeNames != null) {
            MethodMaker mm = cm.addClinit();
            var tgVar = mm.var(TypeGenerator.class);
            for (String typeName : mGeneratedTypeNames) {
                tgVar.invoke("generateFromName", typeName);
            }
        }

        // FIXME: Static initializer must come after generated types.

        for (BaseClassTypeItem inner : innerClassesMap().values()) {
            finished = ((NewClass) inner).finish(finished);
        }

        byte[] bytes = cm.finishBytes();
        String fullName = cm.name();

        if (finished == null) {
            finished = Map.of(fullName, bytes);
        } else {
            LinkedHashMap<String, byte[]> linked;

            if (finished.size() == 1) {
                finished = linked = new LinkedHashMap<>(finished);
            } else {
                linked = (LinkedHashMap) finished;
            }

            linked.putFirst(fullName, bytes);
        }

        return finished;
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

            NewClass outer = outerType();

            if (outer == null) {
                cm = ClassMaker.beginExternal(fullMangledName());
            } else {
                cm = outer.classMaker().addInnerClass(namePath().getLast());
            }

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

        if (mClinits != null) {
            for (BaseCallableItem clinit : mClinits) {
                addCodeGenerator(cm.addClinit(), clinit);
            }
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
    }

    private void addCodeGenerator(MethodMaker mm, BaseCallableItem item) {
        if (item.code() != null) {
            ArrayList<CodeGenerator> generators = mCodeGenerators;
            if (generators == null) {
                mCodeGenerators = generators = new ArrayList<>();
            }
            generators.add(new CodeGenerator(this, mm, item));
        }
    }

    // Called by CodeGenerator
    org.cojen.maker.Type generateType(GeneratedType type) {
        generateType(type.generatedName());
        return type.asMakerType();
    }

    // Called by GeneratedType.
    void generateType(String typeName) {
        if (mGeneratedTypeNames == null) {
            mGeneratedTypeNames = new HashSet<>();
        }
        mGeneratedTypeNames.add(typeName);
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
