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

package org.cojen.motto.internal.tuple;

import java.nio.ByteBuffer;

import java.nio.charset.CharacterCodingException;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import java.util.zip.CRC32C;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class TypeDecoder {
    private byte[] mBuffer;
    private int mOffset;

    private String[] mStringTable;
    private DecodedType[] mTypeTable;

    public TypeDecoder() {
    }

    /**
     * @return null if decoding fails
     * @throws RuntimeException if decoding fails
     */
    public DecodedType tryDecode(String str) {
        mBuffer = Base64.getUrlDecoder().decode(str);

        if (mBuffer.length < 6) {
            return null;
        }

        var crc = new CRC32C();
        crc.update(mBuffer, 4, mBuffer.length - 4);
        int actual = ((int) crc.getValue()) ^ TypeEncoder.MAGIC_VERSION;

        if (actual != (int) TypeEncoder.cIntArrayBEHandle.get(mBuffer, 0)) {
            return null;
        }

        mOffset = 4;

        // Decode the string table.
        {
            int size = decodeUnsignedVarInt();
            mStringTable = new String[size];
            for (int i=0; i<size; i++) {
                mStringTable[i] = decodeUTF_8(decodeUnsignedVarInt());
            }
        }

        // Decode the type table.
        {
            int size = decodeUnsignedVarInt();
            mTypeTable = new DecodedType[size];
            for (int i=0; i<size; i++) {
                DecodedType type = decodeType();
                DecodedType existing = mTypeTable[i];
                if (existing == null) {
                    mTypeTable[i] = type;
                } else if (existing instanceof DecodedType.Wrapper wrapper) {
                    wrapper.resolve(type);
                } else {
                    throw new AssertionError();
                }
            }
        }

        return decodeType();
    }

    private int decodeUnsignedVarInt() {
        long result = decodeUnsignedVarInt(mBuffer, mOffset);
        mOffset = (int) (result >> 32);
        return (int) result;
    }

    private int decodeSignedVarInt() {
        long result = decodeSignedVarInt(mBuffer, mOffset);
        mOffset = (int) (result >> 32);
        return (int) result;
    }

    /**
     * Decodes an integer as encoded by encodeUnsignedVarInt.
     * Value is in the lower word, and updated offset is in the upper word.
     */
    static long decodeUnsignedVarInt(byte[] b, int offset) {
        int v = b[offset++];
        if (v < 0) {
            v = switch ((v >> 4) & 0x07) {
                case 0x00, 0x01, 0x02, 0x03 -> (1 << 7)
                        + (((v & 0x3f) << 8)
                           | (b[offset++] & 0xff));
                case 0x04, 0x05 -> ((1 << 14) + (1 << 7))
                        + (((v & 0x1f) << 16)
                           | ((b[offset++] & 0xff) << 8)
                           | (b[offset++] & 0xff));
                case 0x06 -> ((1 << 21) + (1 << 14) + (1 << 7))
                        + (((v & 0x0f) << 24)
                           | ((b[offset++] & 0xff) << 16)
                           | ((b[offset++] & 0xff) << 8)
                           | (b[offset++] & 0xff));
                default -> ((1 << 28) + (1 << 21) + (1 << 14) + (1 << 7))
                        + ((b[offset++] << 24)
                           | ((b[offset++] & 0xff) << 16)
                           | ((b[offset++] & 0xff) << 8)
                           | (b[offset++] & 0xff));
            };
        }
        return (((long) offset) << 32L) | (v & 0xffff_ffffL);
    }

    /**
     * Decodes an integer as encoded by encodeSignedVarInt.
     * Value is in the lower word, and updated offset is in the upper word.
     */
    public static long decodeSignedVarInt(byte[] b, int offset) {
        long result = decodeUnsignedVarInt(b, offset);
        int v = (int) result;
        v = ((v & 1) != 0) ? ((~(v >> 1)) | (1 << 31)) : (v >>> 1);
        return (result & ~0xffff_ffffL) | (v & 0xffff_ffffL);
    }

    private String decodeString() {
        int code = decodeSignedVarInt();
        return code < 0 ? mStringTable[~code] : (code == 0 ? null : decodeUTF_8(code - 1));
    }

    private List<String> decodePath() {
        int size = decodeUnsignedVarInt();
        if (size == 0) {
            return List.of();
        }
        var list = new ArrayList<String>(size);
        for (int i=0; i<size; i++) {
            list.add(decodeString());
        }
        return list;
    }

    private String decodeUTF_8(int length) {
        if (length == 0) {
            return "";
        }

        try {
            String str = TypeEncoder.UTF_8.newDecoder()
                .decode(ByteBuffer.wrap(mBuffer, mOffset, length)).toString();
            mOffset += length;
            return str;
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private DecodedType lookupType(int index) {
        DecodedType type = mTypeTable[index];
        if (type == null) {
            mTypeTable[index] = type = new DecodedType.Wrapper();
        }
        return type;
    }

    private DecodedType decodeType() {
        int code = decodeUnsignedVarInt();

        if (code <= EncodableType.T_STRING) {
            return new DecodedType.SimpleT(code);
        }

        switch (code) {
            case EncodableType.T_ARRAY -> {
                return new DecodedType.ArrayT(decodeType());
            }

            case EncodableType.T_CLASS -> {
                return new DecodedType.ClassT(decodePath(), decodePath());
            }

            case EncodableType.T_TUPLE -> {
                int numElements = decodeUnsignedVarInt();

                List<DecodedType> types;
                List<String> names;

                if (numElements == 0) {
                    types = List.of();
                    names = List.of();
                } else {
                    types = new ArrayList<DecodedType>(numElements);
                    names = new ArrayList<String>(numElements);
                    for (int i=0; i<numElements; i++) {
                        types.add(decodeType());
                        names.add(decodeString());
                    }
                }

                return new DecodedType.TupleT(types, names);
            }

            case EncodableType.T_FUNCTION -> {
                return new DecodedType.FunctionT(decodeType(), decodeType());
            }

            default -> {
                if (code < EncodableType.T_INDEXED) {
                    throw new IllegalArgumentException();
                }

                return lookupType(code - EncodableType.T_INDEXED);
            }
        }
    }
}
