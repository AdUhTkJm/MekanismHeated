package io.aduhtkjm.mekanismheated.block.cooler;

import io.aduhtkjm.mekanismheated.tile.TileEntityCooler;
import mekanism.common.block.prefab.BlockTile;
import mekanism.common.content.blocktype.Machine;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.lwjgl.system.NonnullDefault;

@NonnullDefault
public class CoolerBlock extends BlockTile<TileEntityCooler, Machine<TileEntityCooler>> {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public CoolerBlock(Machine<TileEntityCooler> type, BlockBehaviour.Properties properties) {
        super(type, properties);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
}
