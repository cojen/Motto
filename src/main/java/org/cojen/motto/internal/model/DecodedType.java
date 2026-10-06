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
        public int typeCode() {
            return mType.typeCode();
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
        public int doCompare(EncodableType other) {
            return mType.doCompare(other);
        }

        @Override
        public org.cojen.maker.Type asMakerType() {
            return mType.asMakerType();
        }
    }

    public final static class SimpleT extends DecodedType {
        private final int mCode;

        SimpleT(int code) {
            mCode = code;
        }

        @Override
        public int typeCode() {
            return mCode;
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
        public int doCompare(EncodableType other) {
            // Nothing more to compare.
            return 0;
        }

        @Override
        public org.cojen.maker.Type asMakerType() {
            Class<?> clazz;

            switch (mCode) {
                // FIXME: Handling of T_UNSPECIFIED and T_NULL might be wrong. The super maker
                // type is for generated types, and so that's an issue.
                case T_UNSPECIFIED, T_NULL -> {
                    return super.asMakerType();
                }

                case T_VOID -> clazz = void.class;
                case T_BOOLEAN -> clazz = boolean.class;
                case T_CHAR -> clazz = char.class;
                case T_BYTE -> clazz = byte.class;
                case T_SHORT -> clazz = short.class;
                case T_INT -> clazz = int.class;
                case T_LONG -> clazz = long.class;
                case T_FLOAT -> clazz = float.class;
                case T_DOUBLE -> clazz = double.class;
                case T_OBJECT -> clazz = Object.class;
                case T_STRING -> clazz = String.class;

                default -> {
                    throw new IllegalStateException();
                }
            };

            return org.cojen.maker.Type.from(clazz);
        }
    }

    public final static class ArrayT extends DecodedType implements EncodableType.ArrayT {
        private final DecodedType mElementType;
        private final org.cojen.maker.Type mMakerType;

        ArrayT(DecodedType elementType) {
            mElementType = elementType;
            mMakerType = elementType.asMakerType().asArray();
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
        public org.cojen.maker.Type asMakerType() {
            return mMakerType;
        }
    }

    public final static class ClassT extends DecodedType implements EncodableType.ClassT {
        private final List<String> mPackagePath, mNamePath;
        private org.cojen.maker.Type mMakerType;

        ClassT(List<String> packagePath, List<String> namePath) {
            mPackagePath = packagePath;
            mNamePath = namePath;
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
        public int simpleClassType() {
            if (mPackagePath.size() == 2 && mNamePath.size() == 1) {
                if (mPackagePath.get(0).equals("java") && mPackagePath.get(1).equals("lang")) {
                    String first = mNamePath.getFirst();
                    if ("Object".equals(first)) {
                        return T_OBJECT;
                    } else if ("String".equals(first)) {
                        return T_STRING;
                    }
                }
            }

            return -1;
        }

        @Override
        public org.cojen.maker.Type asMakerType() {
            if (mMakerType == null) {
                mMakerType = EncodableType.ClassT.super.asMakerType();
            }
            return mMakerType;
        }
    }

    public abstract sealed static class GeneratedT extends DecodedType {
        private org.cojen.maker.Type mMakerType;

        @Override
        public org.cojen.maker.Type asMakerType() {
            if (mMakerType == null) {
                mMakerType = super.asMakerType();
            }
            return mMakerType;
        }
    }

    public final static class CompositeT extends GeneratedT implements EncodableType.CompositeT {
        private final List<DecodedType> mTypes;

        CompositeT(List<DecodedType> types) {
            mTypes = types;
        }

        @Override
        public DecodedType noFieldNames() {
            return this;
        }

        @Override
        public int numFields() {
            return mTypes.size();
        }

        @Override
        public DecodedType fieldType(int index) {
            return mTypes.get(index);
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
        public DecodedType.FunctionT noFieldNames() {
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

    public final static class CodeT extends GeneratedT implements EncodableType.CodeT {
        private final DecodedType mResultType;

        CodeT(DecodedType resultType) {
            mResultType = resultType;
        }

        @Override
        public DecodedType.CodeT noFieldNames() {
            return new DecodedType.CodeT(mResultType.noFieldNames());
        }

        @Override
        public DecodedType resultType() {
            return mResultType;
        }
    }
}
