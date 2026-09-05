package io.aduhtkjm.mekanismheated.recipe;

import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.recipes.ingredients.ChemicalStackIngredient;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import net.minecraft.world.item.crafting.RecipeSerializer;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@NothingNullByDefault
public class BasicReactionChamberRecipe extends ReactionChamberRecipe {

    private final Optional<ItemStackIngredient> itemInput;
    private final List<FluidStackIngredient> fluidInputs;
    private final List<ChemicalStackIngredient> chemicalInputs;
    private final Optional<ItemStackIngredient> itemOutput;
    private final List<FluidStackIngredient> fluidOutputs;
    private final List<ChemicalStackIngredient> chemicalOutputs;
    private final double minTemperature;
    private final double maxTemperature;

    public BasicReactionChamberRecipe(Optional<ItemStackIngredient> itemInput,
          List<FluidStackIngredient> fluidInputs, List<ChemicalStackIngredient> chemicalInputs,
          Optional<ItemStackIngredient> itemOutput,
          List<FluidStackIngredient> fluidOutputs, List<ChemicalStackIngredient> chemicalOutputs,
          double minTemperature, double maxTemperature) {
        Objects.requireNonNull(itemInput, "Item input cannot be null.");
        Objects.requireNonNull(fluidInputs, "Fluid inputs cannot be null.");
        Objects.requireNonNull(chemicalInputs, "Chemical inputs cannot be null.");
        Objects.requireNonNull(itemOutput, "Item output cannot be null.");
        Objects.requireNonNull(fluidOutputs, "Fluid outputs cannot be null.");
        Objects.requireNonNull(chemicalOutputs, "Chemical outputs cannot be null.");
        fluidInputs.forEach(Objects::requireNonNull);
        chemicalInputs.forEach(Objects::requireNonNull);
        fluidOutputs.forEach(Objects::requireNonNull);
        chemicalOutputs.forEach(Objects::requireNonNull);
        if (itemInput.isEmpty() && fluidInputs.isEmpty() && chemicalInputs.isEmpty()) {
            throw new IllegalArgumentException("Reaction chamber recipes must have at least one input.");
        }
        if (itemOutput.isEmpty() && fluidOutputs.isEmpty() && chemicalOutputs.isEmpty()) {
            throw new IllegalArgumentException("Reaction chamber recipes must have at least one output.");
        }
        if (maxTemperature < minTemperature) {
            throw new IllegalArgumentException("Max temperature must be at least the min temperature.");
        }
        this.itemInput = itemInput;
        this.fluidInputs = List.copyOf(fluidInputs);
        this.chemicalInputs = List.copyOf(chemicalInputs);
        this.itemOutput = itemOutput;
        this.fluidOutputs = List.copyOf(fluidOutputs);
        this.chemicalOutputs = List.copyOf(chemicalOutputs);
        this.minTemperature = minTemperature;
        this.maxTemperature = maxTemperature;
    }

    @Override
    public Optional<ItemStackIngredient> getItemInput() {
        return itemInput;
    }

    @Override
    public List<FluidStackIngredient> getFluidInputs() {
        return fluidInputs;
    }

    @Override
    public List<ChemicalStackIngredient> getChemicalInputs() {
        return chemicalInputs;
    }

    @Override
    public Optional<ItemStackIngredient> getItemOutputIngredient() {
        return itemOutput;
    }

    @Override
    public List<FluidStackIngredient> getFluidOutputIngredients() {
        return fluidOutputs;
    }

    @Override
    public List<ChemicalStackIngredient> getChemicalOutputIngredients() {
        return chemicalOutputs;
    }

    @Override
    public double getMinTemperature() {
        return minTemperature;
    }

    @Override
    public double getMaxTemperature() {
        return maxTemperature;
    }

    @Override
    public RecipeSerializer<BasicReactionChamberRecipe> getSerializer() {
        return ModRecipeSerializers.REACTION.get();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BasicReactionChamberRecipe other = (BasicReactionChamberRecipe) o;
        return Double.compare(minTemperature, other.minTemperature) == 0
              && Double.compare(maxTemperature, other.maxTemperature) == 0
              && itemInput.equals(other.itemInput)
              && fluidInputs.equals(other.fluidInputs)
              && chemicalInputs.equals(other.chemicalInputs)
              && itemOutput.equals(other.itemOutput)
              && fluidOutputs.equals(other.fluidOutputs)
              && chemicalOutputs.equals(other.chemicalOutputs);
    }

    @Override
    public int hashCode() {
        int result = itemInput.hashCode();
        result = 31 * result + fluidInputs.hashCode();
        result = 31 * result + chemicalInputs.hashCode();
        result = 31 * result + itemOutput.hashCode();
        result = 31 * result + fluidOutputs.hashCode();
        result = 31 * result + chemicalOutputs.hashCode();
        result = 31 * result + Double.hashCode(minTemperature);
        result = 31 * result + Double.hashCode(maxTemperature);
        return result;
    }
}
