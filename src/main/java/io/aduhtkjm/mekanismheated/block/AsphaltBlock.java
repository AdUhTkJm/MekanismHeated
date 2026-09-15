package io.aduhtkjm.mekanismheated.block;

import io.aduhtkjm.mekanismheated.registries.ModFluids;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.NonnullDefault;

/**
 * Solid asphalt: the paved form of the mod's asphalt fluid, and a road surface that speeds players up
 * while they walk on it (see {@link io.aduhtkjm.mekanismheated.content.asphalt.AsphaltSpeedHandler}).
 *
 * <p>Implementing {@link BucketPickup} is what makes the block "liquid" again: vanilla's empty bucket
 * checks for this interface, so right-clicking the block with a bucket turns it back into an asphalt
 * bucket and leaves air behind. The other direction is {@link io.aduhtkjm.mekanismheated.item.ItemAsphaltBucket},
 * which pours the block back out instead of placing the fluid.
 */
@NonnullDefault
public class AsphaltBlock extends Block implements BucketPickup {

    public AsphaltBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack pickupBlock(@Nullable Player player, LevelAccessor level, BlockPos pos, BlockState state) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        return ModFluids.ASPHALT.getBucket().asStack();
    }

    @Override
    public Optional<SoundEvent> getPickupSound() {
        return Optional.of(SoundEvents.BUCKET_FILL);
    }
}
