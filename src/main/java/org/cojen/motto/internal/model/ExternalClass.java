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

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;

import java.lang.classfile.attribute.InnerClassesAttribute;
import java.lang.classfile.attribute.InnerClassInfo;

import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.Utf8Entry;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import java.util.function.BiFunction;

import java.util.stream.Stream;

import org.cojen.maker.ClassMaker;
import org.cojen.maker.FieldMaker;
import org.cojen.maker.Maker;
import org.cojen.maker.MethodMaker;

import org.cojen.motto.model.CallSignature;

/**
 * Access to a class which isn't loaded into the JVM as a Class object.
 *
 * @author Brian S. O'Neill
 * @see LoadedClass
 */
public final class ExternalClass extends BaseClassTypeItem
    implements org.cojen.maker.Type.Provider
{
    private final BiFunction<BasePath, String, byte[]> mLoader;

    /**
     * @param loader loads the class bytes by package name and class name; can return null if
     * not found
     */
    public ExternalClass(BasePath packagePath, BasePath namePath,
                         BiFunction<BasePath, String, byte[]> loader)
    {
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
                Object[] paramTypes = makerParamsFor(method, flattened);

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
            MethodMaker mm = cm.addConstructor(makerParamsFor(ctor, ctor.signature()));
            ctor.applyModifiers(mm);
        });
    }

    private void doLoad() throws NoClassDefFoundError {
        // Set LOADED early in case the load fails, so as not to try loading again.
        setModifierBits(super.modifierBits() | Modifiers.LOADED);

        byte[] classBytes = mLoader.apply(packagePath(), mangledName());

        if (classBytes == null) {
            throw new NoClassDefFoundError(displayName());
        }

        ClassModel model = ClassFile.of().parse(classBytes);

        setModifierBits(Modifiers.from(model) | Modifiers.LOADED);

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
                tryAddConstructor(modifierBits, params, true);
            } else {
                // FIXME: Look for MacroMethod attribute. If found and is valid, update
                // modifierBits before calling tryAddMethod. If tryAddMethod is successful,
                // then call macroImpl.
                BaseType returnType = toType(methodType.returnType());
                mname = Maker.demangle(mname);
                var sig = BaseCallSignature.from(returnType, mname, params, true);
                tryAddMethod(modifierBits, sig);
            }
        }

        // FIXME: module stuff too perhaps?

        InnerClassesAttribute attr = model.findAttribute(Attributes.innerClasses()).orElse(null);

        // Maps short names to full class names.
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
                            loadableInnerClasses.putIfAbsent
                                (innerName.stringValue(), innerDesc.displayName());
                        }
                    }
                }
            }
        }

        // FIXME: If any loadableInnerClasses, then load them on demand.
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

    @Override // BaseClassTypeItem
    protected void init() {
        load();
    }

    @Override // BaseClassTypeItem
    protected void initFields() {
        load();
    }

    @Override // BaseClassTypeItem
    protected void initMethods() {
        load();
    }

    @Override // BaseClassTypeItem
    protected void initConstructors() {
        load();
    }
}
