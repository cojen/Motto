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

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SequencedCollection;

import org.cojen.motto.internal.compiler.CompilationEnv;
import org.cojen.motto.internal.compiler.CompileError;

import static org.cojen.motto.internal.parser.Token.*;

/**
 * 
 *
 * @author Brian S. O'Neill
 */
public final class Parser implements Closeable {
    /* Note regarding quoted identifiers:

       In some cases identifiers are interpreted as context-sensitive keywords, which in turn
       steers the parser in a specific direction. When these identifiers are quoted (using
       backticks), they're interpreted as plain identifiers. If an identifier is parsed by code
       which doesn't recognize it as a keyword, then the quotes don't make a difference.
     */

    private final CompilationEnv mEnv;
    private final Tokenizer mTokenizer;

    private final ArrayDeque<Token> mTokenStack;

    private CompileError mLastError;

    public Parser(CompilationEnv env) throws FileNotFoundException {
        this(env, new BufferedReader(new FileReader(env.sourceFile())));
    }

    public Parser(CompilationEnv env, Reader in) {
        this(env, new Tokenizer(in));
    }

    Parser(CompilationEnv env, Tokenizer tokenizer) {
        mEnv = Objects.requireNonNull(env);
        mTokenizer = tokenizer;
        mTokenStack = new ArrayDeque<>(4);
    }

    @Override
    public void close() throws IOException {
        mTokenizer.close();
    }

    public int numErrors() {
        return mEnv.numErrors();
    }

    /**
     * Always returns a non-null CompilationUnit. If numErrors returns a non-zero value, then
     * the CompilationUnit should be considered to be broken.
     */
    public CompilationUnit parse() throws IOException {
        // FIXME
        throw null;
    }

    private List<Token.Identifier> tryParseQualifiedIdentifier() throws IOException {
        Token t = nextToken();
        if (t instanceof Token.Identifier first) {
            return parseQualifiedIdentifier(first);
        } else {
            pushToken(t);
            return null;
        }
    }

    /**
     * @param first must be an identifier
     */
    private List<Token.Identifier> parseQualifiedIdentifier(Token.Identifier first)
        throws IOException
    {
        ArrayList<Token.Identifier> list;

        quick: {
            Token t1 = nextToken();
            if (t1.type() == T_DOT) {
                Token t2 = nextToken();
                if (t2 instanceof Token.Identifier id) {
                    list = new ArrayList<>(4);
                    list.add(first);
                    list.add(id);
                    break quick;
                }
                pushToken(t2);
            }
            pushToken(t1);
            return List.of(first);
        }

        while (true) {
            Token t1 = nextToken();
            if (t1.type() == T_DOT) {
                Token t2 = nextToken();
                if (t2 instanceof Token.Identifier id) {
                    list.add(id);
                    continue;
                }
                pushToken(t2);
            }
            pushToken(t1);
            return list;
        }
    }

    private Token nextToken() throws IOException {
        while (true) {
            Token t1 = mTokenStack.pollFirst();

            if (t1 == null) {
                t1 = mTokenizer.next();
            }

            if (t1.type() == T_UNCLOSED) {
                String which = switch (((Token.Unclosed) t1).unclosedType) {
                    case T_IDENTIFIER -> "quoted identifier";
                    case T_STRING -> "string";
                    case T_COMMENT -> "multiline comment";
                    default -> "token";
                };

                error(t1, "unclosed " + which + " at end of file");
                continue;
            }

            return t1;
        }
    }

    private void pushToken(Token t) {
        mTokenStack.addFirst(t);
    }

    private Token peekToken() throws IOException {
        Token t = nextToken();
        pushToken(t);
        return t;
    }

    private void error(Element element, String message) {
        error(new CompileError(element, message));
    }

    private void error(Token start, Token end, String message) {
        error(new CompileError(start, end, message));
    }

    private void error(SequencedCollection<? extends Element> elements, String message) {
        error(new CompileError(elements, message));
    }

    private void error(CompileError error) {
        mLastError = error;
        mEnv.error(error);
    }

    /**
     * Report an error at a single column immediately after the given token.
     */
    private void errorAfter(Token token, String message) {
        int line = token.line();
        int column = token.column() + token.length();
        error(new CompileError(line, column, line, column, message));
    }

    /**
     * Report an error at the first column of the next token. If the next token is on another
     * line, then report an error immediately after the given token. When calling this method,
     * any tokens to be pushed back should already have been pushed back.
     */
    private void errorAtNext(Token token, String message) throws IOException {
        int line = token.line();
        Token peek = peekToken();
        if (peek.line() == line) {
            error(new CompileError(line, peek.column(), line, peek.column(), message));
        } else {
            errorAfter(token, message);
        }
    }
}
