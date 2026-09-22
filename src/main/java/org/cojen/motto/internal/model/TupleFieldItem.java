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

import org.cojen.motto.model.FieldItem;
import org.cojen.motto.model.Item;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public sealed class TupleFieldItem implements FieldItem {
    final BaseTupleType mTuple;
    final BaseType mType;

    TupleFieldItem(BaseTupleType tuple, BaseType type) {
        mTuple = tuple;
        mType = type;
    }

    @Override
    public final BaseTupleType enclosingType() {
        return mTuple;
    }

    @Override
    public final BaseTupleType nearestType() {
        return mTuple;
    }

    @Override
    public final BaseClassTypeItem nearestClass() {
        return null;
    }

    @Override
    public final boolean isStatic() {
        return false;
    }

    @Override
    public final boolean isFinal() {
        return true;
    }

    @Override
    public final boolean isPrivate() {
        return false;
    }

    @Override
    public final boolean isAccessibleVia(Item via) {
        // Tuple fields are public.
        return true;
    }

    @Override
    public final BaseType type() {
        return mType;
    }

    @Override
    public String name() {
        return null;
    }

    public TupleFieldItem withType(BaseType type) {
        return type.equals(mType) ? this : new TupleFieldItem(mTuple, type);
    }

    @Override
    public int hashCode() {
        return mTuple.hashCode() * 31 + mType.hashCode();
    }

    @Override
    public final boolean equals(Object obj) {
        return this == obj || obj instanceof TupleFieldItem other
            && mTuple.equals(other.mTuple) && mType.equals(other.mType)
            && Objects.equals(name(), other.name());
    }

    static final class Named extends TupleFieldItem {
        private final String mName;

        Named(BaseTupleType tuple, BaseType type, String name) {
            super(tuple, type);
            mName = name;
        }

        @Override
        public String name() {
            return mName;
        }

        @Override
        public TupleFieldItem.Named withType(BaseType type) {
            return type.equals(mType) ? this : new Named(mTuple, type, mName);
        }

        @Override
        public int hashCode() {
            return super.hashCode() * 31 + mName.hashCode();
        }
    }
}
