package io.aduhtkjm.mekanismheated.recipe.cache;

import io.aduhtkjm.mekanismheated.tile.TileEntityHeatSmelter;
import java.util.function.BooleanSupplier;
import mekanism.api.annotations.NothingNullByDefault;
import mekanism.api.functions.ConstantPredicates;
import mekanism.api.recipes.ItemStackToItemStackRecipe;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.api.recipes.cache.OneInputCachedRecipe;
import mekanism.api.recipes.inputs.IInputHandler;
import mekanism.api.recipes.outputs.IOutputHandler;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Temperature-aware variant of {@link OneInputCachedRecipe} used by the Heat Smelter. Instead of advancing a flat single
 * tick of progress each tick like the base implementation, each tick of processing advances the recipe's progress by the
 * smelter's current speed factor ({@link TileEntityHeatSmelter#getSpeedFactor()}), a value between zero (too cold) and one
 * (full speed), so the smelting speed scales smoothly with the smelter's temperature. The recipe completes once the
 * accumulated fractional progress reaches the recipe's required ticks.
 * <p>
 * The base implementation hard-codes its progress accumulation ({@code operatingTicks++}) inside {@link CachedRecipe#process()}
 * with no hook to change the increment, so this class keeps its own fractional {@code progress} accumulator and steers the
 * base implementation instead of duplicating it:
 * <ul>
 *     <li>{@link #process()} tentatively adds this tick's speed factor to {@code progress} before delegating to the base
 *     implementation, and rolls the addition back if no operations were actually performed (paused for errors, an error
 *     occurred, or the holder cannot function), so progress never accrues while the machine is blocked.</li>
 *     <li>The required ticks supplier is configured to report {@code operatingTicks + ceil(requiredTicks - progress)},
 *     which makes the base implementation's {@code operatingTicks >= requiredTicks} completion check fire exactly when
 *     {@code progress >= requiredTicks}, regardless of how many raw ticks have elapsed.</li>
 *     <li>On completion any leftover fractional progress carries over to the next recipe, and progress is cleared if the
 *     recipe's progress gets reset (for example because the input no longer produces the output).</li>
 * </ul>
 */
@NothingNullByDefault
public class HeatSensitiveOneInputCachedRecipe extends OneInputCachedRecipe<@NotNull ItemStack, @NotNull ItemStack, ItemStackToItemStackRecipe> {

    private final TileEntityHeatSmelter smelter;
    /**
     * Fractional progress accumulated so far, in units of ticks required by the recipe. Advanced by the smelter's current
     * speed factor each tick of processing, so a recipe completes once this reaches the recipe's required ticks.
     */
    private double progress;
    /**
     * Whether the base implementation performed operations this tick; used to roll back the tentative progress advance when
     * no progress was actually made.
     */
    private boolean madeProgress;
    /**
     * Whether the recipe finished this tick; used to distinguish a finished recipe from a progress reset in
     * {@link #resetCache()}.
     */
    private boolean finishedThisTick;

    /**
     * @param recipe           Recipe.
     * @param recheckAllErrors Returns {@code true} if processing should be continued even if an error is hit in order to gather all the errors.
     * @param inputHandler     Input handler.
     * @param outputHandler    Output handler.
     * @param smelter          The heat smelter this recipe is processing for, used to look up the current speed factor and required ticks.
     */
    public HeatSensitiveOneInputCachedRecipe(ItemStackToItemStackRecipe recipe, BooleanSupplier recheckAllErrors,
          IInputHandler<@NotNull ItemStack> inputHandler, IOutputHandler<@NotNull ItemStack> outputHandler, TileEntityHeatSmelter smelter) {
        super(recipe, recheckAllErrors, inputHandler, outputHandler, recipe::getInput, recipe::getOutput, ConstantPredicates.ITEM_EMPTY, ConstantPredicates.ITEM_EMPTY);
        this.smelter = smelter;
        //The base implementation counts raw ticks via operatingTicks++ with no way to change the increment, so instead we
        // offset the required ticks by the raw tick count, turning the base's completion check into "progress >= required ticks"
        setRequiredTicks(() -> getOperatingTicks() + (int) Math.ceil(smelter.getTicksRequired() - progress));
    }

    @Override
    public void process() {
        //Tentatively advance the fractional progress by this tick's speed factor; rolled back if no operations are performed
        double speed = smelter.getSpeedFactor();
        progress += speed;
        madeProgress = false;
        finishedThisTick = false;
        super.process();
        if (!madeProgress) {
            //No progress was made this tick (paused for errors, an error occurred, or the holder cannot function); undo
            // the tentative advance so progress does not accumulate while the machine is blocked
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
        progress -= smelter.getTicksRequired();
        super.finishProcessing(operations);
    }

    @Override
    protected void resetCache() {
        super.resetCache();
        if (!finishedThisTick) {
            //The base implementation reset the recipe's progress (e.g. the input no longer produces the output); clear our
            // fractional accumulator to match
            progress = 0;
        }
    }

    @Override
    public void loadSavedOperatingTicks(int operatingTicks) {
        super.loadSavedOperatingTicks(operatingTicks);
        //Restore the fractional accumulator to the saved whole-tick progress (the sub-tick remainder is not persisted)
        if (operatingTicks > 0 && operatingTicks < smelter.getTicksRequired()) {
            progress = operatingTicks;
        }
    }

    /**
     * @return Whole ticks of progress accumulated so far (the floor of the fractional progress), for syncing to clients.
     */
    public int getProgressTicks() {
        return (int) progress;
    }
}
