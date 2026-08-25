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

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

import java.util.concurrent.ConcurrentHashMap;

import java.util.function.Predicate;

import java.util.stream.Stream;

import org.cojen.maker.ClassMaker;
import org.cojen.maker.Maker;
import org.cojen.maker.MethodMaker;

import org.cojen.motto.internal.compiler.CompilationEnv;

import org.cojen.motto.model.CallableItem;
import org.cojen.motto.model.CallSignature;
import org.cojen.motto.model.ClassTypeItem;
import org.cojen.motto.model.FieldItem;
import org.cojen.motto.model.Item;
import org.cojen.motto.model.ObjectType;
import org.cojen.motto.model.PrimitiveType;
import org.cojen.motto.model.TupleType;
import org.cojen.motto.model.Type;

import static org.cojen.motto.internal.model.Modifiers.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public abstract sealed class BaseClassTypeItem extends BaseItem
    implements BaseObjectType, ClassTypeItem, EncodableType.ClassT
    permits ExternalClass, LoadedClass, NewClass
{
    private final BasePath mPackagePath, mNamePath;

    private ClassDesc mClassDesc;

    private BaseClassTypeItem mSuperType;
    private Set<BaseClassTypeItem> mSuperInterfaces;

    private Map<String, BaseFieldItem> mFieldMap;
    private Map<String, Map<BaseCallSignature, BaseCallableItem>> mMethodMap;
    private Map<BaseCallSignature, BaseCallableItem> mConstructorMap;
    private volatile Map<String, BaseClassTypeItem> mInnerClassesMap;

    BaseClassTypeItem(int modifierBits, BasePath packagePath, BasePath namePath) {
        super(modifierBits);

        mPackagePath = Objects.requireNonNull(packagePath);
        mNamePath = Objects.requireNonNull(namePath);

        mSuperInterfaces = Set.of();

        mFieldMap = Map.of();
        mMethodMap = Map.of();
        mConstructorMap = Map.of();
        mInnerClassesMap = Map.of();
    }

    /**
     * Returns the CompilationEnv for a class which is being compiled. Is null otherwise.
     */
    public CompilationEnv env() {
        return null;
    }

    @Override
    public final BaseClassTypeItem enclosingType() {
        return outerType();
    }

    @Override
    public final BaseClassTypeItem nearestType() {
        return this;
    }

    @Override
    public final BaseClassTypeItem nearestClass() {
        return this;
    }

    @Override
    public final BaseClassTypeItem superType() {
        try {
            init();
        } catch (InterruptedException e) {
            return null;
        }

        return mSuperType;
    }

    @Override
    public final Set<? extends BaseClassTypeItem> interfaces() {
        try {
            init();
        } catch (InterruptedException e) {
            return Set.of();
        }

        return mSuperInterfaces;
    }

    @Override
    public final BasePath packagePath() {
        return mPackagePath;
    }

    @Override
    public final BasePath namePath() {
        return mNamePath;
    }

    public final int fullPathSize() {
        return packagePath().size() + namePath().size();
    }

    /**
     * Returns the name path, separated with '$' characters, with mangling of special
     * characters.
     */
    public final String mangledName() {
        BasePath namePath = namePath();

        if (namePath.size() == 1) {
            return Maker.mangle(namePath.getFirst());
        }

        var b = new StringBuilder();

        namePath.appendMangledTo(b, '$');

        return b.toString();
    }

    /**
     * Returns a fully qualified class name, dot separated, with mangling of special
     * characters.
     */
    public final String fullMangledName() {
        BasePath packagePath = packagePath();
        BasePath namePath = namePath();

        if (packagePath.isEmpty() && namePath.size() == 1) {
            return Maker.mangle(namePath.getFirst());
        }

        var b = new StringBuilder();

        if (!packagePath.isEmpty()) {
            packagePath.appendMangledTo(b, '.').append('.');
        }

        namePath.appendMangledTo(b, '$');

        return b.toString();
    }

    @Override
    public final ClassDesc asClassDesc() {
        if (mClassDesc == null) {
            mClassDesc = EncodableType.ClassT.super.asClassDesc();
        }
        return mClassDesc;
    }

    @Override
    public final boolean isStringType() {
        if (packagePath().equals(BasePath.JAVA_LANG)) {
            BasePath namePath = namePath();
            return namePath.size() == 1 && namePath.getFirst().equals("String");
        }
        return false;
    }

    @Override
    public final BaseClassTypeItem outerType() {
        // FIXME: outerType
        return null;
    }

    @Override
    public final BaseClassTypeItem nestType() {
        BaseClassTypeItem thisType = this;
        while (true) {
            BaseClassTypeItem outerType = thisType.outerType();
            if (outerType == null) {
                return thisType;
            }
            thisType = outerType;
        }
    }

    @Override
    public final StringBuilder appendDisplayNameTo(StringBuilder b) {
        BasePath packagePath = packagePath();
        if (!packagePath.isEmpty()) {
            packagePath.appendTo(b).append('.');
        }
        return namePath().appendTo(b);
    }

    @Override
    public final boolean isInterface() {
        return (modifierBits() & INTERFACE) != 0;
    }

    @Override
    public final int numFields() {
        return fieldMap().size();
    }

    @Override
    public final Stream<? extends BaseFieldItem> fields() {
        return fieldMap().values().stream();
    }

    @Override
    public final BaseFieldItem field(String name) {
        BaseFieldItem field = fieldMap().get(name);
        if (field == null) {
            throw new NoSuchElementException();
        }
        return field;
    }

    private Map<String, BaseFieldItem> fieldMap() {
        try {
            initFields();
        } catch (InterruptedException e) {
            return Map.of();
        }

        return mFieldMap;
    }

    @Override
    public final BaseFieldItem field(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final int fieldIndex(String name) {
        throw new UnsupportedOperationException();
    }

    /**
     * Attempt to add a field.
     *
     * @return null if a conflicting field definition already exists
     */
    public final BaseFieldItem tryAddField(int modifierBits, BaseType type, String name) {
        var field = new BaseFieldItem(modifierBits, this, type, name);

        Map<String, BaseFieldItem> map = mFieldMap;

        if (map.isEmpty()) {
            mFieldMap = map = new LinkedHashMap<>();
            map.put(name, field);
            return field;
        } else {
            return map.putIfAbsent(name, field) == null ? field : null;
        }
    }

    @Override
    public Set<BaseFieldItem> findField(String name, Predicate<FieldItem> filter) {
        return doFindField(Set.of(), name, filter, new HashSet<>());
    }

    private Set<BaseFieldItem> doFindField(Set<BaseFieldItem> set, String name,
                                           Predicate<FieldItem> filter,
                                           Set<BaseClassTypeItem> seen)
    {
        BaseFieldItem field = fieldMap().get(name);
        if (field != null && (filter == null || filter.test(field))) {
            set = addItemToSet(set, field);
        }

        BaseClassTypeItem superType = superType();

        if (superType != null && seen.add(superType)) {
            set = superType.doFindField(set, name, filter, seen);
        }

        for (BaseClassTypeItem iface : interfaces()) {
            if (seen.add(iface)) {
                set = iface.doFindField(set, name, filter, seen);
            }
        }

        return set;
    }

    /**
     * Returns the modifiers for the given field name, or else returns -1 if not found.
     */
    public int findFieldForImport(String name) {
        BaseFieldItem field = fieldMap().get(name);
        if (field == null) {
            return -1;
        }
        int modifierBits = field.modifierBits();
        return (modifierBits & STATIC) == 0 ? -1 : modifierBits;
    }

    @Override
    public final int numMethods() {
        return (int) methods().count();
    }

    @Override
    public final Stream<? extends BaseCallableItem> methods() {
        return methodMap().values().stream().flatMap(byName -> byName.values().stream());
    }

    @Override
    public final Stream<? extends BaseCallableItem> methods(String name) {
        Map<BaseCallSignature, BaseCallableItem> byName = methodMap().get(name);
        return byName == null ? Stream.empty() : byName.values().stream();
    }

    @Override
    public final BaseCallableItem method(CallSignature sig) {
        Map<BaseCallSignature, BaseCallableItem> byName = methodMap().get(sig.name());
        BaseCallableItem item;
        if (byName == null || (item = byName.get(sig.noFieldNames())) == null) {
            throw new NoSuchElementException();
        }
        return item;
    }

    private Map<String, Map<BaseCallSignature, BaseCallableItem>> methodMap() {
        try {
            initMethods();
        } catch (InterruptedException e) {
            return Map.of();
        }

        return mMethodMap;
    }

    @Override
    public final Map<BaseCallSignature, Set<CallableItem>> findMethod
        (String name, BaseTupleType inputType, Predicate<CallableItem> filter)
    {
        // Note: The evaluated option is ignored. See BaseCallSignature.canBindTo.
        BaseCallSignature sig = BaseCallSignature.from
            (BaseUnspecifiedType.THE, name, inputType, true);

        return findMethod(sig, filter);
    }

    @Override
    public Map<BaseCallSignature, Set<CallableItem>> findMethod
        (CallSignature sig, Predicate<CallableItem> filter)
    {
        return findMethod((BaseCallSignature) sig, filter);
    }

    @Override
    public Map<BaseCallSignature, Set<CallableItem>> findMethod
        (BaseCallSignature sig, Predicate<CallableItem> filter)
    {
        Map<BaseCallSignature, Set<CallableItem>> map =
            doFindMethod(Map.of(), sig, filter, null, new HashSet<>());

        return reduceCallables(map, sig);
    }

    /**
     * @param base should be null for the first call (it becomes "this" for recursive calls)
     */
    private Map<BaseCallSignature, Set<CallableItem>> doFindMethod
        (Map<BaseCallSignature, Set<CallableItem>> map,
         BaseCallSignature sig, Predicate<CallableItem> filter, BaseClassTypeItem base,
         Set<BaseClassTypeItem> seen)
    {
        map = findCallable(map, sig, filter, base, methodMap().get(sig.name()));

        if (base == null) {
            base = this;
        }

        BaseClassTypeItem superType = superType();

        if (superType != null && seen.add(superType)) {
            map = superType.doFindMethod(map, sig, filter, base, seen);
        }

        for (BaseClassTypeItem iface : interfaces()) {
            if (seen.add(iface)) {
                map = iface.doFindMethod(map, sig, filter, base, seen);
            }
        }

        return map;
    }

    /**
     * Attempt to add a method, which initially doesn't have any code.
     *
     * @return null if a conflicting method definition already exists
     * @throws IllegalArgumentException if adding an instance method and the first
     * parameter isn't named "this"
     */
    public final BaseCallableItem tryAddMethod(int modifierBits, BaseCallSignature sig) {
        BaseCallSignature key = sig.noFieldNames();

        if ((modifierBits & STATIC) == 0) {
            validateThis(sig.inputType());
            key = key.trimFirst();
        }

        BaseCallableItem method = BaseCallableItem.from(modifierBits, this, sig);

        Map<String, Map<BaseCallSignature, BaseCallableItem>> map = mMethodMap;

        if (map.isEmpty()) {
            mMethodMap = map = new LinkedHashMap<>();
        }

        String name = sig.name();
        Map<BaseCallSignature, BaseCallableItem> byName = map.get(name);

        if (byName == null) {
            byName = new LinkedHashMap<>();
            map.put(name, byName);
        }

        return byName.putIfAbsent(key, method) == null ? method : null;
    }

    /**
     * Returns the modifiers for the given method name, or else returns -1 if not found. If
     * multiple methods with the same name are found, the more accessible static modifier is
     * selected.
     */
    public int findMethodForImport(String name) {
        Map<BaseCallSignature, BaseCallableItem> byName = methodMap().get(name);
        if (byName == null) {
            return -1;
        }
        int modifierBits = 0;
        for (BaseCallableItem method : byName.values()) {
            int mods = method.modifierBits();
            if ((mods & STATIC) != 0 &&
                (modifierBits == 0 || isMoreAccessible(modifierBits, mods)))
            {
                modifierBits = mods;
            }
        }
        return (modifierBits & STATIC) == 0 ? -1 : modifierBits;
    }

    static boolean isMoreAccessible(int existing, int modifierBits) {
        if ((existing & PUBLIC) != 0) {
            return false;
        } else if ((existing & PROTECTED) != 0) {
            return (modifierBits & PUBLIC) != 0;
        } else if ((existing & INTERNAL) != 0) {
            return (modifierBits & (PUBLIC | PROTECTED)) != 0;
        } else {
            return (modifierBits & (PUBLIC | PROTECTED | INTERNAL)) != 0;
        }
    }

    @Override
    public final int numConstructors() {
        return constructorMap().size();
    }

    @Override
    public final Stream<? extends BaseCallableItem> constructors() {
        return constructorMap().values().stream();
    }

    @Override
    public final BaseCallableItem constructor(CallSignature sig) {
        BaseCallableItem ctor = constructorMap().get(sig);
        if (ctor == null) {
            throw new NoSuchElementException();
        }
        return ctor;
    }

    private Map<BaseCallSignature, BaseCallableItem> constructorMap() {
        try {
            initConstructors();
        } catch (InterruptedException e) {
            return Map.of();
        }

        return mConstructorMap;
    }

    @Override
    public Map<BaseCallSignature, BaseCallableItem> findConstructor
        (BaseTupleType inputType, Predicate<CallableItem> filter)
    {
        // Note: The evaluated option is ignored. See BaseCallSignature.canBindTo.
        BaseCallSignature sig = BaseCallSignature.from(BaseVoidType.THE, "", inputType, true);

        Map<BaseCallSignature, Set<CallableItem>> mapOfSets =
            findCallable(Map.of(), sig, filter, null, constructorMap());

        int size = mapOfSets.size();

        if (size == 0) {
            return Map.of();
        }

        Map<BaseCallSignature, BaseCallableItem> map = LinkedHashMap.newLinkedHashMap(size);

        for (Map.Entry<BaseCallSignature, Set<CallableItem>> e : mapOfSets.entrySet()) {
            Set<CallableItem> set = e.getValue();
            if (set.size() != 1) {
                throw new AssertionError();
            }
            map.put(e.getKey(), (BaseCallableItem) set.iterator().next());
        }

        return map;
    }

    /**
     * Attempt to add a constructor, which initially doesn't have any code.
     *
     * @param inputType the first parameter must be named "this", with the correct type
     * @param evaluated when false, the inputType elements have been converted to function
     * types, except for "this"
     * @return null if a conflicting constructor definition already exists
     * @throws IllegalArgumentException the first parameter isn't named "this"
     */
    public final BaseCallableItem tryAddConstructor(int modifierBits, BaseTupleType inputType,
                                                    boolean evaluated)
    {
        var sig = BaseCallSignature.from(BaseVoidType.THE, "", validateThis(inputType), evaluated);

        var ctor = BaseCallableItem.from(modifierBits, this, sig);

        Map<BaseCallSignature, BaseCallableItem> map = mConstructorMap;
        BaseCallSignature key = sig.noFieldNames();

        if (map.isEmpty()) {
            mConstructorMap = map = new LinkedHashMap<>();
            map.put(key, ctor);
            return ctor;
        } else {
            return map.putIfAbsent(key, ctor) == null ? ctor : null;
        }
    }

    @Override
    public Stream<? extends BaseClassTypeItem> innerClasses() {
        return mInnerClassesMap.values().stream();
    }

    @Override
    public BaseClassTypeItem innerClass(String name) {
        BaseClassTypeItem clazz = mInnerClassesMap.get(name);
        if (clazz == null) {
            throw new NoSuchElementException();
        }
        return clazz;
    }

    @Override
    public Set<BaseClassTypeItem> findInnerClass(String name, Predicate<ClassTypeItem> filter) {
        return doFindInnerClass(Set.of(), name, filter, new HashSet<>());
    }

    private Set<BaseClassTypeItem> doFindInnerClass
        (Set<BaseClassTypeItem> set, String name, Predicate<ClassTypeItem> filter,
         Set<BaseClassTypeItem> seen)
    {
        BaseClassTypeItem inner = mInnerClassesMap.get(name);

        if (inner != null && (filter == null || filter.test(inner))) {
            return addItemToSet(set, inner);
        }

        BaseClassTypeItem superType = superType();

        if (superType != null && seen.add(superType)) {
            set = superType.doFindInnerClass(set, name, filter, seen);
        }

        for (BaseClassTypeItem iface : interfaces()) {
            if (seen.add(iface)) {
                set = iface.doFindInnerClass(set, name, filter, seen);
            }
        }

        return set;
    }

    /**
     * Attempt to add an inner class, which initially doesn't need to have any members. The
     * package of the inner class must match the package of this outer class, and the name path
     * of the inner class must match that of the outer class, plus the simple inner class name.
     *
     * @return false if the inner class already exists
     * @throws IllegalArgumentException the package or name paths don't match
     */
    public final boolean tryAddInnerClass(BaseClassTypeItem inner) {
        if (!isValidInnerClass(inner)) {
            throw new IllegalArgumentException();
        }

        Map<String, BaseClassTypeItem> map = mInnerClassesMap;

        if (map.isEmpty()) {
            mInnerClassesMap = map = new ConcurrentHashMap<>();
        }

        return map.putIfAbsent(inner.namePath().getLast(), inner) == null;
    }

    private boolean isValidInnerClass(BaseClassTypeItem inner) {
        return inner.packagePath().equals(packagePath())
            && inner.namePath().trimLastNonCanonical().equals(namePath());
    }

    /**
     * @return null if not found
     */
    public BaseClassTypeItem findInnerClassForImport(String name) {
        return mInnerClassesMap.get(name);
    }

    @Override
    public final boolean isArray() {
        return false;
    }

    @Override
    public final BaseType noFieldNames() {
        // The field names cannot be removed.
        return this;
    }

    @Override
    public final BasePrimitiveType unbox() {
        if (packagePath().equals(BasePath.JAVA_LANG)) {
            BasePath namePath = namePath();
            if (namePath.size() == 1) {
                return switch (namePath.getFirst()) {
                    case "Void"      -> BaseVoidType.THE;
                    case "Boolean"   -> BaseBooleanType.THE;
                    case "Character" -> BaseCharType.THE;
                    case "Byte"      -> BaseByteType.THE;
                    case "Short"     -> BaseShortType.THE;
                    case "Integer"   -> BaseIntType.THE;
                    case "Long"      -> BaseLongType.THE;
                    case "Float"     -> BaseFloatType.THE;
                    case "Double"    -> BaseDoubleType.THE;
                    default -> null;
                };
            }
        }

        return null;
    }

    @Override
    public final boolean isEquivalentTo(Type other) {
        return other instanceof ClassTypeItem otherClass
            && packagePath().equals(otherClass.packagePath())
            && namePath().equals(otherClass.namePath());
    }

    @Override
    public final int canConvertTo(Type to) {
        int code = BaseObjectType.super.canConvertTo(to);

        if (code != Integer.MAX_VALUE) {
            return code;
        }

        BaseClassTypeItem superType = superType();

        if (superType != null && to.isAssignableFrom(superType)) {
            return 0;
        }

        for (BaseClassTypeItem iface : interfaces()) {
            if (to.isAssignableFrom(iface)) {
                return 0;
            }
        }

        BasePrimitiveType thisUnboxed;
        PrimitiveType toUnboxed;

        if ((thisUnboxed = unbox()) == null || (toUnboxed = to.unbox()) == null) {
            return Integer.MAX_VALUE;
        }

        // This point is reached when converting boxed primitives.

        // Expect 0..6 or max
        code = thisUnboxed.canConvertTo(toUnboxed);

        if (code != Integer.MAX_VALUE) {
            code += to instanceof ObjectType ? 14 : 21;
        }

        return code;
    }

    @Override
    public final boolean isJavaLangObject() {
        BasePath namePath;
        return packagePath().equals(BasePath.JAVA_LANG)
            && (namePath = namePath()).size() == 1 && "Object".equals(namePath.getFirst());
    }

    final void applyModifiers(ClassMaker cm) {
        super.applyModifiers(cm);

        int modifiers = modifierBits();

        if ((modifiers & INTERFACE) != 0) {
            cm.interface_();
        } else if ((modifiers & ABSTRACT) != 0) {
            cm.abstract_();
        }
    }

    /**
     * @param superType optional (only for java.lang.Object)
     * @param interfaces optional
     */
    public final void setSuperTypes(BaseClassTypeItem superType,
                                    Set<BaseClassTypeItem> interfaces)
    {
        mSuperType = superType;
        mSuperInterfaces = interfaces == null ? Set.of() : interfaces;
    }

    /**
     * Validates that the first input element is a "this" parameter.
     *
     * @return the given inputType
     * @throws IllegalArgumentException if the first input element isn't "this", of the same
     * type as the this
     */
    private final BaseTupleType validateThis(BaseTupleType inputType) {
        if (inputType.numFields() == 0 || !inputType.fieldType(0).equals(this) ||
            !"this".equals(inputType.fieldName(0)))
        {
            throw new IllegalArgumentException();
        }
        return inputType;
    }

    static Object[] makerParamsFor(BaseCallableItem item) {
        return makerParamsFor(item, item.signature());
    }

    static Object[] makerParamsFor(BaseCallableItem item, BaseCallSignature sig) {
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

    static void applyParamNames(MethodMaker mm, BaseCallableItem item) {
        applyParamNames(mm, item, item.signature());
    }

    static void applyParamNames(MethodMaker mm, BaseCallableItem item, BaseCallSignature sig) {
        BaseTupleType inputType = sig.inputType();
        int numFields = inputType.numFields();

        int offset = item.isStatic() ? 0 : 1; // Drop the implicit "this" parameter.

        for (int ix = offset; ix < numFields; ix++) {
            String name = inputType.fieldName(ix);
            if (name != null) {
                mm.param(ix - offset).name(Maker.mangle(name));
            }
        }
    }

    /**
     * Override to initialize the super types or wait until they're ready. Implementation is
     * required to check if init has already been called.
     */
    protected void init() throws InterruptedException {
    }

    /**
     * Override to initialize the fields or wait until they're ready. Implementation is
     * required to check if initFields has already been called.
     */
    protected void initFields() throws InterruptedException {
    }

    /**
     * Override to initialize the methods or wait until they're ready. Implementation is
     * required to check if initMethods has already been called.
     */
    protected void initMethods() throws InterruptedException {
    }

    /**
     * Override to initialize the constructors or wait until they're ready. Implementation is
     * required to check if initConstructors has already been called.
     */
    protected void initConstructors() throws InterruptedException {
    }

    /**
     * @param map original map, possibly empty
     * @param via can pass null to only return publicly available methods
     * @param base when non-null, it represents the specific type being called (the base type
     * will remain the same while the super type(s) are examined)
     * @return the actual map
     */
    private static Map<BaseCallSignature, Set<CallableItem>> findCallable
        (Map<BaseCallSignature, Set<CallableItem>> map,
         BaseCallSignature sig, Predicate<CallableItem> filter,
         BaseClassTypeItem base, Map<BaseCallSignature, BaseCallableItem> available)
    {
        if (available == null) {
            return map;
        }

        for (Map.Entry<BaseCallSignature, BaseCallableItem> e : available.entrySet()) {
            BaseCallableItem item = e.getValue();

            if (filter != null && !filter.test(item)) {
                continue;
            }

            // Note: The key doesn't have the implicit "this" parameter for instance methods,
            // unlike the item itself.
            BaseCallSignature key = e.getKey();

            if (!sig.canBindTo(key)) {
                continue;
            }

            if (base != null) {
                if (item.isPrivate()) {
                    // Cannot be inherited.
                    continue;
                }

                if ((item.modifierBits() & INTERNAL) != 0) {
                    // Check if an internal method can be inherited. It must be in the same
                    // package as the base.

                    if (!(item.enclosingType() instanceof ClassTypeItem enclosing)) {
                        // The item cannot be defined in a package, and so it can't really have
                        // inheritable internal methods anyhow.
                        continue;
                    }

                    if (!enclosing.packagePath().equals(base.packagePath())) {
                        // The package doesn't match, and so the method cannot be inherited.
                        continue;
                    }
                }
            }

            if (map.isEmpty()) {
                map = Map.of(key, Set.of(item));
            } else {
                if (map.size() == 1) {
                    map = new LinkedHashMap<>(map);
                }
                map.put(key, addItemToSet(map.get(key), item));
            }
        }

        return map;
    }

    private static Map<BaseCallSignature, Set<CallableItem>> reduceCallables
        (Map<BaseCallSignature, Set<CallableItem>> map, BaseCallSignature sig)
    {
        if (map.size() > 1) {
            Iterator<Map.Entry<BaseCallSignature, Set<CallableItem>>> it =
                map.entrySet().iterator();

            Map.Entry<BaseCallSignature, Set<CallableItem>> best = it.next();

            var bestMap = new LinkedHashMap<BaseCallSignature, Set<CallableItem>>(1);
            bestMap.put(best.getKey(), best.getValue());

            while (it.hasNext()) {
                Map.Entry<BaseCallSignature, Set<CallableItem>> candidate = it.next();
                int cmp = sig.bindCompare(best.getKey(), candidate.getKey());
                if (cmp >= 0) {
                    if (cmp > 0) {
                        best = candidate;
                        bestMap.clear();
                        bestMap.put(best.getKey(), best.getValue());
                    } else {
                        bestMap.put(candidate.getKey(), candidate.getValue());
                    }
                }
            }

            map = bestMap;
        }

        if (map.size() > 1) {
            // If any non-bridge methods, remove the bridge methods. Non-bridge methods are a
            // closer match, and so they're preferred.

            int nonBridges = 0, bridges = 0;

            for (Set<CallableItem> set : map.values()) {
                for (CallableItem callable : set) {
                    if (((BaseCallableItem) callable).isBridge()) {
                        bridges++;
                    } else {
                        nonBridges++;
                    }
                }
            }

            if (nonBridges > 0 && bridges > 0) {
                Iterator<Map.Entry<BaseCallSignature, Set<CallableItem>>> it =
                    map.entrySet().iterator();

                while (it.hasNext()) {
                    Map.Entry<BaseCallSignature, Set<CallableItem>> e = it.next();

                    Set<CallableItem> set = e.getValue();

                    if (set.size() == 1) {
                        if (((BaseCallableItem) set.iterator().next()).isBridge()) {
                            it.remove();
                        }
                    } else {
                        boolean anyRemoved = false;

                        {
                            Iterator<CallableItem> setIt = set.iterator();
                            while (setIt.hasNext()) {
                                if (((BaseCallableItem) setIt.next()).isBridge()) {
                                    setIt.remove();
                                    anyRemoved = true;
                                }
                            }
                        }

                        if (anyRemoved) {
                            int size = set.size();
                            if (size <= 0) {
                                it.remove();
                            } else if (size == 1) {
                                // Replace with a singleton set.
                                e.setValue(Set.of(set.iterator().next()));
                            }
                        }
                    }
                }
            }
        }

        return map;
    }

    /**
     * If the set is empty, always adds the item and returns a new set. If an item is already
     * in the set, and it's defined in a class (not an interface), no new items are added.
     * Multiple items can exist in the set only if they're all defined in interfaces. The
     * caller must ensure that all class items are added first.
     */
    private static <I extends Item> Set<I> addItemToSet(Set<I> set, I item) {
        if (set == null || set.isEmpty()) {
            return Set.of(item);
        }

        if (set.size() == 1) {
            if (!set.iterator().next().enclosingType().isInterface()) {
                return set;
            }
            set = new LinkedHashSet<>(set);
        }

        set.add(item);

        return set;
    }
}
