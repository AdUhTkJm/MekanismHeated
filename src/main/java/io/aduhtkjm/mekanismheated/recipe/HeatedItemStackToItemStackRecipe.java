package io.aduhtkjm.mekanismheated.recipe;

import io.aduhtkjm.mekanismheated.Mod;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.heat.ISidedHeatHandler;
import mekanism.api.recipes.ItemStackToItemStackRecipe;
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
 * Output: ItemStack
 * <br>
 * Condition: the processing machine must be at least {@link #getTemperatureThreshold()} Kelvin to process the recipe.
 *
 * @apiNote Heated smelting recipes are used by heat-powered machines that require a minimum temperature, in Kelvin, to
 * process a recipe. Whether a given machine is hot enough is checked via {@link #canProcess(ISidedHeatHandler)} against the
 * machine's current temperature.
 */
@NothingNullByDefault
public abstract class HeatedItemStackToItemStackRecipe extends ItemStackToItemStackRecipe {

    private static final Holder<Item> HEAT_SMELTER = DeferredHolder.create(Registries.ITEM,
          ResourceLocation.fromNamespaceAndPath(Mod.MODID, "heat_smelter"));

    protected final double temperatureThreshold;

    /**
     * @param recipeType          The recipe type this recipe belongs to.
     * @param temperatureThreshold Minimum temperature, in Kelvin, the processing machine must have to process this recipe.
     *                             Must be greater than zero.
     */
    public HeatedItemStackToItemStackRecipe(RecipeType<ItemStackToItemStackRecipe> recipeType, double temperatureThreshold) {
        super(recipeType);
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

    @Override
    public String getGroup() {
        return "heated_smelting";
    }

    @Override
    public ItemStack getToastSymbol() {
        return new ItemStack(HEAT_SMELTER);
    }
}
