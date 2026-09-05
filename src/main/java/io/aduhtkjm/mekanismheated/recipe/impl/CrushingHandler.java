package io.aduhtkjm.mekanismheated.recipe.impl;

import io.aduhtkjm.mekanismheated.Mod;
import io.aduhtkjm.mekanismheated.recipe.CrushingRecipe;
import io.aduhtkjm.mekanismheated.recipe.CrushingRecipeInput;
import io.aduhtkjm.mekanismheated.recipe.ModRecipeTypes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

import java.util.List;

@EventBusSubscriber(modid = Mod.MODID)
public class CrushingHandler {

    private CrushingHandler() {
    }

    @SubscribeEvent
    public static void onBlockLand(EntityLeaveLevelEvent event) {
        Level level = event.getLevel();
        if (level.isClientSide())
            return;

        // Only handle a falling block that is about to be removed because it landed.
        if (!(event.getEntity() instanceof FallingBlockEntity fallingBlock)) {
            return;
        }
        Block landedBlock = fallingBlock.getBlockState().getBlock();

        // Define a small bounding box where the block landed, and find all dropped items underneath it.
        AABB searchArea = fallingBlock.getBoundingBox().inflate(0.5);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, searchArea);

        for (ItemEntity itemEntity : items) {
            ItemStack currentStack = itemEntity.getItem();
            if (currentStack.isEmpty()) {
                continue;
            }
            // Look up a crushing recipe matching both the item and the block that landed on it.
            RecipeHolder<CrushingRecipe> holder = level.getRecipeManager()
                  .getRecipeFor(ModRecipeTypes.TYPE_CRUSHING.value(), new CrushingRecipeInput(currentStack, landedBlock), level)
                  .orElse(null);
            if (holder == null) {
                continue;
            }
            ItemStack output = holder.value().getOutput(currentStack);
            if (!output.isEmpty()) {
                dropCrushOutput(level, itemEntity, output);
            }
        }
    }

    /**
     * Applies the crushing output to the target item entity, splitting it into multiple dropped
     * item instances so that no single stack exceeds the item's max stack size.
     */
    private static void dropCrushOutput(Level level, ItemEntity target, ItemStack output) {
        int maxStack = output.getMaxStackSize();
        int remaining = output.getCount();
        // The target entity takes the first chunk (up to a full stack).
        int first = Math.min(remaining, maxStack);
        target.setItem(output.copyWithCount(first));
        remaining -= first;
        // Any overflow is spawned as additional dropped items at the same location.
        while (remaining > 0) {
            int chunk = Math.min(remaining, maxStack);
            level.addFreshEntity(new ItemEntity(level, target.getX(), target.getY(), target.getZ(), output.copyWithCount(chunk)));
            remaining -= chunk;
        }
    }
}
