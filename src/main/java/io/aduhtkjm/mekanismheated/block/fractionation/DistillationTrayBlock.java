package io.aduhtkjm.mekanismheated.block.fractionation;

import mekanism.common.lib.multiblock.Structure;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import org.jetbrains.annotations.NotNull;
import io.aduhtkjm.mekanismheated.tile.multiblock.TileEntityFractionationBlock;

public class DistillationTrayBlock extends IronBarsBlock {

    public DistillationTrayBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(5.0F, 6.0F)
              .sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.DESTROY));
    }

    @Override
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!level.isClientSide) {
            requestStructureUpdate(level, pos);
        }
    }

    @Override
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean isMoving) {
        super.onRemove(state, level, pos, newState, isMoving);
        if (!level.isClientSide) {
            requestStructureUpdate(level, pos);
        }
    }

    /**
     * Unformed structures ignore neighbour changes inside them ({@code MultiblockData#isPositionInsideBounds} requires the
     * multiblock to be formed), so adding or removing trays after the shell was completed would never trigger a new
     * validation attempt: the shell alone is a valid tower, the first tray breaks it, and the remaining trays would be
     * ignored. Instead, any tray change nudges the fractionation tiles around it to re-run the formation protocol; in a
     * 4x4 tower every interior position is right next to the shell.
     */
    private static void requestStructureUpdate(Level level, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    cursor.setWithOffset(pos, x, y, z);
                    BlockEntity tile = level.getBlockEntity(cursor);
                    if (tile instanceof TileEntityFractionationBlock fractionationTile) {
                        Structure structure = fractionationTile.getStructure();
                        structure.markForUpdate(level, true);
                    }
                }
            }
        }
    }
}
