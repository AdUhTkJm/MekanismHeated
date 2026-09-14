package io.aduhtkjm.mekanismheated.integration.jei;

import io.aduhtkjm.mekanismheated.Mod;
import io.aduhtkjm.mekanismheated.integration.jei.category.*;
import io.aduhtkjm.mekanismheated.recipe.FractionationRecipe;
import io.aduhtkjm.mekanismheated.recipe.ModRecipeTypes;
import java.util.ArrayList;
import java.util.List;
import mekanism.client.recipe_viewer.jei.CatalystRegistryHelper;
import mekanism.client.recipe_viewer.jei.MekanismJEI;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.lwjgl.system.NonnullDefault;

/**
 * JEI integration reusing Mekanism's recipe viewer framework (categories, catalysts, holder based recipe types). Our
 * recipes are fed straight from the vanilla recipe manager, as addons cannot create Mekanism's internal
 * {@code MekanismRecipeType} instances that the machines' {@code getRecipeType()} would normally return.
 *
 * <p>EMI skips the JEI plugins of every mod that ships an EMI plugin of its own, Mekanism included, so while EMI is
 * installed we register Mekanism's chemical ingredient type ourselves. See {@link #registerIngredients}.</p>
 */
@JeiPlugin
@NonnullDefault
public class MekanismHeatedJEI implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(Mod.MODID, "jei_plugin");
    }

    @Override
    public void registerIngredients(IModIngredientRegistration registry) {
        if (MekanismJEI.shouldLoad()) {
            //Mekanism's own JEI plugin registers its ingredient types for us
            return;
        }
        //EMI skips every JEI plugin whose namespace belongs to a mod that has an EMI plugin (see EMI's PluginCallerMixin),
        //and Mekanism ships MekanismEmi. So with EMI installed, JEI never learns about ChemicalStack, and every category
        //showing one of our chemicals (the atmosphere heater's gas fuel, the reaction chamber's gas pools) fails with
        //"Unknown ingredient type: class mekanism.api.chemical.ChemicalStack". Register the type in Mekanism's stead.
        //Note: shouldLoad() mirrors the EMI check EMI itself uses, so the type is never registered twice.
        //MekanismJEI keeps no state of its own, so instantiating it purely to reuse its registration logic is safe.
        new MekanismJEI().registerIngredients(registry);
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registry) {
        IGuiHelper guiHelper = registry.getJeiHelpers().getGuiHelper();
        registry.addRecipeCategories(
            new HeatedSmeltingRecipeCategory(guiHelper, ModRecipeViewerTypes.HEATED_SMELTING),
            new HeatedMeltingRecipeCategory(guiHelper, ModRecipeViewerTypes.HEATED_MELTING),
            new AlloyingRecipeCategory(guiHelper, ModRecipeViewerTypes.ALLOYING),
            new ShakerRecipeCategory(guiHelper, ModRecipeViewerTypes.SHAKING),
            new CondenserRecipeCategory(guiHelper, ModRecipeViewerTypes.CONDENSING),
            new QuenchingRecipeCategory(guiHelper, ModRecipeViewerTypes.QUENCHING),
            new ReactionChamberRecipeCategory(guiHelper, ModRecipeViewerTypes.REACTION),
            new AtmosphereFuelRecipeCategory(guiHelper, ModRecipeViewerTypes.ATMOSPHERE_FUEL),
            new FractionationRecipeCategory(guiHelper, ModRecipeViewerTypes.FRACTIONATING),
            new RetroentropicArrayRecipeCategory(guiHelper, ModRecipeViewerTypes.RETROENTROPIC_ARRAY_PROCESSING));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registry) {
        registerRecipes(registry, ModRecipeViewerTypes.HEATED_SMELTING, ModRecipeTypes.TYPE_HEATED_SMELTING);
        registerRecipes(registry, ModRecipeViewerTypes.HEATED_MELTING, ModRecipeTypes.TYPE_HEATED_MELTING);
        registerRecipes(registry, ModRecipeViewerTypes.ALLOYING, ModRecipeTypes.TYPE_ALLOYING);
        registerRecipes(registry, ModRecipeViewerTypes.SHAKING, ModRecipeTypes.TYPE_SHAKING);
        registerRecipes(registry, ModRecipeViewerTypes.CONDENSING, ModRecipeTypes.TYPE_CONDENSING);
        registerRecipes(registry, ModRecipeViewerTypes.QUENCHING, ModRecipeTypes.TYPE_QUENCHING);
        registerRecipes(registry, ModRecipeViewerTypes.REACTION, ModRecipeTypes.TYPE_REACTION);
        registerRecipes(registry, ModRecipeViewerTypes.ATMOSPHERE_FUEL, ModRecipeTypes.TYPE_ATMOSPHERE_FUEL);
        registerRecipes(registry, ModRecipeViewerTypes.RETROENTROPIC_ARRAY_PROCESSING, ModRecipeTypes.TYPE_RETROENTROPIC_ARRAY);
        //Both fractionation recipe forms share one category, so their recipes are collected separately and merged.
        registerFractionationRecipes(registry, ModRecipeViewerTypes.FRACTIONATING);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registry) {
        CatalystRegistryHelper.register(registry, ModRecipeViewerTypes.HEATED_SMELTING, ModRecipeViewerTypes.HEATED_MELTING,
              ModRecipeViewerTypes.ALLOYING, ModRecipeViewerTypes.SHAKING, ModRecipeViewerTypes.CONDENSING, ModRecipeViewerTypes.QUENCHING,
              ModRecipeViewerTypes.REACTION, ModRecipeViewerTypes.ATMOSPHERE_FUEL, ModRecipeViewerTypes.FRACTIONATING,
              ModRecipeViewerTypes.RETROENTROPIC_ARRAY_PROCESSING);
    }

    private static <I extends RecipeInput, RECIPE extends Recipe<I>>
    void registerRecipes(IRecipeRegistration registry, IRecipeViewerRecipeType<RECIPE> recipeViewerType, DeferredHolder<RecipeType<?>, RecipeType<RECIPE>> type) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            //No world loaded yet; JEI re-registers recipes once one exists
            return;
        }
        List<RecipeHolder<RECIPE>> recipes = level.getRecipeManager().getAllRecipesFor(type.value())
              .stream()
              .filter(holder -> !holder.value().isIncomplete())
              .toList();
        registry.addRecipes(MekanismJEI.holderRecipeType(recipeViewerType), recipes);
    }

    /**
     * Registers the fractionation tower's recipes. Unlike the single-type machines, the tower has two recipe forms (input
     * based and passive) registered under different recipe types but shown in one category, so their recipes are collected
     * from both types and merged into a single {@code RecipeHolder<FractionationRecipe>} list.
     */
    private static void registerFractionationRecipes(IRecipeRegistration registry, IRecipeViewerRecipeType<FractionationRecipe> recipeViewerType) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            //No world loaded yet; JEI re-registers recipes once one exists
            return;
        }
        List<RecipeHolder<FractionationRecipe>> recipes = new ArrayList<>();
        recipes.addAll(collectFractionationRecipes(level, ModRecipeTypes.TYPE_FRACTIONATING));
        recipes.addAll(collectFractionationRecipes(level, ModRecipeTypes.TYPE_FRACTIONATING_PASSIVE));
        registry.addRecipes(MekanismJEI.holderRecipeType(recipeViewerType), recipes);
    }

    @SuppressWarnings("unchecked")
    private static <T extends FractionationRecipe> List<RecipeHolder<FractionationRecipe>> collectFractionationRecipes(Level level,
          DeferredHolder<RecipeType<?>, RecipeType<T>> type) {
        //Both fractionation recipe types hold subtypes of FractionationRecipe, so this is safe once generics are erased.
        return ((List<RecipeHolder<FractionationRecipe>>) (List<?>) level.getRecipeManager().getAllRecipesFor(type.value()))
              .stream()
              .filter(holder -> !holder.value().isIncomplete())
              .toList();
    }
}
