package io.aduhtkjm.mekanismheated.content.expression;

/**
 * What the temperature controller does with the evaluated expression.
 *
 * <p>This lives in the expression package only because it needs no Minecraft or Mekanism types that way, which keeps
 * the whole package compilable with a plain {@code javac}. It is otherwise a plain controller concept.
 */
public enum OutputMode {
    /**
     * The result is clamped to 0-15 and emitted as a redstone signal strength. Values below 0.5 clamp to 0 and values
     * above 15 clamp to 15.
     */
    REDSTONE,
    /**
     * The result is clamped to 0-{@code maxEnergyOutput}, converted from the player's configured Mekanism energy unit
     * to joules, and written as the energy/tick of every adjacent cooler and resistive heater.
     */
    ENERGY;

    /**
     * Ordinal-indexed lookup that tolerates out-of-range input, for decoding synced values that may be stale or
     * corrupt.
     */
    public static OutputMode byIndex(int index) {
        OutputMode[] modes = values();
        return modes[Math.floorMod(index, modes.length)];
    }
}
