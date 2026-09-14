package io.aduhtkjm.mekanismheated.block.qec;

import io.aduhtkjm.mekanismheated.tile.TileEntityQuenchingEnrichmentChamber;
import mekanism.common.block.prefab.BlockTile;
import mekanism.common.content.blocktype.Machine;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * The Quenching Enrichment Chamber block. It reuses the Enrichment Chamber's block shape, only differing in its
 * (blue-tinted) textures.
 */
@MethodsReturnNonnullByDefault
public class QuenchingEnrichmentChamberBlock extends BlockTile<TileEntityQuenchingEnrichmentChamber, Machine<TileEntityQuenchingEnrichmentChamber>> {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public QuenchingEnrichmentChamberBlock(Machine<TileEntityQuenchingEnrichmentChamber> type, BlockBehaviour.Properties properties) {
        super(type, properties);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
}
