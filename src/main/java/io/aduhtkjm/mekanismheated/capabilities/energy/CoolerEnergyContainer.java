package io.aduhtkjm.mekanismheated.capabilities.energy;

import io.aduhtkjm.mekanismheated.tile.TileEntityCooler;
import java.util.function.Predicate;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.SerializationConstants;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.functions.ConstantPredicates;
import mekanism.api.math.MathUtils;
import mekanism.common.block.attribute.AttributeEnergy;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.util.NBTUtils;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@NothingNullByDefault
public class CoolerEnergyContainer extends MachineEnergyContainer<TileEntityCooler> {

    public static final long USAGE_MULTIPLIER = 20 * SharedConstants.TICKS_PER_SECOND;

    public static CoolerEnergyContainer input(TileEntityCooler tile, @Nullable IContentsListener listener) {
        AttributeEnergy electricBlock = validateBlock(tile);
        return new CoolerEnergyContainer(electricBlock.getStorage(), electricBlock.getUsage(), notExternal, ConstantPredicates.alwaysTrue(), tile, listener);
    }

    private CoolerEnergyContainer(long maxEnergy, long energyPerTick, Predicate<@NotNull AutomationType> canExtract,
          Predicate<@NotNull AutomationType> canInsert, TileEntityCooler tile, @Nullable IContentsListener listener) {
        super(maxEnergy, energyPerTick, canExtract, canInsert, tile, listener);
    }

    @Override
    public boolean adjustableRates() {
        return true;
    }

    public void updateEnergyUsage(long energyUsage) {
        currentEnergyPerTick = energyUsage;
        setMaxEnergy(MathUtils.multiplyClamped(energyUsage, USAGE_MULTIPLIER));
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag nbt = super.serializeNBT(provider);
        nbt.putLong(SerializationConstants.ENERGY_USAGE, getEnergyPerTick());
        return nbt;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        NBTUtils.setLegacyEnergyIfPresent(nbt, SerializationConstants.ENERGY_USAGE, this::updateEnergyUsage);
        super.deserializeNBT(provider, nbt);
    }
}
