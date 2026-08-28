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

import java.io.Closeable;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.cojen.motto.internal.parser.Token.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
final class Tokenizer implements Closeable {
    private final PushbackReader mIn;

    // Temporary space.
    private final StringBuilder mWord = new StringBuilder();

    private int mLine, mColumn, mPrevColumn;

    Tokenizer(Reader in) {
        mIn = new PushbackReader(in, 2);
        mLine = 1;
    }

    @Override
    public void close() throws IOException {
        mIn.close();
    }

    public void closeQuietly() {
        try {
            close();
        } catch (IOException e) {
        }
    }

    /**
     * @return the next token, or T_EOF if none left
     */
    public Token next() throws IOException {
        try {
            return next(read());
        } catch (Abort e) {
            return e.token;
        } catch (IOException e) {
            closeQuietly();
            throw e;
        }
    }

    private Token next(int c) throws IOException, Abort {
        int line, column, type;

        loop: while (true) {
            line = mLine;
            column = mColumn;

            if (c < 0) {
                closeQuietly();
                return new Basic(line, column, 0, T_EOF);
            }

            switch (c) {
            case '\r': case ' ': case '\t': case '\0':
                break;

            case '\n': {
                c = skipWhitespace();
                if (c == '\\' && notOperator()) {
                    // Drop all whitespace after the connector.
                    unread(skipWhitespace());
                    break;
                }
                continue;
            }

            case '\\':
                if (notOperator()) {
                    // Drop all whitespace after the connector.
                    unread(skipWhitespace());
                    break;
                }
                return parseOperator(c);

            case '(':
                type = T_LPAREN;
                break loop;

            case ')':
                type = T_RPAREN;
                if (seekNewline()) {
                    return new Newline(line, column - 1, 1, type);
                }
                break loop;

            case '{':
                type = T_LBRACE;
                break loop;

            case '}': {
                type = T_RBRACE;
                if (seekNewline()) {
                    return new Newline(line, column - 1, 1, type);
                }
                break loop;
            }

            case '[':
                type = T_LBRACK;
                break loop;

            case ']':
                type = T_RBRACK;
                break loop;

            case ',':
                type = T_COMMA;
                break loop;

            case ';':
                type = T_SEMI;
                break loop;

            case '/': {
                int next = read();
                if (next == '/') {
                    skipSingleLineComment();
                    break;
                } else if (next == '*') {
                    skipMultiLineComment();
                    break;
                }
                unread(next);
                return parseOperator(c);
            }

            case '"':
                return parseQuoted(T_STRING, "\"");

            case '\'':
                return parseQuoted(T_STRING, "'");

            case '`':
                return parseQuoted(T_IDENTIFIER, "`");

            case '#':
                /*
                  Check if defining a string or identifier using a custom delimiter.

                  Example: #"abc"message is "hello""abc"
                  Result:  message is "hello"

                  If the custom delimiter is enclosed in double or single quotes, then the
                  result string is normal string. If backticks, then the result string is an
                  identifier.

                  The delimiter string itself is parsed as a normal string. To be matched, it
                  must appear again exactly, enclosed by the same quotes.
                */

                c = read();

                int qtype;
                String quote;

                switch (c) {
                    case '"' -> {
                        qtype = T_STRING;
                        quote = "\"";
                    }
                    case '\'' -> {
                        qtype = T_STRING;
                        quote = "'";
                    }
                    case '`' -> {
                        qtype = T_IDENTIFIER;
                        quote = "`";
                    }
                    default -> {
                        return parseOperator(c);
                    }
                }

                String delimiter = quote + ((Text) parseQuoted(qtype, quote)).text + quote;

                return parseQuoted(qtype, delimiter);

            case '0': case '1': case '2': case '3': case '4':
            case '5': case '6': case '7': case '8': case '9':
                return parseNumber(c, false);

            case '.':
                if (isDigit(peek())) {
                    return parseNumber(c, false);
                }
                return parseOperator(c);

            case '-': {
                int next = read();
                if (isDigit(next)) {
                    return parseNumber(next, true);
                }
                if (Character.isWhitespace(next)) {
                    next = skipWhitespace();
                    if (isDigit(next)) {
                        return parseNumber(next, true);
                    }
                    unread(next);
                    next = ' ';
                }
                unread(next);
                return parseOperator(c);
            }

            case '$': case '_':
            case 'a': case 'b': case 'c': case 'd': case 'e': case 'f': case 'g': case 'h':
            case 'i': case 'j': case 'k': case 'l': case 'm': case 'n': case 'o': case 'p':
            case 'q': case 'r': case 's': case 't': case 'u': case 'v': case 'w': case 'x':
            case 'y': case 'z':
            case 'A': case 'B': case 'C': case 'D': case 'E': case 'F': case 'G': case 'H':
            case 'I': case 'J': case 'K': case 'L': case 'M': case 'N': case 'O': case 'P':
            case 'Q': case 'R': case 'S': case 'T': case 'U': case 'V': case 'W': case 'X':
            case 'Y': case 'Z':
                return parseIdentifier(c);

            default:
                if (Character.isWhitespace(c)) {
                    break;
                }
                if (Character.isJavaIdentifierStart(c)) {
                    return parseIdentifier(c);
                }
                return parseOperator(c);
            }

            c = read();
        }

        return new Basic(line, column - 1, 1, type);
    }

    private int read() throws IOException {
        int c = mIn.read();
        if (c != '\n') {
            mColumn++;
        } else {
            mLine++;
            mPrevColumn = mColumn;
            mColumn = 0;
        }
        return c;
    }

    private void unread(int c) throws IOException {
        if (c != '\n') {
            mColumn--;
            if (c < 0) {
                return;
            }
        } else {
            mLine--;
            mColumn = mPrevColumn;
        }
        mIn.unread(c);
    }

    private int peek() throws IOException {
        int c = mIn.read();
        if (c >= 0) {
            mIn.unread(c);
        }
        return c;
    }

    /**
     * Returns the next non-whitespace and non-comment character, which might be eof.
     */
    private int skipWhitespace() throws IOException, Abort {
        while (true) {
            int c = read();

            switch (c) {
                case '/' -> {
                    if (trySkipComment()) {
                        continue;
                    }
                }
                case ' ', '\t', '\r', '\n' -> {
                    continue;
                }
                default -> {
                    if (Character.isWhitespace(c)) {
                        continue;
                    }
                }
            }

            return c;
        }
    }

    /**
     * Skips whitespace, but stops when a newline is encountered. When a non-whitespace
     * character is encountered, it isn't consumed, and so it can be read back later.
     *
     * @return true if a newline was encountered
     */
    private boolean seekNewline() throws IOException, Abort {
        while (true) {
            int c = read();

            switch (c) {
                case '\n' -> {
                    // Actually, don't stop immediately. Check if a connector is next, which
                    // would annihilate the newline.
                    c = skipWhitespace();
                    if (c == '\\' && notOperator()) {
                        // Drop all whitespace after the connector.
                        unread(skipWhitespace());
                        return false;
                    } else {
                        unread(c);
                        return true;
                    }
                }
                case '/' -> {
                    if (trySkipComment()) {
                        continue;
                    }
                }
                case ' ', '\t', '\r' -> {
                    continue;
                }
                default -> {
                    if (Character.isWhitespace(c)) {
                        continue;
                    }
                }
            }

            unread(c);
            return false;
        }
    }

    /**
     * The leading `/` must have already been consumed.
     */
    private boolean trySkipComment() throws IOException, Abort {
        int peek = peek();
        if (peek == '/') {
            read();
            skipSingleLineComment();
            return true;
        }
        if (peek == '*') {
            read();
            skipMultiLineComment();
            return true;
        }
        return false;
    }

    /**
     * The leading `//` must have already been consumed.
     */
    private void skipSingleLineComment() throws IOException {
        while (true) {
            int c = read();
            if (c < 0) {
                break;
            }
            if (c == '\n') {
                unread(c);
                break;
            }
        }
    }

    /**
     * The leading `/*` must have already been consumed.
     */
    private void skipMultiLineComment() throws IOException, Abort {
        final int line = mLine;
        final int column = mColumn;

        int length = 0;

        while (true) {
            int c = read();

            if (c < 0) {
                throw new Abort(new Unclosed(line, column - 2, 2 + length, T_COMMENT));
            }

            if (c == '*' && peek() == '/') {
                read();
                break;
            }

            length++;
        }
    }

    /**
     * Returns true if the next character doesn't belong in an operator.
     */
    private boolean notOperator() throws IOException {
        return parseOperatorWord(null);
    }

    private Token parseQuoted(int type, String delimiter) throws IOException {
        final int line = mLine;
        final int column = mColumn - delimiter.length();

        mWord.setLength(0);

        int delimiterLength = delimiter.length();

        readLoop: while (true) {
            int c = read();
            if (c < 0) {
                return new Unclosed(line, column, delimiter.length() + mWord.length(), type);
            }

            mWord.append((char) c);

            final int dpos = mWord.length() - delimiterLength;

            if (dpos >= 0) {
                for (int i=0, j=dpos; i<delimiterLength; i++, j++) {
                    if (delimiter.charAt(i) != mWord.charAt(j)) {
                        continue readLoop;
                    }
                }
                mWord.setLength(dpos);
                break;
            }
        }

        String text = mWord.toString();
        int length = text.length() + (delimiter.length() * 2);

        return switch (type) {
            default -> throw new AssertionError();
            case T_IDENTIFIER -> new Identifier(line, column, length, text, true);
            case T_STRING -> new Str(line, column, length, text);
        };
    }

    private Token parseNumber(int c, boolean negate) throws IOException {
        final int column = mColumn - 1;

        mWord.setLength(0);

        boolean bin = false;
        boolean hex = false;
        boolean fp = false;
        boolean exp = false;

        altRadix: if (c == '0') {
            int peek = peek();
            if (peek == 'x' || peek == 'X') {
                hex = true;
            } else if (peek == 'b' || peek == 'B') {
                bin = true;
            } else {
                break altRadix;
            }
            read(); // skip the radix prefix
            c = read();
        }

        for (;; c = read()) {
            if (c < 0) {
                break;
            }

            switch (c) {
            case '.':
                if (!bin && !fp && !exp && peek() != '.') {
                    fp = true;
                    mWord.append((char) c);
                    continue;
                }
                break;

            case '0': case '1':
                mWord.append((char) c);
                continue;

            case '2': case '3': case '4': case '5': case '6': case '7': case '8': case '9':
                if (!bin) {
                    mWord.append((char) c);
                    continue;
                }
                break;

            case 'a': case 'b': case 'c': case 'd': case 'e': case 'f':
            case 'A': case 'B': case 'C': case 'D': case 'E': case 'F':
                if (hex) {
                    mWord.append((char) c);
                    continue;
                }
                int expChar;
                if (!bin && !exp && (c == 'e' || c == 'E') && (expChar = isExponentStart()) >= 0) {
                    fp = true;
                    exp = true;
                    mWord.append((char) c);
                    mWord.append((char) expChar);
                    continue;
                }
                break;

            case 'p': case 'P':
                if (hex && !exp && (expChar = isExponentStart()) >= 0) {
                    fp = true;
                    exp = true;
                    mWord.append((char) c);
                    mWord.append((char) expChar);
                    continue;
                }
                break;

            case '_':
                continue;
            }

            unread(c);
            break;
        }

        if (mWord.isEmpty()) {
            // Number is just 0b or 0x. Interpret it as zero instead of reporting an error.
            int prefix;
            if (bin) {
                prefix = 'b';
            } else if (hex) {
                prefix = 'x';
            } else {
                throw new AssertionError();
            }
            unread(prefix);
            return new Int32(mLine, column, 2, false, 0);
        }

        int suffix = read();

        switch (suffix) {
            case 'f', 'F' -> {
                suffix = 'f';
                fp = true;
            }

            case 'd', 'D' -> {
                suffix = 'd';
                fp = true;
            }

            case 'l', 'L' -> {
                if (fp) {
                    unread(suffix);
                    suffix = 0;
                } else {
                    suffix = 'l';
                }
            }

            case 'g', 'G' -> {
                if (fp && (bin || hex)) {
                    unread(suffix);
                    suffix = 0;
                } else {
                    suffix = 'g';
                }
            }

            default -> {
                unread(suffix);
                suffix = 0;
            }
        }

        int length = mWord.length();

        if (fp) {
            if (hex) {
                mWord.insert(0, "0x");
                if (!exp) {
                    mWord.append("p0");
                }
            }

            String str = mWord.toString();

            switch (suffix) {
                default -> {
                    double value = Double.parseDouble(str);
                    return new Float64(mLine, column, length, negate, negate ? -value : value);
                }

                case 'f' -> {
                    float value = Float.parseFloat(str);
                    return new Float32(mLine, column, length, negate, negate ? -value : value);
                }

                case 'g' -> {
                    var value = new BigDecimal(str);
                    if (negate) {
                        value = value.negate();
                    }
                    return new BigDec(mLine, column, length, negate, value);
                }
            }
        }

        int radix = bin ? 2 : (hex ? 16 : 10);

        switch (suffix) {
        default:
            try {
                int value = Integer.parseInt(mWord, 0, length, radix);
                return new Int32(mLine, column, length, negate, negate ? -value : value);
            } catch (NumberFormatException e) {
                // Fallthrough to the next case.
            }

        case 'l':
            try {
                long value = Long.parseLong(mWord, 0, length, radix);

                if (suffix != 'l' && (bin || hex) && value <= 0xffff_ffffL) {
                    // Allow unsigned forms.
                    int iv = (int) value;
                    return new Int32(mLine, column, length, negate, negate ? -iv : iv);
                }

                if (negate) {
                    value = -value;
                    if (suffix != 'l' && value == Integer.MIN_VALUE) {
                        return new Int32(mLine, column, length, true, Integer.MIN_VALUE);
                    }
                }

                return new Int64(mLine, column, length, negate, value);
            } catch (NumberFormatException e) {
                // Fallthrough to the next case.
            }

        case 'g':
            var value = new BigInteger(mWord.toString(), radix);

            if (suffix != 'g' && (bin || hex) && value.bitLength() <= 64) {
                // Allow unsigned forms.
                long lv = value.longValue();
                return new Int64(mLine, column, length, negate, negate ? -lv : lv);
            }

            if (negate) {
                value = value.negate();
                if (suffix != 'g' && value.equals(BigInteger.valueOf(Long.MIN_VALUE))) {
                    return new Int64(mLine, column, length, true, Long.MIN_VALUE);
                }
            }

            return new BigInt(mLine, column, length, negate, value);
        }
    }

    private static boolean isDigit(int c) {
        return c >= '0' && c <= '9';
    }

    /**
     * @return exponent sign character or else -1
     */
    private int isExponentStart() throws IOException {
        int c = read();
        if (c == '-' || c == '+') {
            if (isDigit(peek())) {
                return c;
            }
            unread(c);
            return -1;
        } else {
            unread(c);
            return isDigit(c) ? '+' : -1;
        }
    }

    private Token parseIdentifier(int c) throws IOException {
        final int column = mColumn - 1;

        mWord.setLength(0);
        mWord.append((char) c);

        while (true) {
            c = read();
            if (c <= 0) {
                unread(c);
                break;
            }
            if (!Character.isJavaIdentifierPart(c)) {
                unread(c);
                break;
            }
            mWord.append((char) c);
        }

        return new Token.Identifier(mLine, column, mColumn - column, mWord.toString(), false);
    }

    /**
     * @return false if word is null and any characters would need to be added to it
     */
    private boolean parseOperatorWord(StringBuilder word) throws IOException {
        int c;

        loop: while (true) {
            c = read();

            if (c <= 0) {
                break loop;
            }

            switch (c) {
                case '\n', '\r', ' ', '\t',
                    '(', ')', '{', '}', '[', ']', ',', ';', '"', '\'', '`',
                    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '$', '_',
                    'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
                    'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
                    'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
                    'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z' ->
                {
                    break loop;
                }

                case '/' -> {
                    int peek = peek();
                    if (peek == '/' || peek == '*') {
                        // Encountered a comment.
                        break loop;
                    }
                }

                default -> {
                    if (Character.isWhitespace(c) || Character.isJavaIdentifierStart(c)) {
                        break loop;
                    }
                }
            }

            if (word == null) {
                unread(c);
                return false;
            }

            word.append((char) c);
        }

        unread(c);

        return true;
    }

    private Token parseOperator(int c) throws IOException {
        final int column = mColumn - 1;

        mWord.setLength(0);
        mWord.append((char) c);

        parseOperatorWord(mWord);

        int length = mWord.length();

        l1: if (length == 1) {
            int type;

            switch (mWord.charAt(0)) {
                case '.' -> type = T_DOT;
                case ':' -> type = T_COLON;
                case '=' -> type = T_ASSIGN;
                case '?' -> type = T_QUESTION;
                case '!' -> type = T_BANG;
                case '~' -> type = T_TILDE;
                case '<' -> type = T_LT;
                case '>' -> type = T_GT;
                case '&' -> type = T_LAND;
                case '|' -> type = T_LOR;
                case '^' -> type = T_LXOR;
                case '+' -> type = T_PLUS;
                case '-' -> type = T_MINUS;
                case '*' -> type = T_MUL;
                case '/' -> type = T_DIV;
                case '%' -> type = T_REM;
                default -> {
                    break l1;
                }
            }

            return new Basic(mLine, column, 1, type);
        }

        l2: if (length == 2) {
            int type;
            int c2 = mWord.charAt(1);

            switch (mWord.charAt(0)) {
                case ':' -> {
                    if (c2 == ':') {
                        type = T_COLON_COLON;
                    } else if (c2 == '+') {
                        type = T_COLON_PLUS;
                    } else if (c2 == '*') {
                        type = T_COLON_MUL;
                    } else {
                        break l2;
                    }
                }
                case '+' -> {
                    if (c2 == '=') {
                        type = T_PLUS_A;
                    } else if (c2 == '+') {
                        type = T_INC;
                    } else {
                        break l2;
                    }
                }
                case '-' -> {
                    if (c2 == '=') {
                        type = T_MINUS_A;
                    } else if (c2 == '-') {
                        type = T_DEC;
                    } else if (c2 == '>') {
                        type = T_ARROW;
                    } else {
                        break l2;
                    }
                }
                case '=' -> {
                    if (c2 == '=') {
                        type = T_EQ;
                    } else {
                        break l2;
                    }
                }
                case '!' -> {
                    if (c2 == '=') {
                        type = T_NE;
                    } else {
                        break l2;
                    }
                }
                case '<' -> {
                    if (c2 == '=') {
                        type = T_LE;
                    } else if (c2 == '<') {
                        type = T_SHL;
                    } else {
                        break l2;
                    }
                }
                case '>' -> {
                    if (c2 == '=') {
                        type = T_GE;
                    } else if (c2 == '>') {
                        type = T_SHR;
                    } else {
                        break l2;
                    }
                }
                case '&' -> {
                    if (c2 == '=') {
                        type = T_LAND_A;
                    } else if (c2 == '&') {
                        type = T_AND;
                    } else {
                        break l2;
                    }
                }
                case '|' -> {
                    if (c2 == '=') {
                        type = T_LOR_A;
                    } else if (c2 == '|') {
                        type = T_OR;
                    } else {
                        break l2;
                    }
                }
                case '^' -> {
                    if (c2 == '=') {
                        type = T_LXOR_A;
                    } else {
                        break l2;
                    }
                }
                case '*' -> {
                    if (c2 == '=') {
                        type = T_MUL_A;
                    } else {
                        break l2;
                    }
                }
                case '/' -> {
                    if (c2 == '=') {
                        type = T_DIV_A;
                    } else {
                        break l2;
                    }
                }
                case '%' -> {
                    if (c2 == '=') {
                        type = T_REM_A;
                    } else {
                        break l2;
                    }
                }

                default -> {
                    break l2;
                }
            }

            return new Basic(mLine, column, 2, type);
        }

        String text = mWord.toString();

        l3: {
            int type;

            switch (text) {
                case ">>>" -> {
                    type = T_USHR;
                }
                case "<<=" -> {
                    type = T_SHL_A;
                }
                case ">>=" -> {
                    type = T_SHR_A;
                }
                case ">>>=" -> {
                    type = T_USHR_A;
                }

                default -> {
                    break l3;
                }
            }

            return new Basic(mLine, column, length, type);
        }

        return new Custom(mLine, column, length, text);
    }
}
