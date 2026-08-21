package io.aduhtkjm.mekanismheated.recipes;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.aduhtkjm.mekanismheated.MekanismHeated;
import mekanism.api.SerializationConstants;
import mekanism.api.SerializerHelper;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import mekanism.common.recipe.serializer.MekanismRecipeSerializer;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModRecipeSerializers {

    private ModRecipeSerializers() {
    }

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MekanismHeated.MODID);

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
}
