package io.aduhtkjm.mekanismheated.content.upgrade;

import mekanism.common.lib.multiblock.MultiblockData;

/**
 * Implemented by this mod's multiblock data that own a heat capacitor, so the heat upgrades installed on any of the
 * structure's member blocks can be applied to the shared capacitor.
 */
public interface IHeatedUpgradeMultiblockData {

    /**
     * Re-applies the heat upgrades installed on this structure's members to its shared heat capacitors.
     */
    default void mekanismheated$recalculateHeatedUpgrades() {
        MultiblockData data = (MultiblockData) this;
        HeatedUpgrades.applyTo(HeatedUpgrades.multipliersFor(data), data.getHeatCapacitors(null));
    }
}
