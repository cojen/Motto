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

import java.util.Map;
import java.util.Objects;

import org.cojen.motto.model.Binding;

import org.cojen.motto.internal.util.InternSet;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public abstract sealed class BaseBinding implements Binding {
    static final BaseBinding[] EMPTY = new BaseBinding[0];

    /* FIXME: Define an Unresolved binding type. It refers to name, and a macro is responsible
       for replacing the binding. If any unresolved bindings remain after the macro is called,
       then report an error. This feature can be used for implementing query macros like prql.

       It might also be possible to rely on declarations instead of unresolved bindings.

       query {
           // from MyTable
           MyTable t;
           filter (t.status == 0);
       }

       In order for this form to work, definite assignment analysis must be postponed until
       after the macro is called.

       I wonder if I should introduce new syntax for operating within a context:

       // In the context of tuple or class t:
       t.{
           // No need to prefix with t.
           filter (status == 0);

           // Possibly a standalone expression works as a filter.
           status == 0;
       }

       MyTable t.{
           ...
       }

       Projection:

       _ sub = table.{yield (status, message)};

       ...which is equivalant to:

       _ sub = (table.status, table.message);

       By the way, I think a `yield` statement (not defined yet) must be required in order for
       a scope to yield a value. Automatic yield of the last statement is error prone. Imagine
       this error:

       _ sub = table.{status, message};

       The sub variable would just be message if yield was automatic.

    */

    @Override
    public abstract BaseType type();

    @Override
    public boolean isVolatile() {
        return false;
    }

    @Override
    public boolean isStable() {
        return true;
    }

    /**
     * If this is an anonymous binding, calls map.putIfAbsent(binding, true). If successful,
     * then the binding is tagged as having a block interdependency. It depends on having a
     * value assigned to it when the block is entered.
     *
     * @see BaseAction#trackBlockLocalBindings
     */
    void trackBlockLocalSource(Map<Anonymous, Boolean> map) {
    }

    /**
     * If this is an anonymous binding, calls map.putIfAbsent(binding, false). If successful,
     * then the binding won't be tagged as having a block interdependency. It doesn't depend on
     * having a value assigned to it when the block is entered.
     *
     * @see BaseAction#trackBlockLocalBindings
     */
    void trackBlockLocalTarget(Map<Anonymous, Boolean> map) {
    }

    public static final class Void extends BaseBinding {
        public static final Void THE = new Void();

        private Void() {
        }

        @Override
        public BaseType type() {
            return BaseVoidType.THE;
        }
    }

    public static final class Null extends BaseBinding {
        public static final Null THE = new Null();

        private Null() {
        }

        @Override
        public BaseType type() {
            return BaseNullType.THE;
        }
    }

    public static final class Constant extends BaseBinding {
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

        public Object value() {
            return mValue;
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
     * Defines a binding which refers to a static field.
     */
    public static final class Static extends BaseBinding {
        public static Static from(BaseFieldItem field) {
            return InternSet.apply(new Static(field));
        }

        private final BaseFieldItem mField;

        private Static(BaseFieldItem field) {
            mField = Objects.requireNonNull(field);
        }

        @Override
        public BaseType type() {
            return mField.type();
        }

        @Override
        public boolean isVolatile() {
            return (mField.modifierBits() & Modifiers.VOLATILE) != 0;
        }

        @Override
        public boolean isStable() {
            return mField.isFinal();
        }

        public BaseFieldItem field() {
            return mField;
        }

        @Override
        public int hashCode() {
            return mField.type().hashCode() * 28576597;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof Static other
                && mField.equals(other.mField);
        }
    }

    /**
     * Defines a binding which refers to an instance field.
     */
    public static final class Instance extends BaseBinding {
        public static Instance from(BaseBinding instance, BaseFieldItem field) {
            return InternSet.apply(new Instance(instance, field));
        }

        private final BaseBinding mInstance;
        private final BaseFieldItem mField;

        private Instance(BaseBinding instance, BaseFieldItem field) {
            mInstance = Objects.requireNonNull(instance);
            mField = Objects.requireNonNull(field);
        }

        @Override
        public BaseType type() {
            return mField.type();
        }

        @Override
        public boolean isVolatile() {
            return (mField.modifierBits() & Modifiers.VOLATILE) != 0;
        }

        @Override
        public boolean isStable() {
            return mField.isFinal();
        }

        public BaseBinding instance() {
            return mInstance;
        }

        public BaseFieldItem field() {
            return mField;
        }

        @Override
        public int hashCode() {
            int hash = mInstance.hashCode();
            hash = hash * 1259428081 + mField.hashCode();
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof Instance other
                && mInstance.equals(other.mInstance) && mField.equals(other.mField);
        }
    }

    /**
     * Defines a binding which refers to a tuple field.
     */
    public static final class Tuple extends BaseBinding {
        public static Tuple from(BaseBinding tuple, int index) {
            return InternSet.apply(new Tuple(tuple, index));
        }

        private final BaseBinding mTuple;
        private final int mIndex;

        private Tuple(BaseBinding tuple, int index) {
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
        public int hashCode() {
            int hash = mTuple.hashCode();
            hash = hash * -633060527 + mIndex;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof Tuple other
                && mTuple.equals(other.mTuple) && mIndex == other.mIndex;
        }
    }

    public static abstract sealed class Local extends BaseBinding {
        final BaseType mType;

        /**
         * @param type required
         */
        Local(BaseType type) {
            mType = Objects.requireNonNull(type);
        }

        @Override
        public final BaseType type() {
            return mType;
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
         * @param name required
         */
        public Named(BaseType type, String name) {
            super(type);
            mName = Objects.requireNonNull(name);
        }

        protected Named(String name, BaseType type) {
            super(type);
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
         * @param name optional
         * @throws IllegalArgumentException if index is negative
         */
        private Parameter(BaseType type, String name, int index) {
            super(name, type);
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

        public Anonymous(BaseType type) {
            super(type);
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
     * Defines a binding which refers to code which can be passed to a macro call.
     */
    public static final class Code extends BaseBinding {
        public static Code from(BaseType resultType, BaseBlock block) {
            return new Code(resultType, block);
        }

        private final BaseType mResultType;
        private final BaseBlock mBlock;

        private Code(BaseType resultType, BaseBlock block) {
            mResultType = Objects.requireNonNull(resultType);
            mBlock = Objects.requireNonNull(block);
        }

        @Override
        public BaseType type() {
            return mResultType;
        }

        public BaseBlock block() {
            return mBlock;
        }
    }
}
