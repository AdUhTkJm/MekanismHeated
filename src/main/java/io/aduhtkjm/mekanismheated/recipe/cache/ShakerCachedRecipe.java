package io.aduhtkjm.mekanismheated.recipe.cache;

import io.aduhtkjm.mekanismheated.recipe.ModRecipeTypes;
import io.aduhtkjm.mekanismheated.recipe.ShakerRecipe;
import io.aduhtkjm.mekanismheated.tile.TileEntityShaker;
import java.util.List;
import java.util.function.BooleanSupplier;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.api.recipes.inputs.IInputHandler;
import mekanism.api.recipes.outputs.IOutputHandler;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Cached recipe for the Shaker, whose recipes take one item plus an optional fluid input and produce 1-3 item stacks.
 * Recipes that declare a fluid input only process while the tank holds a matching amount of fluid; recipes without one
 * ignore the tank contents entirely.
 */
@NothingNullByDefault
public class ShakerCachedRecipe extends CachedRecipe<ShakerRecipe> {

    private final TileEntityShaker shaker;
    private final IOutputHandler<@NotNull List<ItemStack>> outputHandler;
    private final IInputHandler<@NotNull ItemStack> inputHandler;
    private final IInputHandler<@NotNull FluidStack> fluidInputHandler;

    private ItemStack recipeItem = ItemStack.EMPTY;
    private FluidStack recipeFluid = FluidStack.EMPTY;
    //Note: Our output shouldn't be null in places it is actually used, but we mark it as nullable, so we don't have to initialize it
    @Nullable
    private List<ItemStack> output;

    /**
     * @param recipe             Recipe.
     * @param recheckAllErrors   Returns {@code true} if processing should be continued even if an error is hit in order to gather all the errors. It is recommended
     *                           to not do this every tick or if there is no one viewing recipes.
     * @param inputHandler       Item input handler.
     * @param fluidInputHandler  Fluid input handler.
     * @param outputHandler      Output handler, handles all of the recipe's item outputs.
     * @param shaker             The shaker processing this recipe, used to notice when a fluid consuming variant becomes available.
     */
    public ShakerCachedRecipe(ShakerRecipe recipe, BooleanSupplier recheckAllErrors, IInputHandler<@NotNull ItemStack> inputHandler,
          IInputHandler<@NotNull FluidStack> fluidInputHandler, IOutputHandler<@NotNull List<ItemStack>> outputHandler,
          TileEntityShaker shaker) {
        super(recipe, recheckAllErrors);
        this.inputHandler = inputHandler;
        this.fluidInputHandler = fluidInputHandler;
        this.outputHandler = outputHandler;
        this.shaker = shaker;
    }

    @Override
    protected void calculateOperationsThisTick(OperationTracker tracker) {
        super.calculateOperationsThisTick(tracker);
        if (tracker.shouldContinueChecking()) {
            recipeItem = inputHandler.getRecipeInput(recipe.getInput());
            //Test to make sure we can even perform a single operation. This is akin to !recipe.test(inputItem)
            if (recipeItem.isEmpty()) {
                //No input, we don't know if the recipe matches or not so treat it as not matching
                tracker.mismatchedRecipe();
            } else {
                if (recipe.hasFluidInput()) {
                    recipeFluid = fluidInputHandler.getRecipeInput(recipe.getFluidInput().orElseThrow());
                    if (recipeFluid.isEmpty()) {
                        //The tank no longer has enough of the fluid this recipe requires; reset progress so a
                        // switch to a fluid-less variant does not carry over any partial processing
                        tracker.resetProgress(TileEntityShaker.NOT_ENOUGH_FLUID_INPUT_ERROR);
                        return;
                    }
                } else {
                    recipeFluid = FluidStack.EMPTY;
                }
                //Calculate the current max based on the item input
                inputHandler.calculateOperationsCanSupport(tracker, recipeItem);
                if (tracker.shouldContinueChecking()) {
                    if (!recipeFluid.isEmpty()) {
                        //Calculate the current max based on the fluid input
                        fluidInputHandler.calculateOperationsCanSupport(tracker, recipeFluid);
                    }
                    if (tracker.shouldContinueChecking()) {
                        output = recipe.getOutput(recipeItem, recipeFluid);
                        //Calculate the max based on the space in the outputs
                        outputHandler.calculateOperationsCanSupport(tracker, output);
                    }
                }
            }
        }
    }

    @Override
    public boolean isInputValid() {
        ItemStack item = inputHandler.getInput();
        if (item.isEmpty()) {
            return false;
        }
        FluidStack fluid = fluidInputHandler.getInput();
        if (!recipe.test(item, fluid)) {
            return false;
        }
        if (!recipe.hasFluidInput() && !fluid.isEmpty()) {
            //The tank gained fluid, so a fluid consuming variant of this recipe may have become available and should
            // take over; invalidate the cache so the holder re-looks up which recipe to use
            return !fluidConsumingVariantMatches(item, fluid);
        }
        return true;
    }

    /**
     * Checks if any recipe that consumes fluid matches both the given item and fluid.
     */
    private boolean fluidConsumingVariantMatches(ItemStack item, FluidStack fluid) {
        Level level = shaker.getLevel();
        if (level == null) {
            return false;
        }
        for (RecipeHolder<ShakerRecipe> holder : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.TYPE_SHAKING.value())) {
            ShakerRecipe variant = holder.value();
            if (variant.hasFluidInput() && variant.test(item, fluid)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void finishProcessing(int operations) {
        //Validate something didn't go horribly wrong
        if (output != null && !recipeItem.isEmpty() && (!recipe.hasFluidInput() || !recipeFluid.isEmpty())) {
            inputHandler.use(recipeItem, operations);
            if (!recipeFluid.isEmpty()) {
                fluidInputHandler.use(recipeFluid, operations);
            }
            outputHandler.handleOutput(output, operations);
        }
    }

    @Override
    protected void resetCache() {
        super.resetCache();
        recipeItem = ItemStack.EMPTY;
        recipeFluid = FluidStack.EMPTY;
        output = null;
    }
}
