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
    /**
     * @param outputType required
     * @param name required
     * @param inputType required
     */
    public static BaseCallSignature from(BaseType outputType, String name, BaseTupleType inputType)
    {
        return InternSet.apply
            (new BaseCallSignature(Objects.requireNonNull(outputType),
                                   Objects.requireNonNull(name),
                                   Objects.requireNonNull(inputType)));
    }

    private final BaseType mOutputType;
    private final String mName;
    private final BaseTupleType mInputType;

    private volatile BaseCallSignature mNoFieldNames, mFlattened, mTrimmed;

    private BaseCallSignature(BaseType outputType, String name, BaseTupleType inputType) {
        mOutputType = outputType;
        mName = name;
        mInputType = inputType;
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
    public BaseCallSignature noFieldNames() {
        BaseCallSignature noFieldNames = mNoFieldNames;

        if (noFieldNames == null) {
            noFieldNames = new BaseCallSignature
                (mOutputType.noFieldNames(), mName, mInputType.noFieldNames());

            mNoFieldNames = noFieldNames = InternSet.apply(noFieldNames);
        }

        return noFieldNames;
    }

    @Override
    public BaseCallSignature forMacro() {
        /* FIXME
        BaseType bindingType = BaseType.from(Binding.class);
        BaseType blockType = BaseType.from(Block.class);

        BaseTupleType inputType = mInputType;

        int num = inputType.numFields();
        if (num != 0) {
            var types = new BaseType[num];
            Arrays.fill(types, bindingType);
            inputType = inputType.withTypes(types);
        }

        var signature = new BaseCallSignature(blockType, mName, inputType);

        return InternSet.apply(signature);
        */
        throw null;
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
            trimmed = new BaseCallSignature(mOutputType, mName, mInputType.trimFirst());
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
        return InternSet.apply(new BaseCallSignature(mOutputType, mName, newInputType));
    }

    /**
     * Returns true if this signature, representing a call, can bind to the signature of a
     * defined method. The output and inputs might need to be converted, however.
     *
     * @param partial when true, this signature's input must have fewer fields than the other
     * signature
     */
    boolean canBindTo(BaseCallSignature other, boolean partial) {
        return mName.equals(other.mName)
            && other.mOutputType.canConvertTo(mOutputType) != Integer.MAX_VALUE
            && mInputType.canConvertTo(other.mInputType, partial) != Integer.MAX_VALUE;
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
        return this.inputType().bindCompare(aSig.inputType(), bSig.inputType());
    }

    @Override
    public int hashCode() {
        int hash = mOutputType.hashCode();
        hash = hash * 31 + Objects.hashCode(mName);
        hash = hash * 31 + mInputType.hashCode();
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof BaseCallSignature other
            && mName.equals(other.mName)
            && mOutputType.equals(other.mOutputType)
            && mInputType.equals(other.mInputType);
    }
}
