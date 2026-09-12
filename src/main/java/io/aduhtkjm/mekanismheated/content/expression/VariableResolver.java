package io.aduhtkjm.mekanismheated.content.expression;

/**
 * Supplies the values of the built-in variables while an expression is evaluated.
 *
 * <p>The expression package has no idea what a world is; the tile implements this to read the ambient temperature
 * and the neighbouring blocks' heat capacitors. Because a neighbour can disappear between two ticks, a legal
 * variable may still be unreadable, which is what {@link ExpressionRuntimeException} is for.
 */
@FunctionalInterface
public interface VariableResolver {

    /**
     * Resolves one of {@link ExpressionParser#BUILT_IN_VARIABLES}.
     *
     * @param name the variable exactly as written in the expression, e.g. {@code "T"} or {@code "north.T"}
     *
     * @return the variable's current value, in Kelvin
     *
     * @throws ExpressionRuntimeException when the name is legal but cannot be read for the current world state
     */
    double resolve(String name) throws ExpressionRuntimeException;
}
