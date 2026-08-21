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
            //Don't let the smelter process at all when it is too cold to make any progress. The temperature-dependent
            // processing speed itself is handled by HeatSensitiveOneInputCachedRecipe
            cachedRecipe.setBaselineMaxOperations(heatSmelter::getBaselineMaxOperations);
        }
        return cachedRecipe;
    }
}
