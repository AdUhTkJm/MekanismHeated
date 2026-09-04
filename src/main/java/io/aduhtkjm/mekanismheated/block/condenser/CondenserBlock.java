package io.aduhtkjm.mekanismheated.block.condenser;

import io.aduhtkjm.mekanismheated.tile.TileEntityCondenser;
import mekanism.common.block.prefab.BlockTile;
import mekanism.common.content.blocktype.Machine;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

@MethodsReturnNonnullByDefault
public class CondenserBlock extends BlockTile<TileEntityCondenser, Machine<TileEntityCondenser>> {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public CondenserBlock(Machine<TileEntityCondenser> type, BlockBehaviour.Properties properties) {
        super(type, properties);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
}
