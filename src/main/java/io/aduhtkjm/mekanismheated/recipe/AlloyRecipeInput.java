package io.aduhtkjm.mekanismheated.recipe;

import java.util.ArrayList;
import java.util.List;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.recipes.vanilla_input.FluidRecipeInput;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * A recipe input holding the fluids an alloy recipe is matched against. Alloy inputs are an unordered group, so the
 * order of the fluids is not significant when matching against {@link AlloyRecipe}s. A fluid may satisfy more than one
 * input ingredient; the amounts each ingredient needs are checked by the heat smelter, not here.
 *
 * @param fluids The fluids present, at most one entry per distinct fluid.
 */
@NothingNullByDefault
public record AlloyRecipeInput(List<FluidStack> fluids) implements FluidRecipeInput {

    public AlloyRecipeInput {
        fluids = List.copyOf(fluids);
    }

    @Override
    public FluidStack getFluid(int index) {
        return fluids.get(index);
    }

    @Override
    public int size() {
        return fluids.size();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AlloyRecipeInput other = (AlloyRecipeInput) o;
        if (fluids.size() != other.fluids.size()) {
            return false;
        }
        //The fluids are an unordered group, so compare them as a multiset
        List<FluidStack> remaining = new ArrayList<>(other.fluids);
        for (FluidStack fluid : fluids) {
            int match = indexOf(remaining, fluid);
            if (match < 0) {
                return false;
            }
            remaining.remove(match);
        }
        return true;
    }

    @Override
    public int hashCode() {
        //Order independent, matching the unordered equality above
        int hash = 0;
        for (FluidStack fluid : fluids) {
            hash += FluidStack.hashFluidAndComponents(fluid);
        }
        return hash;
    }

    private static int indexOf(List<FluidStack> fluids, FluidStack fluid) {
        for (int i = 0; i < fluids.size(); i++) {
            if (FluidStack.matches(fluids.get(i), fluid)) {
                return i;
            }
        }
        return -1;
    }
}
