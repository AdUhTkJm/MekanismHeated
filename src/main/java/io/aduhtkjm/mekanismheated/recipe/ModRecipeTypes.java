package io.aduhtkjm.mekanismheated.recipe;

import io.aduhtkjm.mekanismheated.Mod;
import mekanism.api.recipes.ItemStackToItemStackRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModRecipeTypes {

    private ModRecipeTypes() {
    }

    public static final ResourceLocation NAME_FUEL_CONVERSION = ResourceLocation.fromNamespaceAndPath(Mod.MODID, "fuel_conversion");
    public static final ResourceLocation NAME_HEATED_SMELTING = ResourceLocation.fromNamespaceAndPath(Mod.MODID, "heated_smelting");
    public static final ResourceLocation NAME_HEATED_MELTING = ResourceLocation.fromNamespaceAndPath(Mod.MODID, "heated_melting");

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, Mod.MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<ItemStackToHeatRecipe>> TYPE_FUEL_CONVERSION =
          RECIPE_TYPES.register(NAME_FUEL_CONVERSION.getPath(), () -> new RecipeType<>() {
          });

    /**
     * Recipe type for {@link HeatedItemStackToItemStackRecipe}s. The type is registered as {@code RecipeType<ItemStackToItemStackRecipe>}
     * because that is the generic the base {@link ItemStackToItemStackRecipe#getType()} returns.
     */
    public static final DeferredHolder<RecipeType<?>, RecipeType<ItemStackToItemStackRecipe>> TYPE_HEATED_SMELTING =
          RECIPE_TYPES.register(NAME_HEATED_SMELTING.getPath(), () -> new RecipeType<>() {
          });

    public static final DeferredHolder<RecipeType<?>, RecipeType<HeatedItemStackToFluidRecipe>> TYPE_HEATED_MELTING =
          RECIPE_TYPES.register(NAME_HEATED_MELTING.getPath(), () -> new RecipeType<>() {
          });
}