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

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;

import java.lang.classfile.constantpool.ClassEntry;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import java.util.function.Function;

import java.util.stream.Stream;

import org.cojen.maker.ClassMaker;
import org.cojen.maker.FieldMaker;
import org.cojen.maker.Maker;
import org.cojen.maker.MethodMaker;

import org.cojen.motto.model.CallSignature;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class ExternalClass extends BaseClassTypeItem
    implements org.cojen.maker.Type.Provider
{
    private final Function<String, byte[]> mLoader;

    /**
     * @param loader loads the class bytes; can return null if not found
     */
    ExternalClass(BasePath packagePath, BasePath namePath, Function<String, byte[]> loader) {
        super(0, packagePath, namePath);
        mLoader = Objects.requireNonNull(loader);
    }

    /**
     * Force this class to be loaded if not done so already.
     */
    public void load() throws NoClassDefFoundError {
        if ((super.modifierBits() & Modifiers.LOADED) == 0) {
            doLoad();
        }
    }

    @Override
    public org.cojen.maker.Type asMakerType() {
        return org.cojen.maker.Type.external(fullMangledName(), this);
    }

    @Override
    public int modifierBits() {
        load();
        return super.modifierBits();
    }

    @Override
    public BaseClassTypeItem superType() {
        load();
        return super.superType();
    }

    @Override
    public Set<? extends BaseClassTypeItem> interfaces() {
        load();
        return super.interfaces();
    }

    @Override
    public int numFields() {
        load();
        return super.numFields();
    }

    @Override
    public Stream<? extends BaseFieldItem> fields() {
        load();
        return super.fields();
    }

    @Override
    public BaseFieldItem field(String name) {
        load();
        return super.field(name);
    }

    @Override
    public int numMethods() {
        load();
        return super.numMethods();
    }

    @Override
    public Stream<? extends BaseCallableItem> methods() {
        load();
        return super.methods();
    }

    @Override
    public Stream<? extends BaseCallableItem> methods(String name) {
        load();
        return super.methods(name);
    }

    @Override
    public BaseCallableItem method(CallSignature sig) {
        load();
        return super.method(sig);
    }

    @Override
    public int numConstructors() {
        load();
        return super.numConstructors();
    }

    @Override
    public Stream<? extends BaseCallableItem> constructors() {
        load();
        return super.constructors();
    }

    @Override
    public BaseCallableItem constructor(CallSignature sig) {
        load();
        return super.constructor(sig);
    }

    @Override // Type.Provider
    public void init(ClassMaker cm) {
        applyModifiers(cm);

        BaseClassTypeItem superType = superType();

        if (superType != null) {
            cm.extend(superType.asMakerType());
        }

        for (BaseClassTypeItem iface : interfaces()) {
            cm.implement(iface.asMakerType());
        }
    }

    @Override // Type.Provider
    public void addFields(ClassMaker cm) {
        fields().filter(f -> !f.isPseudo()).forEach(field -> {
            FieldMaker fm = cm.addField(field.type().asMakerType(), Maker.mangle(field.name()));
            field.applyModifiers(fm);
        });
    }

    @Override // Type.Provider
    public void addMethods(ClassMaker cm) {
        methods().filter(m -> !m.isPseudo()).forEach(method -> {
            // FIXME: If any types are unspecified, use Object (as is currently done), but also
            // define an attribute which has a correct signature. Something special is needed
            // for void parameters too. Also use a signature for macros, or signatures which
            // are unevaluated. Attribute name: "motto.CallSignature"

            MethodMaker mm;

            if (!method.isMacro()) {
                BaseCallSignature flattened = method.signature().flatten();
                Object[] paramTypes = makerParamsFor(method, flattened, cm);

                // FIXME: might have conflicts
                mm = cm.addMethod(flattened.outputType().asMakerType(),
                                  Maker.mangle(flattened.name()), paramTypes);

                method.applyModifiers(mm);
            } else {
                // FIXME: macro method
                throw null;
            }
        });
    }

    @Override // Type.Provider
    public void addConstructors(ClassMaker cm) {
        constructors().filter(c -> !c.isPseudo()).forEach(ctor -> {
            MethodMaker mm = cm.addConstructor(makerParamsFor(ctor, ctor.signature(), cm));
            ctor.applyModifiers(mm);
        });
    }

    private static Object[] makerParamsFor(BaseCallableItem item, BaseCallSignature sig,
                                           ClassMaker cm)
    {
        BaseTupleType inputType = sig.inputType();
        int numFields = inputType.numFields();

        Object[] params;
        int offset;

        if (item.isStatic()) {
            params = new Object[numFields];
            offset = 0;
        } else {
            // Drop the implicit "this" parameter.
            params = new Object[numFields - 1];
            offset = 1;
        }

        for (int ix = offset; ix < numFields; ix++) {
            params[ix - offset] = inputType.fieldType(ix).asMakerType();
        }

        return params;
    }

    private void doLoad() throws NoClassDefFoundError {
        // Set LOADED early in case the load fails, so as not to try loading again.
        {
            int modifierBits = super.modifierBits() | Modifiers.LOADED;
            setModifierBits(modifierBits);
        }

        String className = fullMangledName();

        byte[] classBytes = mLoader.apply(className);

        if (classBytes == null) {
            throw new NoClassDefFoundError(displayName());
        }

        ClassModel model = ClassFile.of().parse(classBytes);

        ExternalClass superclass = toExternalClass(model.superclass().orElse(null));

        Set<BaseClassTypeItem> interfaces;

        {
            List<ClassEntry> ifaces = model.interfaces();
            if (ifaces == null || ifaces.isEmpty()) {
                interfaces = Set.of();
            } else if (ifaces.size() == 1) {
                interfaces = Set.of(toExternalClass(ifaces.getFirst()));
            } else {
                interfaces = HashSet.newHashSet(ifaces.size());
                for (ClassEntry iface : ifaces) {
                    interfaces.add(toExternalClass(iface));
                }
            }
        }

        setSuperTypes(superclass, interfaces);

        for (FieldModel field : model.fields()) {
            BaseType fieldType = toType(field.fieldTypeSymbol());
            String fieldName = Maker.demangle(field.fieldName().stringValue());
            tryAddField(Modifiers.from(field), fieldType, fieldName);
        }

        for (MethodModel method : model.methods()) {
            String mname = method.methodName().stringValue();

            if (mname.equals("<clinit>")) {
                continue;
            }

            int modifierBits = Modifiers.from(method);
            MethodTypeDesc methodType = method.methodTypeSymbol();
            List<ClassDesc> paramDescs = methodType.parameterList();

            BaseTupleType params;
            if ((modifierBits & Modifiers.STATIC) != 0) {
                params = toTupleType(paramDescs);
            } else {
                params = toTupleType(this, "this", paramDescs);
            }

            if (mname.equals("<init>")) {
                tryAddConstructor(modifierBits, params);
            } else {
                // FIXME: Look for MacroMethod attribute. If found and is valid, update
                // modifierBits before calling tryAddMethod. If tryAddMethod is successful,
                // then call macroImpl.
                BaseType returnType = toType(methodType.returnType());
                mname = Maker.demangle(mname);
                tryAddMethod(modifierBits, returnType, mname, params);
            }
        }

        // FIXME: module stuff too perhaps?

        /* FIXME: inner classes
        InnerClassesAttribute attr = model.findAttribute(Attributes.innerClasses()).orElse(null);

        Map<String, String> loadableInnerClasses = null;

        if (attr != null) {
            ClassEntry thisClass = model.thisClass();
            String thisPackage = null;

            for (InnerClassInfo info : attr.classes()) {
                if (thisClass.equals(info.outerClass().orElse(null))) {
                    Utf8Entry innerName = info.innerName().orElse(null);
                    if (innerName != null) {
                        if (thisPackage == null) {
                            thisPackage = thisClass.asSymbol().packageName();
                        }
                        ClassDesc innerDesc = info.innerClass().asSymbol();
                        if (thisPackage.equals(innerDesc.packageName())) {
                            if (loadableInnerClasses == null) {
                                loadableInnerClasses = new HashMap<>();
                            }
                            // Map short name to full class name.
                            loadableInnerClasses.putIfAbsent
                                (innerName.stringValue(), innerDesc.displayName());
                        }
                    }
                }
            }
        }
        */
    }

    private ExternalClass toExternalClass(ClassEntry entry) {
        return entry == null ? null : toExternalClass(entry.asSymbol());
    }

    private ExternalClass toExternalClass(ClassDesc desc) {
        // FIXME: demangle packageName
        BasePath packagePath = BasePath.parse(desc.packageName(), '.');

        // The name path must be split by '$' characters, even if it doesn't match a proper
        // inner class name. This is because name mangling always escapes '$' characters. The
        // fullMangledName method adds back the '$' characters as separators.
        // FIXME: demangle displayName
        BasePath namePath = BasePath.parse(desc.displayName(), '$');

        return new ExternalClass(packagePath, namePath, mLoader);
    }

    private BaseType toType(ClassDesc desc) {
        if (desc.isPrimitive()) {
            return BasePrimitiveType.trySelectByDescriptor(desc.descriptorString());
        }
        ClassDesc elementType = desc.componentType();
        return elementType == null ? toExternalClass(desc) : toType(elementType).asArray();
    }

    /**
     * Returns a TupleType which doesn't have any named elements.
     */
    private BaseTupleType toTupleType(List<ClassDesc> descs) {
        var elementTypes = new BaseType[descs.size()];
        int i = 0;
        for (ClassDesc desc : descs) {
            elementTypes[i++] = toType(desc);
        }
        return BaseTupleType.from(elementTypes);
    }

    /**
     * Returns a TupleType in which the first element is named, but the rest aren't.
     */
    private BaseTupleType toTupleType(BaseType first, String firstName, List<ClassDesc> descs) {
        var elementTypes = new BaseType[1 + descs.size()];
        elementTypes[0] = first;
        int i = 1;
        for (ClassDesc desc : descs) {
            elementTypes[i++] = toType(desc);
        }
        BaseTupleType type = BaseTupleType.from(elementTypes);
        if (firstName != null) {
            type = type.withNames(firstName);
        }
        return type;
    }
}
