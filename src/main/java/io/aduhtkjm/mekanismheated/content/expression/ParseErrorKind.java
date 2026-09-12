package io.aduhtkjm.mekanismheated.content.expression;

/**
 * Why an expression could not be compiled.
 *
 * <p>Every kind carries a column ({@link ExpressionParseException#column()}) so the GUI can point at the offending
 * character, and most carry a {@link ExpressionParseException#detail() detail} string that is substituted into the
 * translated message.
 */
public enum ParseErrorKind {
    /**
     * The lexer hit a character that cannot appear anywhere in an expression, e.g. {@code $} or a lone {@code !}.
     * The detail is that character.
     */
    UNEXPECTED_CHARACTER,
    /**
     * The parser hit a token that cannot continue the rule it is in the middle of, e.g. the second {@code <} of
     * {@code 1 < 2 < 3}. The detail is the token's text.
     */
    UNEXPECTED_TOKEN,
    /**
     * The input ended before the expression was complete, e.g. {@code T +} or {@code a ? b}. There is no detail.
     */
    UNEXPECTED_END,
    /**
     * An identifier that is not a built-in variable. The detail is the identifier.
     *
     * @see ExpressionParser#BUILT_IN_VARIABLES
     */
    UNKNOWN_VARIABLE,
    /**
     * The expression nests deeper than {@link ExpressionParser#MAX_DEPTH}. The parser is recursive, so this bound is
     * what keeps a pathological input from becoming a {@link StackOverflowError} (an {@code Error}, which no
     * {@code catch (Exception)} around the parser would stop). There is no detail.
     */
    TOO_DEEP,
    /**
     * The expression contains more than {@link ExpressionParser#MAX_NODES} nodes. Evaluation recurses over the syntax
     * tree, so the tree's size — not just the parser's nesting — has to be bounded for the same reason as
     * {@link #TOO_DEEP}. This is what rejects a very long flat chain such as {@code 1 + 1 + 1 + …}. There is no
     * detail.
     */
    TOO_LARGE
}
