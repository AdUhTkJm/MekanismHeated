package io.aduhtkjm.mekanismheated.recipe;

import mekanism.api.annotations.NothingNullByDefault;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.crafting.RecipeInput;

/**
 * Recipe input for {@link CrushingRecipe}s, consisting of the item being crushed and the block that is falling on it (the
 * catalyst, e.g. an anvil).
 */
@NothingNullByDefault
public record CrushingRecipeInput(ItemStack item, Block catalyst) implements RecipeInput {

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
        return item.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CrushingRecipeInput other = (CrushingRecipeInput) o;
        return ItemStack.matches(item, other.item) && catalyst == other.catalyst;
    }

    @Override
    public int hashCode() {
        int hash = ItemStack.hashItemAndComponents(item);
        hash = 31 * hash + item.getCount();
        hash = 31 * hash + System.identityHashCode(catalyst);
        return hash;
    }
}
