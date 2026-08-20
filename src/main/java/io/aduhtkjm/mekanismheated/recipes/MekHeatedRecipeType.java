package io.aduhtkjm.mekanismheated.recipes;

import mekanism.api.annotations.NothingNullByDefault;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Helper for looking up {@link ItemStackToHeatRecipe}s from the world's recipe manager.
 */
@NothingNullByDefault
public class MekHeatedRecipeType {

    private MekHeatedRecipeType() {
    }

    @Nullable
    public static ItemStackToHeatRecipe findFirstFuelConversion(@Nullable Level level, ItemStack input) {
        if (level == null || input.isEmpty()) {
            return null;
        }
        return level.getRecipeManager()
              .getRecipeFor(MekHeatedRecipeTypes.TYPE_FUEL_CONVERSION.value(), new SingleRecipeInput(input), level)
              .map(RecipeHolder::value)
              .orElse(null);
    }
}