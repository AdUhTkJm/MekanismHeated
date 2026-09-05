package io.aduhtkjm.mekanismheated.recipe;

import io.aduhtkjm.mekanismheated.Mod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModRecipeTypes {

    private ModRecipeTypes() {
    }

    public static final ResourceLocation NAME_FUEL_CONVERSION = Mod.rl("fuel_conversion");
    public static final ResourceLocation NAME_CRUSHING = Mod.rl("crushing");
    public static final ResourceLocation NAME_HEATED_SMELTING = Mod.rl("heated_smelting");
    public static final ResourceLocation NAME_HEATED_MELTING = Mod.rl("heated_melting");
    public static final ResourceLocation NAME_SHAKING = Mod.rl("shaking");
    public static final ResourceLocation NAME_FRACTIONATING = Mod.rl("fractionating");
    public static final ResourceLocation NAME_ALLOYING = Mod.rl("alloying");
    public static final ResourceLocation NAME_CONDENSING = Mod.rl("condensing");
    public static final ResourceLocation NAME_REACTION = Mod.rl("reaction");

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, Mod.MODID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<ItemStackToHeatRecipe>> TYPE_FUEL_CONVERSION =
          RECIPE_TYPES.register(NAME_FUEL_CONVERSION.getPath(), () -> new RecipeType<>() {});
    public static final DeferredHolder<RecipeType<?>, RecipeType<CrushingRecipe>> TYPE_CRUSHING =
          RECIPE_TYPES.register(NAME_CRUSHING.getPath(), () -> new RecipeType<>() {});
    public static final DeferredHolder<RecipeType<?>, RecipeType<HeatedItemStackToItemStackRecipe>> TYPE_HEATED_SMELTING =
        RECIPE_TYPES.register(NAME_HEATED_SMELTING.getPath(), () -> new RecipeType<>() {});
    public static final DeferredHolder<RecipeType<?>, RecipeType<HeatedItemStackToFluidRecipe>> TYPE_HEATED_MELTING =
        RECIPE_TYPES.register(NAME_HEATED_MELTING.getPath(), () -> new RecipeType<>() {});
    public static final DeferredHolder<RecipeType<?>, RecipeType<ShakerRecipe>> TYPE_SHAKING =
        RECIPE_TYPES.register(NAME_SHAKING.getPath(), () -> new RecipeType<>() {});
    public static final DeferredHolder<RecipeType<?>, RecipeType<FractionationRecipe>> TYPE_FRACTIONATING =
        RECIPE_TYPES.register(NAME_FRACTIONATING.getPath(), () -> new RecipeType<>() {});
    public static final DeferredHolder<RecipeType<?>, RecipeType<AlloyRecipe>> TYPE_ALLOYING =
        RECIPE_TYPES.register(NAME_ALLOYING.getPath(), () -> new RecipeType<>() {});
    public static final DeferredHolder<RecipeType<?>, RecipeType<CondenserRecipe>> TYPE_CONDENSING =
        RECIPE_TYPES.register(NAME_CONDENSING.getPath(), () -> new RecipeType<>() {});
    public static final DeferredHolder<RecipeType<?>, RecipeType<ReactionChamberRecipe>> TYPE_REACTION =
        RECIPE_TYPES.register(NAME_REACTION.getPath(), () -> new RecipeType<>() {});
}