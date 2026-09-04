package io.aduhtkjm.mekanismheated.recipe;

import mekanism.api.annotations.NothingNullByDefault;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.neoforged.neoforge.fluids.FluidStack;

@NothingNullByDefault
public record CondenserRecipeInput(FluidStack fluid, ItemStack item) implements RecipeInput {

    public static final CondenserRecipeInput EMPTY = new CondenserRecipeInput(FluidStack.EMPTY, ItemStack.EMPTY);

    @Override
    public ItemStack getItem(int index) {
        if (index != 0) {
            throw new IllegalArgumentException("No item for index " + index);
        }
        return item;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return fluid.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CondenserRecipeInput other = (CondenserRecipeInput) o;
        return FluidStack.matches(fluid, other.fluid) && ItemStack.matches(item, other.item);
    }

    @Override
    public int hashCode() {
        int hash = FluidStack.hashFluidAndComponents(fluid);
        hash = 31 * hash + fluid.getAmount();
        hash = 31 * hash + ItemStack.hashItemAndComponents(item);
        hash = 31 * hash + item.getCount();
        return hash;
    }
}
