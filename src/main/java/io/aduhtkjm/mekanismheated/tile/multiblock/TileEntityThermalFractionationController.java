package io.aduhtkjm.mekanismheated.tile.multiblock;

import io.aduhtkjm.mekanismheated.content.fractionation.FractionationMultiblockData;
import io.aduhtkjm.mekanismheated.registries.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class TileEntityThermalFractionationController extends TileEntityFractionationBlock {

    public TileEntityThermalFractionationController(BlockPos pos, BlockState state) {
        super(ModBlocks.THERMAL_FRACTIONATION_CONTROLLER, pos, state);
        delaySupplier = NO_DELAY;
    }

    public TileEntityThermalFractionationController(Holder<Block> provider, BlockPos pos, BlockState state) {
        super(provider, pos, state);
        delaySupplier = NO_DELAY;
    }

    @Override
    protected boolean onUpdateServer(FractionationMultiblockData multiblock) {
        boolean needsPacket = super.onUpdateServer(multiblock);
        setActive(multiblock.isFormed());
        return needsPacket;
    }

    @Override
    public boolean canBeMaster() {
        return true;
    }
}
