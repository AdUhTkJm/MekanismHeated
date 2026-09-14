package io.aduhtkjm.mekanismheated.recipe;

import io.aduhtkjm.mekanismheated.registries.ModBlocks;
import java.util.List;
import mekanism.api.recipes.MekanismRecipe;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid.Flowing;
import net.neoforged.neoforge.fluids.FluidStack;
import org.lwjgl.system.NonnullDefault;

import javax.annotation.Nullable;

/**
 * A recipe that melts two or more fluids together into a single alloy fluid.
 * <br>
 * Input: two or more fluids (fed into the heat smelter's fluid inlets). The inputs are treated as an <em>unordered</em>
 * group, so the order they are fed in does not matter, and a single fluid may satisfy more than one input (in which
 * case it is consumed once per matching input).
 * <br>
 * Output: a single fluid, defined by a fluid ingredient (typically a tag) so that other mods' molten fluid alloys can be
 * defined without this mod hard-coding specific fluids.
 * <br>
 * No temperature threshold applies; the heat smelter is assumed to always be hot enough to melt.
 */
@NonnullDefault
public abstract class AlloyRecipe extends MekanismRecipe<AlloyRecipeInput> {

    /**
     * The fewest fluid inputs an alloy recipe may declare.
     */
    public static final int MIN_INPUTS = 2;

    /**
     * The most fluid inputs an alloy recipe may declare. Bounded because the recipe viewer only lays out this many input
     * gauges; the matching logic itself is agnostic to how many inputs a recipe has.
     */
    public static final int MAX_INPUTS = 3;

    /**
     * Checks whether the given fluids match this recipe, treating the inputs as an unordered group.
     *
     * @param input The fluids present in the tank.
     *
     * @return {@code true} if every input ingredient is satisfied by at least one of the given fluids.
     */
    public boolean test(AlloyRecipeInput input) {
        for (FluidStackIngredient ingredient : getInputs()) {
            if (!matchesAny(ingredient, input)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks whether any of the fluids held by the given input satisfies the ingredient.
     */
    private static boolean matchesAny(FluidStackIngredient ingredient, AlloyRecipeInput input) {
        for (int i = 0; i < input.size(); i++) {
            if (ingredient.test(input.getFluid(i))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean matches(AlloyRecipeInput input, Level level) {
        return !isIncomplete() && test(input);
    }

    /**
     * Gets the fluid input ingredients, in the order they were declared. The order carries no meaning: the inputs are
     * matched as an unordered group.
     */
    public abstract List<FluidStackIngredient> getInputs();

    /**
     * Gets the output fluid ingredient.
     */
    public abstract FluidStackIngredient getOutput();

    @Override
    public boolean isIncomplete() {
        if (getOutput().hasNoMatchingInstances()) {
            return true;
        }
        for (FluidStackIngredient input : getInputs()) {
            if (input.hasNoMatchingInstances()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void logMissingTags() {
        for (FluidStackIngredient input : getInputs()) {
            if (input.hasNoMatchingInstances()) {
                input.logMissingTags();
            }
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
    public ItemStack getOutputItem() {
        Fluid fluid = getOutputFluid();
        if (fluid != null) {
            return new ItemStack(fluid.getBucket());
        }
        return new ItemStack(ModBlocks.HEAT_SMELTER.asItem());
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider provider) {
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
