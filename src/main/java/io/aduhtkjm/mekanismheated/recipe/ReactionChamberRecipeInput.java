package io.aduhtkjm.mekanismheated.recipe;

import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.chemical.ChemicalStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

/**
 * The input presented to a {@link ReactionChamberRecipe}: at most one item, plus a (possibly empty) list of fluids and a
 * (possibly empty) list of chemicals.
 *
 * <p>The reaction chamber's contents (the single item slot plus the mixed fluid/chemical tank) are gathered into one of
 * these before looking up recipes. The order of the fluid and chemical lists is not significant; matching is done as an
 * unordered set by {@link ReactionChamberRecipe#test(ReactionChamberRecipeInput)}.
 *
 * @param item      The single item input, or {@link ItemStack#EMPTY} if none.
 * @param fluids    The fluid inputs (may be empty).
 * @param chemicals The chemical inputs (may be empty).
 */
@NothingNullByDefault
public record ReactionChamberRecipeInput(ItemStack item, List<FluidStack> fluids, List<ChemicalStack> chemicals)
      implements RecipeInput {

    public static final ReactionChamberRecipeInput EMPTY = new ReactionChamberRecipeInput(ItemStack.EMPTY, List.of(), List.of());

    public ReactionChamberRecipeInput {
        fluids = List.copyOf(fluids);
        chemicals = List.copyOf(chemicals);
    }

    @Override
    public ItemStack getItem(int index) {
        if (index != 0) {
            throw new IllegalArgumentException("No item for index " + index);
        }
        return item;
    }

    @Override
    public int size() {
        //Only the item occupies an indexed slot; the fluids and chemicals are exposed through their own accessors.
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return item.isEmpty() && fluids.isEmpty() && chemicals.isEmpty();
    }
}
