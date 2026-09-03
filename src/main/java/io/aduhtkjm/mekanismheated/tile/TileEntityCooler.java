package io.aduhtkjm.mekanismheated.tile;

import io.aduhtkjm.mekanismheated.Config;
import io.aduhtkjm.mekanismheated.capabilities.energy.CoolerEnergyContainer;
import io.aduhtkjm.mekanismheated.registries.ModBlocks;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.RelativeSide;
import mekanism.api.SerializationConstants;
import mekanism.common.capabilities.heat.BasicHeatCapacitor;
import mekanism.common.capabilities.heat.CachedAmbientTemperature;
import mekanism.common.capabilities.holder.energy.EnergyContainerHelper;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.heat.HeatCapacitorHelper;
import mekanism.common.capabilities.holder.heat.IHeatCapacitorHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableLong;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.registries.MekanismDataComponents;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.util.NBTUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class TileEntityCooler extends TileEntityMekanism {

    public static final long BASE_USAGE = 100L;

    private long clientEnergyUsed = 0;

    private CoolerEnergyContainer energyContainer;
    private BasicHeatCapacitor hotCapacitor;
    private BasicHeatCapacitor coldCapacitor;
    private EnergyInventorySlot energySlot;

    public TileEntityCooler(BlockPos pos, BlockState state) {
        super(ModBlocks.COOLER, pos, state);
    }

    @NotNull
    @Override
    protected IEnergyContainerHolder getInitialEnergyContainers(IContentsListener listener) {
        EnergyContainerHelper builder = EnergyContainerHelper.forSide(facingSupplier);
        builder.addContainer(energyContainer = CoolerEnergyContainer.input(this, listener), RelativeSide.FRONT);
        return builder.build();
    }

    @NotNull
    @Override
    protected IHeatCapacitorHolder getInitialHeatCapacitors(IContentsListener listener, CachedAmbientTemperature ambientTemperature) {
        HeatCapacitorHelper builder = HeatCapacitorHelper.forSide(facingSupplier);
        double heatCapacity = Config.Cooler.HEAT_CAPACITY.get();
        double inverseConduction = Config.Cooler.INVERSE_CONDUCTION_COEFFICIENT.get();
        double inverseInsulation = Config.Cooler.INVERSE_INSULATION_COEFFICIENT.get();
        builder.addCapacitor(hotCapacitor = BasicHeatCapacitor.create(heatCapacity, inverseConduction, inverseInsulation, ambientTemperature, listener), RelativeSide.FRONT);
        builder.addCapacitor(coldCapacitor = BasicHeatCapacitor.create(heatCapacity, inverseConduction, inverseInsulation, ambientTemperature, listener),
              RelativeSide.BACK, RelativeSide.LEFT, RelativeSide.RIGHT, RelativeSide.TOP, RelativeSide.BOTTOM);
        return builder.build();
    }

    @NotNull
    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = InventorySlotHelper.forSide(facingSupplier);
        builder.addSlot(energySlot = EnergyInventorySlot.fillOrConvert(energyContainer, this::getLevel, listener, 15, 35));
        return builder.build();
    }

    @Override
    protected boolean onUpdateServer() {
        boolean sendUpdatePacket = super.onUpdateServer();
        energySlot.fillContainerOrConvert();
        long toUse = 0;
        if (canFunction()) {
            toUse = energyContainer.extract(energyContainer.getEnergyPerTick(), Action.SIMULATE, AutomationType.INTERNAL);
            if (toUse > 0L) {
                double heat = toUse * Config.Cooler.EFFICIENCY.get();
                coldCapacitor.handleHeat(-heat);
                hotCapacitor.handleHeat(heat);
                energyContainer.extract(toUse, Action.EXECUTE, AutomationType.INTERNAL);
            }
        }
        setActive(toUse > 0L);
        clientEnergyUsed = toUse;
        simulate();
        return sendUpdatePacket;
    }

    public long getEnergyUsed() {
        return clientEnergyUsed;
    }

    public double getHotTemperature() {
        return hotCapacitor.getTemperature();
    }

    public double getColdTemperature() {
        return coldCapacitor.getTemperature();
    }

    public CoolerEnergyContainer getEnergyContainer() {
        return energyContainer;
    }

    public void setEnergyUsageFromPacket(long energyUsage) {
        energyContainer.updateEnergyUsage(energyUsage);
        markForSave();
    }

    @Override
    public CompoundTag getConfigurationData(HolderLookup.Provider provider, Player player) {
        CompoundTag data = super.getConfigurationData(provider, player);
        data.putLong(SerializationConstants.ENERGY_USAGE, energyContainer.getEnergyPerTick());
        return data;
    }

    @Override
    public void setConfigurationData(HolderLookup.Provider provider, Player player, CompoundTag data) {
        super.setConfigurationData(provider, player, data);
        NBTUtils.setLegacyEnergyIfPresent(data, SerializationConstants.ENERGY_USAGE, energyContainer::updateEnergyUsage);
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableLong.create(this::getEnergyUsed, value -> clientEnergyUsed = value));
    }

    @Override
    protected void collectImplicitComponents(@NotNull DataComponentMap.Builder builder) {
        builder.set(MekanismDataComponents.ENERGY_USAGE, energyContainer.getEnergyPerTick());
        super.collectImplicitComponents(builder);
    }

    @Override
    protected void applyImplicitComponents(@NotNull BlockEntity.DataComponentInput input) {
        energyContainer.updateEnergyUsage(input.getOrDefault(MekanismDataComponents.ENERGY_USAGE, BASE_USAGE));
        super.applyImplicitComponents(input);
    }
}
