package io.aduhtkjm.mekanismheated.block;

import io.aduhtkjm.mekanismheated.registries.ModTileEntityTypes;
import io.aduhtkjm.mekanismheated.tile.TileEntityFusedPipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.NonnullDefault;

/**
 * The fused pipe block. A plain full cube for now; connection visuals come in a later phase.
 */
@NonnullDefault
public class BlockFusedPipe extends Block implements EntityBlock {

    public BlockFusedPipe(Properties properties) {
        super(properties.pushReaction(PushReaction.BLOCK));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModTileEntityTypes.FUSED_PIPE.get().create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == ModTileEntityTypes.FUSED_PIPE.get()
              ? (BlockEntityTicker<T>) ModTileEntityTypes.FUSED_PIPE.getTicker(!level.isClientSide)
              : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TileEntityFusedPipe pipe) {
            pipe.onWorldJoin();
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        Direction side = Direction.getNearest(neighborPos.getX() - pos.getX(), neighborPos.getY() - pos.getY(), neighborPos.getZ() - pos.getZ());
        if (level.getBlockEntity(pos) instanceof TileEntityFusedPipe pipe) {
            pipe.onNeighborBlockChange(side);
        }
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
        if (!level.isClientSide && blockEntity instanceof TileEntityFusedPipe pipe && !pipe.isRemoved()) {
            //Drop ourselves with the config preserved; on placement vanilla applies the
            // BLOCK_ENTITY_DATA component back into the created block entity
            ItemStack drop = new ItemStack(this);
            CompoundTag data = pipe.saveForDrop(new CompoundTag(), level.registryAccess());
            drop.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(data));
            level.addFreshEntity(new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, drop));
        }
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
    }
}
