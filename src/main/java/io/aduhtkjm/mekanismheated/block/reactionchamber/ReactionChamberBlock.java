package io.aduhtkjm.mekanismheated.block.reactionchamber;

import io.aduhtkjm.mekanismheated.tile.TileEntityReactionChamber;
import mekanism.common.block.prefab.BlockTile;
import mekanism.common.content.blocktype.Machine;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

@MethodsReturnNonnullByDefault
public class ReactionChamberBlock extends BlockTile<TileEntityReactionChamber, Machine<TileEntityReactionChamber>> {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public ReactionChamberBlock(Machine<TileEntityReactionChamber> type, BlockBehaviour.Properties properties) {
        super(type, properties);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
}
