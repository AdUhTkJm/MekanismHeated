package io.aduhtkjm.mekanismheated.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.aduhtkjm.mekanismheated.Mod;
import mekanism.api.SerializationConstants;
import mekanism.api.SerializerHelper;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import mekanism.common.recipe.serializer.MekanismRecipeSerializer;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModRecipeSerializers {

    private ModRecipeSerializers() {
    }

    /**
     * Codec for the temperature threshold (in Kelvin) shared by the heated recipe types. The threshold must be greater than
     * zero, matching the validation in the recipe constructors.
     */
    private static final Codec<Double> TEMPERATURE_THRESHOLD_CODEC = Codec.DOUBLE.validate(value ->
          value <= 0 ? DataResult.error(() -> "Temperature threshold must be greater than zero") : DataResult.success(value));

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Mod.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<BasicItemStackToHeatRecipe>> FUEL_CONVERSION =
          RECIPE_SERIALIZERS.register("fuel_conversion", () -> new MekanismRecipeSerializer<>(
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                      ItemStackIngredient.CODEC.fieldOf(SerializationConstants.INPUT).forGetter(BasicItemStackToHeatRecipe::getInput),
                      SerializerHelper.POSITIVE_NONZERO_LONG_CODEC.fieldOf(SerializationConstants.OUTPUT).forGetter(BasicItemStackToHeatRecipe::getOutputRaw)
                ).apply(instance, BasicItemStackToHeatRecipe::new)),
                StreamCodec.composite(
                      ItemStackIngredient.STREAM_CODEC, BasicItemStackToHeatRecipe::getInput,
                      ByteBufCodecs.VAR_LONG, BasicItemStackToHeatRecipe::getOutputRaw,
                      BasicItemStackToHeatRecipe::new
                )));

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<BasicHeatedItemStackToItemStackRecipe>> HEATED_SMELTING =
          RECIPE_SERIALIZERS.register("heated_smelting", () -> new MekanismRecipeSerializer<>(
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                      ItemStackIngredient.CODEC.fieldOf(SerializationConstants.INPUT).forGetter(BasicHeatedItemStackToItemStackRecipe::getInput),
                      ItemStack.CODEC.fieldOf(SerializationConstants.OUTPUT).forGetter(BasicHeatedItemStackToItemStackRecipe::getOutputRaw),
                      TEMPERATURE_THRESHOLD_CODEC.fieldOf("temperature").forGetter(HeatedItemStackToItemStackRecipe::getTemperatureThreshold)
                ).apply(instance, BasicHeatedItemStackToItemStackRecipe::new)),
                StreamCodec.composite(
                      ItemStackIngredient.STREAM_CODEC, BasicHeatedItemStackToItemStackRecipe::getInput,
                      ItemStack.STREAM_CODEC, BasicHeatedItemStackToItemStackRecipe::getOutputRaw,
                      ByteBufCodecs.DOUBLE, HeatedItemStackToItemStackRecipe::getTemperatureThreshold,
                      BasicHeatedItemStackToItemStackRecipe::new
                )));

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<BasicHeatedItemStackToFluidRecipe>> HEATED_MELTING =
          RECIPE_SERIALIZERS.register("heated_melting", () -> new MekanismRecipeSerializer<>(
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                      ItemStackIngredient.CODEC.fieldOf(SerializationConstants.INPUT).forGetter(BasicHeatedItemStackToFluidRecipe::getInput),
                      FluidStack.CODEC.fieldOf(SerializationConstants.OUTPUT).forGetter(BasicHeatedItemStackToFluidRecipe::getOutputRaw),
                      TEMPERATURE_THRESHOLD_CODEC.fieldOf("temperature").forGetter(HeatedItemStackToFluidRecipe::getTemperatureThreshold)
                ).apply(instance, BasicHeatedItemStackToFluidRecipe::new)),
                StreamCodec.composite(
                      ItemStackIngredient.STREAM_CODEC, BasicHeatedItemStackToFluidRecipe::getInput,
                      FluidStack.STREAM_CODEC, BasicHeatedItemStackToFluidRecipe::getOutputRaw,
                      ByteBufCodecs.DOUBLE, HeatedItemStackToFluidRecipe::getTemperatureThreshold,
                      BasicHeatedItemStackToFluidRecipe::new
                )));

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<BasicShakerRecipe>> SHAKING =
          RECIPE_SERIALIZERS.register("shaking", () -> new MekanismRecipeSerializer<>(
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                      ItemStackIngredient.CODEC.fieldOf(SerializationConstants.INPUT).forGetter(BasicShakerRecipe::getInput),
                      FluidStackIngredient.CODEC.optionalFieldOf(SerializationConstants.FLUID_INPUT).forGetter(BasicShakerRecipe::getFluidInput),
                      BasicShakerRecipe.OUTPUT_CODEC.fieldOf(SerializationConstants.OUTPUT).forGetter(BasicShakerRecipe::getOutputRaw)
                ).apply(instance, BasicShakerRecipe::new)),
                StreamCodec.composite(
                      ItemStackIngredient.STREAM_CODEC, BasicShakerRecipe::getInput,
                      ByteBufCodecs.optional(FluidStackIngredient.STREAM_CODEC), BasicShakerRecipe::getFluidInput,
                      ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list()), BasicShakerRecipe::getOutputRaw,
                      BasicShakerRecipe::new
                )));
}
