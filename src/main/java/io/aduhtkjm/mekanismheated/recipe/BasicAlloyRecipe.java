package io.aduhtkjm.mekanismheated.recipe;

import java.util.List;
import java.util.Objects;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import net.minecraft.world.item.crafting.RecipeSerializer;

@NothingNullByDefault
public class BasicAlloyRecipe extends AlloyRecipe {

    private final FluidStackIngredient input1;
    private final FluidStackIngredient input2;
    private final FluidStackIngredient output;

    /**
     * @param input1 The first of the two fluid input ingredients.
     * @param input2 The second of the two fluid input ingredients.
     * @param output The single output fluid ingredient.
     */
    public BasicAlloyRecipe(FluidStackIngredient input1, FluidStackIngredient input2, FluidStackIngredient output) {
        this.input1 = Objects.requireNonNull(input1, "First fluid input cannot be null.");
        this.input2 = Objects.requireNonNull(input2, "Second fluid input cannot be null.");
        this.output = Objects.requireNonNull(output, "Output cannot be null.");
    }

    @Override
    public FluidStackIngredient getInput1() {
        return input1;
    }

    @Override
    public FluidStackIngredient getInput2() {
        return input2;
    }

    @Override
    public FluidStackIngredient getOutput() {
        return output;
    }

    /**
     * For serializer use.
     *
     * @return the two input ingredients, in the order they were declared. DO NOT MODIFY RETURN VALUE.
     */
    public List<FluidStackIngredient> getInputsRaw() {
        return List.of(input1, input2);
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
        return input1.equals(other.input1) && input2.equals(other.input2) && output.equals(other.output);
    }

    @Override
    public int hashCode() {
        int result = input1.hashCode();
        result = 31 * result + input2.hashCode();
        result = 31 * result + output.hashCode();
        return result;
    }
}
