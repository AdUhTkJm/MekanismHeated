package io.aduhtkjm.mekanismheated.recipe;

import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.recipes.vanilla_input.FluidRecipeInput;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * A recipe input of exactly two fluids. The order of the two fluids is not significant when matching against
 * {@link AlloyRecipe}s, which treat the two inputs as an unordered pair.
 *
 * @param first  The first fluid in the pair.
 * @param second The second fluid in the pair.
 */
@NothingNullByDefault
public record TwoFluidRecipeInput(FluidStack first, FluidStack second) implements FluidRecipeInput {

    @Override
    public FluidStack getFluid(int index) {
        return switch (index) {
            case 0 -> first;
            case 1 -> second;
            default -> throw new IllegalArgumentException("No fluid for index " + index);
        };
    }

    @Override
    public int size() {
        return 2;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TwoFluidRecipeInput other = (TwoFluidRecipeInput) o;
        return FluidStack.matches(first, other.first) && FluidStack.matches(second, other.second);
    }

    @Override
    public int hashCode() {
        int hash = FluidStack.hashFluidAndComponents(first);
        return 31 * hash + FluidStack.hashFluidAndComponents(second);
    }
}
