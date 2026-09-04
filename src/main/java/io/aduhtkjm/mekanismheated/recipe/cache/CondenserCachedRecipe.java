package io.aduhtkjm.mekanismheated.recipe.cache;

import io.aduhtkjm.mekanismheated.recipe.CondenserRecipe;
import io.aduhtkjm.mekanismheated.recipe.ModRecipeTypes;
import io.aduhtkjm.mekanismheated.tile.TileEntityCondenser;
import java.util.function.BooleanSupplier;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.api.recipes.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.api.recipes.inputs.IInputHandler;
import mekanism.api.recipes.outputs.IOutputHandler;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Cached recipe for the Condenser, whose recipes take a required fluid plus an optional item input and produce a single
 * item stack. Processing speed scales inversely with temperature: the condenser runs fastest when cold and cannot run at
 * all when too hot. Each tick of processing advances the recipe's progress by the condenser's current speed factor
 * (a value between zero and one), so the processing speed scales smoothly with temperature.
 */
@NothingNullByDefault
public class CondenserCachedRecipe extends CachedRecipe<CondenserRecipe> {

    private final TileEntityCondenser condenser;
    private final IInputHandler<@NotNull FluidStack> fluidInputHandler;
    private final IInputHandler<@NotNull ItemStack> itemInputHandler;
    private final IOutputHandler<@NotNull ItemStack> outputHandler;

    private FluidStack recipeFluid = FluidStack.EMPTY;
    private ItemStack recipeItem = ItemStack.EMPTY;
    @Nullable
    private ItemStack output;

    private double progress;
    private boolean madeProgress;
    private boolean finishedThisTick;

    public CondenserCachedRecipe(CondenserRecipe recipe, BooleanSupplier recheckAllErrors,
          IInputHandler<@NotNull FluidStack> fluidInputHandler, IInputHandler<@NotNull ItemStack> itemInputHandler,
          IOutputHandler<@NotNull ItemStack> outputHandler, TileEntityCondenser condenser) {
        super(recipe, recheckAllErrors);
        this.fluidInputHandler = fluidInputHandler;
        this.itemInputHandler = itemInputHandler;
        this.outputHandler = outputHandler;
        this.condenser = condenser;
        setRequiredTicks(() -> getOperatingTicks() + (int) Math.ceil(condenser.getTicksRequired() - progress));
    }

    @Override
    protected void calculateOperationsThisTick(OperationTracker tracker) {
        super.calculateOperationsThisTick(tracker);
        if (tracker.shouldContinueChecking()) {
            recipeFluid = fluidInputHandler.getRecipeInput(recipe.getFluidInput());
            if (recipeFluid.isEmpty()) {
                tracker.mismatchedRecipe();
                return;
            }
            if (recipe.hasItemInput()) {
                recipeItem = itemInputHandler.getRecipeInput(recipe.getItemInput().orElseThrow());
                if (recipeItem.isEmpty()) {
                    tracker.resetProgress(RecipeError.NOT_ENOUGH_INPUT);
                    return;
                }
            } else {
                recipeItem = ItemStack.EMPTY;
            }
            fluidInputHandler.calculateOperationsCanSupport(tracker, recipeFluid);
            if (tracker.shouldContinueChecking()) {
                if (!recipeItem.isEmpty()) {
                    itemInputHandler.calculateOperationsCanSupport(tracker, recipeItem);
                }
                if (tracker.shouldContinueChecking()) {
                    output = recipe.getOutput(recipeFluid, recipeItem);
                    outputHandler.calculateOperationsCanSupport(tracker, output);
                }
            }
        }
    }

    @Override
    public boolean isInputValid() {
        FluidStack fluid = fluidInputHandler.getInput();
        if (fluid.isEmpty()) {
            return false;
        }
        ItemStack item = itemInputHandler.getInput();
        if (!recipe.test(fluid, item)) {
            return false;
        }
        if (!recipe.hasItemInput() && !item.isEmpty()) {
            return !itemConsumingVariantMatches(fluid, item);
        }
        return true;
    }

    private boolean itemConsumingVariantMatches(FluidStack fluid, ItemStack item) {
        Level level = condenser.getLevel();
        if (level == null) {
            return false;
        }
        for (RecipeHolder<CondenserRecipe> holder : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.TYPE_CONDENSING.value())) {
            CondenserRecipe variant = holder.value();
            if (variant.hasItemInput() && variant.test(fluid, item)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void process() {
        double speed = condenser.getSpeedFactor();
        progress += speed;
        madeProgress = false;
        finishedThisTick = false;
        super.process();
        if (!madeProgress) {
            progress = Math.max(0, progress - speed);
        }
    }

    @Override
    protected void useResources(int operations) {
        madeProgress = true;
        super.useResources(operations);
    }

    @Override
    protected void finishProcessing(int operations) {
        madeProgress = true;
        finishedThisTick = true;
        //Carry any leftover fractional progress over to the next recipe so no progress is lost on completion
        progress -= condenser.getTicksRequired();
        if (output != null && !recipeFluid.isEmpty() && (!recipe.hasItemInput() || !recipeItem.isEmpty())) {
            fluidInputHandler.use(recipeFluid, operations);
            if (!recipeItem.isEmpty()) {
                itemInputHandler.use(recipeItem, operations);
            }
            outputHandler.handleOutput(output, operations);
        }
    }

    @Override
    protected void resetCache() {
        super.resetCache();
        recipeFluid = FluidStack.EMPTY;
        recipeItem = ItemStack.EMPTY;
        output = null;
        if (!finishedThisTick) {
            progress = 0;
        }
    }

    @Override
    public void loadSavedOperatingTicks(int operatingTicks) {
        super.loadSavedOperatingTicks(operatingTicks);
        if (operatingTicks > 0 && operatingTicks < condenser.getTicksRequired()) {
            progress = operatingTicks;
        }
    }

    public int getProgressTicks() {
        return (int) progress;
    }
}
