package io.aduhtkjm.mekanismheated.recipe;

import io.aduhtkjm.mekanismheated.registries.ModBlocks;
import mekanism.api.recipes.MekanismRecipe;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid.Flowing;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * A recipe that melts two fluids together into a single alloy fluid.
 * <br>
 * Input: two fluids (fed into the heat smelter's fluid inlets). The two inputs are treated as an <em>unordered</em>
 * pair, so the order they are fed in does not matter.
 * <br>
 * Output: a single fluid, defined by a fluid ingredient (typically a tag) so that other mods' molten fluid alloys can be
 * defined without this mod hard-coding specific fluids.
 * <br>
 * No temperature threshold applies; the heat smelter is assumed to always be hot enough to melt.
 */
public abstract class AlloyRecipe extends MekanismRecipe<TwoFluidRecipeInput> {

    /**
     * Checks whether the given pair of fluids matches this recipe, treating the two inputs as an unordered pair.
     *
     * @param input The two fluids to test.
     *
     * @return {@code true} if one fluid matches the first input ingredient and the other matches the second, in either order.
     */
    public boolean test(TwoFluidRecipeInput input) {
        FluidStack first = input.getFluid(0);
        FluidStack second = input.getFluid(1);
        return getInput1().test(first) && getInput2().test(second)
              || getInput1().test(second) && getInput2().test(first);
    }

    @Override
    public boolean matches(TwoFluidRecipeInput input, Level level) {
        return !isIncomplete() && test(input);
    }

    /**
     * Gets the first of the two fluid input ingredients.
     */
    public abstract FluidStackIngredient getInput1();

    /**
     * Gets the second of the two fluid input ingredients.
     */
    public abstract FluidStackIngredient getInput2();

    /**
     * Gets the output fluid ingredient.
     */
    public abstract FluidStackIngredient getOutput();

    @Override
    public boolean isIncomplete() {
        return getInput1().hasNoMatchingInstances() || getInput2().hasNoMatchingInstances() || getOutput().hasNoMatchingInstances();
    }

    @Override
    public void logMissingTags() {
        if (getInput1().hasNoMatchingInstances()) {
            getInput1().logMissingTags();
        }
        if (getInput2().hasNoMatchingInstances()) {
            getInput2().logMissingTags();
        }
        if (getOutput().hasNoMatchingInstances()) {
            getOutput().logMissingTags();
        }
    }

    /**
     * Resolves the output to a single concrete fluid: the first non-flowing fluid in the output ingredient, or
     * {@code null} if none can be resolved (e.g. the output tag is empty or contains only flowing fluids). This is the
     * fluid the heat smelter actually produces; {@link #getOutputItem()} derives its item representation from it.
     */
    @Nullable
    public Fluid getOutputFluid() {
        return outputFluid(getOutput());
    }

    /**
     * Resolves a fluid output ingredient to a single concrete fluid: the first non-flowing fluid in its representations,
     * or {@code null} if none can be resolved (e.g. the output tag is empty or contains only flowing fluids).
     */
    @Nullable
    public static Fluid outputFluid(FluidStackIngredient output) {
        for (FluidStack representation : output.getRepresentations()) {
            Fluid fluid = representation.getFluid();
            if (!(fluid instanceof Flowing)) {
                return fluid;
            }
        }
        return null;
    }

    /**
     * Resolves the output to a single display item: the bucket of {@link #getOutputFluid()}, or the heat smelter block's
     * item if no fluid can be resolved.
     */
    @Contract("_ -> new")
    public ItemStack getOutputItem() {
        Fluid fluid = getOutputFluid();
        if (fluid != null) {
            return new ItemStack(fluid.getBucket());
        }
        return new ItemStack(ModBlocks.HEAT_SMELTER.asItem());
    }

    @Override
    public ItemStack getResultItem(@NotNull HolderLookup.Provider provider) {
        return getOutputItem();
    }

    @Override
    public ItemStack getToastSymbol() {
        return getOutputItem();
    }

    @Override
    public RecipeType<AlloyRecipe> getType() {
        return ModRecipeTypes.TYPE_ALLOYING.value();
    }

    @Override
    public String getGroup() {
        return "alloying";
    }
}
