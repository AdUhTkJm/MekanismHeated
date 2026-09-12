package io.aduhtkjm.mekanismheated.content.expression;

/**
 * Thrown while evaluating an expression whose syntax is valid but whose value cannot be produced right now, e.g. a
 * side variable pointing at a block with no heat capacitor.
 *
 * <p>"Runtime" here means "at evaluation time", not "unchecked": the exception is checked, so
 * {@link VariableResolver#resolve(String)} and {@link Expr#eval(VariableResolver)} force callers to deal with a
 * world that changes underneath them.
 */
public class ExpressionRuntimeException extends Exception {

    private final ExpressionRuntimeError kind;
    private final Side side;

    public ExpressionRuntimeException(ExpressionRuntimeError kind) {
        this(kind, null);
    }

    public ExpressionRuntimeException(ExpressionRuntimeError kind, Side side) {
        super(side == null ? kind.name() : kind.name() + " on the " + side.sideName() + " side");
        this.kind = kind;
        this.side = side;
    }

    /**
     * What went wrong.
     */
    public ExpressionRuntimeError kind() {
        return kind;
    }

    /**
     * The side the failure applies to, or {@code null} when it is not side-specific.
     */
    public Side side() {
        return side;
    }
}
