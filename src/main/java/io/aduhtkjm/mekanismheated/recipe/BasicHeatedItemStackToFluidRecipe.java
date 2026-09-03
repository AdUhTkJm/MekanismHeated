package io.aduhtkjm.mekanismheated.recipe;

import java.util.List;
import java.util.Objects;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid.Flowing;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Contract;

@NothingNullByDefault
public class BasicHeatedItemStackToFluidRecipe extends HeatedItemStackToFluidRecipe {

    protected final ItemStackIngredient input;
    protected final FluidStackIngredient output;

    /**
     * @param input               Input.
     * @param output              Output.
     * @param temperatureThreshold Minimum temperature, in Kelvin, the processing machine must have to process this recipe.
     *                             Must be greater than zero.
     */
    public BasicHeatedItemStackToFluidRecipe(ItemStackIngredient input, FluidStackIngredient output, double temperatureThreshold) {
        super(temperatureThreshold);
        this.input = Objects.requireNonNull(input, "Input cannot be null.");
        this.output = Objects.requireNonNull(output, "Output cannot be null.");
    }

    @Override
    public boolean test(ItemStack itemStack) {
        return input.test(itemStack);
    }

    @Override
    public ItemStackIngredient getInput() {
        return input;
    }

    @Override
    public FluidStackIngredient getOutputIngredient() {
        return output;
    }

    @Override
    @Contract(value = "_ -> new", pure = true)
    public FluidStack getOutput(ItemStack input) {
        for (FluidStack representation : output.getRepresentations()) {
            if (!(representation.getFluid() instanceof Flowing)) {
                return representation.copy();
            }
        }
        List<FluidStack> reps = output.getRepresentations();
        return reps.isEmpty() ? FluidStack.EMPTY : reps.getFirst().copy();
    }

    @Override
    public List<FluidStack> getOutputDefinition() {
        return output.getRepresentations();
    }

    /**
     * For Serializer use. DO NOT MODIFY RETURN VALUE.
     */
    public FluidStackIngredient getOutputRaw() {
        return output;
    }

    @Override
    public RecipeSerializer<BasicHeatedItemStackToFluidRecipe> getSerializer() {
        return ModRecipeSerializers.HEATED_MELTING.get();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BasicHeatedItemStackToFluidRecipe other = (BasicHeatedItemStackToFluidRecipe) o;
        return Double.compare(temperatureThreshold, other.temperatureThreshold) == 0 && input.equals(other.input) && output.equals(other.output);
    }

    @Override
    public int hashCode() {
        int result = input.hashCode();
        result = 31 * result + output.hashCode();
        result = 31 * result + Double.hashCode(temperatureThreshold);
        return result;
    }
}
