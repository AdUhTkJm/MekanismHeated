package io.aduhtkjm.mekanismheated.recipe;

import mekanism.api.annotations.NothingNullByDefault;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Helpers for looking up the mod's recipes from the world's recipe manager.
 */
@NothingNullByDefault
public class ModRecipeType {

    private ModRecipeType() {
    }

    @Nullable
    public static ItemStackToHeatRecipe findFirstFuelConversion(@Nullable Level level, ItemStack input) {
        if (level == null || input.isEmpty()) {
            return null;
        }
        return level.getRecipeManager()
              .getRecipeFor(ModRecipeTypes.TYPE_FUEL_CONVERSION.value(), new SingleRecipeInput(input), level)
              .map(RecipeHolder::value)
              .orElse(null);
    }

    @Nullable
    public static HeatedItemStackToFluidRecipe findFirstHeatedMelting(@Nullable Level level, ItemStack input) {
        if (level == null || input.isEmpty()) {
            return null;
        }
        return level.getRecipeManager()
              .getRecipeFor(ModRecipeTypes.TYPE_HEATED_MELTING.value(), new SingleRecipeInput(input), level)
              .map(RecipeHolder::value)
              .orElse(null);
    }
}