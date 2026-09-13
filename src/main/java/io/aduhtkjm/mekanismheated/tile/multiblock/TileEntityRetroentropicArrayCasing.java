package io.aduhtkjm.mekanismheated.tile.multiblock;

import io.aduhtkjm.mekanismheated.registries.ModBlocks;
import io.aduhtkjm.mekanismheated.registries.ModMultiblockManagers;
import mekanism.common.lib.multiblock.MultiblockManager;
import mekanism.common.tile.prefab.TileEntityMultiblock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

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

    @Override
    public boolean canBeMaster() {
        return false;
    }
}
