package io.aduhtkjm.mekanismheated.block.shaker;

import mekanism.common.block.prefab.BlockTile;
import mekanism.common.content.blocktype.Machine;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

@MethodsReturnNonnullByDefault
public class ShakerBlock extends BlockTile<ShakerBlockEntity, Machine<ShakerBlockEntity>> {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // Based on the axis-aligned elements in models/block/shaker/model.json. The zero-thickness
    // glass planes use a one-pixel collision thickness.
    private static final VoxelShape OPAQUE_SHAPE_NORTH = Shapes.or(
            Block.box(3.0D, 0.0D, 0.0D, 13.0D, 6.0D, 13.0D),
            Block.box(3.0D, 0.0D, 13.0D, 13.0D, 16.0D, 16.0D),
            Block.box(3.0D, 13.0D, 10.0D, 13.0D, 16.0D, 13.0D),
            Block.box(0.0D, 0.0D, 0.0D, 3.0D, 16.0D, 16.0D),
            Block.box(13.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D),
            Block.box(4.0D, 6.0D, 3.0D, 12.0D, 7.0D, 11.0D)
    ).optimize();

    private static final VoxelShape SHAPE_NORTH = Shapes.or(
            OPAQUE_SHAPE_NORTH,
            Block.box(3.0D, 6.0D, 0.0D, 13.0D, 16.0D, 1.0D),
            Block.box(3.0D, 15.0D, 0.0D, 13.0D, 16.0D, 10.0D)
    ).optimize();

    private static final VoxelShape OPAQUE_SHAPE_EAST = Shapes.or(
            Block.box(3.0D, 0.0D, 3.0D, 16.0D, 6.0D, 13.0D),
            Block.box(0.0D, 0.0D, 3.0D, 3.0D, 16.0D, 13.0D),
            Block.box(3.0D, 13.0D, 3.0D, 6.0D, 16.0D, 13.0D),
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 3.0D),
            Block.box(0.0D, 0.0D, 13.0D, 16.0D, 16.0D, 16.0D),
            Block.box(5.0D, 6.0D, 4.0D, 13.0D, 7.0D, 12.0D)
    ).optimize();

    private static final VoxelShape SHAPE_EAST = Shapes.or(
            OPAQUE_SHAPE_EAST,
            Block.box(15.0D, 6.0D, 3.0D, 16.0D, 16.0D, 13.0D),
            Block.box(6.0D, 15.0D, 3.0D, 16.0D, 16.0D, 13.0D)
    ).optimize();

    private static final VoxelShape OPAQUE_SHAPE_SOUTH = Shapes.or(
            Block.box(3.0D, 0.0D, 3.0D, 13.0D, 6.0D, 16.0D),
            Block.box(3.0D, 0.0D, 0.0D, 13.0D, 16.0D, 3.0D),
            Block.box(3.0D, 13.0D, 3.0D, 13.0D, 16.0D, 6.0D),
            Block.box(13.0D, 0.0D, 0.0D, 16.0D, 16.0D, 16.0D),
            Block.box(0.0D, 0.0D, 0.0D, 3.0D, 16.0D, 16.0D),
            Block.box(4.0D, 6.0D, 5.0D, 12.0D, 7.0D, 13.0D)
    ).optimize();

    private static final VoxelShape SHAPE_SOUTH = Shapes.or(
            OPAQUE_SHAPE_SOUTH,
            Block.box(3.0D, 6.0D, 15.0D, 13.0D, 16.0D, 16.0D),
            Block.box(3.0D, 15.0D, 6.0D, 13.0D, 16.0D, 16.0D)
    ).optimize();

    private static final VoxelShape OPAQUE_SHAPE_WEST = Shapes.or(
            Block.box(0.0D, 0.0D, 3.0D, 13.0D, 6.0D, 13.0D),
            Block.box(13.0D, 0.0D, 3.0D, 16.0D, 16.0D, 13.0D),
            Block.box(10.0D, 13.0D, 3.0D, 13.0D, 16.0D, 13.0D),
            Block.box(0.0D, 0.0D, 13.0D, 16.0D, 16.0D, 16.0D),
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 16.0D, 3.0D),
            Block.box(3.0D, 6.0D, 4.0D, 11.0D, 7.0D, 12.0D)
    ).optimize();

    private static final VoxelShape SHAPE_WEST = Shapes.or(
            OPAQUE_SHAPE_WEST,
            Block.box(0.0D, 6.0D, 3.0D, 1.0D, 16.0D, 13.0D),
            Block.box(0.0D, 15.0D, 3.0D, 10.0D, 16.0D, 13.0D)
    ).optimize();

    public ShakerBlock(Machine<ShakerBlockEntity> type, BlockBehaviour.Properties properties) {
        super(type, properties);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShapeForFacing(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShapeForFacing(state.getValue(FACING));
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return getOpaqueShapeForFacing(state.getValue(FACING));
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    private static VoxelShape getShapeForFacing(Direction facing) {
        return switch (facing) {
            case EAST -> SHAPE_EAST;
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_NORTH;
        };
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
