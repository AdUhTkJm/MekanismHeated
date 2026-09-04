package io.aduhtkjm.mekanismheated.integration.jei;

import io.aduhtkjm.mekanismheated.Mod;
import io.aduhtkjm.mekanismheated.recipe.CondenserRecipe;
import io.aduhtkjm.mekanismheated.recipe.HeatedItemStackToFluidRecipe;
import io.aduhtkjm.mekanismheated.recipe.HeatedItemStackToItemStackRecipe;
import io.aduhtkjm.mekanismheated.recipe.ShakerRecipe;
import io.aduhtkjm.mekanismheated.registries.ModBlocks;

public final class ModRecipeViewerTypes {

    private ModRecipeViewerTypes() {
    }

    /** Dimensions mirror Mekanism's single-input machine categories, which share our slot grid. */
    public static final ModRecipeViewerType<HeatedItemStackToItemStackRecipe> HEATED_SMELTING =
          new ModRecipeViewerType<>(Mod.rl("heated_smelting"), ModBlocks.HEAT_SMELTER, -28, -16, 144, 54);
    public static final ModRecipeViewerType<HeatedItemStackToFluidRecipe> HEATED_MELTING =
          new ModRecipeViewerType<>(Mod.rl("heated_melting"), ModBlocks.HEAT_SMELTER, -28, -16, 144, 54);
    public static final ModRecipeViewerType<ShakerRecipe> SHAKING =
          new ModRecipeViewerType<>(Mod.rl("shaking"), ModBlocks.SHAKER, -6, -10, 164, 62);
    public static final ModRecipeViewerType<CondenserRecipe> CONDENSING =
          new ModRecipeViewerType<>(Mod.rl("condensing"), ModBlocks.CONDENSER, -6, -10, 164, 62);
}
