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

import java.lang.runtime.ExactConversionsSupport;

import java.math.BigDecimal;
import java.math.BigInteger;

import java.util.Map;
import java.util.Objects;

import org.cojen.motto.model.Binding;

import org.cojen.motto.internal.compiler.CompilationEnv;

import org.cojen.motto.internal.util.InternSet;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public abstract sealed class BaseBinding implements Binding {
    static final BaseBinding[] EMPTY = new BaseBinding[0];

    @Override
    public abstract BaseType type();

    /**
     * Returns the environment the binding belongs to, or null if not applicable.
     */
    abstract CompilationEnv env();

    /**
     * @see BaseBlock#resolve
     */
    abstract boolean resolve(CompilationEnv expected, int newVersion);

    /**
     * If this is an anonymous binding or if it accesses any, then map.putIfAbsent(anonymous,
     * true) is called for each of them. If successful, then the anonymous bindings are tagged
     * as having a block interdependency. They depend on having a value assigned when the block
     * is entered.
     *
     * @see BaseAction#trackBlockLocalBindings
     */
    void trackBlockLocalSource(Map<Anonymous, Boolean> map) {
    }

    /**
     * If this is an anonymous binding, calls map.putIfAbsent(anonymous, false). If successful,
     * then the binding won't be tagged as having a block interdependency. It doesn't depend on
     * having a value assigned to it when the block is entered.
     *
     * @see BaseAction#trackBlockLocalBindings
     */
    void trackBlockLocalTarget(Map<Anonymous, Boolean> map) {
    }

    /**
     * Defines a binding which is always stable.
     */
    public static abstract sealed class Stable extends BaseBinding {
        Stable() {
        }

        @Override
        public final boolean isStable() {
            return true;
        }
    }

    public static abstract sealed class Unmodifiable extends Stable {
        Unmodifiable() {
        }

        @Override
        public final boolean isModifiable() {
            return false;
        }
    }

    public static final class Void extends Unmodifiable {
        public static final Void THE = new Void();

        private Void() {
        }

        @Override
        public BaseType type() {
            return BaseVoidType.THE;
        }

        @Override
        CompilationEnv env() {
            return null;
        }

        @Override
        boolean resolve(CompilationEnv expected, int newVersion) {
            return true;
        }
    }

    public static final class Null extends Unmodifiable {
        public static final Null THE = new Null();

        private Null() {
        }

        @Override
        public BaseType type() {
            return BaseNullType.THE;
        }

        @Override
        CompilationEnv env() {
            return null;
        }

        @Override
        boolean resolve(CompilationEnv expected, int newVersion) {
            return true;
        }
    }

    public static final class Constant extends Unmodifiable {
        public static Constant from(BaseType type, Object value) {
            return InternSet.apply(new Constant(type, value));
        }

        private final BaseType mType;
        private final Object mValue;

        private Constant(BaseType type, Object value) {
            mType = Objects.requireNonNull(type);
            mValue = value;
        }

        @Override
        public BaseType type() {
            return mType;
        }

        @Override
        CompilationEnv env() {
            return null;
        }

        @Override
        boolean resolve(CompilationEnv expected, int newVersion) {
            return true;
        }

        public Object value() {
            return mValue;
        }

        /**
         * @throws ArithmeticException
         */
        public long longValueExact() {
            switch (mValue) {
                case Byte n -> {
                    return n;
                }

                case Short n -> {
                    return n;
                }

                case Integer n -> {
                    return n;
                }

                case Long n -> {
                    return n;
                }

                case Float n -> {
                    float v = n;
                    if (ExactConversionsSupport.isFloatToLongExact(v)) {
                        return (long) v;
                    }
                }

                case Double n -> {
                    double v = n;
                    if (ExactConversionsSupport.isDoubleToLongExact(v)) {
                        return (long) v;
                    }
                }

                case BigDecimal n -> {
                    return n.longValueExact();
                }

                case BigInteger n -> {
                    return n.longValueExact();
                }

                default -> {
                    break;
                }
            }

            throw new ArithmeticException();
        }

        @Override
        public int hashCode() {
            return mType.hashCode() * 1908934629 + Objects.hashCode(mValue);
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof Constant other
                && mType.equals(other.mType) && Objects.equals(mValue, other.mValue);
        }
    }

    /**
     * Defines a constant boolean binding which is treated specially by a branch action. When
     * possible, it skips creation of a boolean variable. This kind of binding is used to
     * implement short-circuit boolean logic.
     *
     * @see BaseBlock#branch
     */
    public static final class Branch extends Unmodifiable {
        private final BaseBinding mValue;
        private final BaseBlock mTruePath, mFalsePath, mMerge;

        /**
         * Note: All blocks must belong to the same context.
         *
         * @param truePath the block which will assign a true value
         * @param falsePath the block which will assign a false value
         * @param merge the block to jump to after assigning a value
         * @throws IllegalArgumentException if either path block is terminated
         */
        public Branch(BaseBlock truePath, BaseBlock falsePath, BaseBlock merge) {
            if (truePath.isTerminated() || falsePath.isTerminated()) {
                throw new IllegalArgumentException();
            }

            mValue = new BaseBinding.Anonymous(merge.env(), BaseBooleanType.THE);

            truePath.copy(mValue, true);
            truePath.jump(merge);

            falsePath.copy(mValue, false);
            falsePath.jump(merge);

            mTruePath = truePath;
            mFalsePath = falsePath;

            mMerge = merge;
        }

        @Override
        public BaseType type() {
            return mValue.type();
        }

        @Override
        CompilationEnv env() {
            return mMerge.env();
        }

        @Override
        boolean resolve(CompilationEnv expected, int newVersion) {
            // Note that short-circuit && isn't used. Resolve as much as possible even when
            // recompilation is required.
            return mValue.resolve(expected, newVersion)
                & mTruePath.resolve(expected, newVersion)
                & mFalsePath.resolve(expected, newVersion)
                & mMerge.resolve(expected, newVersion);
        }

        @Override
        void trackBlockLocalSource(Map<Anonymous, Boolean> map) {
            mValue.trackBlockLocalSource(map);
        }

        @Override
        void trackBlockLocalTarget(Map<Anonymous, Boolean> map) {
            mValue.trackBlockLocalTarget(map);
        }

        public BaseBinding value() {
            return mValue;
        }

        BaseBlock mergeBlock() {
            return mMerge;
        }

        /**
         * Skip the merge block and instead jump to the given destinations. This binding should
         * then be discarded.
         */
        void skip(BaseBlock whenTrue, BaseBlock whenFalse) {
            setDestination(mTruePath, whenTrue);
            setDestination(mFalsePath, whenFalse);
        }

        private static void setDestination(BaseBlock path, BaseBlock destination) {
            ((BaseJumpAction) path.lastAction()).setDestination(path, destination);
        }
    }

    /**
     * Defines a binding which refers to a static field.
     */
    public static final class StaticField extends BaseBinding {
        public static StaticField from(ClassFieldItem field) {
            return InternSet.apply(new StaticField(field));
        }

        private final ClassFieldItem mField;

        private StaticField(ClassFieldItem field) {
            mField = Objects.requireNonNull(field);
        }

        @Override
        public BaseType type() {
            return mField.type();
        }

        @Override
        CompilationEnv env() {
            return null;
        }

        @Override
        boolean resolve(CompilationEnv expected, int newVersion) {
            return true;
        }

        @Override
        public boolean isStable() {
            return mField.isFinal();
        }

        @Override
        public boolean isModifiable() {
            return !mField.isFinal();
        }

        public ClassFieldItem field() {
            return mField;
        }

        @Override
        public int hashCode() {
            return mField.type().hashCode() * 28576597;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof StaticField other
                && mField.equals(other.mField);
        }
    }

    /**
     * Defines a binding which refers to an instance field.
     */
    public static final class InstanceField extends BaseBinding {
        private final BaseBinding mInstance;
        private final ClassFieldItem mField;

        public InstanceField(BaseBinding instance, ClassFieldItem field) {
            mInstance = Objects.requireNonNull(instance);
            mField = Objects.requireNonNull(field);
        }

        @Override
        public BaseType type() {
            return mField.type();
        }

        @Override
        CompilationEnv env() {
            return mInstance.env();
        }

        @Override
        boolean resolve(CompilationEnv expected, int newVersion) {
            return mInstance.resolve(expected, newVersion);
        }

        @Override
        public boolean isStable() {
            return mInstance.isStable() && mField.isFinal();
        }

        @Override
        public boolean isModifiable() {
            return !mField.isFinal();
        }

        public BaseBinding instance() {
            return mInstance;
        }

        public ClassFieldItem field() {
            return mField;
        }

        @Override
        void trackBlockLocalSource(Map<Anonymous, Boolean> map) {
            mInstance.trackBlockLocalSource(map);
        }
    }

    /**
     * Defines a binding which obtains the length of an array.
     */
    public static final class ArrayLength extends BaseBinding {
        private final BaseBinding mInstance;

        public ArrayLength(BaseBinding instance) {
            mInstance = Objects.requireNonNull(instance);
        }

        @Override
        public BaseType type() {
            return BaseIntType.THE;
        }

        @Override
        CompilationEnv env() {
            return mInstance.env();
        }

        @Override
        boolean resolve(CompilationEnv expected, int newVersion) {
            return mInstance.resolve(expected, newVersion);
        }

        @Override
        public boolean isStable() {
            return mInstance.isStable();
        }

        @Override
        public boolean isModifiable() {
            return false;
        }

        public BaseBinding instance() {
            return mInstance;
        }

        @Override
        void trackBlockLocalSource(Map<Anonymous, Boolean> map) {
            mInstance.trackBlockLocalSource(map);
        }
    }

    /**
     * Defines a binding for supporting class literals.
     */
    public static final class ClassLiteral extends BaseBinding {
        public static ClassLiteral from(BaseType sourceType) {
            return InternSet.apply(new ClassLiteral(sourceType));
        }

        private final BaseType mSourceType;

        private ClassLiteral(BaseType sourceType) {
            mSourceType = Objects.requireNonNull(sourceType);
        }

        @Override
        public BaseType type() {
            return LoadedClass.classFrom(Class.class);
        }

        @Override
        CompilationEnv env() {
            return null;
        }

        @Override
        boolean resolve(CompilationEnv expected, int newVersion) {
            return true;
        }

        @Override
        public boolean isStable() {
            return true;
        }

        @Override
        public boolean isModifiable() {
            return false;
        }

        public BaseType sourceType() {
            return mSourceType;
        }

        @Override
        public int hashCode() {
            return mSourceType.hashCode() * 1414798747;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof ClassLiteral other
                && mSourceType.equals(other.mSourceType);
        }
    }

    /**
     * Defines a binding which refers to a constant tuple field. Use TupleAction.Get or
     * TupleAction.Set if the field selection is variable.
     */
    public static final class TupleField extends BaseBinding {
        private final BaseBinding mTuple;
        private final int mIndex;

        public TupleField(BaseBinding tuple, int index) {
            if (index < 0 || !(tuple.type() instanceof BaseTupleType tt) ||
                index > tt.numFields())
            {
                throw new IllegalArgumentException();
            }

            mTuple = Objects.requireNonNull(tuple);
            mIndex = index;
        }

        @Override
        public BaseType type() {
            return tupleType().fieldType(mIndex);
        }

        @Override
        CompilationEnv env() {
            return mTuple.env();
        }

        @Override
        boolean resolve(CompilationEnv expected, int newVersion) {
            return mTuple.resolve(expected, newVersion);
        }

        @Override
        public boolean isStable() {
            return mTuple.isStable();
        }

        @Override
        public boolean isModifiable() {
            return false;
        }

        public BaseBinding tuple() {
            return mTuple;
        }

        public BaseTupleType tupleType() {
            return (BaseTupleType) mTuple.type();
        }

        public int index() {
            return mIndex;
        }

        @Override
        void trackBlockLocalSource(Map<Anonymous, Boolean> map) {
            mTuple.trackBlockLocalSource(map);
        }
    }

    public static abstract sealed class Local extends Stable {
        private final CompilationEnv mEnv;
        final BaseType mType;

        private int mVersion;

        /**
         * @param env required
         * @param type required
         */
        Local(CompilationEnv env, BaseType type) {
            mEnv = Objects.requireNonNull(env);
            mType = Objects.requireNonNull(type);
        }

        @Override
        public final BaseType type() {
            return mType;
        }

        @Override
        final CompilationEnv env() {
            return mEnv;
        }

        @Override
        final boolean resolve(CompilationEnv expected, int newVersion) {
            BaseBlock.checkEnv(mEnv, expected);

            if (BaseBlock.checkVersion(mVersion, newVersion)) {
                mVersion = newVersion;
            }

            return true;
        }

        @Override
        public boolean isModifiable() {
            return true;
        }

        /**
         * @return -1 if not a parameter
         */
        public int index() {
            return -1;
        }

        /**
         * @return optional name
         */
        public String name() {
            return null;
        }
    }

    public static sealed class Named extends Local {
        final String mName;

        /**
         * @param env required
         * @param type required
         * @param name required
         */
        public Named(CompilationEnv env, BaseType type, String name) {
            super(env, type);
            mName = Objects.requireNonNull(name);
        }

        /**
         * @param env required
         * @param name optional
         * @param type required
         */
        protected Named(CompilationEnv env, String name, BaseType type) {
            super(env, type);
            mName = name;
        }

        @Override
        public final String name() {
            return mName;
        }
    }

    public static final class Parameter extends Named {
        private final int mIndex;

        /**
         * @param env required
         * @param type required
         * @param name optional
         * @throws IllegalArgumentException if index is negative
         */
        public Parameter(CompilationEnv env, BaseType type, String name, int index) {
            super(env, name, type);
            if (index < 0) {
                throw new IllegalArgumentException();
            }
            mIndex = index;
        }

        @Override
        public int index() {
            return mIndex;
        }
    }

    /**
     * An anonymous variable can be eliminated during code generation as an optimization.
     */
    public static final class Anonymous extends Local {
        private boolean mHasBlockInterdependency;

        /**
         * @param env required
         * @param type required
         */
        public Anonymous(CompilationEnv env, BaseType type) {
            super(env, type);
        }

        @Override
        void trackBlockLocalSource(Map<Anonymous, Boolean> map) {
            if (map.putIfAbsent(this, true) == null) {
                mHasBlockInterdependency = true;
            }
        }

        @Override
        void trackBlockLocalTarget(Map<Anonymous, Boolean> map) {
            map.putIfAbsent(this, false);
        }

        /**
         * Returns true if this binding has been tracked as a block local source at least once.
         */
        boolean hasBlockInterdependency() {
            return mHasBlockInterdependency;
        }
    }

    /**
     * Defines a binding which refers to a captured local variable, used by a local inner class.
     */
    public static final class Captured extends BaseBinding {
        private final CompilationEnv mEnv;
        private final BaseCallableItem mCallable;
        private final BaseType mType;
        private final String mName;

        /**
           shared:       binding is shared by multiple threads
           non-escaping: binding doesn't escape the current thread
           copied:       binding value is copied into the local inner class (implies non-escaping)
         */
        public static final int MODE_SHARED = 0, MODE_NON_ESCAPING = 1, MODE_COPIED = 2;

        private int mAccessMode;

        /**
         * @param env required
         * @param callable declares the local variable
         */
        Captured(CompilationEnv env, BaseCallableItem callable, BaseType type, String name) {
            mEnv = Objects.requireNonNull(env);
            mCallable = Objects.requireNonNull(callable);
            mType = Objects.requireNonNull(type);
            mName = Objects.requireNonNull(name);
        }

        @Override
        public BaseType type() {
            return mType;
        }

        @Override
        CompilationEnv env() {
            return mEnv;
        }

        @Override
        boolean resolve(CompilationEnv expected, int newVersion) {
            BaseBlock.checkEnv(mEnv, expected);
            return true;
        }

        @Override
        public boolean isStable() {
            return mAccessMode != MODE_SHARED;
        }

        @Override
        public boolean isModifiable() {
            return true;
        }

        public BaseCallableItem callable() {
            return mCallable;
        }

        public String name() {
            return mName;
        }
    }

    /**
     * Defines a binding which refers to code which can be passed to a macro call.
     */
    public static final class CodeBinding extends Unmodifiable {
        private final BaseBlock mEntry;
        private final BaseCodeType mType;

        public CodeBinding(BaseBlock entry, BaseType resultType) {
            mEntry = entry;
            mType = BaseCodeType.from(resultType);
        }

        @Override
        public BaseCodeType type() {
            return mType;
        }

        @Override
        CompilationEnv env() {
            return mEntry.env();
        }

        @Override
        boolean resolve(CompilationEnv expected, int newVersion) {
            return mEntry.resolve(expected, newVersion);
        }

        public BaseBlock entry() {
            return mEntry;
        }
    }
}
