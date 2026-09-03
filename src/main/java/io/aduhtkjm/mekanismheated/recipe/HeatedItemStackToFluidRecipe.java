package io.aduhtkjm.mekanismheated.recipe;

import io.aduhtkjm.mekanismheated.Mod;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.heat.ISidedHeatHandler;
import mekanism.api.recipes.ItemStackToFluidRecipe;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Input: ItemStack
 * <br>
 * Output: FluidStack
 * <br>
 * Condition: the processing machine must be at least {@link #getTemperatureThreshold()} Kelvin to process the recipe.
 *
 * @apiNote Heated melting recipes are used by heat-powered machines that require a minimum temperature, in Kelvin, to melt
 * an item into a fluid. Whether a given machine is hot enough is checked via {@link #canProcess(ISidedHeatHandler)} against
 * the machine's current temperature.
 */
@NothingNullByDefault
public abstract class HeatedItemStackToFluidRecipe extends ItemStackToFluidRecipe {

    private static final Holder<Item> HEAT_SMELTER = DeferredHolder.create(Registries.ITEM,
          ResourceLocation.fromNamespaceAndPath(Mod.MODID, "heat_smelter"));

    protected final double temperatureThreshold;

    /**
     * @param temperatureThreshold Minimum temperature, in Kelvin, the processing machine must have to process this recipe.
     *                             Must be greater than zero.
     */
    public HeatedItemStackToFluidRecipe(double temperatureThreshold) {
        if (temperatureThreshold <= 0) {
            throw new IllegalArgumentException("Temperature threshold must be greater than zero.");
        }
        this.temperatureThreshold = temperatureThreshold;
    }

    /**
     * Gets the minimum temperature, in Kelvin, the processing machine must have to process this recipe.
     */
    public double getTemperatureThreshold() {
        return temperatureThreshold;
    }

    /**
     * Checks if the given machine is hot enough to process this recipe.
     *
     * @param machine The machine that would process the recipe.
     *
     * @return {@code true} if the machine's temperature is at least {@link #getTemperatureThreshold()}.
     */
    public boolean canProcess(ISidedHeatHandler machine) {
        return machine.getTotalTemperature() >= temperatureThreshold;
    }

    /**
     * Gets the output fluid ingredient.
     */
    public abstract FluidStackIngredient getOutputIngredient();

    @Override
    public boolean isIncomplete() {
        return getInput().hasNoMatchingInstances() || getOutputIngredient().hasNoMatchingInstances();
    }

    @Override
    public void logMissingTags() {
        if (getInput().hasNoMatchingInstances()) {
            getInput().logMissingTags();
        }
        if (getOutputIngredient().hasNoMatchingInstances()) {
            getOutputIngredient().logMissingTags();
        }
    }

    @Override
    public final RecipeType<HeatedItemStackToFluidRecipe> getType() {
        return ModRecipeTypes.TYPE_HEATED_MELTING.value();
    }

    @Override
    public String getGroup() {
        return "heated_melting";
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(HEAT_SMELTER);
    }
}
