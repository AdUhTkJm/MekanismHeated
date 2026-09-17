package io.aduhtkjm.mekanismheated.content.upgrade;

import io.aduhtkjm.mekanismheated.Config;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import mekanism.api.Upgrade;
import mekanism.api.heat.IHeatCapacitor;
import mekanism.common.block.attribute.AttributeUpgradeSupport;
import mekanism.common.lib.multiblock.IMultiblock;
import mekanism.common.lib.multiblock.MultiblockData;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.tile.component.TileComponentUpgrade;
import mekanism.common.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Shared helpers for the heat upgrades: computing the multipliers from the installed counts and pushing those
 * multipliers onto heat capacitors.
 *
 * <p>The installed counts live in Mekanism's own {@link TileComponentUpgrade}, keyed by {@link Upgrade}, because the
 * heat upgrades are real members of that enum (see {@link HeatedUpgrade}). Nothing is stored or synced by this mod
 * itself.</p>
 */
public final class HeatedUpgrades {

    /**
     * Supported upgrades of a machine that takes the standard machine upgrades plus the heat upgrades.
     */
    public static final AttributeUpgradeSupport MACHINE_UPGRADES = AttributeUpgradeSupport.create(
          Upgrade.SPEED, Upgrade.ENERGY, Upgrade.MUFFLING,
          HeatedUpgrade.CONDUCTION, HeatedUpgrade.INSULATION, HeatedUpgrade.CAPACITY,
          HeatedUpgrade.INV_CONDUCTION, HeatedUpgrade.INV_INSULATION, HeatedUpgrade.INV_CAPACITY);
    /**
     * Supported upgrades of a machine that only supports the heat upgrades.
     */
    public static final AttributeUpgradeSupport HEAT_UPGRADES_ONLY = AttributeUpgradeSupport.create(
          HeatedUpgrade.CONDUCTION, HeatedUpgrade.INSULATION, HeatedUpgrade.CAPACITY,
          HeatedUpgrade.INV_CONDUCTION, HeatedUpgrade.INV_INSULATION, HeatedUpgrade.INV_CAPACITY);

    private HeatedUpgrades() {
    }

    /**
     * The factors a machine's heat capacitor values get scaled by. {@link #NONE} restores the values a capacitor was
     * constructed with.
     *
     * @param conductionDivisor    Factor the inverse conduction coefficient is divided by.
     * @param insulationMultiplier Factor the inverse insulation coefficient is multiplied by.
     * @param capacityMultiplier   Factor the heat capacity is multiplied by.
     */
    public record Multipliers(double conductionDivisor, double insulationMultiplier, double capacityMultiplier) {

        public static final Multipliers NONE = new Multipliers(1, 1, 1);
    }

    /**
     * Checks whether the given machine accepts this mod's heat upgrades. Machines of this mod that own a heat capacitor
     * list them in their block's supported upgrades.
     */
    public static boolean supports(TileEntityMekanism tile) {
        if (!tile.supportsUpgrades()) {
            return false;
        }
        for (Upgrade upgrade : HeatedUpgrade.HEAT_UPGRADES) {
            if (tile.getSupportedUpgrade().contains(upgrade)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the configured per-upgrade bonus as a fraction: 0.1 means every upgrade is a 10% improvement.
     */
    public static double bonus(Upgrade upgrade) {
        if (upgrade == HeatedUpgrade.CONDUCTION) {
            return Config.Upgrades.CONDUCTION.get();
        } else if (upgrade == HeatedUpgrade.INSULATION) {
            return Config.Upgrades.INSULATION.get();
        }
        return Config.Upgrades.CAPACITY.get();
    }

    /**
     * Gets the factor the given heat upgrade applies to its capacitor value for the given installed count, which is
     * {@code (1 + bonus)^count}.
     */
    public static double multiplier(Upgrade upgrade, int count) {
        return count <= 0 ? 1 : Math.pow(1 + bonus(upgrade), count);
    }

    /**
     * Gets the multipliers for the given installed heat upgrades.
     */
    public static Multipliers multipliers(Map<Upgrade, Integer> installed) {
        if (installed.isEmpty()) {
            return Multipliers.NONE;
        }
        int cdt = installed.getOrDefault(HeatedUpgrade.CONDUCTION, 0) - installed.getOrDefault(HeatedUpgrade.INV_CONDUCTION, 0);
        int ins = installed.getOrDefault(HeatedUpgrade.INSULATION, 0) - installed.getOrDefault(HeatedUpgrade.INV_INSULATION, 0);
        int cap = installed.getOrDefault(HeatedUpgrade.CAPACITY, 0) -  installed.getOrDefault(HeatedUpgrade.INV_CAPACITY, 0);
        return new Multipliers(
              multiplier(HeatedUpgrade.CONDUCTION, cdt),
              multiplier(HeatedUpgrade.INSULATION, ins),
              multiplier(HeatedUpgrade.CAPACITY, cap)
        );
    }

    /**
     * Gets the multipliers from a single machine's own installed heat upgrades.
     */
    public static Multipliers multipliersFor(TileEntityMekanism tile) {
        if (!tile.supportsUpgrades()) {
            return Multipliers.NONE;
        }
        TileComponentUpgrade component = tile.getComponent();
        int cdt = component.getUpgrades(HeatedUpgrade.CONDUCTION) - component.getUpgrades(HeatedUpgrade.INV_CONDUCTION);
        int ins = component.getUpgrades(HeatedUpgrade.INSULATION) - component.getUpgrades(HeatedUpgrade.INV_INSULATION);
        int cap = component.getUpgrades(HeatedUpgrade.CAPACITY) - component.getUpgrades(HeatedUpgrade.INV_CAPACITY);
        return new Multipliers(
              multiplier(HeatedUpgrade.CONDUCTION, cdt),
              multiplier(HeatedUpgrade.INSULATION, ins),
              multiplier(HeatedUpgrade.CAPACITY, cap)
        );
    }

    /**
     * Gets the multipliers a formed multiblock should use: the highest count of each upgrade installed on any of its
     * member blocks, so installing anywhere in the structure works and no member has to be singled out as the one that
     * counts.
     */
    public static Multipliers multipliersFor(MultiblockData data) {
        Level level = data.getLevel();
        if (level == null) {
            return Multipliers.NONE;
        }
        Map<Upgrade, Integer> highest = new EnumMap<>(Upgrade.class);
        for (BlockPos pos : data.locations) {
            if (WorldUtils.getTileEntity(level, pos) instanceof TileEntityMekanism tile && tile.supportsUpgrades()) {
                TileComponentUpgrade component = tile.getComponent();
                for (Upgrade type : HeatedUpgrade.HEAT_UPGRADES) {
                    highest.merge(type, component.getUpgrades(type), Math::max);
                }
            }
        }
        return multipliers(highest);
    }

    /**
     * Re-applies a machine's installed heat upgrades to its heat capacitors, delegating to the shared structure when the
     * machine is currently formed as part of a multiblock.
     *
     * <p>Called from the machine's {@code recalculateUpgrades} whenever a heat upgrade is installed or removed, when a
     * machine is read from NBT (see {@code MixinTileEntityMekanism}), and by the multiblock code when a structure forms
     * or falls apart. Only a machine that already lives in a client level is skipped, since its scaled values arrive
     * through the container and update tag sync instead.</p>
     *
     * <p>The level is {@code null} while a tile is being read from NBT ({@code BlockEntity#loadAdditional} runs before
     * the tile is added to a level), and {@code TileEntityMekanism#isRemote()} throws in that case. That path must not
     * be skipped, both because it is where the scaled coefficients get restored after a reload (they are not persisted)
     * and because throwing out of it makes {@code BlockEntity#loadStatic} discard the whole block entity - losing the
     * machine's upgrades and contents on every load. So {@code null} is treated as the server.</p>
     */
    public static void reapply(TileEntityMekanism tile) {
        Level level = tile.getLevel();
        if (level != null && level.isClientSide()) {
            return;
        }
        if (tile instanceof IMultiblock<?> multiblock) {
            MultiblockData shared = multiblock.getMultiblock();
            if (shared.isFormed() && shared instanceof IHeatedUpgradeMultiblockData data) {
                data.mekanismheated$recalculateHeatedUpgrades();
                return;
            }
        }
        applyTo(multipliersFor(tile), tile.getHeatCapacitors(null));
    }

    /**
     * Applies the given multipliers to every heat capacitor in the list that supports them.
     */
    public static void applyTo(Multipliers multipliers, List<IHeatCapacitor> capacitors) {
        for (IHeatCapacitor capacitor : capacitors) {
            applyTo(capacitor, multipliers);
        }
    }

    /**
     * Applies the given multipliers to a single heat capacitor.
     */
    public static void applyTo(IHeatCapacitor capacitor, Multipliers multipliers) {
        if (capacitor instanceof IHeatedHeatCapacitor heated) {
            heated.mekanismheated$applyMultipliers(multipliers);
        }
    }

    /**
     * Applies the given multipliers to a single heat capacitor whose base capacity the owner has just changed (for
     * example when a multiblock scales its capacitor by its volume).
     */
    public static void applyTo(IHeatCapacitor capacitor, double baseHeatCapacity, Multipliers multipliers) {
        if (capacitor instanceof IHeatedHeatCapacitor heated) {
            heated.mekanismheated$applyMultipliers(multipliers, baseHeatCapacity);
        }
    }
}
