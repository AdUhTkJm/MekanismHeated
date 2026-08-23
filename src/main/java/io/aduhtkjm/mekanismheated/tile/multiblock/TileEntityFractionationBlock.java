package io.aduhtkjm.mekanismheated.tile.multiblock;

import io.aduhtkjm.mekanismheated.content.fractionation.FractionationMultiblockData;
import io.aduhtkjm.mekanismheated.content.fractionation.ModFractionation;
import io.aduhtkjm.mekanismheated.registries.ModBlocks;
import mekanism.common.lib.multiblock.MultiblockManager;
import mekanism.common.tile.prefab.TileEntityMultiblock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class TileEntityFractionationBlock extends TileEntityMultiblock<FractionationMultiblockData> {

    public TileEntityFractionationBlock(BlockPos pos, BlockState state) {
        this(ModBlocks.THERMAL_FRACTIONATION_CASING, pos, state);
    }

    public TileEntityFractionationBlock(Holder<Block> provider, BlockPos pos, BlockState state) {
        super(provider, pos, state);
    }

    @Override
    public FractionationMultiblockData createMultiblock() {
        return new FractionationMultiblockData(this);
    }

    @Override
    public MultiblockManager<FractionationMultiblockData> getManager() {
        return ModFractionation.FRACTIONATION_MANAGER;
    }

    @Override
    public boolean canBeMaster() {
        return false;
    }
}
