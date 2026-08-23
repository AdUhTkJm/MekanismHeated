package io.aduhtkjm.mekanismheated.tile.multiblock;

import io.aduhtkjm.mekanismheated.content.fractionation.FractionationMultiblockData;
import io.aduhtkjm.mekanismheated.registries.ModBlocks;
import java.util.Collections;
import java.util.List;
import mekanism.api.IContentsListener;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.common.attachments.containers.ContainerType;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.holder.heat.IHeatCapacitorHolder;
import mekanism.common.capabilities.heat.CachedAmbientTemperature;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class TileEntityThermalFractionationValve extends TileEntityFractionationBlock {

    public TileEntityThermalFractionationValve(BlockPos pos, BlockState state) {
        super(ModBlocks.THERMAL_FRACTIONATION_VALVE, pos, state);
    }

    public TileEntityThermalFractionationValve(Holder<Block> provider, BlockPos pos, BlockState state) {
        super(provider, pos, state);
    }

    /**
     * Fluid access is restricted to the tower layer this valve is embedded in: valves below the lowest tray feed the
     * sump (insert-only), each valve above a tray extracts from exactly that tray's output bank, and valves on a
     * distillation tray layer have no fluid access at all.
     */
    @NotNull
    @Override
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener) {
        return side -> {
            FractionationMultiblockData multiblock = getMultiblock();
            if (!multiblock.isFormed()) {
                return Collections.emptyList();
            }
            if (side == null) {
                //Internal view sees everything
                return multiblock.getFluidTanks(null);
            }
            IExtendedFluidTank tank = multiblock.getTankForLevel(getBlockPos().getY());
            return tank == null ? Collections.emptyList() : List.of(tank);
        };
    }

    @NotNull
    @Override
    protected IHeatCapacitorHolder getInitialHeatCapacitors(IContentsListener listener, CachedAmbientTemperature ambientTemperature) {
        //Heat is accepted from anywhere regardless of the valve's layer
        return side -> getMultiblock().getHeatCapacitors(side);
    }

    @Override
    public boolean persists(ContainerType<?, ?, ?> type) {
        //We do not handle fluid or heat when it comes to syncing it/saving this tile to disk
        if (type == ContainerType.FLUID || type == ContainerType.HEAT) {
            return false;
        }
        return super.persists(type);
    }
}
