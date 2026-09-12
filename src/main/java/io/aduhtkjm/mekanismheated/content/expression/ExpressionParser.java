package io.aduhtkjm.mekanismheated.content.expression;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Compiles a temperature-controller expression into an {@link Expr} tree.
 *
 * <h2>Lexical structure</h2>
 * <pre>
 * NUMBER   := [0-9]+ ( "." [0-9]+ )?
 * IDENT    := [A-Za-z_][A-Za-z0-9_]* ( "." [A-Za-z_][A-Za-z0-9_]* )*
 * OPERATOR := "+" | "-" | "*" | "/" | "&gt;" | "&lt;" | "!=" | "==" | "=" | "?" | ":" | "(" | ")"
 * </pre>
 * Whitespace is skipped between tokens. There is no scientific notation, no hex, no strings, no {@code &&}/{@code ||},
 * and no comments; every number is a {@code double}.
 *
 * <p>Because {@code .} is part of an identifier when an identifier character follows it, {@code north.T} is a single
 * token. Since there are no user-defined variables, this needs no lookahead beyond that one character, and it is
 * also why a stray dot ({@code foo.}, {@code foo..bar}, {@code .5}) is a lex error rather than a field access.
 *
 * <h2>Grammar</h2>
 * <pre>
 * expression     := ternary EOF
 * ternary        := comparison ( "?" expression ":" ternary )?      // right-associative
 * comparison     := additive ( ("&gt;" | "&lt;" | "=" | "==" | "!=") additive )?
 * additive       := multiplicative ( ("+" | "-") multiplicative )*
 * multiplicative := unary ( ("*" | "/") unary )*
 * unary          := "-" unary | primary
 * primary        := NUMBER | IDENT | "(" expression ")"
 * </pre>
 * Deliberate choices:
 * <ul>
 *   <li>Parentheses are supported even though the original operator list did not include them; without grouping a
 *       ternary cannot be composed with anything.</li>
 *   <li>Ternary binds loosest and nests to the right, so {@code a ? b : c ? d : e} is {@code a ? b : (c ? d : e)}.</li>
 *   <li>Comparisons are non-associative: {@code comparison} accepts at most one comparison operator, so
 *       {@code 1 < 2 < 3} is a parse error instead of silently becoming {@code (1 < 2) < 3}.</li>
 *   <li>Unary minus binds tighter than {@code *}, so {@code -2 * 3} is {@code (-2) * 3}.</li>
 * </ul>
 *
 * <h2>Variables</h2>
 * The built-in names are {@link Side#AMBIENT_VARIABLE} and the six {@link Side#variable() side variables}. The set is
 * passed in as a predicate so the parser stays independent of the world; {@link #parse(String)} uses the real set.
 *
 * <h2>Bounding the recursion</h2>
 * Two bounds keep the parser total — able to return a result or a {@link ExpressionParseException} for <em>any</em>
 * input, never an {@link Error}:
 * <ul>
 *   <li>{@link #MAX_DEPTH} bounds the parser's own recursion, which grows with nesting.</li>
 *   <li>{@link #MAX_NODES} bounds the size of the tree it builds. This is not redundant: evaluation recurses over the
 *       tree, so a very long but completely flat chain such as {@code 1 + 1 + 1 + …} would otherwise produce a deep
 *       left-nested tree and overflow the stack in {@link Expr#evaluate}, and even {@link Object#toString()} on the
 *       result would recurse that far.</li>
 * </ul>
 * This matters because an {@link Error} slips past the {@code catch (ExpressionRuntimeException)} the controller uses
 * around evaluation, which would take down a server tick. The tile also caps the stored text (see
 * {@code Config.TemperatureController}), but this class does not rely on that.
 */
public final class ExpressionParser {

    /**
     * Maximum parser recursion depth.
     *
     * <p>This counts parser levels rather than user-visible nesting: a parenthesised sub-expression costs two (one
     * {@code ternary} plus one {@code unary}), while a flat {@code 1 + 1 + 1 …} chain costs a constant amount because
     * the additive and multiplicative rules loop instead of recursing. The default 256-character expression cap can
     * therefore reach at most about 258 units (127 nested parentheses), so 512 leaves headroom while staying far below
     * any realistic stack limit: one unit is roughly seven stack frames, so this allows a few thousand frames.
     *
     * <p>The point of the bound is that <em>no</em> input, however long, can turn into a {@link StackOverflowError}.
     */
    public static final int MAX_DEPTH = 512;

    /**
     * Maximum number of syntax-tree nodes an expression may contain.
     *
     * <p>Evaluation recurses over the tree, so this bounds the evaluation stack as well as the size of the result. A
     * flat {@code 1 + 1 + 1 …} chain costs one node per literal plus one per operator, so the default 256-character
     * expression cap can reach roughly 256 nodes; 2048 leaves eight times the headroom. See {@link ParseErrorKind#TOO_LARGE}.
     */
    public static final int MAX_NODES = 2048;

    /**
     * Every identifier an expression may contain: {@code T} and {@code north.T} … {@code down.T}.
     */
    public static final Set<String> BUILT_IN_VARIABLES = builtInVariables();

    private ExpressionParser() {
    }

    private static Set<String> builtInVariables() {
        Set<String> names = new LinkedHashSet<>();
        names.add(Side.AMBIENT_VARIABLE);
        for (Side side : Side.values()) {
            names.add(side.variable());
        }
        return Set.copyOf(names);
    }

    /**
     * Compiles an expression, accepting the real built-in variables.
     *
     * @param source the expression text; must not be {@code null} (an empty or blank string is a legitimate input that
     *               fails with {@link ParseErrorKind#UNEXPECTED_END})
     *
     * @throws ExpressionParseException when the source is empty, malformed, or uses a name that is not built in
     */
    public static Expr parse(String source) throws ExpressionParseException {
        return parse(source, BUILT_IN_VARIABLES::contains);
    }

    /**
     * Compiles an expression against a caller-supplied set of legal variable names.
     *
     * @param isVariable returns whether an identifier is a legal variable; identifiers it rejects produce
     *                   {@link ParseErrorKind#UNKNOWN_VARIABLE}
     */
    public static Expr parse(String source, Predicate<String> isVariable) throws ExpressionParseException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(isVariable, "isVariable");
        Parser parser = new Parser(Lexer.tokenize(source), isVariable);
        Expr expression = parser.expression();
        Token trailing = parser.peek();
        if (trailing.type() != TokenType.EOF) {
            throw parser.error(ParseErrorKind.UNEXPECTED_TOKEN, trailing);
        }
        return expression;
    }

    private enum TokenType {
        NUMBER,
        IDENT,
        PLUS,
        MINUS,
        STAR,
        SLASH,
        GREATER,
        LESS,
        EQUAL,
        NOT_EQUAL,
        QUESTION,
        COLON,
        OPEN_PAREN,
        CLOSE_PAREN,
        EOF
    }

    private record Token(TokenType type, String text, double number, int column) {

        static Token symbol(TokenType type, char character, int column) {
            return new Token(type, String.valueOf(character), 0.0D, column);
        }
    }

    /**
     * Turns the source into a token list terminated by a single {@link TokenType#EOF}. Columns are zero-based.
     */
    private static final class Lexer {

        private Lexer() {
        }

        static List<Token> tokenize(String source) throws ExpressionParseException {
            List<Token> tokens = new ArrayList<>();
            int length = source.length();
            int index = 0;
            while (index < length) {
                char character = source.charAt(index);
                if (isWhitespace(character)) {
                    index++;
                    continue;
                }
                int column = index;
                if (isDigit(character)) {
                    index = readNumber(source, index, length);
                    String text = source.substring(column, index);
                    tokens.add(new Token(TokenType.NUMBER, text, Double.parseDouble(text), column));
                    continue;
                }
                if (isIdentifierStart(character)) {
                    index = readIdentifier(source, index, length);
                    tokens.add(new Token(TokenType.IDENT, source.substring(column, index), 0.0D, column));
                    continue;
                }
                switch (character) {
                    case '+' -> tokens.add(Token.symbol(TokenType.PLUS, character, column));
                    case '-' -> tokens.add(Token.symbol(TokenType.MINUS, character, column));
                    case '*' -> tokens.add(Token.symbol(TokenType.STAR, character, column));
                    case '/' -> tokens.add(Token.symbol(TokenType.SLASH, character, column));
                    case '>' -> tokens.add(Token.symbol(TokenType.GREATER, character, column));
                    case '<' -> tokens.add(Token.symbol(TokenType.LESS, character, column));
                    case '?' -> tokens.add(Token.symbol(TokenType.QUESTION, character, column));
                    case ':' -> tokens.add(Token.symbol(TokenType.COLON, character, column));
                    case '(' -> tokens.add(Token.symbol(TokenType.OPEN_PAREN, character, column));
                    case ')' -> tokens.add(Token.symbol(TokenType.CLOSE_PAREN, character, column));
                    case '!' -> {
                        if (index + 1 >= length || source.charAt(index + 1) != '=') {
                            //A lone '!' is not an operator in this language.
                            throw new ExpressionParseException(ParseErrorKind.UNEXPECTED_CHARACTER, column, "!");
                        }
                        tokens.add(new Token(TokenType.NOT_EQUAL, "!=", 0.0D, column));
                        index++;
                    }
                    case '=' -> {
                        if (index + 1 < length && source.charAt(index + 1) == '=') {
                            //'==' and '=' are the same operator; keep the canonical spelling for error text.
                            tokens.add(new Token(TokenType.EQUAL, "=", 0.0D, column));
                            index++;
                        } else {
                            tokens.add(Token.symbol(TokenType.EQUAL, character, column));
                        }
                    }
                    default -> throw new ExpressionParseException(ParseErrorKind.UNEXPECTED_CHARACTER, column,
                          String.valueOf(character));
                }
                index++;
            }
            tokens.add(new Token(TokenType.EOF, "", 0.0D, length));
            return tokens;
        }

        /** Reads {@code [0-9]+ ("." [0-9]+)?}; a trailing dot with no digit after it is left for the main loop. */
        private static int readNumber(String source, int index, int length) {
            while (index < length && isDigit(source.charAt(index))) {
                index++;
            }
            if (index + 1 < length && source.charAt(index) == '.' && isDigit(source.charAt(index + 1))) {
                index++;
                while (index < length && isDigit(source.charAt(index))) {
                    index++;
                }
            }
            return index;
        }

        /** Reads {@code [A-Za-z_][A-Za-z0-9_]* ("." [A-Za-z_][A-Za-z0-9_]*)*}. */
        private static int readIdentifier(String source, int index, int length) {
            index = readIdentifierPart(source, index, length);
            while (index + 1 < length && source.charAt(index) == '.' && isIdentifierStart(source.charAt(index + 1))) {
                index = readIdentifierPart(source, index + 1, length);
            }
            return index;
        }

        private static int readIdentifierPart(String source, int index, int length) {
            index++;
            while (index < length && isIdentifierPart(source.charAt(index))) {
                index++;
            }
            return index;
        }

        private static boolean isWhitespace(char character) {
            return character == ' ' || character == '\t' || character == '\n' || character == '\r';
        }

        private static boolean isDigit(char character) {
            return character >= '0' && character <= '9';
        }

        private static boolean isIdentifierStart(char character) {
            return character >= 'a' && character <= 'z' || character >= 'A' && character <= 'Z' || character == '_';
        }

        private static boolean isIdentifierPart(char character) {
            return isIdentifierStart(character) || isDigit(character);
        }
    }

    private static final class Parser {

        private final List<Token> tokens;
        private final Predicate<String> isVariable;
        private int index;
        private int depth;
        private int nodes;

        Parser(List<Token> tokens, Predicate<String> isVariable) {
            this.tokens = tokens;
            this.isVariable = isVariable;
        }

        Token peek() {
            return tokens.get(index);
        }

        private Token previous() {
            return tokens.get(index - 1);
        }

        private boolean check(TokenType type) {
            return peek().type() == type;
        }

        private boolean match(TokenType... types) {
            for (TokenType type : types) {
                if (check(type)) {
                    index++;
                    return true;
                }
            }
            return false;
        }

        ExpressionParseException error(ParseErrorKind kind, Token token) {
            return new ExpressionParseException(kind, token.column(),
                  token.type() == TokenType.EOF ? "" : token.text());
        }

        private Token expect(TokenType type) throws ExpressionParseException {
            Token token = peek();
            if (token.type() == type) {
                index++;
                return token;
            }
            throw error(token.type() == TokenType.EOF ? ParseErrorKind.UNEXPECTED_END : ParseErrorKind.UNEXPECTED_TOKEN,
                  token);
        }

        Expr expression() throws ExpressionParseException {
            return ternary();
        }

        private Expr ternary() throws ExpressionParseException {
            enter();
            try {
                Expr condition = comparison();
                if (match(TokenType.QUESTION)) {
                    Expr ifTrue = expression();
                    expect(TokenType.COLON);
                    //Right-associative: the false branch may itself be a ternary.
                    return node(new Expr.Ternary(condition, ifTrue, ternary()));
                }
                return condition;
            } finally {
                depth--;
            }
        }

        /**
         * Counts one level of nesting, rejecting anything past {@link ExpressionParser#MAX_DEPTH}. Guarding
         * {@code ternary} and {@code unary} bounds the whole parser: every other rule either loops instead of
         * recursing, or passes through one of these two, and each nested construct resolves to one of them.
         */
        private void enter() throws ExpressionParseException {
            if (++depth > MAX_DEPTH) {
                depth--;
                throw error(ParseErrorKind.TOO_DEEP, peek());
            }
        }

        /**
         * Registers a freshly built node against {@link ExpressionParser#MAX_NODES}. Every construction site goes
         * through here so that the bound cannot be forgotten for a new node type.
         */
        private Expr node(Expr expression) throws ExpressionParseException {
            if (++nodes > MAX_NODES) {
                throw error(ParseErrorKind.TOO_LARGE, peek());
            }
            return expression;
        }

        private Expr comparison() throws ExpressionParseException {
            Expr left = additive();
            if (match(TokenType.GREATER, TokenType.LESS, TokenType.EQUAL, TokenType.NOT_EQUAL)) {
                Expr.Operator operator = switch (previous().type()) {
                    case GREATER -> Expr.Operator.GREATER;
                    case LESS -> Expr.Operator.LESS;
                    case EQUAL -> Expr.Operator.EQUAL;
                    default -> Expr.Operator.NOT_EQUAL;
                };
                return node(new Expr.Binary(operator, left, additive()));
            }
            return left;
        }

        private Expr additive() throws ExpressionParseException {
            Expr expression = multiplicative();
            while (match(TokenType.PLUS, TokenType.MINUS)) {
                Expr.Operator operator = previous().type() == TokenType.PLUS ? Expr.Operator.ADD : Expr.Operator.SUBTRACT;
                expression = node(new Expr.Binary(operator, expression, multiplicative()));
            }
            return expression;
        }

        private Expr multiplicative() throws ExpressionParseException {
            Expr expression = unary();
            while (match(TokenType.STAR, TokenType.SLASH)) {
                Expr.Operator operator = previous().type() == TokenType.STAR ? Expr.Operator.MULTIPLY : Expr.Operator.DIVIDE;
                expression = node(new Expr.Binary(operator, expression, unary()));
            }
            return expression;
        }

        private Expr unary() throws ExpressionParseException {
            enter();
            try {
                if (match(TokenType.MINUS)) {
                    return node(new Expr.Negate(unary()));
                }
                return primary();
            } finally {
                depth--;
            }
        }

        private Expr primary() throws ExpressionParseException {
            Token token = peek();
            if (match(TokenType.NUMBER)) {
                return node(new Expr.Literal(previous().number()));
            }
            if (match(TokenType.IDENT)) {
                Token identifier = previous();
                if (!isVariable.test(identifier.text())) {
                    throw new ExpressionParseException(ParseErrorKind.UNKNOWN_VARIABLE, identifier.column(),
                          identifier.text());
                }
                return node(new Expr.Variable(identifier.text()));
            }
            if (match(TokenType.OPEN_PAREN)) {
                Expr inner = expression();
                expect(TokenType.CLOSE_PAREN);
                return inner;
            }
            throw error(token.type() == TokenType.EOF ? ParseErrorKind.UNEXPECTED_END : ParseErrorKind.UNEXPECTED_TOKEN,
                  token);
        }
    }
}
