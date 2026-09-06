package io.aduhtkjm.mekanismheated.integration.jei;

import io.aduhtkjm.mekanismheated.Mod;
import io.aduhtkjm.mekanismheated.integration.jei.category.CondenserRecipeCategory;
import io.aduhtkjm.mekanismheated.integration.jei.category.HeatedMeltingRecipeCategory;
import io.aduhtkjm.mekanismheated.integration.jei.category.HeatedSmeltingRecipeCategory;
import io.aduhtkjm.mekanismheated.integration.jei.category.ReactionChamberRecipeCategory;
import io.aduhtkjm.mekanismheated.integration.jei.category.ShakerRecipeCategory;
import io.aduhtkjm.mekanismheated.recipe.ModRecipeTypes;
import java.util.List;
import mekanism.client.recipe_viewer.jei.CatalystRegistryHelper;
import mekanism.client.recipe_viewer.jei.MekanismJEI;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
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
 */
@JeiPlugin
@NonnullDefault
public class MekanismHeatedJEI implements IModPlugin {

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(Mod.MODID, "jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registry) {
        IGuiHelper guiHelper = registry.getJeiHelpers().getGuiHelper();
        registry.addRecipeCategories(
              new HeatedSmeltingRecipeCategory(guiHelper, ModRecipeViewerTypes.HEATED_SMELTING),
              new HeatedMeltingRecipeCategory(guiHelper, ModRecipeViewerTypes.HEATED_MELTING),
              new ShakerRecipeCategory(guiHelper, ModRecipeViewerTypes.SHAKING),
              new CondenserRecipeCategory(guiHelper, ModRecipeViewerTypes.CONDENSING),
              new ReactionChamberRecipeCategory(guiHelper, ModRecipeViewerTypes.REACTION));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registry) {
        registerRecipes(registry, ModRecipeViewerTypes.HEATED_SMELTING, ModRecipeTypes.TYPE_HEATED_SMELTING);
        registerRecipes(registry, ModRecipeViewerTypes.HEATED_MELTING, ModRecipeTypes.TYPE_HEATED_MELTING);
        registerRecipes(registry, ModRecipeViewerTypes.SHAKING, ModRecipeTypes.TYPE_SHAKING);
        registerRecipes(registry, ModRecipeViewerTypes.CONDENSING, ModRecipeTypes.TYPE_CONDENSING);
        registerRecipes(registry, ModRecipeViewerTypes.REACTION, ModRecipeTypes.TYPE_REACTION);
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registry) {
        CatalystRegistryHelper.register(registry, ModRecipeViewerTypes.HEATED_SMELTING, ModRecipeViewerTypes.HEATED_MELTING,
              ModRecipeViewerTypes.SHAKING, ModRecipeViewerTypes.CONDENSING, ModRecipeViewerTypes.REACTION);
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
}
