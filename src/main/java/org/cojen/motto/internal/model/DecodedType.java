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
import java.lang.constant.ConstantDescs;

import java.util.ArrayList;
import java.util.List;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public abstract sealed class DecodedType implements EncodableType {
    @Override
    public abstract DecodedType noFieldNames();

    /**
     * Used when decoding the type table and a type is referenced which hasn't been decoded yet.
     */
    final static class Wrapper extends DecodedType {
        private DecodedType mType;

        Wrapper() {
        }

        void resolve(DecodedType type) {
            mType = type;
        }

        @Override
        public DecodedType noFieldNames() {
            return mType.noFieldNames();
        }

        @Override
        public void encodePrepare(TypeEncoder encoder) {
            mType.encodePrepare(encoder);
        }

        @Override
        public void encode(TypeEncoder encoder) {
            mType.encode(encoder);
        }

        @Override
        public void doEncode(TypeEncoder encoder) {
            mType.doEncode(encoder);
        }

        @Override
        public ClassDesc asClassDesc() {
            return mType.asClassDesc();
        }
    }

    public final static class SimpleT extends DecodedType {
        private final int mCode;

        SimpleT(int code) {
            mCode = code;
        }

        @Override
        public DecodedType noFieldNames() {
            return this;
        }

        @Override
        public void encode(TypeEncoder encoder) {
            encoder.encodeByte(mCode);
        }

        @Override
        public ClassDesc asClassDesc() {
            return switch (mCode) {
                case T_UNSPECIFIED, T_NULL -> super.asClassDesc();
                case T_VOID -> ConstantDescs.CD_Void;
                case T_BOOLEAN -> ConstantDescs.CD_boolean;
                case T_CHAR -> ConstantDescs.CD_char;
                case T_BYTE -> ConstantDescs.CD_byte;
                case T_SHORT -> ConstantDescs.CD_short;
                case T_INT -> ConstantDescs.CD_int;
                case T_LONG -> ConstantDescs.CD_long;
                case T_FLOAT -> ConstantDescs.CD_float;
                case T_DOUBLE -> ConstantDescs.CD_double;
                case T_STRING -> ConstantDescs.CD_String;
                default -> {
                    throw new IllegalStateException();
                }
            };
        }
    }

    public final static class ArrayT extends DecodedType implements EncodableType.ArrayT {
        private final DecodedType mElementType;
        private final ClassDesc mClassDesc;

        ArrayT(DecodedType elementType) {
            mElementType = elementType;
            mClassDesc = elementType.asClassDesc().arrayType();
        }

        @Override
        public DecodedType noFieldNames() {
            return new DecodedType.ArrayT(mElementType.noFieldNames());
        }

        @Override
        public DecodedType arrayElementType() {
            return mElementType;
        }

        @Override
        public ClassDesc asClassDesc() {
            return mClassDesc;
        }
    }

    public final static class ClassT extends DecodedType implements EncodableType.ClassT {
        private final List<String> mPackagePath, mNamePath;
        private final ClassDesc mClassDesc;

        ClassT(List<String> packagePath, List<String> namePath) {
            mPackagePath = packagePath;
            mNamePath = namePath;

            var b = new StringBuilder().append('L');

            // FIXME: The descriptor elements might need to be mangled.

            if (!packagePath.isEmpty()) {
                for (String name : packagePath) {
                    b.append(name).append('/');
                }
            }

            {
                int size = namePath.size();
                for (int i=0; i<size; i++) {
                    if (i > 0) {
                        b.append('$');
                    }
                    b.append(namePath.get(i));
                }
            }

            mClassDesc = ClassDesc.ofDescriptor(b.append(';').toString());
        }

        @Override
        public DecodedType noFieldNames() {
            return this;
        }

        @Override
        public List<String> packagePath() {
            return mPackagePath;
        }

        @Override
        public List<String> namePath() {
            return mNamePath;
        }

        @Override
        public boolean isStringType() {
            return mClassDesc.descriptorString().equals("Ljava/lang/String;");
        }

        @Override
        public ClassDesc asClassDesc() {
            return mClassDesc;
        }
    }

    public abstract sealed static class GeneratedT extends DecodedType {
        private ClassDesc mClassDesc;

        @Override
        public ClassDesc asClassDesc() {
            if (mClassDesc == null) {
                mClassDesc = super.asClassDesc();
            }
            return mClassDesc;
        }
    }

    public final static class TupleT extends GeneratedT implements EncodableType.TupleT {
        private final List<DecodedType> mTypes;
        private final List<String> mNames;

        /**
         * @param names elements can be null
         */
        TupleT(List<DecodedType> types, List<String> names) {
            mTypes = types;
            mNames = names;
        }

        @Override
        public DecodedType noFieldNames() {
            var types = new ArrayList<DecodedType>(mTypes.size());
            var names = new ArrayList<String>(types.size());

            for (DecodedType type : mTypes) {
                types.add(type.noFieldNames());
                names.add(null);
            }

            return new DecodedType.TupleT(types, names);
        }

        @Override
        public int numFields() {
            return mTypes.size();
        }

        @Override
        public DecodedType fieldType(int index) {
            return mTypes.get(index);
        }

        @Override
        public String fieldName(int index) {
            return mNames.get(index);
        }
    }

    public final static class FunctionT extends GeneratedT implements EncodableType.FunctionT {
        private final DecodedType mOutputType, mInputType;

        FunctionT(DecodedType outputType, DecodedType inputType) {
            mOutputType = outputType;
            mInputType = inputType;
        }

        @Override
        public DecodedType noFieldNames() {
            return new DecodedType.FunctionT(mOutputType.noFieldNames(), mInputType.noFieldNames());
        }

        @Override
        public DecodedType outputType() {
            return mOutputType;
        }

        @Override
        public DecodedType inputType() {
            return mInputType;
        }
    }
}
