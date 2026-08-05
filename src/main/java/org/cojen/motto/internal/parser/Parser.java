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
        List<Token.Identifier> packageName = List.of();
        List<ImportDirective> imports = List.of();

        directives: while (true) {
            Token t1 = nextToken();

            if (t1 instanceof Token.Identifier id && !id.quoted) {
                String text = id.text;

                if ("import".equals(text)) {
                    List<Token.Identifier> name = tryParseQualifiedIdentifier();

                    if (name == null) {
                        errorAtNext(t1, "import path expected");
                        continue directives;
                    }

                    Token wildcard = null;

                    wildcard: {
                        Token t2 = nextToken();

                        switch (t2.type()) {
                            case T_CUSTOM_OP -> {
                                if (".*".equals(((Token.Custom) t2).text)) {
                                    wildcard = new Token.Basic
                                        (t2.line(), t2.column() + 1, 1, T_MUL);
                                    break wildcard;
                                }
                            }
                            case T_DOT -> {
                                Token t3 = nextToken();
                                if (t3.type() == T_MUL) {
                                    wildcard = t3;
                                    break wildcard;
                                }
                                pushToken(t3);
                            }
                        }

                        pushToken(t2);
                    }

                    if (imports.isEmpty()) {
                        imports = new ArrayList<>();
                    }

                    imports.add(new ImportDirective(name, wildcard));

                    continue directives;
                }

                if ("package".equals(text)) {
                    List<Token.Identifier> name = tryParseQualifiedIdentifier();
                    if (name == null) {
                        errorAtNext(t1, "package name expected");
                    }

                    if (!packageName.isEmpty()) {
                        error(t1, "package is already specified");
                    } else if (!imports.isEmpty()) {
                        error(t1, "cannot specify package after imports");
                    } else {
                        packageName = name;
                    }

                    continue directives;
                }
            }

            switch (t1.type()) {
                case T_COMMA, T_SEMI -> {
                    // Directives aren't part of a tuple, and so separators aren't required.
                    // There's no harm in providing them, so just skip and move on.
                    continue;
                }
                default -> {
                    pushToken(t1);
                    break directives;
                }
            }
        }

        List<DefinitionStatement> definitions = List.of();

        // FIXME: definitions

        return new CompilationUnit(mEnv.sourceFile(), packageName, imports, definitions);
    }

    /**
     * @return null or a non-empty list
     */
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
     * @return a non-empty list
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
