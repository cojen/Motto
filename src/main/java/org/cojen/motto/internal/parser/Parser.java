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

        directives: while (true) try {
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
                    continue directives;
                }
                default -> {
                    pushToken(t1);
                    break directives;
                }
            }
        } catch (IOException e) {
            if (numErrors() == 0) {
                throw e;
            }
        }

        List<DefinitionStatement> definitions = List.of();

        try {
            definitions = parseDefinitions();
        } catch (Abort e) {
            // Assume an error was reported.
        } catch (IOException e) {
            if (numErrors() == 0) {
                throw e;
            }
        }

        return new CompilationUnit(mEnv.sourceFile(), packageName, imports, definitions);
    }

    private List<DefinitionStatement> parseDefinitions() throws IOException, Abort {
        var definitions = new ArrayList<DefinitionStatement>(1);

        outer: while (true) {
            Token t = nextToken();

            switch (t.type()) {
                case T_EOF -> {
                    return definitions;
                }

                case T_COMMA, T_SEMI -> {
                    continue outer;
                }

                default -> {
                    Statement st = parseLabeledStatement(t, 0, "definition");
                    if (st instanceof DefinitionStatement def) {
                        definitions.add(def);
                    } else {
                        error(st, "must be a method or class definition");
                    }
                }
            }
        }
    }

    /**
     * Parse an optionally labeled statement.
     *
     * @param int maxLabels if the statement has more labels than this, report an error and
     * skip the extra ones
     * @param which optional type of statement being parsed (used for error reporting)
     */
    private Statement parseLabeledStatement(Token t1, int maxLabels, String which)
        throws IOException, Abort
    {
        if (t1.type() == T_IDENTIFIER) {
            Token t2 = nextToken();

            if (t2.type() == T_COLON) {
                var label = (Token.Identifier) t1;

                if (maxLabels <= 0) {
                    error(label, "label isn't allowed");
                }

                Statement st;
                Token t3 = nextToken();

                switch (t3.type()) {
                    case T_RPAREN, T_RBRACE, T_COMMA, T_SEMI -> {
                        pushToken(t3);
                        st = new EmptyStatement(t2);
                    }
                    default -> {
                        st = parseLabeledStatement(t3, maxLabels - 1, which);
                    }
                }

                return maxLabels <= 0 ? st : new LabeledStatement(label, st);
            }

            pushToken(t2);
        }

        pushToken(t1);

        return parseStatement(which);
    }

    /**
     * Parse a non-labeled statement.
     *
     * @param which optional type of statement being parsed (used for error reporting)
     */
    private Statement parseStatement(String which) throws IOException, Abort {
        int localErrors = 0;

        while (true) {
            Statement st = tryParseStatement();

            if (st != null) {
                return st;
            }

            Token t = nextToken();

            if (t.type() == T_EOF) {
                if (numErrors() == 0) {
                    error(t, "reached end of file while parsing");
                }
                throw new Abort(t);
            }

            if (localErrors++ == 0) {
                if (isDifferentErrorPosition(t)) {
                    String message = "unexpected token";
                    if (which != null) {
                        message = message + " while parsing " + which;
                    }
                    error(t, message);
                }
            }
        }
    }

    private Statement tryParseStatement() throws IOException, Abort {
        Statement st = tryParseBaseStatement();
        if (st != null) {
            st = tryParseStatementChain(st);
        }
        return st;
    }

    private Statement tryParseStatementChain(Statement st) throws IOException, Abort {
        while (true) {
            Statement chained = tryParseChainedStatement(st);
            if (chained == null) {
                break;
            }
            st = chained;
        }
        return st;
    }

    private Statement tryParseChainedStatement(Statement st) throws IOException, Abort {
        // FIXME: chained
        return null;
    }

    private Statement tryParseBaseStatement() throws IOException, Abort {
        Token t1 = nextToken();

        switch (t1.type()) {
            case T_STRING, T_INT32, T_INT64, T_BIGINT, T_FLOAT32, T_FLOAT64, T_BIGDEC -> {
                return new LiteralStatement(t1);
            }

            case T_IDENTIFIER -> {
                // FIXME: parseIdentifierStatement
                //return parseIdentifierStatement((Token.Identifier) t1);
                throw null;
            }

            case T_LPAREN -> {
                return parseTuple(t1, Token.T_RPAREN);
            }

            case T_LBRACE -> {
                return parseTuple(t1, Token.T_RBRACE);
            }

            // FIXME: T_INC, T_DEC

            case T_BANG, T_TILDE, T_PLUS, T_MINUS -> {
                return new PrefixStatement(t1, parseStatement("prefix statement"));
            }
        }

        pushToken(t1);

        return null;
    }

    private TupleStatement parseTuple(Token open, int closeTokenType) throws IOException, Abort {
        List<Statement> statements = new ArrayList<>(4);

        Statement st = null;
        List<Statement> sequence = null;

        while (true) {
            Token t = nextToken();
            int tType = t.type();

            switch (tType) {
                default -> {
                    if (st != null) {
                        // Check if the statement can be automatically separated.

                        if (sequence == null) {
                            statements.add(st);

                            Token lastToken = st.end();
                            if (!(lastToken instanceof Token.Newline)) {
                                errorAfter(lastToken, sepMessage(','));
                            }
                        } else {
                            sequence.add(st);
                            Token lastToken = sequence.getLast().end();
                            if (!(lastToken instanceof Token.Newline)) {
                                String message;
                                if (statements.isEmpty()) {
                                    message = sepMessage(';');
                                } else {
                                    message = "expected a `;` or ',' separator";
                                }
                                errorAfter(lastToken, message);
                            }
                        }
                    }

                    st = parseLabeledStatement(t, Integer.MAX_VALUE, null);
                }

                case T_COMMA -> {
                    if (sequence == null) {
                        if (st == null) {
                            st = new EmptyStatement(t);
                        }
                    } else {
                        if (st != null) {
                            sequence.add(st);
                        }
                        st = toSequenceStatement(sequence);
                        sequence = null;
                    }
                    statements.add(st);
                    st = null;
                }

                case T_SEMI -> {
                    if (sequence == null) {
                        sequence = new ArrayList<Statement>(4);
                    }
                    if (st == null) {
                        st = new EmptyStatement(t);
                    }
                    sequence.add(st);
                    st = null;
                }

                case T_RPAREN, T_RBRACE, T_RBRACK, T_EOF -> {
                    if (tType != closeTokenType) {

                        String message = "incorrect tuple terminator";

                        detail: {
                            char c;
                            if (closeTokenType == T_RPAREN) {
                                c = ')';
                            } else if (closeTokenType == T_RBRACE) {
                                c = '}';
                            } else {
                                break detail;
                            }

                            message = message + " (expected a `" + c + "` character)";
                        }

                        error(t, message);
                    }

                    if (sequence != null) {
                        if (st != null) {
                            sequence.add(st);
                        }
                        statements.add(toSequenceStatement(sequence));
                    } else if (st != null) {
                        statements.add(st);
                    } else if (statements.isEmpty()) {
                        statements = List.of();
                    }

                    return new TupleStatement(open, statements, t);
                }
            }
        }
    }

    private static Statement toSequenceStatement(List<Statement> sequence) {
        return sequence.size() == 1 ? sequence.getFirst() : new SequenceStatement(sequence);
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

    /**
     * @param type expected separator type
     */
    private static String sepMessage(char type) {
        return "expected a `" + type + "` separator";
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

    /**
     * @return true if the token is positioned outside the range of the last error, or if the
     * last error is null
     */
    private boolean isDifferentErrorPosition(Element e) {
        CompileError last = mLastError;
        return last == null
            || e.start().line() < last.startLine()
            || e.end().line() > last.endLine()         // endLine is inclusive
            || e.start().column() < last.startColumn()
            || e.end().column() >= last.endColumn();   // endColumn is exclusive
    }
}
