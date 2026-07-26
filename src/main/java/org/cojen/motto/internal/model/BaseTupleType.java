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

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

import java.util.function.Supplier;

import java.util.stream.Stream;

import org.cojen.motto.model.ClassTypeItem;
import org.cojen.motto.model.FieldItem;
import org.cojen.motto.model.Item;
import org.cojen.motto.model.ObjectType;
import org.cojen.motto.model.Path;
import org.cojen.motto.model.TupleType;
import org.cojen.motto.model.Type;

import org.cojen.motto.internal.util.InternSet;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public sealed abstract class BaseTupleType extends GeneratedType
    implements BaseObjectType, TupleType, EncodableType.TupleT
{
    public static final BaseTupleType EMPTY;

    static {
        EMPTY = InternSet.apply(new NoNames(new BaseType[0]));
    }

    public static BaseTupleType from(Type... fieldTypes) {
        if (fieldTypes.length == 0) {
            return EMPTY;
        }

        var newFieldTypes = new BaseType[fieldTypes.length];

        for (int i=0; i<fieldTypes.length; i++) {
            if (Objects.requireNonNull(fieldTypes[i]) instanceof BaseType t) {
                newFieldTypes[i] = t;
            } else {
                throw new IllegalArgumentException();
            }
        }

        return InternSet.apply(new NoNames(newFieldTypes));
    }

    public static BaseTupleType from(BaseType fieldType) {
        return InternSet.apply(new NoNames(new BaseType[] {fieldType}));
    }

    public static BaseTupleType from(BaseType... fieldTypes) {
        if (fieldTypes.length == 0) {
            return EMPTY;
        }
        fieldTypes = fieldTypes.clone();
        for (BaseType t : fieldTypes) {
            Objects.requireNonNull(t);
        }
        return InternSet.apply(new NoNames(fieldTypes));
    }

    public static BaseTupleType from(BaseType firstType, BaseType[] moreTypes) {
        Objects.requireNonNull(firstType);
        var fieldTypes = new BaseType[1 + moreTypes.length];
        fieldTypes[0] = firstType;
        for (int i=1; i<fieldTypes.length; i++) {
            fieldTypes[i] = Objects.requireNonNull(moreTypes[i - 1]);
        }
        return InternSet.apply(new NoNames(fieldTypes));
    }

    static BaseTupleType from(Collection<BaseType> types) {
        if (types == null || types.isEmpty()) {
            return EMPTY;
        }
        var fieldTypes = new BaseType[types.size()];
        int i = 0;
        for (BaseType type : types) {
            fieldTypes[i++] = Objects.requireNonNull(type);
        }
        return InternSet.apply(new NoNames(fieldTypes));
    }

    private volatile BaseTupleType mNoFieldNames, mTrimmed;

    BaseTupleType() {
    }

    @Override
    public final StringBuilder appendDisplayNameTo(StringBuilder b) {
        b.append('(');

        int numFields = numFields();

        for (int i=0; i<numFields; i++) {
            if (i > 0) {
                b.append(", ");
            }
            FieldItem field = field(i);
            field.type().appendDisplayNameTo(b);
            String name = field.name();
            if (name != null) {
                b.append(' ').append(name);
            }
        }

        return b.append(')');
    }

    @Override
    public final boolean isInterface() {
        return false;
    }

    @Override
    public boolean isArray() {
        return false;
    }

    @Override
    public abstract int numFields();

    @Override
    public abstract BaseType fieldType(int index);

    @Override
    public abstract String fieldName(int index);

    @Override
    public final boolean isAccessibleVia(Item via) {
        int numFields = numFields();

        for (int i=0; i<numFields; i++) {
            if (!fieldType(i).isAccessibleVia(via)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public final BaseTupleType noFieldNames() {
        BaseTupleType noFieldNames = mNoFieldNames;

        if (noFieldNames == null) {
            BaseType[] newFieldTypes = null;
            int numFields = numFields();

            for (int i=0; i<numFields; i++) {
                BaseType t = fieldType(i);
                BaseType nt = t.noFieldNames();
                if (!t.equals(nt)) {
                    if (newFieldTypes == null) {
                        newFieldTypes = new BaseType[numFields];
                        for (int j=0; j<numFields; j++) {
                            newFieldTypes[j] = fieldType(j);
                        }
                    }
                    newFieldTypes[i] = nt;
                }
            }

            if (newFieldTypes == null) {
                noFieldNames = withoutNames();
            } else {
                noFieldNames = InternSet.apply(new NoNames(newFieldTypes));
            }

            mNoFieldNames = noFieldNames;
        }

        return noFieldNames;
    }

    /**
     * Returns this tuple type with the first field removed.
     */
    final BaseTupleType trimFirst() {
        BaseTupleType trimmed = mTrimmed;

        if (trimmed == null) {
            mTrimmed = trimmed = numFields() == 1 ? EMPTY : InternSet.apply(doTrimFirst());
        }

        return trimmed;
    }

    protected abstract BaseTupleType doTrimFirst();

    @Override
    public final boolean isAssignableFrom(Type other) {
        if (super.isAssignableFrom(other)) {
            return true;
        }

        int numFields = numFields();

        if (!(other instanceof BaseTupleType ott)) {
            return numFields == 1 && fieldType(0).isAssignableFrom(other);
        }

        if (numFields != other.numFields()) {
            return false;
        }

        for (int i=0; i<numFields; i++) {
            if (!fieldType(i).isAssignableFrom(other.fieldType(i))) {
                return false;
            }
        }

        return true;
    }

    @Override
    public int canConvertTo(Type to) {
        int code = super.canConvertTo(to);

        if (code != Integer.MAX_VALUE) {
            return code;
        }

        int numFields = numFields();

        if (!(to instanceof BaseTupleType ott)) {
            if (numFields == 1 && fieldType(0) instanceof BaseType t0) {
                return t0.canConvertTo(to);
            }

            if (to instanceof ClassTypeItem toObj &&
                toObj.packagePath().equals(BasePath.JAVA_LANG))
            {
                Path namePath = toObj.namePath();
                if (namePath.size() == 1) {
                    String name = namePath.getFirst();
                    if ("Object".equals(name)) {
                        return 0;
                    }
                }
            }

            return Integer.MAX_VALUE;
        }

        if (numFields != ott.numFields()) {
            return Integer.MAX_VALUE;
        }

        code = 0;

        for (int i=0; i<numFields; i++) {
            if (!(fieldType(0) instanceof BaseType ti)) {
                return Integer.MAX_VALUE;
            }
            int fieldCode = ti.canConvertTo(ott.fieldType(i));
            if (fieldCode == Integer.MAX_VALUE) {
                return fieldCode;
            }
            code = Math.max(code, fieldCode);
        }

        return code;
    }

    @Override
    public BaseTupleType withNames(String... fieldNames) {
        return withNames(fieldNames, null);
    }

    /**
     * @param hasDupsRef if non-null and has duplicates, instead of throwing an
     * IllegalArgumentException, duplicates are dropped and hasDupsRef[0] is set to the first
     * duplicated index; is set to -1 if no duplicates
     */
    public BaseTupleType withNames(String[] fieldNames, int[] hasDupsRef) {
        if (hasDupsRef != null) {
            hasDupsRef[0] = -1;
        }

        if (fieldNames == null || fieldNames.length == 0) {
            return withoutNames();
        }

        int numFields = numFields();

        if (fieldNames.length > numFields) {
            throw new IllegalArgumentException();
        }

        checkNames: {
            for (String name : fieldNames) {
                if (name != null) {
                    break checkNames;
                }
            }
            return withoutNames();
        }

        var fields = new TupleFieldItem[numFields];

        for (int i=0; i<fields.length; i++) {
            BaseType fieldType = fieldType(i);
            fields[i] = i < fieldNames.length
                ? new TupleFieldItem.Named(this, fieldType, fieldNames[i])
                : new TupleFieldItem(this, fieldType);
        }

        Map<String, Integer> nameMap = buildNameMap(fields, hasDupsRef);

        return InternSet.apply(new WithNames(fields, nameMap));
    }

    /**
     * Returns this tuple type without any field names, non-recursively.
     */
    protected abstract BaseTupleType withoutNames();

    /**
     * Returns a version of this TupleType in which the first type is the one given.
     *
     * @throws IllegalStateException if this type has no fields
     */
    public final BaseTupleType withFirstType(BaseType type) {
        if (numFields() == 0) {
            throw new IllegalStateException();
        }
        return fieldType(0).equals(type) ? this : InternSet.apply(doWithFirstType(type));
    }

    protected abstract BaseTupleType doWithFirstType(BaseType type);

    public abstract BaseTupleType withTypes(BaseType[] types);

    private static Map<String, Integer> buildNameMap(TupleFieldItem[] fields, int[] hasDupsRef) {
        int numFields = fields.length;

        if (numFields == 1) {
            return Map.of(fields[0].name(), 0);
        }

        var map = HashMap.<String, Integer>newHashMap(numFields);

        for (int i=0; i<numFields; i++) {
            String fieldName = fields[i].name();

            if (fieldName != null && map.putIfAbsent(fieldName, i) != null) {
                if (hasDupsRef == null) {
                    throw new IllegalArgumentException("tuple has duplicate names");
                }
                if (hasDupsRef[0] < 0) {
                    hasDupsRef[0] = i;
                }
            }
        }

        return map;
    }

    private static final class NoNames extends BaseTupleType {
        protected final BaseType[] mFieldTypes;

        private NoNames(BaseType[] fieldTypes) {
            mFieldTypes = fieldTypes;
        }

        @Override
        public int numFields() {
            return mFieldTypes.length;
        }

        @Override
        public Stream<? extends FieldItem> fields() {
            return Arrays.stream(mFieldTypes).map(type -> new TupleFieldItem(NoNames.this, type));
        }

        @Override
        public FieldItem field(String name) {
            throw new NoSuchElementException();
        }

        @Override
        public TupleFieldItem field(int index) {
            return new TupleFieldItem(this, fieldType(index));
        }

        @Override
        public BaseType fieldType(int index) {
            return mFieldTypes[index];
        }

        @Override
        public String fieldName(int index) {
            fieldType(index); // check index bounds
            return null;
        }

        @Override
        public int fieldIndex(String name) {
            throw new NoSuchElementException();
        }

        @Override
        protected NoNames doTrimFirst() {
            return new NoNames(Arrays.copyOfRange(mFieldTypes, 1, mFieldTypes.length));
        }

        @Override
        protected NoNames withoutNames() {
            return this;
        }

        @Override
        protected NoNames doWithFirstType(BaseType type) {
            var fieldTypes = mFieldTypes.clone();
            fieldTypes[0] = type;
            return new NoNames(fieldTypes);
        }

        @Override
        public NoNames withTypes(BaseType[] types) {
            if (numFields() != types.length) {
                throw new IllegalStateException();
            }
            return InternSet.apply(new NoNames(types.clone()));
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(mFieldTypes) ^ 1106244117;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof NoNames other
                && Arrays.equals(mFieldTypes, other.mFieldTypes);
        }
    }

    private static final class WithNames extends BaseTupleType {
        private final TupleFieldItem[] mFields;
        private final Map<String, Integer> mNameMap;

        private WithNames(TupleFieldItem[] fields) {
            this(fields, buildNameMap(fields, null));
        }

        private WithNames(TupleFieldItem[] fields, Map<String, Integer> nameMap) {
            mFields = fields;
            mNameMap = nameMap;
        }

        @Override
        public int numFields() {
            return mFields.length;
        }

        @Override
        public Stream<? extends FieldItem> fields() {
            return Arrays.stream(mFields);
        }

        @Override
        public TupleFieldItem field(String name) {
            return field(fieldIndex(name));
        }

        @Override
        public TupleFieldItem field(int index) {
            return mFields[index];
        }

        @Override
        public BaseType fieldType(int index) {
            return field(index).type();
        }

        @Override
        public String fieldName(int index) {
            return field(index).name();
        }

        @Override
        public int fieldIndex(String name) {
            Integer index = mNameMap.get(name);
            if (index == null) {
                throw new NoSuchElementException();
            }
            return index;
        }

        @Override
        protected WithNames doTrimFirst() {
            return new WithNames(Arrays.copyOfRange(mFields, 1, mFields.length));
        }

        @Override
        protected NoNames withoutNames() {
            var fieldTypes = new BaseType[mFields.length];
            for (int i=0; i<fieldTypes.length; i++) {
                fieldTypes[i] = mFields[i].type();
            }
            return InternSet.apply(new BaseTupleType.NoNames(fieldTypes));
        }

        @Override
        protected WithNames doWithFirstType(BaseType type) {
            var fields = mFields.clone();
            fields[0] = fields[0].withType(type);
            return new WithNames(fields);
        }

        @Override
        public WithNames withTypes(BaseType[] types) {
            if (numFields() != types.length) {
                throw new IllegalStateException();
            }

            var fields = mFields.clone();

            for (int i=0; i<fields.length; i++) {
                fields[i] = fields[i].withType(types[i]);
            }

            return InternSet.apply(new WithNames(fields, mNameMap));
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(mFields);
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj || obj instanceof WithNames other
                && Arrays.equals(mFields, other.mFields);
        }
    }
}
