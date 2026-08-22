package io.aduhtkjm.mekanismheated.recipe;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Contract;

@NothingNullByDefault
public class BasicHeatedItemStackToFluidRecipe extends HeatedItemStackToFluidRecipe {

    protected final ItemStackIngredient input;
    protected final FluidStack output;

    /**
     * @param input               Input.
     * @param output              Output.
     * @param temperatureThreshold Minimum temperature, in Kelvin, the processing machine must have to process this recipe.
     *                             Must be greater than zero.
     */
    public BasicHeatedItemStackToFluidRecipe(ItemStackIngredient input, FluidStack output, double temperatureThreshold) {
        super(temperatureThreshold);
        this.input = Objects.requireNonNull(input, "Input cannot be null.");
        Objects.requireNonNull(output, "Output cannot be null.");
        if (output.isEmpty()) {
            throw new IllegalArgumentException("Output cannot be empty.");
        }
        this.output = output.copy();
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
    @Contract(value = "_ -> new", pure = true)
    public FluidStack getOutput(ItemStack input) {
        return output.copy();
    }

    @Override
    public List<FluidStack> getOutputDefinition() {
        return Collections.singletonList(output);
    }

    /**
     * For Serializer use. DO NOT MODIFY RETURN VALUE.
     *
     * @return the uncopied basic output
     */
    public FluidStack getOutputRaw() {
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
        return Double.compare(temperatureThreshold, other.temperatureThreshold) == 0 && input.equals(other.input) && FluidStack.matches(output, other.output);
    }

    @Override
    public int hashCode() {
        int result = input.hashCode();
        result = 31 * result + FluidStack.hashFluidAndComponents(output);
        result = 31 * result + output.getAmount();
        result = 31 * result + Double.hashCode(temperatureThreshold);
        return result;
    }
}
