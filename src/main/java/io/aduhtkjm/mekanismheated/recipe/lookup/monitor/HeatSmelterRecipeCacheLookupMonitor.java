package io.aduhtkjm.mekanismheated.recipe.lookup.monitor;

import io.aduhtkjm.mekanismheated.tile.TileEntityHeatSmelter;
import mekanism.api.recipes.ItemStackToItemStackRecipe;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.common.recipe.lookup.monitor.RecipeCacheLookupMonitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HeatSmelterRecipeCacheLookupMonitor extends RecipeCacheLookupMonitor<ItemStackToItemStackRecipe> {

    private final TileEntityHeatSmelter heatSmelter;

    public HeatSmelterRecipeCacheLookupMonitor(TileEntityHeatSmelter heatSmelter) {
        super(heatSmelter);
        this.heatSmelter = heatSmelter;
    }

    @Nullable
    @Override
    public CachedRecipe<ItemStackToItemStackRecipe> createNewCachedRecipe(@NotNull ItemStackToItemStackRecipe recipe, int cacheIndex) {
        CachedRecipe<ItemStackToItemStackRecipe> cachedRecipe = super.createNewCachedRecipe(recipe, cacheIndex);
        if (cachedRecipe != null) {
            //Speed up or slow down processing based on the smelter's current temperature
            cachedRecipe.setRequiredTicks(heatSmelter::getTicksRequiredForTemperature)
                  .setBaselineMaxOperations(heatSmelter::getBaselineMaxOperations);
        }
        return cachedRecipe;
    }
}
