package io.aduhtkjm.mekanismheated.item;

import io.aduhtkjm.mekanismheated.capabilities.energy.CoolerEnergyContainer;
import io.aduhtkjm.mekanismheated.tile.TileEntityCooler;
import mekanism.api.AutomationType;
import mekanism.api.SerializationConstants;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.functions.ConstantPredicates;
import mekanism.api.math.MathUtils;
import mekanism.common.attachments.containers.ContainerType;
import mekanism.common.attachments.containers.energy.ComponentBackedEnergyContainer;
import mekanism.common.capabilities.energy.BasicEnergyContainer;
import mekanism.common.registries.MekanismDataComponents;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.NBTUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

@NothingNullByDefault
public class ComponentBackedCoolerEnergyContainer extends ComponentBackedEnergyContainer {

    public static ComponentBackedCoolerEnergyContainer create(ContainerType<?, ?, ?> ignored, ItemStack attachedTo, int containerIndex) {
        return new ComponentBackedCoolerEnergyContainer(attachedTo, containerIndex);
    }

    private ComponentBackedCoolerEnergyContainer(ItemStack attachedTo, int containerIndex) {
        super(attachedTo, containerIndex, BasicEnergyContainer.manualOnly, ConstantPredicates.alwaysTrue(), ConstantPredicates.ZERO_LONG, ConstantPredicates.ZERO_LONG);
    }

    @Override
    public long getMaxEnergy() {
        return MathUtils.multiplyClamped(getEnergyPerTick(), CoolerEnergyContainer.USAGE_MULTIPLIER);
    }

    private long getRate() {
        return MekanismUtils.calculateUsage(getMaxEnergy());
    }

    @Override
    protected long getInsertRate(@Nullable AutomationType automationType) {
        return automationType == null || automationType == AutomationType.MANUAL ? Long.MAX_VALUE : getRate();
    }

    @Override
    protected long getExtractRate(@Nullable AutomationType automationType) {
        return automationType == null || automationType == AutomationType.MANUAL ? Long.MAX_VALUE : getRate();
    }

    private long getEnergyPerTick() {
        return attachedTo.getOrDefault(MekanismDataComponents.ENERGY_USAGE, TileEntityCooler.BASE_USAGE);
    }

    private void updateEnergyUsage(long energyUsage) {
        attachedTo.set(MekanismDataComponents.ENERGY_USAGE, energyUsage);
        setEnergy(getEnergy());
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
