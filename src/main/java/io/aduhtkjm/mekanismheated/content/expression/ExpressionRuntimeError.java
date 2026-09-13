package io.aduhtkjm.mekanismheated.content.expression;

/**
 * Why evaluating an otherwise valid expression failed for the current world state.
 *
 * <p>Unlike {@link ExpressionParseException} this is a small, stable enum: the container sync sends the ordinal to
 * the client, which then builds the translated message locally, so that no error text ever crosses the wire.
 */
public enum ExpressionRuntimeError {
    /**
     * No failure; the expression evaluated fine.
     */
    NONE,
    /**
     * A side variable was read while the block on that side had no heat capacitor (no block entity, no heat
     * capability, or a heat handler with zero capacitors). See {@link ExpressionRuntimeException#side()}.
     */
    NO_HEAT_CAPACITOR,
    /**
     * The expression's result was infinite.
     */
    RESULT_INFINITE,
    /**
     * The expression's result was NaN.
     */
    RESULT_NAN;

    /**
     * Ordinal-indexed lookup that tolerates out-of-range input, for decoding synced values that may be stale or
     * corrupt.
     */
    public static ExpressionRuntimeError byIndex(int index) {
        ExpressionRuntimeError[] errors = values();
        return errors[Math.floorMod(index, errors.length)];
    }
}
