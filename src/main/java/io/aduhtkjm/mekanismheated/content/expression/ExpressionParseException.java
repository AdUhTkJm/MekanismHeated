package io.aduhtkjm.mekanismheated.content.expression;

/**
 * Thrown by {@link ExpressionParser} when an expression cannot be compiled.
 *
 * <p>The GUI does not display {@link #getMessage()}: it builds a translated message from {@link #kind()},
 * {@link #column()} and {@link #detail()} so that the text follows the client's language. The message this class
 * produces is for logs and stack traces only.
 */
public class ExpressionParseException extends Exception {

    private final ParseErrorKind kind;
    private final int column;
    private final String detail;

    public ExpressionParseException(ParseErrorKind kind, int column, String detail) {
        super(buildMessage(kind, column, detail));
        this.kind = kind;
        this.column = column;
        this.detail = detail;
    }

    private static String buildMessage(ParseErrorKind kind, int column, String detail) {
        StringBuilder message = new StringBuilder(kind.name()).append(" at column ").append(column);
        if (!detail.isEmpty()) {
            message.append(": ").append(detail);
        }
        return message.toString();
    }

    /**
     * What went wrong.
     */
    public ParseErrorKind kind() {
        return kind;
    }

    /**
     * Zero-based offset into the source of the offending character or token. For {@link ParseErrorKind#UNEXPECTED_END}
     * this is the length of the source, i.e. one past the last character.
     */
    public int column() {
        return column;
    }

    /**
     * The offending text, or the empty string when the error has no text of its own (see {@link ParseErrorKind}).
     */
    public String detail() {
        return detail;
    }
}
