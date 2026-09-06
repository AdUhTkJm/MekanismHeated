package io.aduhtkjm.mekanismheated.block.reactionchamber;

import io.aduhtkjm.mekanismheated.tile.TileEntityReactionChamber;
import mekanism.common.block.prefab.BlockTile;
import mekanism.common.content.blocktype.Machine;
import net.minecraft.core.BlockPos;
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
public class ReactionChamberBlock extends BlockTile<TileEntityReactionChamber, Machine<TileEntityReactionChamber>> {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // The block is a full solid cube for collision (the glass walls are still solid),
    // so the shape and collision shape are the full block.
    private static final VoxelShape FULL_SHAPE = Shapes.block();

    // Opaque (occlusion) shape: the full block minus the transparent glass walls.
    // Based on the opaque axis-aligned elements in models/block/reaction_chamber.json:
    // the bottom cap, the top cap, and the central pillar. This model is symmetric about
    // the vertical axis, so a single shape works for every facing.
    private static final VoxelShape OPAQUE_SHAPE = Shapes.or(
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D),
            Block.box(0.0D, 14.0D, 0.0D, 16.0D, 16.0D, 16.0D),
            Block.box(6.5D, 2.0D, 6.5D, 9.5D, 14.0D, 9.5D)
    ).optimize();

    public ReactionChamberBlock(Machine<TileEntityReactionChamber> type, BlockBehaviour.Properties properties) {
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
        return OPAQUE_SHAPE;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

}
