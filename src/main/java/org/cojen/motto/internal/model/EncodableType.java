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

import java.util.List;

import org.cojen.maker.ClassMaker;
import org.cojen.maker.Maker;

import org.cojen.motto.internal.util.Utils;

/**
 * @author Brian S. O'Neill
 * @see TypeEncoder
 */
public interface EncodableType extends Comparable<EncodableType>, org.cojen.maker.Type.Provider {
    public static final int T_UNSPECIFIED = 0, T_NULL = 1, T_VOID = 2, T_BOOLEAN = 3, T_CHAR = 4,
        T_BYTE = 5, T_SHORT = 6, T_INT = 7, T_LONG = 8, T_FLOAT = 9, T_DOUBLE = 10, T_OBJECT = 11,
        T_STRING = 12, T_ARRAY = 13, T_CLASS = 14, T_COMPOSITE = 15, T_TUPLE = 16, T_FUNCTION = 17,
        T_CODE = 18,

        // 19: reserved for future use

        T_INDEXED = 20; // not a real type code; real type codes must have a lower value

    // Generated classes are in the "motto" package.
    public static final String GENERATED_PREFIX = "motto";

    /**
     * @return T_* code
     */
    public int typeCode();

    /**
     * Returns this type without any field names, recursively.
     */
    public EncodableType noFieldNames();

    public default org.cojen.maker.Type asMakerType() {
        String desc = 'L' + GENERATED_PREFIX + '/' + TypeEncoder.encodeBase64(this) + ';';
        return org.cojen.maker.Type.from(desc);
    }

    /**
     * Should be overridden by types which refer to strings or other types.
     */
    public default void encodePrepare(TypeEncoder encoder) {
    }

    public void encode(TypeEncoder encoder);

    /**
     * Should be overridden by types which can be indexed.
     */
    public default void doEncode(TypeEncoder encoder) {
        throw new UnsupportedOperationException();
    }

    @Override
    public default int compareTo(EncodableType other) {
        int cmp = Integer.compare(typeCode(), other.typeCode());

        if (cmp == 0) {
            cmp = doCompare(other);
        }

        return cmp;
    }

    /**
     * @param other should be the same type as this
     */
    public int doCompare(EncodableType other);

    @Override // Type.Provider
    public default void init(ClassMaker cm) {
        if (cm.superclass() == null) {
            doInit(cm);
        }
    }

    @Override // Type.Provider
    public default void addFields(ClassMaker cm) {
        init(cm);
    }

    @Override // Type.Provider
    public default void addMethods(ClassMaker cm) {
        init(cm);
    }

    @Override // Type.Provider
    public default void addConstructors(ClassMaker cm) {
        init(cm);
    }

    default void doInit(ClassMaker cm) {
    }

    private static void encodeIndexed(EncodableType type, TypeEncoder encoder) {
        int index = encoder.lookup(type);
        if (index >= 0) {
            encoder.encodeUnsignedVarInt(T_INDEXED + index);
        } else {
            type.doEncode(encoder);
        }
    }

    public static interface ArrayT extends EncodableType {
        @Override
        public default int typeCode() {
            return T_ARRAY;
        }

        @Override
        public default org.cojen.maker.Type asMakerType() {
            return arrayElementType().asMakerType().asArray();
        }

        @Override
        public default void encodePrepare(TypeEncoder encoder) {
            if (encoder.prepare(this)) {
                arrayElementType().encodePrepare(encoder);
            }
        }

        @Override
        public default void encode(TypeEncoder encoder) {
            encodeIndexed(this, encoder);
        }

        @Override
        public default void doEncode(TypeEncoder encoder) {
            encoder.encodeByte(T_ARRAY);
            arrayElementType().encode(encoder);
        }

        @Override
        public default int doCompare(EncodableType other) {
            return arrayElementType().compareTo(((ArrayT) other).arrayElementType());
        }

        public EncodableType arrayElementType();
    }

    public static interface ClassT extends EncodableType {
        @Override
        public default int typeCode() {
            return T_CLASS;
        }

        @Override
        public default org.cojen.maker.Type asMakerType() {
            List<String> packagePath = packagePath();
            List<String> namePath = namePath();

            var b = new StringBuilder().append('L');

            if (!packagePath.isEmpty()) {
                for (String name : packagePath) {
                    b.append(Maker.mangle(name)).append('/');
                }
            }

            {
                int size = namePath.size();
                for (int i=0; i<size; i++) {
                    if (i > 0) {
                        b.append('$');
                    }
                    b.append(Maker.mangle(namePath.get(i)));
                }
            }

            return org.cojen.maker.Type.from(b.append(';').toString());
        }

        @Override
        public default void encodePrepare(TypeEncoder encoder) {
            if (simpleClassType() < 0 && encoder.prepare(this)) {
                preparePath(encoder, packagePath());
                preparePath(encoder, namePath());
            }
        }

        private static void preparePath(TypeEncoder encoder, List<String> path) {
            for (String name : path) {
                encoder.prepare(name);
            }
        }

        @Override
        public default void encode(TypeEncoder encoder) {
            int type = simpleClassType();
            if (type < 0) {
                encodeIndexed(this, encoder);
            } else {
                encoder.encodeByte(type);
            }
        }

        @Override
        public default void doEncode(TypeEncoder encoder) {
            encoder.encodeByte(T_CLASS);
            encodePath(encoder, packagePath());
            encodePath(encoder, namePath());
        }

        private static void encodePath(TypeEncoder encoder, List<String> path) {
            encoder.encodeUnsignedVarInt(path.size());
            for (String name : path) {
                encoder.encodeString(name);
            }
        }

        @Override
        public default int doCompare(EncodableType other) {
            var otherClass = (ClassT) other;

            int cmp = Utils.compare(packagePath(), otherClass.packagePath());

            if (cmp == 0) {
                cmp = Utils.compare(namePath(), otherClass.namePath());
            }

            return cmp;
        }

        public List<String> packagePath();

        public List<String> namePath();

        /**
         * Returns T_OBJECT, T_STRING, or else -1 if not applicable.
         */
        public int simpleClassType();
    }

    public static interface CompositeT extends EncodableType {
        @Override
        public default int typeCode() {
            return T_COMPOSITE;
        }

        @Override
        public default void encodePrepare(TypeEncoder encoder) {
            if (encoder.prepare(this)) {
                int numFields = numFields();
                for (int i=0; i<numFields; i++) {
                    fieldType(i).encodePrepare(encoder);
                }
            }
        }

        @Override
        public default void encode(TypeEncoder encoder) {
            encodeIndexed(this, encoder);
        }

        @Override
        public default void doEncode(TypeEncoder encoder) {
            encoder.encodeByte(T_COMPOSITE);
            int numFields = numFields();
            encoder.encodeUnsignedVarInt(numFields);
            for (int i=0; i<numFields; i++) {
                fieldType(i).encode(encoder);
            }
        }

        @Override
        public default int doCompare(EncodableType other) {
            var otherComposite = (CompositeT) other;

            int num = Math.min(numFields(), otherComposite.numFields());

            for (int i=0; i<num; i++) {
                int cmp = fieldType(i).compareTo(otherComposite.fieldType(i));
                if (cmp != 0) {
                    return cmp;
                }
            }

            return Integer.compare(numFields(), otherComposite.numFields());
        }

        public int numFields();

        public EncodableType fieldType(int index);

        @Override
        public default void doInit(ClassMaker cm) {
            TheTypeGenerator.makeCompositeClass(cm, this);
        }
    }

    public static interface TupleT extends EncodableType {
        @Override
        public default int typeCode() {
            return T_TUPLE;
        }

        @Override
        public default void encodePrepare(TypeEncoder encoder) {
            if (encoder.prepare(this)) {
                int numFields = numFields();
                for (int i=0; i<numFields; i++) {
                    fieldType(i).encodePrepare(encoder);
                    String name = fieldName(i);
                    if (name != null) {
                        encoder.prepare(name);
                    }
                }
            }
        }

        @Override
        public default void encode(TypeEncoder encoder) {
            encodeIndexed(this, encoder);
        }

        @Override
        public default void doEncode(TypeEncoder encoder) {
            encoder.encodeByte(T_TUPLE);
            int numFields = numFields();
            encoder.encodeUnsignedVarInt(numFields);
            for (int i=0; i<numFields; i++) {
                fieldType(i).encode(encoder);
                encoder.encodeString(fieldName(i));
            }
        }

        @Override
        public default int doCompare(EncodableType other) {
            var otherTuple = (TupleT) other;

            int num = Math.min(numFields(), otherTuple.numFields());

            for (int i=0; i<num; i++) {
                int cmp = fieldType(i).compareTo(otherTuple.fieldType(i));
                if (cmp != 0) {
                    return cmp;
                }

                String thisName = fieldName(i);
                String otherName = otherTuple.fieldName(i);

                // Nulls ordered first.
                if (thisName == null) {
                    return otherName == null ? 0 : -1;
                } else if (otherName == null) {
                    return 1;
                }

                cmp = thisName.compareTo(otherName);
                if (cmp != 0) {
                    return cmp;
                }
            }

            return Integer.compare(numFields(), otherTuple.numFields());
        }

        public int numFields();

        public EncodableType fieldType(int index);

        /**
         * Returns a possibly null name.
         */
        public String fieldName(int index);

        /**
         * Returns a non-null name, possibly mangled.
         */
        public default String mangledFieldName(int index) {
            if (index < 0 || index > numFields()) {
                throw new IllegalArgumentException();
            }

            String name = fieldName(index);

            if (name == null) {
                return "\\=" + index;
            }

            if (isObjectMethod(name)) {
                return "\\=" + name;
            }

            return Maker.mangle(name);
        }

        public static boolean isObjectMethod(String name) {
            return switch (name) {
                case "clone", "equals", "finalize", "getClass", "hashCode",
                    "notify", "notifyAll", "toString", "wait" -> true;
                default -> false;
            };
        }

        @Override
        public default void doInit(ClassMaker cm) {
            TheTypeGenerator.makeTupleClass(cm, this);
        }
    }

    public static interface FunctionT extends EncodableType {
        @Override
        public default int typeCode() {
            return T_FUNCTION;
        }

        @Override
        public FunctionT noFieldNames();

        @Override
        public default void encodePrepare(TypeEncoder encoder) {
            if (encoder.prepare(this)) {
                var thisFunction = noFieldNames();
                thisFunction.outputType().encodePrepare(encoder);
                thisFunction.inputType().encodePrepare(encoder);
            }
        }

        @Override
        public default void encode(TypeEncoder encoder) {
            encodeIndexed(this, encoder);
        }

        @Override
        public default void doEncode(TypeEncoder encoder) {
            encoder.encodeByte(T_FUNCTION);
            var thisFunction = noFieldNames();
            thisFunction.outputType().encode(encoder);
            thisFunction.inputType().encode(encoder);
        }

        @Override
        public default int doCompare(EncodableType other) {
            var thisFunction = this.noFieldNames();
            var otherFunction = ((FunctionT) other).noFieldNames();

            int cmp = thisFunction.outputType().compareTo(otherFunction.outputType());

            if (cmp == 0) {
                cmp = thisFunction.inputType().compareTo(otherFunction.inputType());
            }

            return cmp;
        }

        public EncodableType outputType();

        public EncodableType inputType();


        @Override
        public default void doInit(ClassMaker cm) {
            TheTypeGenerator.makeFunctionClass(cm, this);
        }
    }

    /**
     * Defines a pseudo type which indicates that a macro parameter is to receive code.
     */
    public static interface CodeT extends EncodableType {
        @Override
        public default int typeCode() {
            return T_CODE;
        }

        @Override
        public default void encodePrepare(TypeEncoder encoder) {
            if (encoder.prepare(this)) {
                resultType().encodePrepare(encoder);
            }
        }

        @Override
        public default void encode(TypeEncoder encoder) {
            encoder.encodeByte(T_CODE);
            resultType().encode(encoder);
        }

        @Override
        public default int doCompare(EncodableType other) {
            return resultType().compareTo(((CodeT) other).resultType());
        }

        /**
         * Returns the type yielded by the code body.
         */
        public EncodableType resultType();
    }
}
