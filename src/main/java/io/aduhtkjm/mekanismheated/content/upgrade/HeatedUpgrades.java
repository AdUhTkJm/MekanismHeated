package io.aduhtkjm.mekanismheated.content.upgrade;

import io.aduhtkjm.mekanismheated.Config;
import io.aduhtkjm.mekanismheated.item.ItemHeatedUpgrade;
import io.aduhtkjm.mekanismheated.registries.ModItems;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import mekanism.api.SerializationConstants;
import mekanism.api.heat.IHeatCapacitor;
import mekanism.common.lib.multiblock.MultiblockData;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Shared helpers for the heat upgrades: reading/writing them, resolving the item they are installed with, computing the
 * multipliers from an installed count, and pushing those multipliers onto heat capacitors.
 */
public final class HeatedUpgrades {

    /**
     * Key the installed heat upgrades are stored under inside Mekanism's upgrade component NBT.
     */
    public static final String NBT_KEY = "heated_upgrades";

    private HeatedUpgrades() {
    }

    /**
     * The factors a machine's heat capacitor values get scaled by. {@link #NONE} restores the values a capacitor was
     * constructed with.
     *
     * @param conductionDivisor     Factor the inverse conduction coefficient is divided by.
     * @param insulationMultiplier  Factor the inverse insulation coefficient is multiplied by.
     * @param capacityMultiplier    Factor the heat capacity is multiplied by.
     */
    public record Multipliers(double conductionDivisor, double insulationMultiplier, double capacityMultiplier) {

        public static final Multipliers NONE = new Multipliers(1, 1, 1);
    }

    /**
     * Gets the heat upgrade item for the given stack, or {@code null} if the stack isn't a heat upgrade.
     */
    @Nullable
    public static ItemHeatedUpgrade getItem(ItemStack stack) {
        return stack.getItem() instanceof ItemHeatedUpgrade item ? item : null;
    }

    /**
     * Gets the heat upgrade type of the given stack, or {@code null} if the stack isn't a heat upgrade.
     */
    @Nullable
    public static HeatedUpgrade getType(ItemStack stack) {
        ItemHeatedUpgrade item = getItem(stack);
        return item == null ? null : item.getHeatedUpgradeType();
    }

    /**
     * Checks whether the given stack is one of this mod's heat upgrades.
     */
    public static boolean isHeatedUpgrade(ItemStack stack) {
        return stack.getItem() instanceof ItemHeatedUpgrade;
    }

    /**
     * Gets a stack of the item used to install the given heat upgrade.
     */
    public static ItemStack getStack(HeatedUpgrade upgrade, int count) {
        return new ItemStack(ModItems.HEATED_UPGRADES.get(upgrade).get(), count);
    }

    /**
     * Checks whether the given tile accepts this mod's heat upgrades. Only the mod's own machines with a heat capacitor
     * implement {@link IHeatedUpgradeTile}, so Mekanism's machines are left alone.
     */
    public static boolean supports(TileEntityMekanism tile) {
        return tile.supportsUpgrades() && tile instanceof IHeatedUpgradeTile;
    }

    /**
     * Gets the multipliers for the given installed heat upgrades.
     */
    public static Multipliers multipliers(Map<HeatedUpgrade, Integer> installed) {
        if (installed.isEmpty()) {
            return Multipliers.NONE;
        }
        return new Multipliers(
              HeatedUpgrade.CONDUCTION.getMultiplier(installed.getOrDefault(HeatedUpgrade.CONDUCTION, 0)),
              HeatedUpgrade.INSULATION.getMultiplier(installed.getOrDefault(HeatedUpgrade.INSULATION, 0)),
              HeatedUpgrade.CAPACITY.getMultiplier(installed.getOrDefault(HeatedUpgrade.CAPACITY, 0))
        );
    }

    /**
     * Gets the multipliers from a single machine's own installed heat upgrades.
     */
    public static Multipliers multipliersFor(TileEntityMekanism tile) {
        if (tile.supportsUpgrades() && tile.getComponent() instanceof IHeatedUpgradeComponent component) {
            return multipliers(component.mekanismheated$getHeatedUpgrades());
        }
        return Multipliers.NONE;
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
        Map<HeatedUpgrade, Integer> highest = new EnumMap<>(HeatedUpgrade.class);
        for (BlockPos pos : data.locations) {
            if (WorldUtils.getTileEntity(level, pos) instanceof TileEntityMekanism tile && tile.supportsUpgrades()
                  && tile.getComponent() instanceof IHeatedUpgradeComponent component) {
                for (HeatedUpgrade type : HeatedUpgrade.values()) {
                    highest.merge(type, component.mekanismheated$getHeatedUpgrades(type), Math::max);
                }
            }
        }
        return multipliers(highest);
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

    /**
     * Reads the map of installed heat upgrades to their amounts from NBT.
     *
     * @implNote Unmodifiable if empty.
     */
    public static Map<HeatedUpgrade, Integer> read(@Nullable CompoundTag nbt) {
        if (nbt == null || !nbt.contains(NBT_KEY, Tag.TAG_LIST)) {
            return Collections.emptyMap();
        }
        ListTag list = nbt.getList(NBT_KEY, Tag.TAG_COMPOUND);
        Map<HeatedUpgrade, Integer> upgrades = null;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag compound = list.getCompound(i);
            HeatedUpgrade upgrade = HeatedUpgrade.BY_ID.apply(compound.getInt(SerializationConstants.TYPE));
            int installed = Math.clamp(compound.getInt(SerializationConstants.AMOUNT), 0, Config.Upgrades.MAX_HEAT_UPGRADES.get());
            if (installed > 0) {
                if (upgrades == null) {
                    upgrades = new EnumMap<>(HeatedUpgrade.class);
                }
                upgrades.put(upgrade, installed);
            }
        }
        return upgrades == null ? Collections.emptyMap() : upgrades;
    }

    /**
     * Writes a map of installed heat upgrades to a new NBT list, mirroring how Mekanism serializes its own upgrades.
     */
    public static ListTag write(Map<HeatedUpgrade, Integer> upgrades) {
        ListTag list = new ListTag();
        for (Map.Entry<HeatedUpgrade, Integer> entry : upgrades.entrySet()) {
            CompoundTag compound = new CompoundTag();
            compound.putInt(SerializationConstants.TYPE, entry.getKey().ordinal());
            compound.putInt(SerializationConstants.AMOUNT, entry.getValue());
            list.add(compound);
        }
        return list;
    }
}
