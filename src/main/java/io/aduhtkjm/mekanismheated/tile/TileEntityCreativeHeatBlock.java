package io.aduhtkjm.mekanismheated.tile;

import io.aduhtkjm.mekanismheated.registries.ModBlocks;
import mekanism.api.IContentsListener;
import mekanism.api.heat.HeatAPI.HeatTransfer;
import mekanism.common.capabilities.heat.BasicHeatCapacitor;
import mekanism.common.capabilities.heat.CachedAmbientTemperature;
import mekanism.common.capabilities.holder.heat.HeatCapacitorHelper;
import mekanism.common.capabilities.holder.heat.IHeatCapacitorHolder;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableDouble;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class TileEntityCreativeHeatBlock extends TileEntityMekanism {

    public static final double HEAT_CAPACITY = 1_000_000;
    public static final double INVERSE_CONDUCTION_COEFFICIENT = 1;
    public static final double INVERSE_INSULATION_COEFFICIENT = 100;

    private static final String TAG_TARGET_TEMPERATURE = "targetTemperature";
    private static final double MAX_TARGET_TEMPERATURE = 1_000_000_000;

    private BasicHeatCapacitor heatCapacitor;

    private double targetTemperature;
    private double lastEnvironmentLoss;
    private double lastTransferLoss;

    public TileEntityCreativeHeatBlock(BlockPos pos, BlockState state) {
        super(ModBlocks.CREATIVE_HEAT_BLOCK, pos, state);
    }

    @NotNull
    @Override
    protected IHeatCapacitorHolder getInitialHeatCapacitors(IContentsListener listener, CachedAmbientTemperature ambientTemperature) {
        HeatCapacitorHelper builder = HeatCapacitorHelper.forSide(facingSupplier);
        builder.addCapacitor(heatCapacitor = BasicHeatCapacitor.create(HEAT_CAPACITY, INVERSE_CONDUCTION_COEFFICIENT,
              INVERSE_INSULATION_COEFFICIENT, ambientTemperature, listener));
        return builder.build();
    }

    @Override
    protected boolean onUpdateServer() {
        boolean sendUpdatePacket = super.onUpdateServer();
        heatCapacitor.setHeat(targetTemperature * HEAT_CAPACITY);
        HeatTransfer transfer = simulate();
        lastEnvironmentLoss = transfer.environmentTransfer();
        lastTransferLoss = transfer.adjacentTransfer();
        setActive(targetTemperature > 0);
        return sendUpdatePacket;
    }

    public void setTargetTemperature(double temperature) {
        this.targetTemperature = Math.clamp(temperature, 0, MAX_TARGET_TEMPERATURE);
        markForSave();
    }

    public double getTargetTemperature() {
        return targetTemperature;
    }

    public double getLastTransferLoss() {
        return lastTransferLoss;
    }

    public double getLastEnvironmentLoss() {
        return lastEnvironmentLoss;
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableDouble.create(this::getTargetTemperature, value -> targetTemperature = value));
        container.track(SyncableDouble.create(this::getLastTransferLoss, value -> lastTransferLoss = value));
        container.track(SyncableDouble.create(this::getLastEnvironmentLoss, value -> lastEnvironmentLoss = value));
    }

    @Override
    public void saveAdditional(@NotNull CompoundTag nbt, @NotNull HolderLookup.Provider provider) {
        super.saveAdditional(nbt, provider);
        nbt.putDouble(TAG_TARGET_TEMPERATURE, targetTemperature);
    }

    @Override
    public void loadAdditional(@NotNull CompoundTag nbt, @NotNull HolderLookup.Provider provider) {
        super.loadAdditional(nbt, provider);
        targetTemperature = nbt.getDouble(TAG_TARGET_TEMPERATURE);
    }
}
