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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import java.util.function.IntFunction;

import java.util.zip.CRC32C;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class TypeEncoder {
    static final int MAGIC_VERSION = 0xc2d36206;

    static final Charset UTF_8 = Charset.forName("UTF-8");

    static final VarHandle cIntArrayBEHandle;

    static {
        try {
            cIntArrayBEHandle = MethodHandles.byteArrayViewVarHandle
                (int[].class, ByteOrder.BIG_ENDIAN);
        } catch (Throwable e) {
            throw new ExceptionInInitializerError();
        }
    }

    public static String encodeBase64(EncodableType type) {
        var encoder = new TypeEncoder();
        type.encodePrepare(encoder);
        encoder.finishPreparation();
        type.encode(encoder);
        return encoder.finishBase64();
    }

    private final Map<String, Integer> mStringMap;
    private final Map<EncodableType, Integer> mTypeMap;

    private byte[] mBuffer;
    private int mSize;

    private TypeEncoder() {
        mStringMap = new LinkedHashMap<>();
        mTypeMap = new LinkedHashMap<>();
    }

    /**
     * Prepare a string to be indexed by a table.
     */
    public void prepare(String str) {
        if (str != null && !str.isEmpty()) {
            mStringMap.compute(str, (k, v) -> (v == null) ? 1 : v + 1);
        }
    }

    /**
     * Prepare a type to be indexed by a table.
     *
     * @return false if the type has already been seen
     */
    public boolean prepare(EncodableType type) {
        return mTypeMap.compute(type, (k, v) -> (v == null) ? 1 : v + 1) == 1;
    }

    /**
     * Must be called before encoding can begin, by building the type table.
     */
    public void finishPreparation() {
        mBuffer = new byte[32];
        mSize = 4; // reserve space for the CRC

        String[] stringTable = convertToTable(mStringMap, String[]::new);
        EncodableType[] typeTable = convertToTable(mTypeMap, EncodableType[]::new);

        encodeUnsignedVarInt(stringTable.length);

        for (String str : stringTable) {
            ByteBuffer bb = encodeUTF_8(str);
            int length = bb.limit();
            encodeUnsignedVarInt(length); // note: unsigned; encodeString uses signed
            ensureCapacity(length);
            bb.get(0, mBuffer, mSize, length);
            mSize += length;
        }

        encodeUnsignedVarInt(typeTable.length);

        for (EncodableType type : typeTable) {
            type.doEncode(this);
        }
    }

    /**
     * Converts a map of usage counts to a table of indexes.
     *
     * @return the table of entries to be encoded
     */
    @SuppressWarnings("unchecked")
    private static <K> K[] convertToTable(Map<K, Integer> map, IntFunction<K[]> generator) {
        // Remove entries which are only used once.
        Iterator<Integer> it = map.values().iterator();
        while (it.hasNext()) {
            Integer count = it.next();
            if (count <= 1) {
                it.remove();
            }
        }

        // Sort the entries by usage count. Those which are used often more will have lower
        // index values, potentially reducing varint encoding size.
        K[] table = map.keySet().toArray(generator);
        Arrays.sort(table, (a, b) -> Integer.compare(map.get(b), map.get(a)));

        // Replace the counts with indexes.
        for (int i=0; i<table.length; i++) {
            map.put((K) table[i], i);
        }

        return table;
    }

    public byte[] finish() {
        var crc = new CRC32C();
        crc.update(mBuffer, 4, mSize - 4);
        cIntArrayBEHandle.set(mBuffer, 0, ((int) crc.getValue()) ^ MAGIC_VERSION);

        if (mBuffer.length == mSize) {
            return mBuffer;
        } else {
            return Arrays.copyOfRange(mBuffer, 0, mSize);
        }
    }

    public String finishBase64() {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        return encoder.encodeToString(finish());
    }

    /**
     * Note: Must only be called after finishPreparation has been called.
     *
     * @return a table index, or else -1 if not in the table
     */
    public int lookup(String str) {
        Integer index = mStringMap.get(str);
        return index == null ? -1 : index;
    }

    /**
     * Note: Must only be called after finishPreparation has been called.
     *
     * @return a table index, or else -1 if not in the table
     */
    public int lookup(EncodableType type) {
        Integer index = mTypeMap.get(type);
        return index == null ? -1 : index;
    }

    public void encodeByte(int v) {
        ensureCapacity(1);
        mBuffer[mSize++] = (byte) v;
    }

    public void encodeUnsignedVarInt(int v) {
        ensureCapacity(5);
        mSize = encodeUnsignedVarInt(mBuffer, mSize, v);
    }

    public void encodeSignedVarInt(int v) {
        encodeUnsignedVarInt(convertSignedVarInt(v));        
    }

    /**
     * Encode the given integer using 1 to 5 bytes. Values closer to zero are
     * encoded in fewer bytes.
     *
     * <pre>
     * Value range                                Required bytes  Header
     * ---------------------------------------------------------------------
     * 0..127                                     1               0b0xxxxxxx
     * 128..16511                                 2               0b10xxxxxx
     * 16512..2113663                             3               0b110xxxxx
     * 2113664..270549119                         4               0b1110xxxx
     * 270549120..4294967295                      5               0b11110000
     * </pre>
     *
     * @return new offset
     */
    private static int encodeUnsignedVarInt(byte[] b, int offset, int v) {
        if (v < (1 << 7)) {
            if (v < 0) {
                v -= (1 << 28) + (1 << 21) + (1 << 14) + (1 << 7);
                b[offset++] = (byte) (0xff);
                b[offset++] = (byte) (v >> 24);
                b[offset++] = (byte) (v >> 16);
                b[offset++] = (byte) (v >> 8);
            }
        } else {
            v -= (1 << 7);
            if (v < (1 << 14)) {
                b[offset++] = (byte) (0x80 | (v >> 8));
            } else {
                v -= (1 << 14);
                if (v < (1 << 21)) {
                    b[offset++] = (byte) (0xc0 | (v >> 16));
                } else {
                    v -= (1 << 21);
                    if (v < (1 << 28)) {
                        b[offset++] = (byte) (0xe0 | (v >> 24));
                    } else {
                        v -= (1 << 28);
                        b[offset++] = (byte) (0xf0);
                        b[offset++] = (byte) (v >> 24);
                    }
                    b[offset++] = (byte) (v >> 16);
                }
                b[offset++] = (byte) (v >> 8);
            }
        }
        b[offset++] = (byte) v;
        return offset;
    }

    /**
     * Converts a signed int such that it can be efficiently encoded as unsigned.
     *
     * <pre>
     * Value range(s)                                    Required bytes
     * ----------------------------------------------------------------
     * -64..63                                           1
     * -8256..-65, 64..8255                              2
     * -1056832..-8257, 8256..1056831                    3
     * -135274560..-1056833, 1056832..135274559          4
     * -2147483648..-135274561, 135274560..2147483647    5
     * </pre>
     */
    private static int convertSignedVarInt(int v) {
        if (v < 0) {
            // Complement negative value to turn all the ones to zeros, which
            // can be compacted. Shift and put sign bit at LSB.
            v = ((~v) << 1) | 1;
        } else {
            // Shift and put sign bit at LSB.
            v <<= 1;
        }
        return v;
    }

    /**
     * @param str can be null
     */
    public void encodeString(String str) {
        if (str == null) {
            encodeSignedVarInt(0);
        } else {
            int index = lookup(str);

            if (index >= 0) {
                encodeSignedVarInt(~index);
            } else {
                ByteBuffer bb = encodeUTF_8(str);
                int length = bb.limit();
                encodeSignedVarInt(length + 1); // +1 because 0 means null
                ensureCapacity(length);
                bb.get(0, mBuffer, mSize, length);
                mSize += length;
            }
        }
    }

    private static ByteBuffer encodeUTF_8(String str) {
        try {
            return UTF_8.newEncoder().encode(CharBuffer.wrap(str));
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private void ensureCapacity(int amt) {
        if (mSize + amt > mBuffer.length) {
            mBuffer = Arrays.copyOf(mBuffer, Math.max(mSize + amt, mSize << 1));
        }
    }
}
