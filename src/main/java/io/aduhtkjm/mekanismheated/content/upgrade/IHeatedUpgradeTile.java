package io.aduhtkjm.mekanismheated.content.upgrade;

import mekanism.common.lib.multiblock.IMultiblock;
import mekanism.common.lib.multiblock.MultiblockData;
import mekanism.common.tile.base.TileEntityMekanism;

/**
 * Marker for the machines of this mod that accept the heat upgrades, and the entry point for (re)applying their effects.
 *
 * <p>Only machines that also carry Mekanism's upgrade component (i.e. the block has
 * {@code AttributeUpgradeSupport}) can actually be given upgrades; this interface is what decides whether the upgrade
 * slot of such a machine accepts them. Mekanism's own machines do not implement it, so they are left untouched.</p>
 */
public interface IHeatedUpgradeTile {

    /**
     * Re-applies this machine's installed heat upgrades to its heat capacitors. Safe to call at any time; a no-op on the
     * client, where the scaled values arrive through the container and update tag sync.
     *
     * <p>A machine that is currently formed as part of a multiblock delegates to the shared structure instead, since
     * that is where its capacitors live.</p>
     */
    default void mekanismheated$recalculateHeatedUpgrades() {
        TileEntityMekanism tile = (TileEntityMekanism) this;
        if (tile.isRemote()) {
            return;
        }
        if (tile instanceof IMultiblock<?> multiblock) {
            MultiblockData shared = multiblock.getMultiblock();
            if (shared.isFormed() && shared instanceof IHeatedUpgradeMultiblockData data) {
                data.mekanismheated$recalculateHeatedUpgrades();
                return;
            }
        }
        HeatedUpgrades.applyTo(HeatedUpgrades.multipliersFor(tile), tile.getHeatCapacitors(null));
    }
}
