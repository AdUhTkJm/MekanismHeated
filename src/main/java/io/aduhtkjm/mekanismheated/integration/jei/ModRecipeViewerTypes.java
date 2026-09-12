package io.aduhtkjm.mekanismheated.integration.jei;

import io.aduhtkjm.mekanismheated.Mod;
import io.aduhtkjm.mekanismheated.ModLang;
import io.aduhtkjm.mekanismheated.recipe.AlloyRecipe;
import io.aduhtkjm.mekanismheated.recipe.AtmosphereFuelRecipe;
import io.aduhtkjm.mekanismheated.recipe.CondenserRecipe;
import io.aduhtkjm.mekanismheated.recipe.FractionationRecipe;
import io.aduhtkjm.mekanismheated.recipe.HeatedItemStackToFluidRecipe;
import io.aduhtkjm.mekanismheated.recipe.HeatedItemStackToItemStackRecipe;
import io.aduhtkjm.mekanismheated.recipe.QuenchingRecipe;
import io.aduhtkjm.mekanismheated.recipe.ReactionChamberRecipe;
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
    //Shares the heat smelter with the two categories above, so it needs its own title instead of the machine's name
    public static final ModRecipeViewerType<AlloyRecipe> ALLOYING =
          new ModRecipeViewerType<>(Mod.rl("alloying"), ModBlocks.HEAT_SMELTER, ModLang.GUI_ALLOYING.translate(), -28, -16, 144, 54);
    public static final ModRecipeViewerType<ShakerRecipe> SHAKING =
          new ModRecipeViewerType<>(Mod.rl("shaking"), ModBlocks.SHAKER, -6, -10, 164, 62);
    public static final ModRecipeViewerType<CondenserRecipe> CONDENSING =
          new ModRecipeViewerType<>(Mod.rl("condensing"), ModBlocks.CONDENSER, -6, -10, 164, 62);
    public static final ModRecipeViewerType<QuenchingRecipe> QUENCHING =
          new ModRecipeViewerType<>(Mod.rl("quenching"), ModBlocks.QUENCHING_ENRICHMENT_CHAMBER, -6, -10, 164, 62);
    public static final ModRecipeViewerType<ReactionChamberRecipe> REACTION =
          new ModRecipeViewerType<>(Mod.rl("reaction"), ModBlocks.REACTION_CHAMBER, -2, -12, 164, 72);
    public static final ModRecipeViewerType<AtmosphereFuelRecipe> ATMOSPHERE_FUEL =
          new ModRecipeViewerType<>(Mod.rl("atmosphere_fuel"), ModBlocks.ATMOSPHERE_HEATER, -6, -10, 164, 72);
    //Taller than the single-row machines: the fractionation tower stacks its output banks vertically.
    public static final ModRecipeViewerType<FractionationRecipe> FRACTIONATING =
          new ModRecipeViewerType<>(Mod.rl("fractionating"), ModBlocks.THERMAL_FRACTIONATION_CONTROLLER, -2, -12, 164, 96);
}
