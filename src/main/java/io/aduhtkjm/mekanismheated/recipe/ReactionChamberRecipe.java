package io.aduhtkjm.mekanismheated.recipe;

import mekanism.api.chemical.ChemicalStack;
import mekanism.api.recipes.MekanismRecipe;
import mekanism.api.recipes.ingredients.ChemicalStackIngredient;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.BaseFlowingFluid.Flowing;
import net.neoforged.neoforge.fluids.FluidStack;
import org.lwjgl.system.NonnullDefault;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * A recipe for the reaction chamber.
 *
 * <p>Inputs: at most one item (tag or ID), a (possibly empty) list of fluids, and a (possibly empty) list of chemicals.
 * Each input is a sized ingredient, so tags and IDs are both supported.
 *
 * <p>Outputs: at most one item, a (possibly empty) list of fluids, and a (possibly empty) list of chemicals. Each output
 * is also a sized ingredient, so it may be a tag or an ID. A tagged output behaves like the mod's other tagged outputs:
 * the recipe is {@linkplain #isIncomplete() incomplete} (and therefore never matches) while its output tag has no members,
 * and once the tag is populated it resolves to the <em>first</em> member of the tag (see {@link #getItemOutput()},
 * {@link #getFluidOutputs()} and {@link #getChemicalOutputs()}).
 *
 * <p>A recipe also carries a {@linkplain #getMinTemperature() minimum} and {@linkplain #getMaxTemperature() maximum}
 * temperature (Kelvin) it can run at; see {@link #temperatureAllows(double)}.
 */
@NonnullDefault
public abstract class ReactionChamberRecipe extends MekanismRecipe<ReactionChamberRecipeInput> {

    /**
     * @return The single item input, if this recipe consumes one.
     */
    public abstract Optional<ItemStackIngredient> getItemInput();

    /**
     * @return The fluid inputs (may be empty).
     */
    public abstract List<FluidStackIngredient> getFluidInputs();

    /**
     * @return The chemical inputs (may be empty).
     */
    public abstract List<ChemicalStackIngredient> getChemicalInputs();

    /**
     * @return The single item output ingredient, if this recipe produces one.
     */
    public abstract Optional<ItemStackIngredient> getItemOutputIngredient();

    /**
     * @return The fluid output ingredients (may be empty).
     */
    public abstract List<FluidStackIngredient> getFluidOutputIngredients();

    /**
     * @return The chemical output ingredients (may be empty).
     */
    public abstract List<ChemicalStackIngredient> getChemicalOutputIngredients();

    /**
     * @return The minimum temperature (Kelvin) at which this recipe can run.
     */
    public abstract double getMinTemperature();

    /**
     * @return The maximum temperature (Kelvin) at which this recipe can run.
     */
    public abstract double getMaxTemperature();

    public final boolean hasItemInput() {
        return getItemInput().isPresent();
    }

    public final boolean hasItemOutput() {
        return getItemOutputIngredient().isPresent();
    }

    /**
     * @return {@code true} if the given temperature (Kelvin) is within this recipe's [min, max] range (inclusive).
     */
    public boolean temperatureAllows(double temperature) {
        return temperature >= getMinTemperature() && temperature <= getMaxTemperature();
    }

    @Override
    public boolean matches(ReactionChamberRecipeInput input, Level level) {
        return !isIncomplete() && test(input);
    }

    /**
     * Checks whether the given input satisfies every required input of this recipe, ignoring temperature.
     *
     * <p>The item (when required) must match the item input ingredient. The fluid and chemical inputs are matched against
     * the provided fluids and chemicals as an <em>unordered set</em>: every required ingredient must be covered by a
     * distinct provided stack whose type matches and whose amount is at least the required amount.
     *
     * @param input The input to test.
     *
     * @return {@code true} if every required input can be satisfied by a distinct input of the given input.
     */
    public boolean test(ReactionChamberRecipeInput input) {
        if (hasItemInput() && !getItemInput().orElseThrow().test(input.item())) {
            return false;
        }
        if (!canSatisfy(getFluidInputs(), input.fluids())) {
            return false;
        }
        return canSatisfy(getChemicalInputs(), input.chemicals());
    }

    /**
     * Decides whether every required ingredient can be assigned a distinct available stack that it tests against.
     *
     * @param <T>       The stack type.
     * @param required  The required ingredients.
     * @param available The available stacks (empty slots are fine; they simply never satisfy a requirement).
     *
     * @return {@code true} if all requirements can be covered by distinct available stacks.
     */
    private static <T> boolean canSatisfy(List<? extends Predicate<T>> required, List<T> available) {
        int requiredCount = required.size();
        if (requiredCount == 0) {
            return true;
        }
        int availableCount = available.size();
        if (requiredCount > availableCount) {
            return false;
        }
        //Kuhn's algorithm: matchAvailable[a] = index of the requirement assigned to available[a], or -1 if unused.
        int[] matchAvailable = new int[availableCount];
        Arrays.fill(matchAvailable, -1);
        for (int r = 0; r < requiredCount; r++) {
            boolean[] seen = new boolean[availableCount];
            if (!augment(r, required, available, matchAvailable, seen)) {
                return false;
            }
        }
        return true;
    }

    private static <T> boolean augment(int requirement, List<? extends Predicate<T>> required, List<T> available, int[] matchAvailable,
          boolean[] seen) {
        Predicate<T> predicate = required.get(requirement);
        for (int a = 0; a < available.size(); a++) {
            if (seen[a] || !predicate.test(available.get(a))) {
                continue;
            }
            seen[a] = true;
            if (matchAvailable[a] == -1 || augment(matchAvailable[a], required, available, matchAvailable, seen)) {
                matchAvailable[a] = requirement;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isIncomplete() {
        if (hasItemInput() && getItemInput().orElseThrow().hasNoMatchingInstances()) {
            return true;
        }
        for (FluidStackIngredient ingredient : getFluidInputs()) {
            if (ingredient.hasNoMatchingInstances()) {
                return true;
            }
        }
        for (ChemicalStackIngredient ingredient : getChemicalInputs()) {
            if (ingredient.hasNoMatchingInstances()) {
                return true;
            }
        }
        //Tagged outputs: incomplete (and thus never matching) while the output tag has no members.
        if (hasItemOutput() && getItemOutputIngredient().orElseThrow().hasNoMatchingInstances()) {
            return true;
        }
        for (FluidStackIngredient ingredient : getFluidOutputIngredients()) {
            if (ingredient.hasNoMatchingInstances()) {
                return true;
            }
        }
        for (ChemicalStackIngredient ingredient : getChemicalOutputIngredients()) {
            if (ingredient.hasNoMatchingInstances()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void logMissingTags() {
        Optional<ItemStackIngredient> itemInput = getItemInput();
        if (itemInput.isPresent() && itemInput.get().hasNoMatchingInstances()) {
            itemInput.get().logMissingTags();
        }
        getFluidInputs().stream().filter(FluidStackIngredient::hasNoMatchingInstances).forEach(FluidStackIngredient::logMissingTags);
        getChemicalInputs().stream().filter(ChemicalStackIngredient::hasNoMatchingInstances).forEach(ChemicalStackIngredient::logMissingTags);
        Optional<ItemStackIngredient> itemOutput = getItemOutputIngredient();
        if (itemOutput.isPresent() && itemOutput.get().hasNoMatchingInstances()) {
            itemOutput.get().logMissingTags();
        }
        getFluidOutputIngredients().stream().filter(FluidStackIngredient::hasNoMatchingInstances).forEach(FluidStackIngredient::logMissingTags);
        getChemicalOutputIngredients().stream().filter(ChemicalStackIngredient::hasNoMatchingInstances).forEach(ChemicalStackIngredient::logMissingTags);
    }

    /**
     * @return The single item this recipe produces, resolved to the first representation of its output ingredient, or empty
     *         if the recipe has no item output or the output ingredient is empty.
     */
    public Optional<ItemStack> getItemOutput() {
        if (!hasItemOutput()) {
            return Optional.empty();
        }
        List<ItemStack> representations = getItemOutputIngredient().orElseThrow().getRepresentations();
        return representations.isEmpty() ? Optional.empty() : Optional.of(representations.getFirst().copy());
    }

    /**
     * @return The fluids this recipe produces, each resolved to the first (non-flowing) representation of its output ingredient.
     */
    public List<FluidStack> getFluidOutputs() {
        List<FluidStack> result = new ArrayList<>(getFluidOutputIngredients().size());
        for (FluidStackIngredient ingredient : getFluidOutputIngredients()) {
            result.add(resolveFluidOutput(ingredient));
        }
        return List.copyOf(result);
    }

    /**
     * @return The chemicals this recipe produces, each resolved to the first representation of its output ingredient.
     */
    public List<ChemicalStack> getChemicalOutputs() {
        List<ChemicalStack> result = new ArrayList<>(getChemicalOutputIngredients().size());
        for (ChemicalStackIngredient ingredient : getChemicalOutputIngredients()) {
            List<ChemicalStack> representations = ingredient.getRepresentations();
            result.add(representations.isEmpty() ? ChemicalStack.EMPTY : representations.getFirst().copy());
        }
        return List.copyOf(result);
    }

    /**
     * Resolves a fluid output ingredient to the single concrete fluid to produce: the first non-flowing representation
     * (a tank cannot store a flowing fluid), falling back to the first representation, or {@link FluidStack#EMPTY} if none.
     */
    private static FluidStack resolveFluidOutput(FluidStackIngredient ingredient) {
        List<FluidStack> representations = ingredient.getRepresentations();
        for (FluidStack representation : representations) {
            if (!(representation.getFluid() instanceof Flowing)) {
                return representation.copy();
            }
        }
        return representations.isEmpty() ? FluidStack.EMPTY : representations.getFirst().copy();
    }

    /**
     * For JEI: all representations of the item output (empty list if there is no item output).
     */
    public List<ItemStack> getItemOutputDefinition() {
        return hasItemOutput() ? getItemOutputIngredient().orElseThrow().getRepresentations() : List.of();
    }

    /**
     * For JEI: all representations of every fluid output.
     */
    public List<FluidStack> getFluidOutputDefinition() {
        List<FluidStack> result = new ArrayList<>();
        for (FluidStackIngredient ingredient : getFluidOutputIngredients()) {
            result.addAll(ingredient.getRepresentations());
        }
        return List.copyOf(result);
    }

    /**
     * For JEI: all representations of every chemical output.
     */
    public List<ChemicalStack> getChemicalOutputDefinition() {
        List<ChemicalStack> result = new ArrayList<>();
        for (ChemicalStackIngredient ingredient : getChemicalOutputIngredients()) {
            result.addAll(ingredient.getRepresentations());
        }
        return List.copyOf(result);
    }

    @Override
    public ItemStack assemble(ReactionChamberRecipeInput input, HolderLookup.Provider provider) {
        if (!isIncomplete() && test(input)) {
            return getItemOutput().orElse(ItemStack.EMPTY);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider provider) {
        return getItemOutput().orElse(ItemStack.EMPTY);
    }

    @Override
    public ItemStack getToastSymbol() {
        return getItemOutput().orElse(ItemStack.EMPTY);
    }

    @Override
    public RecipeType<ReactionChamberRecipe> getType() {
        return ModRecipeTypes.TYPE_REACTION.value();
    }

    @Override
    public String getGroup() {
        return "reaction";
    }
}
