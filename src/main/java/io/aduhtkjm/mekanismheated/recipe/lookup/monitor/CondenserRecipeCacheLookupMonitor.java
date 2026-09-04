package io.aduhtkjm.mekanismheated.recipe.lookup.monitor;

import io.aduhtkjm.mekanismheated.recipe.CondenserRecipe;
import io.aduhtkjm.mekanismheated.tile.TileEntityCondenser;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.common.recipe.lookup.monitor.RecipeCacheLookupMonitor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CondenserRecipeCacheLookupMonitor extends RecipeCacheLookupMonitor<CondenserRecipe> {

    private final TileEntityCondenser condenser;

    public CondenserRecipeCacheLookupMonitor(TileEntityCondenser condenser) {
        super(condenser);
        this.condenser = condenser;
    }

    @Nullable
    @Override
    public CachedRecipe<CondenserRecipe> createNewCachedRecipe(@NotNull CondenserRecipe recipe, int cacheIndex) {
        CachedRecipe<CondenserRecipe> cachedRecipe = super.createNewCachedRecipe(recipe, cacheIndex);
        if (cachedRecipe != null) {
            //Don't let the condenser process when it is too hot to make any progress. Returning zero operations
            // just idles the recipe for the tick, so it resumes automatically once the condenser cools down enough.
            cachedRecipe.setBaselineMaxOperations(() -> condenser.getBaselineMaxOperations());
        }
        return cachedRecipe;
    }

    @Override
    public boolean hasNoRecipe(int cacheIndex) {
        if (!super.hasNoRecipe(cacheIndex)) {
            return false;
        }
        //While the condenser is cold enough to run, keep re-checking so a recipe is picked up; once it is too hot
        // for any recipe to run, a confirmed "no recipe" can safely stand.
        return condenser.getSpeedFactor() <= 0;
    }
}
