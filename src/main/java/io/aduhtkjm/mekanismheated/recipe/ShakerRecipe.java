package io.aduhtkjm.mekanismheated.recipe;

import java.util.List;
import java.util.Optional;
import mekanism.api.recipes.MekanismRecipe;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Contract;

/**
 * Input: ItemStack
 * <br>
 * Optional Input: FluidStack
 * <br>
 * Output: 1-3 ItemStacks
 *
 * <p>A recipe may declare an optional fluid input. When it does, the shaker only runs it if its fluid tank contains a
 * matching amount of that fluid, which gets consumed during processing. Recipes without a fluid input ignore the tank
 * contents entirely. If two recipes match the same item but differ in whether they consume fluid, the one consuming
 * fluid takes precedence.</p>
 */
public abstract class ShakerRecipe extends MekanismRecipe<ShakerRecipeInput> {

    /**
     * Checks if this recipe matches the given item and fluid.
     *
     * @param itemStack Item being shaken.
     * @param fluidStack Current contents of the shaker's fluid tank; ignored by recipes without a fluid input.
     */
    public abstract boolean test(ItemStack itemStack, FluidStack fluidStack);

    @Override
    public boolean matches(ShakerRecipeInput input, Level level) {
        //Don't match incomplete recipes or ones that don't match
        return !isIncomplete() && test(input.item(), input.fluid());
    }

    /**
     * Gets the item input ingredient.
     */
    public abstract ItemStackIngredient getInput();

    /**
     * Gets the optional fluid input ingredient. Empty means this recipe does not consume any fluid.
     */
    public abstract Optional<FluidStackIngredient> getFluidInput();

    /**
     * {@return true} if this recipe consumes fluid when processing.
     */
    public final boolean hasFluidInput() {
        return getFluidInput().isPresent();
    }

    /**
     * Gets a new output based on the given inputs.
     *
     * @param input      Specific item input.
     * @param fluidStack Specific fluid input, or empty for recipes that don't use fluid.
     *
     * @return 1-3 item stacks to produce.
     *
     * @apiNote While Mekanism does not currently make use of the inputs, it is important to support them and pass the proper values in case any addons define input based
     * outputs where things like NBT may be different.
     * @implNote The passed in inputs should <strong>NOT</strong> be modified.
     */
    @Contract(value = "_, _ -> new", pure = true)
    public abstract List<ItemStack> getOutput(ItemStack input, FluidStack fluidStack);

    /**
     * For recipe viewers, gets the output representations to display (1-3 stacks).
     *
     * @return Representation of the outputs, <strong>MUST NOT</strong> be modified.
     */
    public abstract List<ItemStack> getOutputDefinition();

    @Override
    public boolean isIncomplete() {
        if (getInput().hasNoMatchingInstances()) {
            return true;
        }
        Optional<FluidStackIngredient> fluidInput = getFluidInput();
        return fluidInput.isPresent() && fluidInput.get().hasNoMatchingInstances();
    }

    @Override
    public void logMissingTags() {
        getInput().logMissingTags();
        Optional<FluidStackIngredient> fluidInput = getFluidInput();
        if (fluidInput.isPresent()) {
            fluidInput.get().logMissingTags();
        }
    }
}
