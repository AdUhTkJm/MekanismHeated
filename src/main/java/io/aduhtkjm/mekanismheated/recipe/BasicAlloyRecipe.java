package io.aduhtkjm.mekanismheated.recipe;

import java.util.List;
import java.util.Objects;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import net.minecraft.world.item.crafting.RecipeSerializer;

@NothingNullByDefault
public class BasicAlloyRecipe extends AlloyRecipe {

    private final List<FluidStackIngredient> inputs;
    private final FluidStackIngredient output;

    /**
     * @param inputs The fluid input ingredients, treated as an unordered group. Must hold between
     *               {@link AlloyRecipe#MIN_INPUTS} and {@link AlloyRecipe#MAX_INPUTS} ingredients.
     * @param output The single output fluid ingredient.
     */
    public BasicAlloyRecipe(List<FluidStackIngredient> inputs, FluidStackIngredient output) {
        Objects.requireNonNull(inputs, "Fluid inputs cannot be null.");
        if (inputs.size() < MIN_INPUTS) {
            throw new IllegalArgumentException("An alloy recipe needs at least " + MIN_INPUTS + " fluid inputs, got " + inputs.size() + ".");
        } else if (inputs.size() > MAX_INPUTS) {
            throw new IllegalArgumentException("An alloy recipe supports at most " + MAX_INPUTS + " fluid inputs, got " + inputs.size() + ".");
        }
        this.inputs = List.copyOf(inputs);
        this.output = Objects.requireNonNull(output, "Output cannot be null.");
    }

    @Override
    public List<FluidStackIngredient> getInputs() {
        return inputs;
    }

    @Override
    public FluidStackIngredient getOutput() {
        return output;
    }

    /**
     * For serializer use. DO NOT MODIFY RETURN VALUE.
     */
    public List<FluidStackIngredient> getInputsRaw() {
        return inputs;
    }

    /**
     * For serializer use. DO NOT MODIFY RETURN VALUE.
     */
    public FluidStackIngredient getOutputRaw() {
        return output;
    }

    @Override
    public RecipeSerializer<BasicAlloyRecipe> getSerializer() {
        return ModRecipeSerializers.ALLOYING.get();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BasicAlloyRecipe other = (BasicAlloyRecipe) o;
        return inputs.equals(other.inputs) && output.equals(other.output);
    }

    @Override
    public int hashCode() {
        int result = inputs.hashCode();
        result = 31 * result + output.hashCode();
        return result;
    }
}
