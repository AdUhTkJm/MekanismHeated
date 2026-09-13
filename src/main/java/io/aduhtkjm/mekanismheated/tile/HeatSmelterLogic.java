package io.aduhtkjm.mekanismheated.tile;

import io.aduhtkjm.mekanismheated.Config;
import io.aduhtkjm.mekanismheated.recipe.AlloyRecipe;
import io.aduhtkjm.mekanismheated.recipe.HeatSmelterRecipe;
import io.aduhtkjm.mekanismheated.recipe.ItemStackToHeatRecipe;
import io.aduhtkjm.mekanismheated.recipe.ModRecipeType;
import io.aduhtkjm.mekanismheated.recipe.ModRecipeTypes;
import io.aduhtkjm.mekanismheated.tank.MultiFluidTank;
import java.util.List;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.heat.IHeatCapacitor;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.common.recipe.MekanismRecipeType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Pure, stateless heat-smelter processing logic, shared by the standalone {@link TileEntityHeatSmelter} and the
 * multiblock large heat smelter so both always use identical math.
 * <p>
 * These methods operate on explicit inputs (a temperature, a heat target, a fluid tank) rather than a tile, so they can be
 * driven by either a single-block smelter or the shared brain of a multiblock.
 */
public final class HeatSmelterLogic {

    private HeatSmelterLogic() {
    }

    /**
     * Finds the recipe for the given input, checking in the order: oversmelt -> melt -> normal smelt. When
     * {@code enforceTemperature} is set, heated recipes are skipped while {@code temperature} is colder than their
     * threshold, letting a too-cold smelter fall back to plain smelting.
     *
     * @param temperature          the machine's current total temperature, in Kelvin.
     * @param enforceTemperature   when set, temperature-gated recipes are only used if hot enough.
     */
    @Nullable
    public static HeatSmelterRecipe findRecipeFor(@Nullable Level level, @NotNull ItemStack input, double temperature, boolean enforceTemperature) {
        if (level == null || input.isEmpty()) {
            return null;
        }
        var oversmelt = ModRecipeType.findFirstSingleItemRecipe(ModRecipeTypes.TYPE_HEATED_SMELTING, level, input);
        if (oversmelt != null && (!enforceTemperature || temperature >= oversmelt.getTemperatureThreshold())) {
            return HeatSmelterRecipe.oversmelt(oversmelt);
        }
        var melt = ModRecipeType.findFirstSingleItemRecipe(ModRecipeTypes.TYPE_HEATED_MELTING, level, input);
        if (melt != null && (!enforceTemperature || temperature >= melt.getTemperatureThreshold())) {
            return HeatSmelterRecipe.melt(melt);
        }
        var smelt = MekanismRecipeType.SMELTING.getInputCache().findFirstRecipe(level, input);
        if (smelt != null) {
            return HeatSmelterRecipe.smelt(smelt);
        }
        return null;
    }

    /**
     * The share of a recipe's total heat cost that one tick of processing draws from the machine's heat capacitor. The
     * recipe's total heat is spread proportionally over its processing ticks, so the full cost is paid exactly once per
     * completed recipe regardless of how fast the machine runs: one tick at speed factor {@code s} out of
     * {@code ticksRequired} ticks pays {@code totalHeat * s / ticksRequired} heat. Slower (colder) processing therefore
     * consumes less heat per tick, spread over more ticks.
     *
     * @param totalHeat     the recipe's total heat consumption, in heat units (Joules).
     * @param ticksRequired the recipe's processing duration in ticks (at full speed).
     * @param speedFactor   the machine's current speed factor (0 to 1).
     * @return the heat to draw for this tick, in heat units (0 when not processing).
     */
    public static double heatForTick(double totalHeat, int ticksRequired, double speedFactor) {
        if (totalHeat <= 0 || ticksRequired <= 0 || speedFactor <= 0) {
            return 0;
        }
        return totalHeat * speedFactor / ticksRequired;
    }

    /**
     * The number of recipe operations a large heat smelter performs per completed processing cycle: one per member
     * block of the structure, limited by the input items actually available (and, by the caller, by output space).
     */
    public static int parallelOperations(int smelterCount, int inputCount) {
        return Math.clamp(smelterCount, 0, Math.max(inputCount, 0));
    }

    /**
     * The heat multiplier applied when a large heat smelter processes {@code operations} recipes at once: the batch's
     * total heat cost scales with the square root of the operation count (e.g. 8 parallel operations cost
     * {@code sqrt(8)}x one operation's heat, 64 operations cost {@code 8}x), so batching is more heat-efficient than
     * running the equivalent number of separate smelters. The multiplier is capped at
     * {@link Config.HeatSmelter#MAX_HEAT_MULTIPLIER} ({@code sqrt(64) = 8} by default) so very large structures do
     * not keep paying more heat.
     *
     * @param operations the number of recipe operations performed in parallel this cycle (1 or fewer means no scaling).
     */
    public static double heatMultiplier(int operations) {
        return operations <= 1 ? 1 : Math.min(Math.sqrt(operations), Config.HeatSmelter.MAX_HEAT_MULTIPLIER.get());
    }

    /**
     * Speed multiplier based on the given temperature. Runs linearly from zero at {@link Config.HeatSmelter#BASE_TEMPERATURE}
     * up to one at {@link Config.HeatSmelter#FULL_SPEED_TEMPERATURE}, clamped to a minimum of zero.
     */
    public static double speedFactor(double temperature) {
        double base = Config.HeatSmelter.BASE_TEMPERATURE.get();
        double full = Config.HeatSmelter.FULL_SPEED_TEMPERATURE.get();
        double range = full - base;
        if (range <= 0) {
            //Invalid configuration, treat everything above the base temperature as full speed
            return temperature > base ? 1 : 0;
        }
        return Math.clamp((temperature - base) / range, 0, 1);
    }

    /**
     * Burns one fuel item into the given heat target, if the machine is not already at its maximum temperature and the fuel
     * is valid. The produced heat is added to {@code heat}; the fuel item itself is NOT consumed here (the caller applies the
     * returned count to its inventory/slot).
     *
     * @param currentTemperature the machine's current temperature, in Kelvin.
     * @return the number of fuel items to consume (0 if none were burned).
     */
    public static int burnFuel(@Nullable Level level, double currentTemperature, @NotNull IHeatCapacitor heat, @NotNull ItemStack fuel) {
        if (level == null || fuel.isEmpty()) {
            return 0;
        }
        if (currentTemperature >= Config.HeatSmelter.MAX_FUEL_TEMPERATURE.get()) {
            return 0;
        }
        ItemStackToHeatRecipe recipe = ModRecipeType.findFirstSingleItemRecipe(ModRecipeTypes.TYPE_FUEL_CONVERSION, level, fuel);
        if (recipe == null) {
            return 0;
        }
        ItemStack itemInput = recipe.getInput().getMatchingInstance(fuel);
        if (itemInput.isEmpty()) {
            return 0;
        }
        heat.handleHeat(recipe.getOutput(itemInput));
        return itemInput.getCount();
    }

    /**
     * Attempts to passively alloy the tank's contents once. If a matching alloy recipe can be applied, performs exactly one
     * alloy operation on {@code tank} and returns the applied configuration (so the caller can remember it and skip the
     * re-scan next time). A previously applied configuration is tried first as a fast path.
     *
     * @param lastApplied the configuration applied last tick, or {@code null}.
     * @return the applied configuration (possibly {@code lastApplied}), or the unchanged {@code lastApplied} if no operation
     *         could be performed.
     */
    @Nullable
    public static AlloyConfig tryAlloyOnce(@Nullable Level level, @NotNull MultiFluidTank tank, @Nullable AlloyConfig lastApplied) {
        if (level == null || level.isClientSide || tank.isEmpty()) {
            return lastApplied;
        }
        // Snapshot once; safe because we stop after the first operation applied this tick
        List<FluidStack> fluids = tank.getFluids();
        // Fast path: if the last-applied recipe still matches, reuse it instead of re-scanning every alloy recipe
        if (lastApplied != null && applyAlloy(tank, lastApplied.input1(), lastApplied.input2(), lastApplied.output(), fluids)) {
            return lastApplied;
        }
        // Full scan: find any applicable recipe and remember it for the next tick
        for (RecipeHolder<AlloyRecipe> holder : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.TYPE_ALLOYING.value())) {
            AlloyRecipe recipe = holder.value();
            if (applyAlloy(tank, recipe.getInput1(), recipe.getInput2(), recipe.getOutput(), fluids)) {
                return new AlloyConfig(recipe.getInput1(), recipe.getInput2(), recipe.getOutput());
            }
        }
        return lastApplied;
    }

    /**
     * If the output tank can currently satisfy the given alloy ingredients (both inputs present in sufficient amount and room
     * for the output), performs one alloy operation: drains the two inputs and adds the alloy.
     *
     * @return {@code true} if the operation was applied.
     */
    public static boolean applyAlloy(@NotNull MultiFluidTank tank, @NotNull FluidStackIngredient in1, @NotNull FluidStackIngredient in2,
          @NotNull FluidStackIngredient output, @NotNull List<FluidStack> fluids) {
        Fluid outFluid = AlloyRecipe.outputFluid(output);
        if (outFluid == null) {
            return false;
        }
        FluidStack match1 = findMatchingFluid(in1, fluids);
        FluidStack match2 = findMatchingFluid(in2, fluids);
        if (match1 == null || match2 == null) {
            return false;
        }
        int need1 = (int) in1.getNeededAmount(match1);
        int need2 = (int) in2.getNeededAmount(match2);
        boolean sameFluid = FluidStack.isSameFluidSameComponents(match1, match2);
        if (sameFluid) {
            if (match1.getAmount() < need1 + need2) {
                return false;
            }
        } else if (match1.getAmount() < need1 || match2.getAmount() < need2) {
            return false;
        }
        FluidStack outProbe = new FluidStack(outFluid, 1);
        int outAmount = (int) output.getNeededAmount(outProbe);
        if (outAmount <= 0) {
            return false;
        }
        // Room available once the inputs have been drained (the alloy is typically volume-neutral, but need not be)
        int projectedFree = tank.getTotalNeeded() + need1 + need2;
        if (projectedFree < outAmount) {
            return false;
        }
        if (!tank.containsFluid(outProbe)) {
            // A new fluid type can only occupy an empty slot; verify draining frees one up (or one already exists)
            int projectedEmpty = tank.getSlots().size() - tank.getFluidCount();
            if (sameFluid) {
                if (match1.getAmount() == need1 + need2) {
                    projectedEmpty++;
                }
            } else {
                if (match1.getAmount() == need1) {
                    projectedEmpty++;
                }
                if (match2.getAmount() == need2) {
                    projectedEmpty++;
                }
            }
            if (projectedEmpty < 1) {
                return false;
            }
        }
        tank.extract(match1, need1, Action.EXECUTE, AutomationType.INTERNAL);
        tank.extract(match2, need2, Action.EXECUTE, AutomationType.INTERNAL);
        tank.insert(outProbe.copyWithAmount(outAmount), Action.EXECUTE, AutomationType.INTERNAL);
        return true;
    }

    /**
     * Finds the first fluid in the given list that matches the provided ingredient (type match only; amounts are checked by the caller).
     */
    @Nullable
    public static FluidStack findMatchingFluid(@NotNull FluidStackIngredient ingredient, @NotNull List<FluidStack> fluids) {
        for (FluidStack fluid : fluids) {
            if (ingredient.test(fluid)) {
                return fluid;
            }
        }
        return null;
    }

    /**
     * A successfully applied alloy configuration, cached so the same recipe can be reapplied without re-scanning every alloy
     * recipe each tick.
     */
    public record AlloyConfig(FluidStackIngredient input1, FluidStackIngredient input2, FluidStackIngredient output) {
    }
}
