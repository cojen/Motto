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

import java.util.Objects;

import org.cojen.motto.model.FunctionType;
import org.cojen.motto.model.Item;
import org.cojen.motto.model.Type;

import org.cojen.motto.internal.util.InternSet;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class BaseFunctionType extends GeneratedType
    implements FunctionType, EncodableType.FunctionT
{
    public static BaseFunctionType from(BaseType outputType, BaseTupleType inputType) {
        return InternSet.apply(new BaseFunctionType(outputType, inputType));
    }

    private final BaseType mOutputType;
    private final BaseTupleType mInputType;

    private volatile BaseFunctionType mNoFieldNames;

    private BaseFunctionType(BaseType outputType, BaseTupleType inputType) {
        mOutputType = Objects.requireNonNull(outputType);
        mInputType = Objects.requireNonNull(inputType);
    }

    @Override
    public StringBuilder appendDisplayNameTo(StringBuilder b) {
        mInputType.appendDisplayNameTo(b).append(" -> ");
        mOutputType.appendDisplayNameTo(b);
        return b;
    }

    @Override
    public boolean isPrimitive() {
        return false;
    }

    @Override
    public boolean isObject() {
        return false;
    }

    @Override
    public boolean isInterface() {
        return false;
    }

    @Override
    public boolean isArray() {
        return false;
    }

    @Override
    public BaseFunctionType noFieldNames() {
        BaseFunctionType noFieldNames = mNoFieldNames;

        if (noFieldNames == null) {
            noFieldNames = from(mOutputType.noFieldNames(), mInputType.noFieldNames());
            mNoFieldNames = noFieldNames;
        }

        return noFieldNames;
    }

    /* FIXME: Function type compatibility is more nuanced.
    @Override
    public boolean isAssignableFrom(Type other) {
        return super.isAssignableFrom(other)
            || (other instanceof BaseFunctionType oft
                && mInputType.isAssignableFrom(oft.mInputType)
                && mOutputType.isAssignableFrom(oft.mOutputType));
    }
    */

    @Override
    public boolean isAccessibleVia(Item via) {
        return mInputType.isAccessibleVia(via) && mOutputType.isAccessibleVia(via);
    }

    @Override
    public int bindCompare(Type aParam, Type bParam) {
        if (aParam instanceof BaseFunctionType aft && bParam instanceof BaseFunctionType bft) {
            return inputType().bindCompare(aft.inputType(), bft.inputType());
        }

        return super.bindCompare(aParam, bParam);
    }

    @Override
    public BaseType outputType() {
        return mOutputType;
    }

    @Override
    public BaseTupleType inputType() {
        return mInputType;
    }
}
