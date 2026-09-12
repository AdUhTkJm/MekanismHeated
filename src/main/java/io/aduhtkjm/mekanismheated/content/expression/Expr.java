package io.aduhtkjm.mekanismheated.content.expression;

import java.util.Objects;

/**
 * An immutable syntax tree for a temperature-controller expression.
 *
 * <p>Produced by {@link ExpressionParser#parse(String)} and evaluated with {@link #evaluate(Expr, VariableResolver)}.
 * The tree is pure data: it holds no world state, so the same compiled expression is reused every tick (and can be
 * shared with the client, which re-parses the same text to produce localised syntax errors).
 *
 * <p>The language is documented in the grammar at {@link ExpressionParser}. The pieces that are easy to get wrong
 * are the ones with helpers here:
 * <ul>
 *   <li>{@link #equalWithinEpsilon(double, double)} — {@code =} / {@code ==} / {@code !=} use a relative epsilon
 *       rather than exact {@code ==} on accumulated doubles.</li>
 *   <li>{@link #isTrue(double)} — what counts as a true ternary condition.</li>
 *   <li>{@link #evaluate(Expr, VariableResolver)} — the "result must be finite" rule, which is the single place
 *       that turns division by zero, {@code 0/0} and overflow into {@link ExpressionRuntimeError#RESULT_NOT_FINITE}.</li>
 * </ul>
 */
public sealed interface Expr {

    /**
     * Relative tolerance of the {@code =}, {@code ==} and {@code !=} operators.
     */
    double EPSILON = 1.0E-9D;

    /**
     * Evaluates this node. Most failures come out of {@link VariableResolver}; everything else (division by zero,
     * overflow) is only rejected once, by {@link #evaluate(Expr, VariableResolver)}.
     */
    double eval(VariableResolver resolver) throws ExpressionRuntimeException;

    /**
     * Evaluates a whole expression and applies the result-is-finite rule.
     *
     * <p>This is the only supported entry point for evaluation. Every division by zero produces {@code ±Infinity} and
     * every {@code 0/0} produces {@code NaN}, so one finiteness check covers all of them instead of special-casing
     * the division operator.
     *
     * @throws ExpressionRuntimeException with {@link ExpressionRuntimeError#RESULT_NOT_FINITE} when the result is
     *         {@code NaN} or infinite, or with whatever the resolver reported
     */
    static double evaluate(Expr expression, VariableResolver resolver) throws ExpressionRuntimeException {
        double value = expression.eval(resolver);
        if (!Double.isFinite(value)) {
            throw new ExpressionRuntimeException(ExpressionRuntimeError.RESULT_NOT_FINITE);
        }
        return value;
    }

    /**
     * Whether a value counts as a true ternary condition: anything non-zero except {@code NaN}.
     */
    static boolean isTrue(double value) {
        return value != 0.0D && !Double.isNaN(value);
    }

    /**
     * Equality as the {@code =}, {@code ==} and {@code !=} operators mean it: exact for bit-identical values
     * (including {@code 0.0} vs {@code -0.0}), otherwise within {@link #EPSILON} relative to the larger magnitude.
     *
     * <p>The absolute floor of {@code max(1, ...)} keeps the tolerance sane for values near zero, and the relative
     * term keeps it sane for temperatures in the hundreds or thousands. {@code NaN} is never equal to anything.
     */
    static boolean equalWithinEpsilon(double left, double right) {
        if (left == right) {
            // Covers 0.0 == -0.0; NaN == NaN is false, so it falls through to the NaN check below.
            return true;
        }
        if (Double.isNaN(left) || Double.isNaN(right)) {
            return false;
        }
        return Math.abs(left - right) <= EPSILON * Math.max(1.0D, Math.max(Math.abs(left), Math.abs(right)));
    }

    /**
     * The binary operators, in the order they appear in the grammar.
     */
    enum Operator {
        ADD("+"),
        SUBTRACT("-"),
        MULTIPLY("*"),
        DIVIDE("/"),
        GREATER(">"),
        LESS("<"),
        EQUAL("="),
        NOT_EQUAL("!=");

        private final String symbol;

        Operator(String symbol) {
            this.symbol = symbol;
        }

        /**
         * How the operator is spelled in an expression. Note that {@code ==} is lexed to {@link #EQUAL} as well, so
         * this is the canonical spelling rather than the only one.
         */
        public String symbol() {
            return symbol;
        }

        /**
         * Applies the operator. Comparisons return {@code 1.0} or {@code 0.0} so that their results are ordinary
         * numbers that can be fed back into arithmetic.
         */
        public double apply(double left, double right) {
            return switch (this) {
                case ADD -> left + right;
                case SUBTRACT -> left - right;
                case MULTIPLY -> left * right;
                case DIVIDE -> left / right;
                case GREATER -> left > right ? 1.0D : 0.0D;
                case LESS -> left < right ? 1.0D : 0.0D;
                case EQUAL -> Expr.equalWithinEpsilon(left, right) ? 1.0D : 0.0D;
                case NOT_EQUAL -> Expr.equalWithinEpsilon(left, right) ? 0.0D : 1.0D;
            };
        }
    }

    /**
     * A number literal. All literals are parsed as {@code double}.
     */
    record Literal(double value) implements Expr {

        @Override
        public double eval(VariableResolver resolver) {
            return value;
        }
    }

    /**
     * A built-in variable, e.g. {@code T} or {@code north.T}. The parser guarantees the name is one of
     * {@link ExpressionParser#BUILT_IN_VARIABLES}.
     */
    record Variable(String name) implements Expr {

        public Variable {
            Objects.requireNonNull(name, "name");
        }

        @Override
        public double eval(VariableResolver resolver) throws ExpressionRuntimeException {
            return resolver.resolve(name);
        }
    }

    /**
     * Unary minus.
     */
    record Negate(Expr operand) implements Expr {

        public Negate {
            Objects.requireNonNull(operand, "operand");
        }

        @Override
        public double eval(VariableResolver resolver) throws ExpressionRuntimeException {
            return -operand.eval(resolver);
        }
    }

    /**
     * A binary operation. Both operands are always evaluated, left to right.
     */
    record Binary(Operator operator, Expr left, Expr right) implements Expr {

        public Binary {
            Objects.requireNonNull(operator, "operator");
            Objects.requireNonNull(left, "left");
            Objects.requireNonNull(right, "right");
        }

        @Override
        public double eval(VariableResolver resolver) throws ExpressionRuntimeException {
            return operator.apply(left.eval(resolver), right.eval(resolver));
        }
    }

    /**
     * {@code condition ? ifTrue : ifFalse}. Only the taken branch is evaluated, which is what makes the ternary the
     * way to guard a side read: {@code T > 500 ? north.T : 0} never touches the north neighbour unless the ambient
     * temperature is above 500 K.
     */
    record Ternary(Expr condition, Expr ifTrue, Expr ifFalse) implements Expr {

        public Ternary {
            Objects.requireNonNull(condition, "condition");
            Objects.requireNonNull(ifTrue, "ifTrue");
            Objects.requireNonNull(ifFalse, "ifFalse");
        }

        @Override
        public double eval(VariableResolver resolver) throws ExpressionRuntimeException {
            return isTrue(condition.eval(resolver)) ? ifTrue.eval(resolver) : ifFalse.eval(resolver);
        }
    }
}
