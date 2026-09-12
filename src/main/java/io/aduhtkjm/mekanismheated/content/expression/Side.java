package io.aduhtkjm.mekanismheated.content.expression;

/**
 * The six block sides a temperature controller reads, together with the spelling of the variables that read them.
 *
 * <p>This is deliberately <em>not</em> {@code net.minecraft.core.Direction}: the expression package is free of
 * Minecraft and Mekanism types so that it can be compiled and exercised with a plain {@code javac}. The tile maps
 * between the two; the ordinals intentionally do <strong>not</strong> match {@code Direction}'s, so never rely on
 * {@code ordinal()} for that mapping.
 *
 * @see ExpressionParser#BUILT_IN_VARIABLES
 */
public enum Side {
    NORTH("north"),
    SOUTH("south"),
    EAST("east"),
    WEST("west"),
    UP("up"),
    DOWN("down");

    /**
     * The variable holding the ambient temperature at the controller's own position.
     */
    public static final String AMBIENT_VARIABLE = "T";

    private final String sideName;

    Side(String sideName) {
        this.sideName = sideName;
    }

    /**
     * The lower-case side name, e.g. {@code "north"}. Matches {@code Direction#getName()} for the corresponding
     * Minecraft direction, which is what makes the tile-side mapping a plain lookup.
     */
    public String sideName() {
        return sideName;
    }

    /**
     * The variable that reads this side's block temperature, e.g. {@code "north.T"}.
     */
    public String variable() {
        return sideName + ".T";
    }

    /**
     * Capitalised name for user-facing text, e.g. {@code "North"}.
     */
    public String displayName() {
        return Character.toUpperCase(sideName.charAt(0)) + sideName.substring(1);
    }

    /**
     * Resolves a built-in side variable to its side, or returns {@code null} when the name is not one.
     *
     * @param variable a variable name such as {@code "north.T"}; matching is case sensitive
     */
    public static Side byVariable(String variable) {
        for (Side side : values()) {
            if (side.variable().equals(variable)) {
                return side;
            }
        }
        return null;
    }

    /**
     * Looks a side up by its lower-case name, or returns {@code null} when there is no such side.
     */
    public static Side byName(String name) {
        for (Side side : values()) {
            if (side.sideName.equals(name)) {
                return side;
            }
        }
        return null;
    }

    /**
     * Ordinal-indexed lookup that tolerates out-of-range input, for decoding synced values that may be stale or
     * corrupt.
     */
    public static Side byIndex(int index) {
        Side[] sides = values();
        return sides[Math.floorMod(index, sides.length)];
    }
}
