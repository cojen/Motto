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

import java.lang.constant.ClassDesc;

import java.util.List;

import org.cojen.maker.Maker;

/**
 * @author Brian S. O'Neill
 * @see TypeEncoder
 */
public interface EncodableType {
    public static final int T_UNSPECIFIED = 0, T_NULL = 1, T_VOID = 2, T_BOOLEAN = 3, T_CHAR = 4,
        T_BYTE = 5, T_SHORT = 6, T_INT = 7, T_LONG = 8, T_FLOAT = 9, T_DOUBLE = 10,
        T_STRING = 11, T_ARRAY = 12, T_CLASS = 13, T_TUPLE = 14, T_FUNCTION = 15,

        // 16..19: reserved for future use

        // FIXME: Define T_REFERENCE which is a simple wrapper around a collection of public
        // anonymous fields. It's to be used for supporting inner functions which need to
        // access variables in the outer lexical scope. Only use references when the variable
        // isn't effectively final. Can this create confusing side-effects? Perhaps require a
        // `reference` modifier.

        T_INDEXED = 20; // not a real type code; real type codes must have a lower value

    public static final String GENERATED_PREFIX = "motto";

    /**
     * Returns this type without any field names, recursively.
     */
    public EncodableType noFieldNames();

    public default ClassDesc asClassDesc() {
        String desc = 'L' + GENERATED_PREFIX + '/' + TypeEncoder.encodeBase64(this) + ';';
        return ClassDesc.ofDescriptor(desc);
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
        public default ClassDesc asClassDesc() {
            return arrayElementType().asClassDesc().arrayType();
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

        public EncodableType arrayElementType();
    }

    public static interface ClassT extends EncodableType {
        @Override
        public default ClassDesc asClassDesc() {
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

            return ClassDesc.ofDescriptor(b.append(';').toString());
        }

        @Override
        public default void encodePrepare(TypeEncoder encoder) {
            if (!isStringType() && encoder.prepare(this)) {
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
            if (isStringType()) {
                encoder.encodeByte(T_STRING);
            } else {
                encodeIndexed(this, encoder);
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

        public List<String> packagePath();

        public List<String> namePath();

        public boolean isStringType();
    }

    public static interface TupleT extends EncodableType {
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

            switch (name) {
                case "clone", "equals", "finalize", "getClass", "hashCode",
                    "notify", "notifyAll", "toString", "wait" ->
                {
                    return "\\=" + name;
                }
            }

            return Maker.mangle(name);
        }
    }

    public static interface FunctionT extends EncodableType {
        @Override
        public default void encodePrepare(TypeEncoder encoder) {
            if (encoder.prepare(this)) {
                inputType().noFieldNames().encodePrepare(encoder);
                outputType().noFieldNames().encodePrepare(encoder);
            }
        }

        @Override
        public default void encode(TypeEncoder encoder) {
            encodeIndexed(this, encoder);
        }

        @Override
        public default void doEncode(TypeEncoder encoder) {
            encoder.encodeByte(T_FUNCTION);
            outputType().noFieldNames().encode(encoder);
            inputType().noFieldNames().encode(encoder);
        }

        public EncodableType outputType();

        public EncodableType inputType();
    }
}
