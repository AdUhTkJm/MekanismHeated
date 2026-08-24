package io.aduhtkjm.mekanismheated.recipe.lookup.monitor;

import io.aduhtkjm.mekanismheated.recipe.HeatSmelterRecipe;
import io.aduhtkjm.mekanismheated.tile.TileEntityHeatSmelter;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.common.recipe.lookup.monitor.RecipeCacheLookupMonitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class HeatSmelterRecipeCacheLookupMonitor
      extends RecipeCacheLookupMonitor<HeatSmelterRecipe> {

    private final TileEntityHeatSmelter heatSmelter;

    public HeatSmelterRecipeCacheLookupMonitor(TileEntityHeatSmelter heatSmelter) {
        super(heatSmelter);
        this.heatSmelter = heatSmelter;
    }

    @Nullable
    @Override
    public CachedRecipe<HeatSmelterRecipe> createNewCachedRecipe(
          @NotNull HeatSmelterRecipe recipe, int cacheIndex) {
        CachedRecipe<HeatSmelterRecipe> cachedRecipe =
              super.createNewCachedRecipe(recipe, cacheIndex);
        if (cachedRecipe != null) {
            //Don't let the smelter process when it is too cold to make any progress or colder than the cached recipe's
            // own temperature threshold. Returning zero operations (rather than marking a RecipeError) just idles the
            // recipe for the tick, so it resumes automatically once the smelter is hot enough again. The
            // temperature-dependent processing speed itself is handled by HeatSensitiveOneInputCachedRecipe
            cachedRecipe.setBaselineMaxOperations(() ->
                  recipe.canProcess(heatSmelter) ? heatSmelter.getBaselineMaxOperations() : 0);
        }
        return cachedRecipe;
    }
}
