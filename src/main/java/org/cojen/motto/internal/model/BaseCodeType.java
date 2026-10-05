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
import java.util.Set;

import org.cojen.motto.internal.util.InternSet;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class BaseCodeType extends GeneratedType
    implements BaseObjectType, EncodableType.CodeT
{
    public static BaseCodeType from(BaseType resultType) {
        return InternSet.apply(new BaseCodeType(resultType));
    }

    private final BaseType mResultType;

    private volatile BaseCodeType mNoFieldNames;

    private BaseCodeType(BaseType resultType) {
        mResultType = Objects.requireNonNull(resultType);
    }

    @Override
    public StringBuilder appendDisplayNameTo(StringBuilder b) {
        b.append('{');
        mResultType.appendDisplayNameTo(b);
        return b.append('}');
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
    public BaseClassTypeItem superType() {
        return LoadedClass.forObject();
    }

    @Override
    public Set<BaseClassTypeItem> interfaces() {
        return Set.of();
    }

    @Override
    public BaseCodeType noFieldNames() {
        BaseCodeType noFieldNames = mNoFieldNames;

        if (noFieldNames == null) {
            noFieldNames = from(mResultType.noFieldNames());
            mNoFieldNames = noFieldNames;
        }

        return noFieldNames;
    }

    @Override
    public BaseType resultType() {
        return mResultType;
    }

    @Override
    public int hashCode() {
        return mResultType.hashCode() * 1730744277;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof BaseCodeType other
            && mResultType.equals(other.mResultType);
    }
}
