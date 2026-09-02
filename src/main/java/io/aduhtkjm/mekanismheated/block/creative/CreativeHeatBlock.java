package io.aduhtkjm.mekanismheated.block.creative;

import io.aduhtkjm.mekanismheated.tile.TileEntityCreativeHeatBlock;
import mekanism.common.block.prefab.BlockTile;
import mekanism.common.content.blocktype.Machine;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class CreativeHeatBlock extends BlockTile<TileEntityCreativeHeatBlock, Machine<TileEntityCreativeHeatBlock>> {

    public CreativeHeatBlock(Machine<TileEntityCreativeHeatBlock> type, BlockBehaviour.Properties properties) {
        super(type, properties);
    }
}
