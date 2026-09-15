package io.aduhtkjm.mekanismheated.item;

import io.aduhtkjm.mekanismheated.registries.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The asphalt bucket. Asphalt is a fluid everywhere it is moved around - tanks, pipes, machines and the
 * fluid capability attached in {@code Mod#registerBucketCapabilities} all still see plain asphalt fluid -
 * but pouring it out in the world sets a solid {@link io.aduhtkjm.mekanismheated.block.AsphaltBlock}
 * instead of the fluid block, because that is what asphalt looks like once it has set. An empty bucket
 * over an asphalt block turns it back into this bucket.
 *
 * <p>Vanilla's {@link BucketItem} funnels both the right-click path and Mekanism's dispenser path through
 * {@link #emptyContents(Player, Level, BlockPos, BlockHitResult, ItemStack)}, and the four argument
 * overload delegates to that one, so overriding the five argument version is all it takes.
 */
public class ItemAsphaltBucket extends BucketItem {

    public ItemAsphaltBucket(Fluid fluid, Item.Properties properties) {
        super(fluid, properties);
    }

    @Override
    public boolean emptyContents(@Nullable Player player, Level level, BlockPos pos, @Nullable BlockHitResult hitResult, @Nullable ItemStack container) {
        BlockState state = level.getBlockState(pos);
        // Replacing air is the common case; the other replaceable blocks let the bucket be poured onto
        // tall grass or into water, and anything solid (a chest, a fence, ...) just refuses.
        if (!state.canBeReplaced()) {
            return false;
        }
        level.setBlock(pos, ModBlocks.ASPHALT_BLOCK.defaultState(), Block.UPDATE_ALL);
        playEmptySound(player, level, pos);
        return true;
    }
}
