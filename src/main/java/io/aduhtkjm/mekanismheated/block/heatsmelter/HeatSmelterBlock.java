package io.aduhtkjm.mekanismheated.block.heatsmelter;

import io.aduhtkjm.mekanismheated.tile.TileEntityHeatSmelter;
import mekanism.common.block.prefab.BlockTile;
import mekanism.common.content.blocktype.Machine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.lwjgl.system.NonnullDefault;

@NonnullDefault
public class HeatSmelterBlock extends BlockTile<TileEntityHeatSmelter, Machine<TileEntityHeatSmelter>> {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // The block is a full solid cube for collision (the glass corner column is still solid),
    // so the shape and collision shape are the full block.
    private static final VoxelShape FULL_SHAPE = Shapes.block();

    // Opaque (occlusion) shape: the full cube minus the glass corner at (0, 2, 0)-(6, 14, 6).
    // Based on the opaque axis-aligned elements in models/block/heat_smelter.json.
    private static final VoxelShape OPAQUE_SHAPE_NORTH = Shapes.or(
            Block.box(6.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D),
            Block.box(0.0D, 0.0D, 6.0D, 6.0D, 16.0D, 16.0D),
            Block.box(0.0D, 0.0D, 0.0D, 6.0D, 2.0D, 6.0D),
            Block.box(0.0D, 14.0D, 0.0D, 6.0D, 16.0D, 6.0D)
    ).optimize();

    private static final VoxelShape OPAQUE_SHAPE_EAST = Shapes.or(
            Block.box(0.0D, 0.0D, 6.0D, 16.0D, 16.0D, 16.0D),
            Block.box(0.0D, 0.0D, 0.0D, 10.0D, 16.0D, 6.0D),
            Block.box(10.0D, 0.0D, 0.0D, 16.0D, 2.0D, 6.0D),
            Block.box(10.0D, 14.0D, 0.0D, 16.0D, 16.0D, 6.0D)
    ).optimize();

    private static final VoxelShape OPAQUE_SHAPE_SOUTH = Shapes.or(
            Block.box(0.0D, 0.0D, 0.0D, 10.0D, 16.0D, 16.0D),
            Block.box(10.0D, 0.0D, 0.0D, 16.0D, 16.0D, 10.0D),
            Block.box(10.0D, 0.0D, 10.0D, 16.0D, 2.0D, 16.0D),
            Block.box(10.0D, 14.0D, 10.0D, 16.0D, 16.0D, 16.0D)
    ).optimize();

    private static final VoxelShape OPAQUE_SHAPE_WEST = Shapes.or(
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 10.0D),
            Block.box(6.0D, 0.0D, 10.0D, 16.0D, 16.0D, 16.0D),
            Block.box(0.0D, 0.0D, 10.0D, 6.0D, 2.0D, 16.0D),
            Block.box(0.0D, 14.0D, 10.0D, 6.0D, 16.0D, 16.0D)
    ).optimize();

    public HeatSmelterBlock(Machine<TileEntityHeatSmelter> type, BlockBehaviour.Properties properties) {
        super(type, properties);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return FULL_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return FULL_SHAPE;
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return getOpaqueShapeForFacing(state.getValue(FACING));
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    private static VoxelShape getOpaqueShapeForFacing(Direction facing) {
        return switch (facing) {
            case EAST -> OPAQUE_SHAPE_EAST;
            case SOUTH -> OPAQUE_SHAPE_SOUTH;
            case WEST -> OPAQUE_SHAPE_WEST;
            default -> OPAQUE_SHAPE_NORTH;
        };
    }

}
