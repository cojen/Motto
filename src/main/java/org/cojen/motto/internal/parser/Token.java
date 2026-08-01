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

package org.cojen.motto.internal.parser;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.cojen.motto.internal.model.BaseAction;
import org.cojen.motto.internal.model.BaseIntType;
import org.cojen.motto.internal.model.BaseFloatType;
import org.cojen.motto.internal.model.BaseDoubleType;
import org.cojen.motto.internal.model.BaseLongType;
import org.cojen.motto.internal.model.BaseType;
import org.cojen.motto.internal.model.LoadedClass;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public abstract sealed class Token extends Element {
    public static final int T_EOF = 0, T_UNCLOSED = 1;

    // Grouping tokens.
    public static final int T_LPAREN = 2, T_RPAREN = 3, T_LBRACE = 4, T_RBRACE = 5,
        T_LBRACK = 6, T_RBRACK = 7, T_COMMA = 8, T_SEMI = 9;

    // Regular tokens.
    public static final int T_DOT = 10, T_COLON = 11, T_ASSIGN = 12,
        T_INC = 13, T_DEC = 14, T_QUESTION = 15, T_BANG = 16, T_TILDE = 17;

    // Standard infix operators.
    public static final int T_EQ = 18, T_NE = 19, T_GE = 20, T_LT = 21, T_LE = 22, T_GT = 23,
        T_AND = 24, T_OR = 25, T_LAND = 26, T_LOR = 27, T_LXOR = 28,
        T_PLUS = 29, T_MINUS = 30, T_MUL = 31, T_DIV = 32, T_REM = 33,
        T_SHL = 34, T_SHR = 35, T_USHR = 36;

    // Standard infix assignment operators.
    public static final int T_LAND_A = 37, T_LOR_A = 38, T_LXOR_A = 39,
        T_PLUS_A = 40, T_MINUS_A = 41, T_MUL_A = 42, T_DIV_A = 43, T_REM_A = 44,
        T_SHL_A = 45, T_SHR_A = 46, T_USHR_A = 47;

    // Tokens which have a text value.
    public static final int T_CUSTOM_OP = 48, T_IDENTIFIER = 49, T_STRING = 50, T_COMMENT = 51;

    // Numerical constants.
    public static final int T_INT32 = 52, T_INT64 = 53, T_BIGINT = 54,
        T_FLOAT32 = 55, T_FLOAT64 = 56, T_BIGDEC = 57;

    private final int mPosition, mLength;

    /**
     * @param line source code start line, one-based; is 0 if not applicable
     * @param column source code start column, zero-based; is -1 if not applicable
     * @param length token length in code units (UTF-16)
     */
    Token(int line, int column, int length) {
        mPosition = BaseAction.encodePosition(line, column);
        mLength = length;
    }

    /**
     * Returns the encoded position value, which must be decoded to extract the line and column
     * number.
     */
    public final int position() {
        return mPosition;
    }

    public final int line() {
        return BaseAction.decodeLine(mPosition);
    }

    public final int column() {
        return BaseAction.decodeColumn(mPosition);
    }

    public final int length() {
        return mLength;
    }

    @Override
    public final Token start() {
        return this;
    }

    @Override
    public final Token end() {
        return this;
    }

    /**
     * Returns a T_* constant.
     */
    public abstract int type();

    /**
     * @return null if token isn't a literal
     */
    public BaseType literalType() {
        return null;
    }

    public Object literalValue() {
        return null;
    }

    public static sealed class Basic extends Token {
        private final int mType;

        Basic(int line, int column, int length, int type) {
            super(line, column, length);
            mType = type;
        }

        @Override
        public int type() {
            return mType;
        }
    }

    /**
     * This token indicates an error because the file end was reached when parsing a quoted
     * item or a multiline comment. The parser should report an error like: "Unclosed $type at
     * end of file", where $type is "string", "quoted identifier", or "multiline comment".
     */
    public static final class Unclosed extends Basic {
        public final int unclosedType;

        Unclosed(int line, int column, int length, int unclosedType) {
            super(line, column, length, T_UNCLOSED);
            this.unclosedType = unclosedType;
        }
    }

    /**
     * This token indicates that what originally followed the token was a newline. It only
     * needs to be used in special cases: `)` or `}`
     */
    public static final class Newline extends Basic {
        Newline(int line, int column, int length, int type) {
            super(line, column, length, type);
        }
    }

    public static abstract sealed class Text extends Token {
        public final String text;

        Text(int line, int column, int length, String text) {
            super(line, column, length);
            this.text = text.intern();
        }

        @Override
        public int hashCode() {
            return text.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return obj == this || obj instanceof Text t
                && getClass() == t.getClass() && text.equals(t.text);
                
        }

        @Override
        public String toString() {
            return text;
        }
    }

    public static final class Custom extends Text {
        Custom(int line, int column, int length, String text) {
            super(line, column, length, text);
        }

        @Override
        public int type() {
            return T_CUSTOM_OP;
        }
    }

    public static final class Identifier extends Text {
        public final boolean quoted;

        Identifier(int line, int column, int length, String text, boolean quoted) {
            super(line, column, length, text);
            this.quoted = quoted;
        }

        @Override
        public int type() {
            return T_IDENTIFIER;
        }

        @Override
        public String toString() {
            return quoted ? ('`' + text + '`') : text;
        }
    }

    public static final class Str extends Text {
        Str(int line, int column, int length, String text) {
            super(line, column, length, text);
        }

        @Override
        public int type() {
            return T_STRING;
        }

        @Override
        public String literalValue() {
            return text;
        }

        @Override
        public BaseType literalType() {
            return LoadedClass.classFrom(String.class);
        }

        @Override
        public String toString() {
            return '"' + text + '"';
        }
    }

    public static abstract sealed class Num extends Token {
        // Is true if the number literal had a negation prefix which got absorbed.
        public final boolean negated;

        Num(int line, int column, int length, boolean negated) {
            super(line, column, length);
            this.negated = negated;
        }

        /**
         * Returns a negated value, possibly expanding the bit width.
         */
        public abstract Num negate();
    }

    public static final class Int32 extends Num {
        public final int value;

        Int32(int line, int column, int length, boolean negated, int value) {
            super(line, column, length, negated);
            this.value = value;
        }

        @Override
        public int type() {
            return T_INT32;
        }

        @Override
        public Integer literalValue() {
            return value;
        }

        @Override
        public BaseIntType literalType() {
            return BaseIntType.THE;
        }

        @Override
        public Num negate() {
            if (value != Integer.MIN_VALUE) {
                return new Int32(line(), column(), length(), false, -value);
            }
            return new Int64(line(), column(), length(), false, -((long) value));
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    public static final class Int64 extends Num {
        public final long value;

        Int64(int line, int column, int length, boolean negated, long value) {
            super(line, column, length, negated);
            this.value = value;
        }

        @Override
        public int type() {
            return T_INT64;
        }

        @Override
        public Long literalValue() {
            return value;
        }

        @Override
        public BaseLongType literalType() {
            return BaseLongType.THE;
        }

        @Override
        public Num negate() {
            if (value != Long.MIN_VALUE) {
                return new Int64(line(), column(), length(), false, -value);
            }
            return new BigInt(line(), column(), length(), false,
                              BigInteger.valueOf(value).negate());
        }

        @Override
        public String toString() {
            return value + "L";
        }
    }

    public static final class BigInt extends Num {
        public final BigInteger value;

        BigInt(int line, int column, int length, boolean negated, BigInteger value) {
            super(line, column, length, negated);
            this.value = value;
        }

        @Override
        public int type() {
            return T_BIGINT;
        }

        @Override
        public BigInteger literalValue() {
            return value;
        }

        @Override
        public BaseType literalType() {
            return LoadedClass.from(BigInteger.class);
        }

        @Override
        public BigInt negate() {
            return new BigInt(line(), column(), length(), false, value.negate());
        }

        @Override
        public String toString() {
            return value + "g";
        }
    }

    public static final class Float32 extends Num {
        public final float value;

        Float32(int line, int column, int length, boolean negated, float value) {
            super(line, column, length, negated);
            this.value = value;
        }

        @Override
        public int type() {
            return T_FLOAT32;
        }

        @Override
        public Float literalValue() {
            return value;
        }

        @Override
        public BaseFloatType literalType() {
            return BaseFloatType.THE;
        }

        @Override
        public Float32 negate() {
            return new Float32(line(), column(), length(), false, -value);
        }

        @Override
        public String toString() {
            return value + "f";
        }
    }

    public static final class Float64 extends Num {
        public final double value;

        Float64(int line, int column, int length, boolean negated, double value) {
            super(line, column, length, negated);
            this.value = value;
        }

        @Override
        public int type() {
            return T_FLOAT64;
        }

        @Override
        public Double literalValue() {
            return value;
        }

        @Override
        public BaseDoubleType literalType() {
            return BaseDoubleType.THE;
        }

        @Override
        public Float64 negate() {
            return new Float64(line(), column(), length(), false, -value);
        }

        @Override
        public String toString() {
            return value + "d";
        }
    }

    public static final class BigDec extends Num {
        public final BigDecimal value;

        BigDec(int line, int column, int length, boolean negated, BigDecimal value) {
            super(line, column, length, negated);
            this.value = value;
        }

        @Override
        public int type() {
            return T_BIGDEC;
        }

        @Override
        public BigDecimal literalValue() {
            return value;
        }

        @Override
        public BaseType literalType() {
            return LoadedClass.classFrom(BigDecimal.class);
        }

        @Override
        public BigDec negate() {
            return new BigDec(line(), column(), length(), false, value.negate());
        }

        @Override
        public String toString() {
            return value + "g";
        }
    }
}
