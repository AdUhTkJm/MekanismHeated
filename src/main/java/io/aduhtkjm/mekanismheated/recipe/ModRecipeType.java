package io.aduhtkjm.mekanismheated.recipe;

import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.recipes.MekanismRecipe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredHolder;
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
    public static <RECIPE extends MekanismRecipe<SingleRecipeInput>> RECIPE
    findFirstSingleItemRecipe(DeferredHolder<RecipeType<?>, RecipeType<RECIPE>> record, Level level, ItemStack input) {
        if (input.isEmpty()) {
            return null;
        }
        return level.getRecipeManager()
                .getRecipeFor(record.value(), new SingleRecipeInput(input), level)
                .map(RecipeHolder::value)
                .orElse(null);
    }
}
