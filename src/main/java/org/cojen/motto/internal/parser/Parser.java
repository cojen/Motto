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
    /*
      Note regarding quoted identifiers:

      In some cases identifiers are interpreted as context-sensitive keywords, which in turn
      steers the parser in a specific direction. When these identifiers are quoted (using
      backticks), they're interpreted as plain identifiers. If an identifier is parsed by code
      which doesn't recognize it as a keyword, then the quotes don't make a difference.
    */

    // Defines parsing levels for rules which start with an identifier. It's mostly enforced by
    // the parseIdentifierStatement method. The level isn't recursive, and so statements which
    // reference other statements can pass along a different level.
    private static final int
        ID_BASIC          = 1, // Parse keywords, Load, CoordinateLoad, and MethodCall
        ID_NO_NEW_SYMBOLS = 2, // Parse Store and CoordinateStore
        ID_FULL           = 3; // Parse Declaration and *Definition (these define new symbols)

    private final CompilationEnv mEnv;
    private final Tokenizer mTokenizer;

    private final ArrayDeque<Token> mTokenStack;

    private DefinitionContext mContextStack;

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
        List<Identifier> packageName = List.of();
        List<ImportDirective> imports = List.of();

        directives: while (true) try {
            Token t1 = nextToken();

            if (t1 instanceof Identifier id && !id.quoted) {
                String text = id.text;

                if ("import".equals(text)) {
                    List<Identifier> name = tryParseQualifiedIdentifier();

                    if (name == null) {
                        errorAtNext(t1, "import path expected");
                        continue directives;
                    }

                    Token wildcard = null;

                    wildcard: {
                        Token t2 = nextToken();

                        switch (t2.type()) {
                            case T_CUSTOM_OP -> {
                                if (".*".equals(((Custom) t2).text)) {
                                    wildcard = new Basic(t2.line(), t2.column() + 1, 1, T_MUL);
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
                    List<Identifier> name = tryParseQualifiedIdentifier();
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
     * @param maxLabels if the statement has more labels than this, report an error and
     * skip the extra ones
     * @param which optional type of statement being parsed (used for error reporting)
     */
    private Statement parseLabeledStatement(Token t1, int maxLabels, String which)
        throws IOException, Abort
    {
        if (t1.type() == T_IDENTIFIER) {
            Token t2 = nextToken();

            if (t2.type() == T_COLON) {
                var label = (Identifier) t1;

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
        return parseStatement(which, ID_FULL);
    }

    /**
     * Parse a non-labeled statement.
     *
     * @param which optional type of statement being parsed (used for error reporting)
     * @param idLevel see ID_* constants
     */
    private Statement parseStatement(String which, int idLevel)
        throws IOException, Abort
    {
        int localErrors = 0;

        while (true) {
            Statement st = tryParseStatement(idLevel);

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

    /**
     * Try to parse a non-labeled statement.
     *
     * @param idLevel see ID_* constants
     */
    private Statement tryParseStatement(int idLevel) throws IOException, Abort {
        Statement st = tryParseBaseStatement(idLevel);

        if (st == null) {
            return null;
        }

        st = parseStatementChain(st);

        // Check if the statement is a tuple which starts a declaration or method definition,
        // unless new symbols aren't allowed.

        if (idLevel <= ID_NO_NEW_SYMBOLS || st.end() instanceof Newline) {
            return st;
        }

        // What follows needs to be a simple name (not a qualified name with dots).

        Token t = nextToken();
        TupleVarType vtype;

        check: {
            if (t.type() == T_IDENTIFIER && peekToken().type() != T_DOT) {
                if (st instanceof TupleStatement ts) {
                    vtype = new TupleVarType(ts, null);
                    break check;
                } else if (st instanceof CoordinateLoadStatement cls &&
                           cls.source instanceof TupleStatement ts)
                {
                    vtype = new TupleVarType(ts, cls.coordinates);
                    break check;
                }
            }

            pushToken(t);
            return st;
        }

        var sname = (Token.Identifier) t;

        return parseDefinitionOrDeclaration(List.of(), vtype, List.of(sname));
    }

    /**
     * Try to parse a non-labeled base statement.
     *
     * @param idLevel see ID_* constants
     */
    private Statement tryParseBaseStatement(int idLevel) throws IOException, Abort {
        Token t = nextToken();
        int tType = t.type();

        switch (tType) {
            case T_STRING, T_INT32, T_INT64, T_BIGINT, T_FLOAT32, T_FLOAT64, T_BIGDEC -> {
                return new LiteralStatement(t);
            }

            case T_IDENTIFIER -> {
                return parseIdentifierStatement((Identifier) t, idLevel);
            }

            case T_LPAREN -> {
                return parseTuple(t, T_RPAREN);
            }

            case T_LBRACE -> {
                return parseTuple(t, T_RBRACE);
            }

            case T_BANG, T_TILDE, T_PLUS, T_MINUS -> {
                if (tType != T_BANG || !peekToken().isTextOperator()) {
                    Statement source = tryParseBaseStatement(ID_BASIC);
                    if (source != null) {
                        return new PrefixStatement(t, source);
                    }
                }
            }

            case T_INC, T_DEC -> {
                Statement st = tryParsePreArith(t, tType == T_INC ? T_PLUS : T_MINUS);
                if (st != null) {
                    return st;
                }
            }

            default -> {
            }
        }

        pushToken(t);

        return null;
    }

    /**
     * Convert `++a` to `a = a + 1` or `--a` to `a = a - 1`.
     *
     * @param t T_INC or T_DEC
     */
    private Statement tryParsePreArith(Token t, int tType) throws IOException, Abort {
        // This is quite lenient. Later compilation phases must deal with it.
        Statement st = tryParseBaseStatement(ID_BASIC);

        if (st == null) {
            return null;
        }

        return new StoreStatement
            (st, new InfixStatement
             (st, new Basic(t, tType), new LiteralStatement(new Int32(t, 1))));
    }

    private Statement parseStatementChain(Statement st) throws IOException, Abort {
        while (true) {
            Token t1 = nextToken();

            switch (t1.type()) {
                default -> {
                    // The chain doesn't continue.
                    pushToken(t1);
                    return st;
                }

                case T_LBRACK -> {
                    st = new CoordinateLoadStatement(st, parseCoordinates(t1));
                }

                case T_DOT -> {
                    Token t2 = nextToken();

                    if (t2.type() != T_IDENTIFIER) {
                        // The chain doesn't continue.
                        pushToken(t2);
                        pushToken(t1);
                        return st;
                    }

                    var name = (Identifier) t2;

                    TupleStatement params;

                    Token t3 = nextToken();
                    switch (t3.type()) {
                        default -> {
                            pushToken(t3);
                            st = new FieldLoadStatement(st, name);
                            continue;
                        }
                        case T_LPAREN -> {
                            params = parseTuple(t3, T_RPAREN);
                        }
                        case T_LBRACE -> {
                            params = parseTuple(t3, T_RBRACE);
                        }
                    }

                    st = parseMethodCall(st, List.of(name), params);
                }

                case T_EQ, T_NE, T_GE, T_LT, T_LE, T_GT, T_AND, T_OR, T_LAND, T_LOR, T_LXOR,
                    T_PLUS, T_MINUS, T_MUL, T_DIV, T_REM, T_SHL, T_SHR, T_USHR ->
                {
                    st = new InfixStatement(st, t1, parseStatement("infix statement"));
                }

                case T_INC, T_DEC -> {
                    // FIXME: post T_INC, T_DEC
                    throw null;
                }

                case T_INT32, T_INT64, T_BIGINT, T_FLOAT32, T_FLOAT64, T_BIGDEC -> {
                    // Check if there's a need to convert this: `a -1` to this: `a - 1`
                    var num = (Num) t1;
                    if (!num.negated) {
                        // The chain doesn't continue.
                        pushToken(t1);
                        return st;
                    }
                    // Synthesize a minus operator.
                    t1 = new Basic(0, -1, 0, T_MINUS);
                    pushToken(num.negate());
                    st = new InfixStatement(st, t1, parseStatement("infix statement"));
                }

                case T_BANG -> {
                    Token t2 = nextToken();
                    if (t2 instanceof Identifier id && !id.quoted && id.text.equals("is")) {
                        return new IsStatement(st, (Basic) t1, parseVarType());
                    }
                    // The chain doesn't continue.
                    pushToken(t2);
                    pushToken(t1);
                    return st;
                }

                case T_IDENTIFIER -> {
                    var id = (Identifier) t1;
                    if (!id.quoted) {
                        if ("as".equals(id.text)) {
                            return new AsStatement(st, parseVarType());
                        } else if ("is".equals(id.text)) {
                            return new IsStatement(st, null, parseVarType());
                        }
                    }
                    // The chain doesn't continue.
                    pushToken(t1);
                    return st;
                }

                case T_ASSIGN -> {
                    // Assignment terminates the chain.
                    return new StoreStatement(st, parseStatement("assignment source"));
                }

                case T_LAND_A, T_LOR_A, T_LXOR_A,
                    T_PLUS_A, T_MINUS_A, T_MUL_A, T_DIV_A, T_REM_A, T_SHL_A, T_SHR_A, T_USHR_A ->
                {
                    // Convert `a += b` to `a = a + b`. Assignment terminates the chain.
                    Statement source = parseStatement("assignment source");
                    return new StoreStatement(st, new InfixStatement(st, t1, source));
                }

                case T_CUSTOM_OP -> {
                    var op = (Custom) t1;
                    String opText = op.text;

                    if (opText.indexOf('=') == (opText.length() - 1)) {
                        // Convert `a <op>= b` to `a = a <op> b`. Assignment terminates the chain.
                        Statement source = parseStatement("assignment source");
                        // Drop the "=" suffix from the operator.
                        op = new Custom(op, opText.substring(0, opText.length() - 1));
                        return new StoreStatement(st, new InfixStatement(st, op, source));
                    }

                    st = new InfixStatement(st, t1, parseStatement("infix statement"));
                }
            }
        }
    }

    private TupleStatement parseTuple(Token open) throws IOException, Abort {
        int closeTokenType = switch (open.type()) {
            case T_LPAREN -> T_RPAREN;
            case T_LBRACE -> T_RBRACE;
            case T_LBRACK -> T_RBRACK;
            default -> throw new AssertionError();
        };

        return parseTuple(open, closeTokenType);
    }

    private TupleStatement parseTuple(Token open, int closeTokenType) throws IOException, Abort {
        DefinitionContext context = mContextStack;

        if (context != null) {
            if (closeTokenType == T_RBRACE) {
                context.scopeDepth++;
            } else {
                context = null;
            }
        }

        try {
            return doParseTuple(open, closeTokenType);
        } finally {
            if (context != null) {
                context.scopeDepth--;
            }
        }
    }

    private TupleStatement doParseTuple(Token open, int closeTokenType) throws IOException, Abort {
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
                            if (!(lastToken instanceof Newline)) {
                                errorAfter(lastToken, sepMessage(','));
                            }
                        } else {
                            sequence.add(st);
                            Token lastToken = sequence.getLast().end();
                            if (!(lastToken instanceof Newline)) {
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
                        char termChar = switch (closeTokenType) {
                            case T_RPAREN -> ')';
                            case T_RBRACE -> '}';
                            default -> '\0';
                        };
                        error(t, termMessage("tuple", termChar));
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

    private List<Coordinate> tryParseCoordinates() throws IOException, Abort {
        Coordinate c1 = tryParseCoordinate();
        return c1 == null ? null : parseCoordinates(c1);
    }

    private List<Coordinate> parseCoordinates(Token lbrack) throws IOException, Abort {
        return parseCoordinates(parseCoordinate(lbrack));
    }

    private List<Coordinate> parseCoordinates(Coordinate c1) throws IOException, Abort {
        Coordinate cn = tryParseCoordinate();

        if (cn == null) {
            return List.of(c1);
        }

        var coordinates = new ArrayList<Coordinate>(4);
        coordinates.add(c1);
        coordinates.add(cn);

        while ((cn = tryParseCoordinate()) != null) {
            coordinates.add(cn);
        }

        return coordinates;
    }

    private Coordinate tryParseCoordinate() throws IOException, Abort {
        Token t = nextToken();
        if (t.type() != T_LBRACK) {
            pushToken(t);
            return null;
        }
        return parseCoordinate(t);
    }

    private Coordinate parseCoordinate(Token lbrack) throws IOException, Abort {
        List<Statement> items = new ArrayList<>(4);
        Statement nextItem = null;
        Token t;

        loop: while (true) {
            t = nextToken();

            switch (t.type()) {
                case T_COMMA -> {
                    items.add(nextItem);
                    nextItem = null;
                }

                case T_SEMI -> {
                    items.add(nextItem);
                    nextItem = null;
                    error(t, sepMessage(','));
                }

                case T_RBRACK -> {
                    break loop;
                }

                case T_RPAREN, T_RBRACE, T_EOF -> {
                    error(t, termMessage("coordinate", ']'));
                    break loop;
                }

                default -> {
                    nextItem = parseLabeledStatement(t, 0, "coordinate item");
                }
            }
        }

        if (nextItem == null && items.isEmpty()) {
            items = Coordinate.ONE_DIMENSION;
        } else {
            items.add(nextItem);
        }

        return new Coordinate(lbrack, items, t);
    }

    /**
     * @param source optional
     */
    private MethodCallStatement parseMethodCall(Statement source, List<Identifier> name,
                                                TupleStatement params)
        throws IOException, Abort
    {
        // Try to parse method call segments, stopping when a separator is seen, or if the last
        // parsed statement ends with an automatic separator.
        List<Statement> segments;
        if (params.end() instanceof Newline) {
            segments = List.of();
        } else {
            segments = parseCallSegments();
        }

        if (source == null) {
            return new MethodCallStatement(name, params, segments);
        } else {
            return new MethodCallStatement(source, simpleName(name), params, segments);
        }
    }

    private List<Statement> parseCallSegments() throws IOException, Abort {
        List<Statement> segments = List.of();

        while (true) {
            // Stop if a context-sensitive keyword is seen.
            if (peekToken().isTextOperator()) {
                break;
            }

            // Must not parse new symbols because when the statement leads with more than one
            // identifier, it consumes identifiers which should be interpreted as segment
            // names. The inability to declare or define symbols as standalone statements isn't
            // big issue, considering that in practice the symbol would be in a lone
            // inaccessible scope. If this behavior is desired, the declaration/definition must
            // be wrapped in a tuple statement.
            Statement st = tryParseStatement(ID_NO_NEW_SYMBOLS);

            if (st == null) {
                break;
            }

            if (st.end() instanceof Newline) {
                if (segments.isEmpty()) {
                    return List.of(st);
                }
                segments.add(st);
                break;
            }

            if (segments.isEmpty()) {
                segments = new ArrayList<>(4);
            }

            segments.add(st);
        }

        return segments;
    }

    /**
     * Parses a statement which leads with an identifier.
     *
     * @param first must be an identifier
     * @param idLevel see ID_* constants
     */
    private Statement parseIdentifierStatement(final Identifier first, int idLevel)
        throws IOException, Abort
    {
        List<Identifier> qname = parseQualifiedIdentifier(first);

        if (qname.size() == 1 && !first.quoted) {
            // Look for a context-sensitive keyword.

            switch (first.text) {
                case "yield" -> {
                    return new YieldStatement(first, tryParseStatement(ID_FULL));
                }

                case "return" -> {
                    return new ReturnStatement(first, tryParseStatement(ID_FULL));
                }

                case "throw" -> {
                    return new ThrowStatement(first, parseStatement("throw statement"));
                }

                case "break", "continue" -> {
                    return new JumpStatement(first, tryParseLabel(first));
                }

                case "goto" -> {
                    return new JumpStatement(first, tryParseLabel(first));
                }

                case "new" -> {
                    return parseNewStatement(first);
                }

                case "class", "interface" -> {
                    if (idLevel > ID_NO_NEW_SYMBOLS) {
                        return parseClassDefinitionStatement(List.of(), first);
                    }
                }
            }
        }

        // Stop if a context-sensitive keyword is seen, which isn't a generic identifier.
        // Assume that parseStatementChain will handle it.
        if (peekToken().isTextOperator()) {
            return new LoadStatement(qname);
        }

        /*
          qname: qualified name (can have dots)
          sname: simple name (no dots)
          vtype: ( qname | tuple ) [ coordinates ]
          ctype: class type (class or interface)

          - MethodCall             qname Tuple ...
          - CoordinateStore        qname[a] = v
          - CoordinateLoad         qname[a]
          - Store                  qname = v
          - Load                   qname
          - ClassDefinition        [ modifiers ] ctype sname UnevaluatedTuple
          - ConstructorDefinition  [ modifiers ] sname Tuple UnevaluatedTuple
          - MethodDefinition       [ modifiers ] vtype sname Tuple ...
          - Declaration            [ modifiers ] vtype sname [ = v ]

          Gather as many modifiers as possible. They must be simple unquoted identifiers, and
          they must match modifier keywords. If a class type is observed, then parse a
          ClassDefinition.

          As a side-effect, qname is updated, and it might be null if the last token
          encountered wasn't an identifier.

          If new symbols cannot be defined, modifiers aren't gathered, because they're only
          consumed by rules which generate new symbols.
        */

        List<Identifier> modifiers = List.of();

        if (idLevel > ID_NO_NEW_SYMBOLS) {
            loop: while (true) {
                if (qname.size() > 1) {
                    break loop;
                }

                Identifier id = qname.getFirst();

                if (id.quoted) {
                    break loop;
                }

                switch (id.text) {
                    default -> {
                        break loop;
                    }

                    case "public", "internal", "protected", "private", "static", "final",
                        "synchronized", "volatile", "transient", "native", "abstract", "enum",
                        "struct", "sealed", "non-sealed", "override", "macro" ->
                    {
                        if (modifiers.isEmpty()) {
                            modifiers = new ArrayList<>(4);
                        }
                        modifiers.add(id);
                    }

                    case "class", "interface" -> {
                        return parseClassDefinitionStatement(modifiers, id);
                    }
                }

                qname = tryParseQualifiedIdentifier();

                if (qname == null) {
                    break loop;
                }
            }
        }

        /*
          The following forms are still possible:

          - MethodCall             qname Tuple ...
          - CoordinateStore        qname[a] = v
          - CoordinateLoad         qname[a]
          - Store                  qname = v
          - Load                   qname
          - ConstructorDefinition  [ modifiers ] sname Tuple UnevaluatedTuple
          - MethodDefinition       [ modifiers ] vtype sname Tuple ...
          - Declaration            [ modifiers ] vtype sname [ = v ]

          If qname is null and the next token is T_LPAREN, then parse MethodDefinition or
          Declaration. This only handles the tuple case of vtype.
        */

        if (qname == null) {
            // If this point is reached, then at least one modifier was parsed. It also implies
            // that idLevel is greater than ID_NO_NEW_SYMBOLS.

            Token t = nextToken();
            int tType = t.type();

            if (tType == T_LPAREN || tType == T_LBRACE) {
                TupleStatement tuple = parseTuple(t);
                List<Coordinate> coordinates = tryParseCoordinates();

                var vtype = new TupleVarType(tuple, coordinates);

                qname = tryParseQualifiedIdentifier();

                t = nextToken();
                Statement source;

                switch (t.type()) {
                    case T_LPAREN, T_LBRACE -> {
                        qname = requireName(qname, t, "method definition name");
                        return parseMethodDefinition(modifiers, vtype, qname, parseTuple(t));
                    }
                    case T_ASSIGN -> {
                        qname = requireName(qname, t, "declaration name");
                        source = parseStatement("assignment source");
                    }
                    default -> {
                        qname = requireName(qname, t, "declaration name");
                        source = null;
                    }
                }

                Identifier name = simpleName(qname, "declaration name");

                return new DeclarationStatement(modifiers, vtype, name, source);
            }

            // Going forward, a qname is needed, so take back the last modifier.

            qname = List.of(modifiers.removeLast());

            if (modifiers.isEmpty()) {
                modifiers = List.of();
            }
        }

        List<Coordinate> coordinates = tryParseCoordinates();

        VarType vtype;

        vtype: {
            Token t = nextToken();

            TupleStatement params;            

            params: {
                switch (t.type()) {
                    case T_ASSIGN -> {
                        if (idLevel > ID_BASIC) {
                            if (!modifiers.isEmpty()) {
                                error(modifiers, "modifiers aren't allowed here");
                            }
                            Statement source = parseStatement("assignment source");
                            Statement target = new LoadStatement(qname);
                            if (coordinates != null) {
                                // Effectively becomes a CoordinateStore when combined with the
                                // StoreStatement below.
                                target = new CoordinateLoadStatement(target, coordinates);
                            }
                            return new StoreStatement(target, source);
                        }
                    }

                    case T_LPAREN, T_LBRACE -> {
                        if (coordinates == null) {
                            params = parseTuple(t);
                            break params;
                        }
                    }

                    case T_IDENTIFIER -> {
                        if (idLevel > ID_NO_NEW_SYMBOLS) {
                            vtype = new SimpleVarType(qname, coordinates);
                            qname = parseQualifiedIdentifier((Identifier) t);
                            break vtype;
                        }
                    }
                }

                pushToken(t);

                // At this point, if idLevel doesn't allow new symbols, then modifiers is empty.

                if (modifiers.isEmpty()) {
                    Statement st = new LoadStatement(qname);
                    if (coordinates != null) {
                        st = new CoordinateLoadStatement(st, coordinates);
                    }
                    return st;
                }

                // If this point is reached, idLevel must allow new symbols.

                vtype = new SimpleVarType(List.of(modifiers.removeLast()), null);

                if (modifiers.isEmpty()) {
                    modifiers = List.of();
                }

                break vtype;
            }

            if (idLevel > ID_NO_NEW_SYMBOLS) {
                Statement st = tryParseConstructorDefinition(modifiers, qname, params);
                if (st != null) {
                    return st;
                }
                if (!modifiers.isEmpty()) {
                    error(qname, "mismatched constructor name");
                }
            }

            return parseMethodCall(null, qname, params);
        }

        // If this point is reached, idLevel must allow new symbols.

        return parseDefinitionOrDeclaration(modifiers, vtype, qname);
    }

    /**
     * Parses these forms:
     *
     * - MethodDefinition       [ modifiers ] vtype sname Tuple ...
     * - Declaration            [ modifiers ] vtype sname [ = v ]
     */
    private Statement parseDefinitionOrDeclaration(List<Identifier> modifiers,
                                                   VarType vtype, List<Identifier> qname)
        throws IOException, Abort
    {
        Token t = nextToken();
        Statement source;

        switch (t.type()) {
            case T_LPAREN, T_LBRACE -> {
                return parseMethodDefinition(modifiers, vtype, qname, parseTuple(t));
            }
            case T_ASSIGN -> {
                source = parseStatement("assignment source");
            }
            default -> {
                pushToken(t);
                source = null;
            }
        }

        Identifier sname = simpleName(qname, "declaration name");

        return new DeclarationStatement(modifiers, vtype, sname, source);
    }

    private VarType parseVarType() throws IOException, Abort {
        // vtype: ( qname | tuple ) [ coordinates ]

        Token t = nextToken();

        if (t instanceof Identifier id) {
            return new SimpleVarType(parseQualifiedIdentifier(id), tryParseCoordinates());
        }

        TupleStatement tuple;

        switch (t.type()) {
            case T_LPAREN -> {
                tuple = parseTuple(t, T_RPAREN);
            }

            case T_LBRACE -> {
                tuple = parseTuple(t, T_RBRACE);
            }

            default -> {
                // Parse the rest and throw it away.
                Statement type = parseLabeledStatement(t, 0, null);
                error(type, "illegal type");
                // Create a bogus type.
                tuple = new TupleStatement(type.start(), List.of(), type.end());
            }
        }

        return new TupleVarType(tuple, tryParseCoordinates());
    }

    /**
     * @param required when non-null, an error is reported if no statement could be parsed
     */
    private Identifier tryParseLabel(Token required) throws IOException, Abort {
        Statement st = tryParseStatement(ID_FULL);

        if (st instanceof LoadStatement ls) {
            return simpleName(ls.path, "label");
        }

        if (st != null) {
            error(st, "illegal label");
        } else if (required != null) {
            errorAtNext(required, "label expected");
        }

        return null;
    }

    /**
     * If the format is wrong, an error is reported and an incorrect statement is returned.
     */
    private Statement parseNewStatement(Identifier newKeyword) throws IOException, Abort {
        Statement st = parseStatement("new statement");
        Statement newSt = tryConvertToNewStatement(st);
        if (newSt != null) {
            return newSt;
        }
        error(newKeyword, st.end(), "invalid new statement");
        return st;
    }

    private Statement tryConvertToNewStatement(Statement st) throws IOException, Abort {
        if (st instanceof MethodCallStatement what) {
            Statement source = what.source;

            if (source != null) {
                if (what.path.size() == 1) {
                    Identifier name = what.path.getFirst();
                    Statement newSource = tryConvertToNewStatement(source);
                    if (newSource != null) {
                        return new MethodCallStatement(newSource, name, what.params, what.segments);
                    }
                }
            } else {
                List<Statement> segments = what.segments;
                if (segments.isEmpty()) {
                    return new NewStatement(what.path, what.params);
                }
                if (segments.size() == 1) {
                    Statement seg = segments.getFirst();
                    if (seg instanceof TupleStatement code && code.isUnevaluated()) {
                        return new NewClassDefinitionStatement(what.path, what.params, code);
                    }
                }
            }
        } else if (st instanceof CoordinateLoadStatement what) {
            if (what.source instanceof LoadStatement ls) {
                TupleStatement values;
                {
                    Token t = nextToken();
                    values = switch (t.type()) {
                        case T_LPAREN -> parseTuple(t, T_RPAREN);
                        case T_LBRACE -> parseTuple(t, T_RBRACE);
                        default -> {
                            pushToken(t);
                            yield null;
                        }
                    };
                }

                var base = new SimpleVarType(ls.path, null);
                return new NewArrayStatement(base, what.coordinates, values);
            }
        }

        return null;
    }

    /**
     * @param ctype "class" or "interface"
     */
    private ClassDefinitionStatement parseClassDefinitionStatement
        (List<Identifier> modifiers, Identifier ctype) throws IOException, Abort
    {
        List<Identifier> cname = tryParseQualifiedIdentifier();

        if (cname == null) {
            errorAtNext(ctype, "class name expected");
            cname = List.of(ctype);
        }

        List<Clause> clauses = parseClauses();

        pushDefinitionContext(cname, DefinitionContext.T_CLASS);

        try {
            Identifier sname = simpleName(cname, "class name");
            TupleStatement code = codeScope(parseStatement("class definition", ID_BASIC));
            return new ClassDefinitionStatement(modifiers, ctype, sname, clauses, code);
        } finally {
            popDefinitionContext();
        }
    }

    private Statement parseMethodDefinition(List<Identifier> modifiers, VarType returnType,
                                            List<Identifier> qname, TupleStatement params)
        throws IOException, Abort
    {
        Identifier sname = simpleName(qname, "method name");

        List<DefinitionSegment> segments = List.of();

        while (true) {
            DefinitionSegment seg = tryParseDefinitionSegment();

            if (seg == null) {
                break;
            }

            if (segments.isEmpty()) {
                segments = new ArrayList<>(4);
            }

            segments.add(seg);
        }

        List<Clause> clauses = parseClauses();

        TupleStatement code;

        if (params.end() instanceof Newline nl) {
            errorAfter(nl, "an explicit `;` terminator is required when no code is provided");
            code = null;
        } else {
            pushDefinitionContext(qname, DefinitionContext.T_METHOD);

            try {
                Token peek = peekToken();

                switch (peek.type()) {
                    default -> {
                        code = codeScope(parseStatement("method definition", ID_BASIC));
                    }
                    case T_COMMA, T_SEMI -> {
                        code = null;
                    }
                }
            } finally {
                popDefinitionContext();
            }
        }

        return new MethodDefinitionStatement
            (modifiers, sname, clauses, code, returnType, params, segments);
    }

    private DefinitionSegment tryParseDefinitionSegment() throws IOException, Abort {
        Token repToken = nextToken();
        int repetition;

        switch (repToken.type()) {
            default -> {
                pushToken(repToken);
                return null;
            }
            case T_COLON_COLON -> { // once
                repetition = -1;
            }
            case T_COLON_MUL -> { // zero or more
                repetition = 0;
            }
            case T_COLON_PLUS -> { // one or more
                repetition = 1;
            }
        }

        Token t = nextToken();

        Identifier name = null;

        if (t.type() == T_IDENTIFIER) {
            name = (Identifier) t;
            t = nextToken();
        }

        TupleStatement params;

        switch (t.type()) {
            case T_LPAREN -> {
                params = parseTuple(t, T_RPAREN);
            }

            case T_LBRACE -> {
                params = parseTuple(t, T_RBRACE);
            }

            default -> {
                params = null;
                error(t, "illegal parameter type");
            }
        }

        return new DefinitionSegment(repetition, name, params);
    }

    private ConstructorDefinitionStatement tryParseConstructorDefinition
        (List<Identifier> modifiers, List<Identifier> qname, TupleStatement params)
        throws IOException, Abort
    {
        DefinitionContext ctx = mContextStack;

        if (ctx == null || ctx.scopeDepth != 1 ||
            ctx.type != DefinitionContext.T_CLASS || !qname.equals(ctx.qname))
        {
            // Note that the constructor name check is applied early. Otherwise `int a() {}`
            // would look like a constructor definition with an `int` modifier. Most likely,
            // it's a method which returns `int`.
            return null;
        }

        List<Clause> clauses = parseClauses();

        if (peekToken().type() != T_LBRACE && clauses.isEmpty()) {
            return null;
        }

        Identifier sname = simpleName(qname, "constructor name");

        pushDefinitionContext(qname, DefinitionContext.T_CONSTRUCTOR);

        try {
            TupleStatement code = codeScope(parseStatement("constructor definition", ID_BASIC));
            return new ConstructorDefinitionStatement(modifiers, sname, clauses, code, params);
        } finally {
            popDefinitionContext();
        }
    }

    private List<Clause> parseClauses() throws IOException {
        Clause clause = tryParseClause();

        if (clause == null) {
            return List.of();
        }

        var clauses = new ArrayList<Clause>(4);

        do {
            clauses.add(clause);
        } while ((clause = tryParseClause()) != null);

        return clauses;
    }

    private Clause tryParseClause() throws IOException {
        List<Identifier> qual = tryParseQualifiedIdentifier();

        if (qual == null) {
            return null;
        }

        Identifier kind = simpleName(qual, "clause");

        var items = new ArrayList<List<Identifier>>(4);

        while (true) {
            List<Identifier> item = tryParseQualifiedIdentifier();
            if (item == null) {
                errorAfter(kind, "identifier required");
                break;
            }
            items.add(item);
            Token t = nextToken();
            if (t.type() != T_COMMA) {
                pushToken(t);
                break;
            }
        }

        return new Clause(kind, items);
    }

    /**
     * @return null or a non-empty list
     */
    private List<Identifier> tryParseQualifiedIdentifier() throws IOException {
        Token t = nextToken();
        if (t instanceof Identifier first) {
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
    private List<Identifier> parseQualifiedIdentifier(Identifier first)
        throws IOException
    {
        ArrayList<Identifier> list;

        quick: {
            Token t1 = nextToken();
            if (t1.type() == T_DOT) {
                Token t2 = nextToken();
                if (t2 instanceof Identifier id) {
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
                if (t2 instanceof Identifier id) {
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
                String which = switch (((Unclosed) t1).unclosedType) {
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
     * Is used to determine if a method definition is a constructor, by comparing the name to
     * an enclosing class name.
     *
     * @param qname name of the definition being parsed
     * @param type DefinitionContext T_* type
     */
    private void pushDefinitionContext(List<Identifier> qname, int type) {
        mContextStack = new DefinitionContext(mContextStack, qname, type);
    }

    private void popDefinitionContext() {
        mContextStack = mContextStack.prev;
    }

    private static final class DefinitionContext {
        static final int T_CLASS = 1, T_CONSTRUCTOR = 2, T_METHOD = 3;

        final DefinitionContext prev;
        final List<Identifier> qname;
        final int type;

        // Counts the number of times a code scope tuple has been entered.
        int scopeDepth;

        DefinitionContext(DefinitionContext prev, List<Identifier> qname, int type) {
            this.prev = prev;
            this.qname = qname;
            this.type = type;
        }
    }

    /**
     * Returns the first item from the qualified name. An error is reported if the qualified
     * name has more than one identifier.
     */
    private Identifier simpleName(List<Identifier> qualified) {
        return simpleName(qualified, "name");
    }

    /**
     * Returns the first item from the qualified name. An error is reported if the qualified
     * name has more than one identifier.
     *
     * @param which type of name to report in the error
     */
    private Identifier simpleName(List<Identifier> qualified, String which) {
        Identifier name = qualified.getFirst();

        if (qualified.size() > 1) {
            error(name, qualified.getLast(), which + " must be simple (no dots)");
        }

        return name;
    }

    /**
     * Returns the given name if not null, or else reports an error and returns a fake name.
     *
     * @param which type of name which is required
     * @return a non-null name list
     */
    private List<Identifier> requireName(List<Identifier> name, Token t, String which) {
        if (name != null) {
            return name;
        }
        error(t, which + " expected");
        return List.of(new Identifier(t.line(), t.column(), 0, "", false));
    }

    /**
     * Returns the given statement if it's an unevaluated TupleStatement, or else report an
     * error and return null.
     */
    private TupleStatement codeScope(Statement st) {
        if (st instanceof TupleStatement tuple && tuple.isUnevaluated()) {
            return tuple;
        } else {
            error(st, "code scope required");
            return null;
        }
    }

    /**
     * @param sepChar expected separator character
     */
    private static String sepMessage(char sepChar) {
        return "expected a `" + sepChar + "` separator";
    }

    /**
     * @param which type of statement being parsed
     * @param termChar expected terminator character (pass 0 if unspecified)
     */
    private static String termMessage(String which, char termChar) {
        String message = "incorrect " + which + " terminator";
        if (termChar != 0) {
            message += " (expected a `" + termChar + "` character)";
        }
        return message;
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
