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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.cojen.motto.model.Binding;
import org.cojen.motto.model.Block;
import org.cojen.motto.model.CallSignature;

import org.cojen.motto.internal.util.InternSet;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class BaseCallSignature implements CallSignature {
    // Masks for flags.
    private static final int REPETITION = 0b0011, EVALUATED = 0b0100;

    /**
     * @param outputType required
     * @param name required
     * @param inputType required
     * @param evaluated true indicates a normal input type, false indicates a code block
     */
    public static BaseCallSignature from(BaseType outputType, String name, BaseTupleType inputType,
                                         boolean evaluated)
    {
        return from(outputType, name, inputType, evaluated, (BaseClause[]) null);
    }

    /**
     * @param outputType required
     * @param name required
     * @param inputType required
     * @param evaluated true indicates a normal input type, false indicates a code block
     */
    public static BaseCallSignature from(BaseType outputType, String name, BaseTupleType inputType,
                                         boolean evaluated,
                                         BaseClause... clauses)
    {
        int flags = 0;

        if (evaluated) {
            flags |= EVALUATED;
        }

        if (clauses != null) {
            clauses = clauses.length == 0 ? null : clauses.clone();
        }

        return InternSet.apply
            (new BaseCallSignature(Objects.requireNonNull(outputType), Objects.requireNonNull(name),
                                  Objects.requireNonNull(inputType), flags, clauses));
    }

    private final BaseType mOutputType;
    private final String mName;
    private final BaseTupleType mInputType;
    private final int mFlags;
    private final BaseClause[] mClauses;

    private volatile BaseCallSignature mNoFieldNames, mFlattened, mTrimmed;

    private BaseCallSignature(BaseType outputType, String name, BaseTupleType inputType,
                              int flags, BaseClause... clauses)
    {
        mOutputType = outputType;
        mName = name;
        mInputType = inputType;
        mFlags = flags;
        if (clauses != null && clauses.length == 0) {
            clauses = null;
        }
        mClauses = clauses;
    }

    @Override
    public BaseType outputType() {
        return mOutputType;
    }

    @Override
    public String name() {
        return mName;
    }

    @Override
    public BaseTupleType inputType() {
        return mInputType;
    }

    @Override
    public boolean isInputEvaluated() {
        return (mFlags & EVALUATED) != 0;
    }

    @Override
    public int numClauses() {
        BaseClause[] clauses = mClauses;
        return clauses == null ? 0 : clauses.length;
    }

    @Override
    public BaseClause clause(int index) {
        BaseClause[] clauses = mClauses;
        if (clauses == null) {
            throw new IndexOutOfBoundsException();
        }
        return clauses[index];
    }

    @Override
    public BaseCallSignature noFieldNames() {
        BaseCallSignature noFieldNames = mNoFieldNames;

        if (noFieldNames == null) {
            BaseClause[] clauses = mClauses;

            if (clauses != null) {
                for (int i=0; i<clauses.length; i++) {
                    BaseClause clause = clauses[i];
                    BaseClause newClause = clause.noFieldNames();
                    if (newClause != clause) {
                        if (clauses == mClauses) {
                            clauses = clauses.clone();
                        }
                        clauses[i] = newClause;
                    }
                }
            }

            noFieldNames = new BaseCallSignature(mOutputType.noFieldNames(), mName,
                                                mInputType.noFieldNames(), mFlags,
                                                clauses);

            mNoFieldNames = noFieldNames = InternSet.apply(noFieldNames);
        }

        return noFieldNames;
    }

    @Override
    public BaseCallSignature forMacro() {
        BaseType bindingType = BaseType.from(Binding.class);
        BaseType blockType = BaseType.from(Block.class);

        BaseTupleType inputType = mInputType;

        int num = inputType.numFields();
        if (num != 0) {
            var types = new BaseType[num];
            Arrays.fill(types, isInputEvaluated() ? bindingType : blockType);
            inputType = inputType.withTypes(types);
        }

        BaseClause[] clauses = null;

        if (mClauses != null) {
            clauses = new BaseClause[mClauses.length];
            for (int i=0; i<clauses.length; i++) {
                clauses[i] = mClauses[i].forMacro(bindingType, blockType);
            }
        }

        var signature = new BaseCallSignature
            (blockType, mName, inputType, mFlags | EVALUATED, clauses);

        return InternSet.apply(signature);
    }

    @Override
    public BaseCallSignature flatten() {
        BaseCallSignature flattened = mFlattened;

        if (flattened == null) {
            flattened = doFlatten();
            if (flattened != this) {
                flattened = InternSet.apply(flattened);
            }
            mFlattened = flattened;
        }

        return flattened;
    }

    @SuppressWarnings("unchecked")
    private BaseCallSignature doFlatten() {
        boolean evaluated = isInputEvaluated();

        if (evaluated && mClauses == null) {
            // Already flattened.
            return this;
        }

        // Maps parameter or clause names to types. Unnamed parameters are keyed by plain
        // objects. For clauses defined more than once, the map values are lists. These lists
        // will later become tuple types in the flattened signature.
        var map = new LinkedHashMap<Object, Object>();

        // First fill with normal parameters.
        {
            BaseTupleType inputType = mInputType;
            int num = inputType.numFields();

            for (int i=0; i<num; i++) {
                BaseType type = inputType.fieldType(i);

                if (!isInputEvaluated()) {
                    type = BaseFunctionType.from(type, BaseTupleType.EMPTY);
                }

                String name = inputType.fieldName(i);
                putElement(map, name != null ? name : new Object(), type);
            }
        }

        // Now fill with clauses, which are converted to parameters.
        if (mClauses != null) {
            for (BaseClause clause : mClauses) {
                clause.doFlatten(map);
            }
        }

        var newInputTypes = new BaseType[map.size()];
        var newInputNames = new String[newInputTypes.length];

        int ix = 0;

        for (Map.Entry<Object, Object> e : map.entrySet()) {
            BaseType type;
            {
                Object value = e.getValue();
                if (value instanceof BaseType t) {
                    type = t;
                } else {
                    type = BaseTupleType.from((List<BaseType>) value);
                }
            }

            newInputTypes[ix] = type;

            Object key = e.getKey();
            if (key instanceof String name) {
                newInputNames[ix] = name;
            }

            ix++;
        }

        var newInputType = BaseTupleType.from(newInputTypes).withNames(newInputNames);

        return from(mOutputType, mName, newInputType, true);
    }

    /**
     * Put an element into the map. If the key is put more than once, the associated map entry
     * will refer to a List.
     */
    @SuppressWarnings("unchecked")
    private static void putElement(LinkedHashMap<Object, Object> map, Object key, Object obj) {
        Object existing = map.get(key);

        if (existing == null) {
            map.put(key, obj);
        } else if (existing instanceof ArrayList list) {
            list.add(obj);
        } else {
            var list = new ArrayList(4);
            list.add(existing);
            list.add(obj);
        }
    }

    /**
     * Returns a signature with the first input element removed.
     */
    BaseCallSignature trimFirst() {
        BaseCallSignature trimmed = mTrimmed;

        if (trimmed == null) {
            trimmed = new BaseCallSignature
                (mOutputType, mName, mInputType.trimFirst(), mFlags, mClauses);
            mTrimmed = trimmed = InternSet.apply(trimmed);
        }

        return trimmed;
    }

    /**
     * Returns true if this signature, representing a call, can bind to the signature of a
     * defined method. The output and inputs might need to be converted, however.
     *
     * <p>If this call has any clauses, the repetition value should be -1, although the value
     * is ignored. Actual repetition should be specified using duplicate clauses.
     */
    boolean canBindTo(BaseCallSignature other) {
        if (!mName.equals(other.mName) || isInputEvaluated() != other.isInputEvaluated() ||
            other.mOutputType.canConvertTo(mOutputType) == Integer.MAX_VALUE ||
            mInputType.canConvertTo(other.mInputType) == Integer.MAX_VALUE)
        {
            return false;
        }

        if (mClauses == null) {
            if (other.mClauses != null) {
                for (BaseClause clause : other.mClauses) {
                    if (clause.repetition() != 0) {
                        return false;
                    }
                }
            }
            return true;
        }

        if (other.mClauses == null) {
            return false;
        }

        int thisIndex = 0;
        int otherIndex = 0;

        while (true) {
            if (thisIndex >= mClauses.length) {
                for (; otherIndex < other.mClauses.length; otherIndex++) {
                    if (other.mClauses[otherIndex].repetition() != 0) {
                        return false;
                    }
                }
                return true;
            } else if (otherIndex >= other.mClauses.length) {
                return false;
            }

            BaseClause thisClause = mClauses[thisIndex];
            BaseClause otherClause = other.mClauses[otherIndex];
            int repetition = otherClause.repetition();

            if (thisClause.canBindTo(otherClause)) {
                thisIndex++;
                if (repetition == -1) { // once
                    otherIndex++;
                }
            } else {
                if (repetition != 0) {
                    // Must bind at least once, and so it cannot be skipped.
                    return false;
                }
                otherIndex++;
            }
        }
    }

    /**
     * Compares this argument set against the given parameter sets, to select which one is a
     * better candidate to bind to for a method call. The given signatures are expected to be
     * valid matching candidates. For a selected signature to be strictly "better" than
     * another, all parameter types must be equal or better based on conversion cost.
     *
     * @return -1 if aSig is better, 1 if bSig is better, or 0 if neither is strictly better
     */
    int bindCompare(BaseCallSignature aSig, BaseCallSignature bSig) {
        int cmp = this.inputType().bindCompare(aSig.inputType(), bSig.inputType());

        if (cmp != 0) {
            return cmp;
        }

        // FIXME: Clause comparison is wrong. It needs to follow the same rules as canBindTo.

        int numClauses = this.numClauses();

        if (numClauses != bSig.numClauses()) {
            return 0;
        }

        for (int i=0; i<numClauses; i++) {
            BaseCallSignature.BaseClause clause = this.clause(i);
            BaseCallSignature.BaseClause aClause = aSig.clause(i);
            BaseCallSignature.BaseClause bClause = bSig.clause(i);
            cmp = clause.inputType().bindCompare(aClause.inputType(), bClause.inputType());
            if (cmp != 0) {
                return cmp;
            }
        }

        return 0;
    }

    @Override
    public int hashCode() {
        int hash = mFlags ^ -546471431;
        hash = hash * mOutputType.hashCode();
        hash = hash * 31 + Objects.hashCode(mName);
        hash = hash * 31 + mInputType.hashCode();
        hash = hash * 31 + Arrays.hashCode(mClauses);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof BaseCallSignature other
            && mFlags == other.mFlags
            && mName.equals(other.mName)
            && mOutputType.equals(other.mOutputType)
            && mInputType.equals(other.mInputType)
            && Arrays.equals(mClauses, other.mClauses);
    }

    public static final class BaseClause implements Clause {
        /**
         * Returns a clause which accepts any code statement.
         *
         * @param repetition -1: once, 0: zero or more, 1: one or more
         * @param name required (can be empty, except when repetition is >= 0)
         */
        public static BaseClause from(int repetition, String name) {
            if (name.isEmpty() && repetition >= 0) {
                throw new IllegalArgumentException();
            }
            return InternSet.apply(new BaseClause(repetition, name, null, false));
        }

        /**
         * Returns a clause which accepts a tuple or code block.
         *
         * @param repetition -1: once, 0: zero or more, 1: one or more
         * @param name required (can be empty)
         * @param inputType required
         * @param evaluated true indicates a normal input type, false indicates a code block
         */
        public static BaseClause from(int repetition, String name,
                                      BaseTupleType inputType, boolean evaluated)
        {
            Objects.requireNonNull(name);
            Objects.requireNonNull(inputType);
            return InternSet.apply(new BaseClause(repetition, name, inputType, evaluated));
        }

        private final String mName;
        private final BaseTupleType mInputType;
        private final int mFlags;

        private volatile BaseClause mNoFieldNames;

        private BaseClause(int repetition, String name, BaseTupleType inputType, boolean evaluated)
        {
            this(name, inputType, (repetition & 0b11) | (evaluated ? EVALUATED : 0));
        }

        private BaseClause(String name, BaseTupleType inputType, int flags) {
            mName = name;
            mInputType = inputType;
            mFlags = flags;
        }

        /**
         * -1: once, 0: zero or more, 1: one or more, -2: illegal
         */
        public int repetition() {
            return ((mFlags & REPETITION) << 30) >> 30;
        }

        @Override
        public boolean isRequired() {
            return repetition() != 0;
        }

        @Override
        public boolean hasRepetition() {
            return repetition() >= 0;
        }

        @Override
        public String name() {
            return mName;
        }

        @Override
        public BaseTupleType inputType() {
            return mInputType;
        }

        @Override
        public boolean isInputEvaluated() {
            return (mFlags & EVALUATED) != 0;
        }

        @Override
        public BaseClause noFieldNames() {
            BaseClause noFieldNames = mNoFieldNames;

            if (noFieldNames == null) {
                BaseTupleType in = mInputType;
                BaseTupleType newIn;
                if (in == null || (newIn = in.noFieldNames()) == in) {
                    noFieldNames = this;
                } else {
                    noFieldNames = InternSet.apply(new BaseClause(mName, newIn, mFlags));
                }
                mNoFieldNames = noFieldNames;
            }

            return noFieldNames;
        }

        private BaseClause forMacro(BaseType bindingType, BaseType blockType) {
            BaseTupleType inputType = mInputType;

            int num = inputType.numFields();
            if (num != 0) {
                var types = new BaseType[num];
                Arrays.fill(types, isInputEvaluated() ? bindingType : blockType);
                inputType = inputType.withTypes(types);
            }

            return InternSet.apply(new BaseClause(mName, inputType, mFlags | EVALUATED));
        }

        private void doFlatten(LinkedHashMap<Object, Object> map) {
            BaseTupleType inputTupleType = mInputType;

            if (!isInputEvaluated()) {
                // Convert inputs to functions.
                int num = inputTupleType.numFields();
                var types = new BaseType[num];
                for (int i=0; i<num; i++) {
                    BaseType type = inputTupleType.fieldType(i);
                    types[i] = BaseFunctionType.from(type, BaseTupleType.EMPTY);
                }
                inputTupleType = inputTupleType.withTypes(types);
            }

            BaseType inputType = inputTupleType;

            if (hasRepetition()) {
                inputType = BaseArrayType.from(inputType);
            }

            putElement(map, mName, inputType);
        }

        private boolean canBindTo(BaseClause other) {
            return mName.equals(other.mName) && isInputEvaluated() == other.isInputEvaluated()
                && mInputType.canConvertTo(other.mInputType) != Integer.MAX_VALUE;
        }

        @Override
        public int hashCode() {
            int hash = mFlags ^ 1734594280;
            hash = hash * 31 + Objects.hashCode(mName);
            hash = hash * 31 + Objects.hashCode(mInputType);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof BaseClause other
                && mFlags == other.mFlags
                && Objects.equals(mName, other.mName)
                && Objects.equals(mInputType, other.mInputType);
        }
    }
}
