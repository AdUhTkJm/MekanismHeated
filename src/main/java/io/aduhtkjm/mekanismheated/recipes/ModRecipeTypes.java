package io.aduhtkjm.mekanismheated.recipes;

import io.aduhtkjm.mekanismheated.MekanismHeated;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModRecipeTypes {

    private ModRecipeTypes() {
    }

    public static final ResourceLocation NAME_FUEL_CONVERSION = ResourceLocation.fromNamespaceAndPath(MekanismHeated.MODID, "fuel_conversion");

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, MekanismHeated.MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<ItemStackToHeatRecipe>> TYPE_FUEL_CONVERSION =
          RECIPE_TYPES.register(NAME_FUEL_CONVERSION.getPath(), () -> new RecipeType<>() {
          });
}
