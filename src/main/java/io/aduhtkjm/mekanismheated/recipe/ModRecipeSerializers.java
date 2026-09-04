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

    /**
     * Codec for a recipe's minimum temperature (Kelvin); must be greater than zero.
     */
    private static final Codec<Double> POSITIVE_TEMPERATURE_CODEC = TEMPERATURE_THRESHOLD_CODEC;

    /**
     * Codec for a recipe's base temperature (Kelvin); must be greater than zero. The "at least min" cross-validation is done
     * in {@link BasicFractionationRecipe}'s constructor, which runs after decoding both fields.
     */
    private static final Codec<Double> TEMPERATURE_CODEC = TEMPERATURE_THRESHOLD_CODEC;

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Mod.MODID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<FusedPipeRecipe>> FUSED_PIPE =
          RECIPE_SERIALIZERS.register("fused_pipe", () -> new MekanismRecipeSerializer<>(
                FusedPipeRecipe.CODEC,
                FusedPipeRecipe.STREAM_CODEC));

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
                      FluidStackIngredient.CODEC.fieldOf(SerializationConstants.OUTPUT).forGetter(BasicHeatedItemStackToFluidRecipe::getOutputRaw),
                      TEMPERATURE_THRESHOLD_CODEC.fieldOf("temperature").forGetter(HeatedItemStackToFluidRecipe::getTemperatureThreshold)
                ).apply(instance, BasicHeatedItemStackToFluidRecipe::new)),
                StreamCodec.composite(
                      ItemStackIngredient.STREAM_CODEC, BasicHeatedItemStackToFluidRecipe::getInput,
                      FluidStackIngredient.STREAM_CODEC, BasicHeatedItemStackToFluidRecipe::getOutputRaw,
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

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<BasicFractionationRecipe>> FRACTIONATING =
          RECIPE_SERIALIZERS.register("fractionating", () -> new MekanismRecipeSerializer<>(
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                      FluidStackIngredient.CODEC.fieldOf(SerializationConstants.INPUT).forGetter(BasicFractionationRecipe::getInput),
                      FractionationRecipe.BankOutput.CODEC.listOf().fieldOf("outputs").forGetter(BasicFractionationRecipe::getOutputsRaw),
                      POSITIVE_TEMPERATURE_CODEC.fieldOf("min_temperature").forGetter(BasicFractionationRecipe::getMinTemperature),
                      POSITIVE_TEMPERATURE_CODEC.fieldOf("max_temperature").forGetter(BasicFractionationRecipe::getMinTemperature),
                      TEMPERATURE_CODEC.fieldOf("base_temperature").forGetter(BasicFractionationRecipe::getBaseTemperature)
                ).apply(instance, BasicFractionationRecipe::new)),
                StreamCodec.composite(
                      FluidStackIngredient.STREAM_CODEC, BasicFractionationRecipe::getInput,
                      FractionationRecipe.BankOutput.STREAM_CODEC.apply(ByteBufCodecs.list()), BasicFractionationRecipe::getOutputsRaw,
                      ByteBufCodecs.DOUBLE, BasicFractionationRecipe::getMinTemperature,
                      ByteBufCodecs.DOUBLE, BasicFractionationRecipe::getMaxTemperature,
                       ByteBufCodecs.DOUBLE, BasicFractionationRecipe::getBaseTemperature,
                       BasicFractionationRecipe::new
                 )));

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<BasicAlloyRecipe>> ALLOYING =
          RECIPE_SERIALIZERS.register("alloying", () -> new MekanismRecipeSerializer<>(
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                      FluidStackIngredient.CODEC.listOf().fieldOf("inputs").forGetter(BasicAlloyRecipe::getInputsRaw),
                      FluidStackIngredient.CODEC.fieldOf(SerializationConstants.OUTPUT).forGetter(BasicAlloyRecipe::getOutputRaw)
                ).apply(instance, (inputs, output) -> new BasicAlloyRecipe(inputs.get(0), inputs.get(1), output))),
                StreamCodec.composite(
                      FluidStackIngredient.STREAM_CODEC, BasicAlloyRecipe::getInput1,
                      FluidStackIngredient.STREAM_CODEC, BasicAlloyRecipe::getInput2,
                      FluidStackIngredient.STREAM_CODEC, BasicAlloyRecipe::getOutput,
                      BasicAlloyRecipe::new
                )));

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<BasicCondenserRecipe>> CONDENSING =
          RECIPE_SERIALIZERS.register("condensing", () -> new MekanismRecipeSerializer<>(
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                      FluidStackIngredient.CODEC.fieldOf(SerializationConstants.INPUT).forGetter(BasicCondenserRecipe::getFluidInput),
                      ItemStackIngredient.CODEC.optionalFieldOf("item_input").forGetter(BasicCondenserRecipe::getItemInput),
                      ItemStackIngredient.CODEC.fieldOf(SerializationConstants.OUTPUT).forGetter(BasicCondenserRecipe::getOutputRaw)
                ).apply(instance, BasicCondenserRecipe::new)),
                StreamCodec.composite(
                      FluidStackIngredient.STREAM_CODEC, BasicCondenserRecipe::getFluidInput,
                      ByteBufCodecs.optional(ItemStackIngredient.STREAM_CODEC), BasicCondenserRecipe::getItemInput,
                      ItemStackIngredient.STREAM_CODEC, BasicCondenserRecipe::getOutputRaw,
                      BasicCondenserRecipe::new
                )));
}
