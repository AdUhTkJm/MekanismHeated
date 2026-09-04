package io.aduhtkjm.mekanismheated.recipe;

import java.util.Optional;
import mekanism.api.recipes.MekanismRecipe;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Input: FluidStack (required)
 * <br>
 * Optional Input: ItemStack
 * <br>
 * Output: single ItemStack
 *
 * <p>A recipe may declare an optional item input. When it does, the condenser only runs it if its item slot contains a
 * matching item, which gets consumed during processing. Recipes without an item input ignore the slot contents entirely.
 * If two recipes match the same fluid but differ in whether they consume an item, the one consuming an item takes
 * precedence.</p>
 */
public abstract class CondenserRecipe extends MekanismRecipe<CondenserRecipeInput> {

    public abstract boolean test(FluidStack fluidStack, ItemStack itemStack);

    @Override
    public boolean matches(CondenserRecipeInput input, Level level) {
        return !isIncomplete() && test(input.fluid(), input.item());
    }

    public abstract FluidStackIngredient getFluidInput();

    public abstract Optional<ItemStackIngredient> getItemInput();

    public final boolean hasItemInput() {
        return getItemInput().isPresent();
    }

    public abstract ItemStack getOutput(FluidStack fluid, ItemStack item);

    public abstract ItemStack getOutputDefinition();

    @Override
    public boolean isIncomplete() {
        if (getFluidInput().hasNoMatchingInstances()) {
            return true;
        }
        Optional<ItemStackIngredient> itemInput = getItemInput();
        return itemInput.isPresent() && itemInput.get().hasNoMatchingInstances();
    }

    @Override
    public void logMissingTags() {
        getFluidInput().logMissingTags();
        Optional<ItemStackIngredient> itemInput = getItemInput();
        if (itemInput.isPresent()) {
            itemInput.get().logMissingTags();
        }
    }
}
