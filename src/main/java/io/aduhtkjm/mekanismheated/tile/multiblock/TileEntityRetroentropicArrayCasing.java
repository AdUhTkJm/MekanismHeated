package io.aduhtkjm.mekanismheated.tile.multiblock;

import io.aduhtkjm.mekanismheated.registries.ModBlocks;
import io.aduhtkjm.mekanismheated.registries.ModMultiblockManagers;
import mekanism.api.IContentsListener;
import mekanism.common.attachments.containers.ContainerType;
import mekanism.common.capabilities.holder.heat.IHeatCapacitorHolder;
import mekanism.common.capabilities.heat.CachedAmbientTemperature;
import mekanism.common.lib.multiblock.MultiblockManager;
import mekanism.common.tile.prefab.TileEntityMultiblock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class TileEntityRetroentropicArrayCasing extends TileEntityMultiblock<RetroentropicArrayData> {

    public TileEntityRetroentropicArrayCasing(BlockPos pos, BlockState state) {
        this(ModBlocks.RETROENTROPIC_ARRAY_CASING, pos, state);
    }

    public TileEntityRetroentropicArrayCasing(Holder<Block> provider, BlockPos pos, BlockState state) {
        super(provider, pos, state);
    }

    @Override
    public RetroentropicArrayData createMultiblock() {
        return new RetroentropicArrayData(this);
    }

    @Override
    public MultiblockManager<RetroentropicArrayData> getManager() {
        return ModMultiblockManagers.RETROENTROPIC_MANAGER;
    }

    /**
     * The array has no separate valve block, so the shared heat capacitor has to be exposed directly by every casing to
     * let adjacent coolers reach it. Item access is already provided for every multiblock node by {@link TileEntityMultiblock#getInitialInventory}.
     */
    @NotNull
    @Override
    protected IHeatCapacitorHolder getInitialHeatCapacitors(IContentsListener listener, CachedAmbientTemperature ambientTemperature) {
        return side -> getMultiblock().getHeatCapacitors(side);
    }

    @Override
    public boolean persists(ContainerType<?, ?, ?> type) {
        return type != ContainerType.HEAT && super.persists(type);
    }
}
