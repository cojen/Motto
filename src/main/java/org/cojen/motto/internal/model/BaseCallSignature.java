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
     * @param evaluated when false, the inputType elements have been converted to function
     * types, except for "this"
     */
    public static BaseCallSignature from(BaseType outputType, String name, BaseTupleType inputType,
                                         boolean evaluated)
    {
        return from(outputType, name, inputType, evaluated, (BaseSegment[]) null);
    }

    /**
     * @param outputType required
     * @param name required
     * @param inputType required
     * @param evaluated when false, the inputType elements have been converted to function
     * types, except for "this"
     */
    public static BaseCallSignature from(BaseType outputType, String name, BaseTupleType inputType,
                                         boolean evaluated,
                                         BaseSegment... segments)
    {
        int flags = 0;

        if (evaluated) {
            flags |= EVALUATED;
        }

        if (segments != null) {
            segments = segments.length == 0 ? null : segments.clone();
        }

        return InternSet.apply
            (new BaseCallSignature(Objects.requireNonNull(outputType),
                                   Objects.requireNonNull(name),
                                   Objects.requireNonNull(inputType), flags, segments));
    }

    private final BaseType mOutputType;
    private final String mName;
    private final BaseTupleType mInputType;
    private final int mFlags;
    private final BaseSegment[] mSegments;

    private volatile BaseCallSignature mNoFieldNames, mFlattened, mTrimmed;

    private BaseCallSignature(BaseType outputType, String name, BaseTupleType inputType,
                              int flags, BaseSegment... segments)
    {
        mOutputType = outputType;
        mName = name;
        mInputType = inputType;
        mFlags = flags;
        if (segments != null && segments.length == 0) {
            segments = null;
        }
        mSegments = segments;
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
    public int numSegments() {
        BaseSegment[] segments = mSegments;
        return segments == null ? 0 : segments.length;
    }

    @Override
    public BaseSegment segment(int index) {
        BaseSegment[] segments = mSegments;
        if (segments == null) {
            throw new IndexOutOfBoundsException();
        }
        return segments[index];
    }

    @Override
    public BaseCallSignature noFieldNames() {
        BaseCallSignature noFieldNames = mNoFieldNames;

        if (noFieldNames == null) {
            BaseSegment[] segments = mSegments;

            if (segments != null) {
                for (int i=0; i<segments.length; i++) {
                    BaseSegment segment = segments[i];
                    BaseSegment newSegment = segment.noFieldNames();
                    if (newSegment != segment) {
                        if (segments == mSegments) {
                            segments = segments.clone();
                        }
                        segments[i] = newSegment;
                    }
                }
            }

            noFieldNames = new BaseCallSignature(mOutputType.noFieldNames(), mName,
                                                 mInputType.noFieldNames(), mFlags,
                                                 segments);

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

        BaseSegment[] segments = null;

        if (mSegments != null) {
            segments = new BaseSegment[mSegments.length];
            for (int i=0; i<segments.length; i++) {
                segments[i] = mSegments[i].forMacro(bindingType, blockType);
            }
        }

        var signature = new BaseCallSignature
            (blockType, mName, inputType, mFlags | EVALUATED, segments);

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

        if (evaluated && mSegments == null) {
            // Already flattened.
            return this;
        }

        // Maps parameter or segment names to types. Unnamed parameters are keyed by plain
        // objects. For segments defined more than once, the map values are lists. These lists
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

        // Now fill with segments, which are converted to parameters.
        if (mSegments != null) {
            for (BaseSegment segment : mSegments) {
                segment.doFlatten(map);
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
                (mOutputType, mName, mInputType.trimFirst(), mFlags, mSegments);
            mTrimmed = trimmed = InternSet.apply(trimmed);
        }

        return trimmed;
    }

    /**
     * Returns a version of this CallSignature in which the first input type is the one given.
     *
     * @throws IllegalStateException if the input type has no elements
     */
    BaseCallSignature withFirstInputType(BaseType type) {
        BaseTupleType newInputType = mInputType.withFirstType(type);
        if (newInputType.equals(mInputType)) {
            return this;
        }
        return InternSet.apply
            (new BaseCallSignature(mOutputType, mName, newInputType, mFlags, mSegments));
    }

    /**
     * Returns true if this signature, representing a call, can bind to the signature of a
     * defined method. The output and inputs might need to be converted, however.
     *
     * <p>If this call has any segments, the repetition value should be -1, although the value
     * is ignored. Actual repetition should be specified using duplicate segments.
     */
    boolean canBindTo(BaseCallSignature other) {
        if (!mName.equals(other.mName) || isInputEvaluated() != other.isInputEvaluated() ||
            other.mOutputType.canConvertTo(mOutputType) == Integer.MAX_VALUE ||
            mInputType.canConvertTo(other.mInputType) == Integer.MAX_VALUE)
        {
            return false;
        }

        if (mSegments == null) {
            if (other.mSegments != null) {
                for (BaseSegment segment : other.mSegments) {
                    if (segment.repetition() != 0) {
                        return false;
                    }
                }
            }
            return true;
        }

        if (other.mSegments == null) {
            return false;
        }

        int thisIndex = 0;
        int otherIndex = 0;

        while (true) {
            if (thisIndex >= mSegments.length) {
                for (; otherIndex < other.mSegments.length; otherIndex++) {
                    if (other.mSegments[otherIndex].repetition() != 0) {
                        return false;
                    }
                }
                return true;
            } else if (otherIndex >= other.mSegments.length) {
                return false;
            }

            BaseSegment thisSegment = mSegments[thisIndex];
            BaseSegment otherSegment = other.mSegments[otherIndex];
            int repetition = otherSegment.repetition();

            if (thisSegment.canBindTo(otherSegment)) {
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

        // FIXME: Segment comparison is wrong. It needs to follow the same rules as canBindTo.

        int numSegments = this.numSegments();

        if (numSegments != bSig.numSegments()) {
            return 0;
        }

        for (int i=0; i<numSegments; i++) {
            BaseCallSignature.BaseSegment segment = this.segment(i);
            BaseCallSignature.BaseSegment aSegment = aSig.segment(i);
            BaseCallSignature.BaseSegment bSegment = bSig.segment(i);
            cmp = segment.inputType().bindCompare(aSegment.inputType(), bSegment.inputType());
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
        hash = hash * 31 + Arrays.hashCode(mSegments);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof BaseCallSignature other
            && mFlags == other.mFlags
            && mName.equals(other.mName)
            && mOutputType.equals(other.mOutputType)
            && mInputType.equals(other.mInputType)
            && Arrays.equals(mSegments, other.mSegments);
    }

    public static final class BaseSegment implements Segment {

        /**
         * @param repetition -1: once, 0: zero or more, 1: one or more
         * @param name required (can be empty)
         * @param inputType required
         * @param evaluated when false, the inputType elements have been converted to function
         * types
         */
        public static BaseSegment from(int repetition, String name,
                                       BaseTupleType inputType, boolean evaluated)
        {
            Objects.requireNonNull(name);
            Objects.requireNonNull(inputType);
            return InternSet.apply(new BaseSegment(repetition, name, inputType, evaluated));
        }

        private final String mName;
        private final BaseTupleType mInputType;
        private final int mFlags;

        private volatile BaseSegment mNoFieldNames;

        private BaseSegment(int repetition, String name,
                            BaseTupleType inputType, boolean evaluated)
        {
            this(name, inputType, (repetition & 0b11) | (evaluated ? EVALUATED : 0));
        }

        private BaseSegment(String name, BaseTupleType inputType, int flags) {
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
        public BaseSegment noFieldNames() {
            BaseSegment noFieldNames = mNoFieldNames;

            if (noFieldNames == null) {
                BaseTupleType in = mInputType;
                BaseTupleType newIn;
                if (in == null || (newIn = in.noFieldNames()) == in) {
                    noFieldNames = this;
                } else {
                    noFieldNames = InternSet.apply(new BaseSegment(mName, newIn, mFlags));
                }
                mNoFieldNames = noFieldNames;
            }

            return noFieldNames;
        }

        private BaseSegment forMacro(BaseType bindingType, BaseType blockType) {
            BaseTupleType inputType = mInputType;

            int num = inputType.numFields();
            if (num != 0) {
                var types = new BaseType[num];
                Arrays.fill(types, isInputEvaluated() ? bindingType : blockType);
                inputType = inputType.withTypes(types);
            }

            return InternSet.apply(new BaseSegment(mName, inputType, mFlags | EVALUATED));
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

        private boolean canBindTo(BaseSegment other) {
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
            return this == obj || obj instanceof BaseSegment other
                && mFlags == other.mFlags
                && Objects.equals(mName, other.mName)
                && Objects.equals(mInputType, other.mInputType);
        }
    }
}
