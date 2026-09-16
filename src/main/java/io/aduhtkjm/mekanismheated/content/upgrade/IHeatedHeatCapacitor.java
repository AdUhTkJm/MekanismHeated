package io.aduhtkjm.mekanismheated.content.upgrade;

/**
 * Implemented (via mixin) by Mekanism's {@code BasicHeatCapacitor} and {@code VariableHeatCapacitor} so this mod can
 * scale a machine's capacitor values for installed heat upgrades.
 *
 * <p>Implementations remember the value their capacitor was constructed with, plus any capacity the owner explicitly
 * passed in (for example a multiblock scaling by its volume), so applying multipliers is always relative to that base
 * and never compounds.</p>
 */
public interface IHeatedHeatCapacitor {

    /**
     * Scales this capacitor's inverse conduction, inverse insulation and heat capacity by the given multipliers,
     * relative to its base values.
     *
     * @param multipliers Multipliers to apply; {@link HeatedUpgrades.Multipliers#NONE} restores the base values.
     */
    void mekanismheated$applyMultipliers(HeatedUpgrades.Multipliers multipliers);

    /**
     * Same as {@link #mekanismheated$applyMultipliers(HeatedUpgrades.Multipliers)} but also records a new base capacity
     * first. Used by machines that derive their capacity from something other than their configuration, such as a
     * multiblock scaling its capacitor by its volume.
     *
     * @param multipliers      Multipliers to apply.
     * @param baseHeatCapacity Heat capacity the multipliers should be applied to.
     */
    void mekanismheated$applyMultipliers(HeatedUpgrades.Multipliers multipliers, double baseHeatCapacity);

    /**
     * The factor this capacitor's inverse conduction coefficient is currently divided by.
     */
    double mekanismheated$getConductionDivisor();

    /**
     * The factor this capacitor's inverse insulation coefficient is currently multiplied by.
     */
    double mekanismheated$getInsulationMultiplier();
}
