package io.aduhtkjm.mekanismheated.tile;

import io.aduhtkjm.mekanismheated.Config;
import io.aduhtkjm.mekanismheated.recipe.*;
import io.aduhtkjm.mekanismheated.recipe.cache.HeatSensitiveOneInputCachedRecipe;
import io.aduhtkjm.mekanismheated.recipe.lookup.monitor.HeatSmelterRecipeCacheLookupMonitor;
import io.aduhtkjm.mekanismheated.registries.ModBlocks;
import io.aduhtkjm.mekanismheated.tank.MultiFluidTank;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.RelativeSide;
import mekanism.api.SerializationConstants;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.functions.ConstantPredicates;
import mekanism.api.heat.HeatAPI.HeatTransfer;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.api.recipes.cache.CachedRecipe.OperationTracker;
import mekanism.api.recipes.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.api.recipes.inputs.IInputHandler;
import mekanism.api.recipes.inputs.InputHelper;
import mekanism.api.recipes.outputs.IOutputHandler;
import mekanism.api.recipes.outputs.OutputHelper;
import mekanism.common.capabilities.heat.BasicHeatCapacitor;
import mekanism.common.capabilities.heat.CachedAmbientTemperature;
import mekanism.common.capabilities.holder.fluid.FluidTankHelper;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.holder.heat.HeatCapacitorHelper;
import mekanism.common.capabilities.holder.heat.IHeatCapacitorHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableDouble;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.inventory.warning.WarningTracker.WarningType;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.recipe.IMekanismRecipeTypeProvider;
import mekanism.common.recipe.MekanismRecipeType;
import mekanism.common.recipe.lookup.IRecipeLookupHandler;
import mekanism.common.recipe.lookup.monitor.RecipeCacheLookupMonitor;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.component.config.slot.InventorySlotInfo;
import mekanism.common.tile.prefab.TileEntityProgressMachine;
import mekanism.common.util.EnumUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.NBTUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TileEntityHeatSmelter
      extends TileEntityProgressMachine<HeatSmelterRecipe>
      implements IRecipeLookupHandler<HeatSmelterRecipe> {

    /** Error for the melting input slot, separate from the smelting input's error so their warnings do not cross-talk. */
    public static final RecipeError NOT_ENOUGH_MELT_INPUT_ERROR = RecipeError.create();
    /** Error for the melting fluid output tank, separate from the item output's error so their warnings do not cross-talk. */
    public static final RecipeError NOT_ENOUGH_FLUID_OUTPUT_SPACE_ERROR = RecipeError.create();

    private static final List<RecipeError> TRACKED_ERROR_TYPES = List.of(
          RecipeError.NOT_ENOUGH_ENERGY,
          RecipeError.NOT_ENOUGH_INPUT,
          RecipeError.NOT_ENOUGH_OUTPUT_SPACE,
          RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT,
          NOT_ENOUGH_MELT_INPUT_ERROR,
          NOT_ENOUGH_FLUID_OUTPUT_SPACE_ERROR
    );

    /** Capacity of the melting fluid output tank, in milli-buckets. */
    public static final int MAX_FLUID = 10 * FluidType.BUCKET_VOLUME;

    protected final IInputHandler<@NotNull ItemStack> inputHandler;
    protected final IOutputHandler<@NotNull ItemStack> outputHandler;
    protected final IOutputHandler<@NotNull FluidStack> fluidOutputHandler;

    private BasicHeatCapacitor heatCapacitor;

    private double lastEnvironmentLoss;
    private double lastTransferLoss;

    InputInventorySlot inputSlot;
    InputInventorySlot fuelSlot;
    OutputInventorySlot outputSlot;
    public MultiFluidTank fluidTank;

    /** Set whenever the output tank's contents change; {@link #tryAlloying()} only runs while this is set. */
    private boolean fluidChanged;
    /** The last successfully applied alloy configuration, cached to avoid re-scanning every recipe each tick. */
    @Nullable
    private AlloyCache lastAlloy;

    public TileEntityHeatSmelter(BlockPos pos, BlockState state) {
        super(ModBlocks.HEAT_SMELTER, pos, state, TRACKED_ERROR_TYPES, Config.HeatSmelter.BASE_SPEED.get());
        ConfigInfo itemConfig = configComponent.getConfig(TransmissionType.ITEM);
        if (itemConfig != null) {
            itemConfig.addSlotInfo(DataType.INPUT, new InventorySlotInfo(true, false, inputSlot));
            itemConfig.addSlotInfo(DataType.OUTPUT, new InventorySlotInfo(false, true, outputSlot));
            itemConfig.addSlotInfo(DataType.INPUT_OUTPUT, new InventorySlotInfo(true, true, inputSlot, outputSlot));
        }
        ConfigInfo fluidConfig = configComponent.getConfig(TransmissionType.FLUID);
        if (fluidConfig != null) {
            List<IExtendedFluidTank> slotTanks = new ArrayList<>();
            for (MultiFluidTank.Slot slot : fluidTank.getSlots()) {
                slotTanks.add(slot);
            }
            fluidConfig.addSlotInfo(DataType.OUTPUT, TileComponentConfig.createInfo(TransmissionType.FLUID, false, true, slotTanks));
        }
        configComponent.setupInputConfig(TransmissionType.HEAT, heatCapacitor);
        //Default to accepting heat from all sides; players can still restrict it via the side config GUI
        ConfigInfo heatConfig = configComponent.getConfig(TransmissionType.HEAT);
        if (heatConfig != null) {
            for (RelativeSide side : EnumUtils.SIDES) {
                heatConfig.setDataType(DataType.INPUT, side);
            }
        }

        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.ITEM);
        ejectorComponent.setOutputData(configComponent, TransmissionType.FLUID);

        inputHandler = InputHelper.getInputHandler(inputSlot, RecipeError.NOT_ENOUGH_INPUT);
        outputHandler = OutputHelper.getOutputHandler(outputSlot, RecipeError.NOT_ENOUGH_OUTPUT_SPACE);
        fluidOutputHandler = new IOutputHandler<>() {
            @Override
            public void handleOutput(FluidStack toOutput, int operations) {
                fluidTank.insert(toOutput.copyWithAmount(toOutput.getAmount() * operations), Action.EXECUTE, AutomationType.INTERNAL);
            }

            @Override
            public void calculateOperationsCanSupport(OperationTracker tracker, FluidStack toOutput) {
                if (!toOutput.isEmpty()) {
                    FluidStack maxOutput = toOutput.copyWithAmount(Integer.MAX_VALUE);
                    FluidStack remainder = fluidTank.insert(maxOutput, Action.SIMULATE, AutomationType.INTERNAL);
                    int amountUsed = maxOutput.getAmount() - remainder.getAmount();
                    int operations = amountUsed / toOutput.getAmount();
                    tracker.updateOperations(operations);
                    if (operations == 0) {
                        if (amountUsed == 0 && fluidTank.getTotalNeeded() > 0) {
                            tracker.addError(RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT);
                        } else {
                            tracker.addError(NOT_ENOUGH_FLUID_OUTPUT_SPACE_ERROR);
                        }
                    }
                }
            }
        };
    }

    @NotNull
    @Override
    protected IHeatCapacitorHolder getInitialHeatCapacitors(IContentsListener listener, IContentsListener recipeCacheListener, IContentsListener recipeCacheUnpauseListener,
          CachedAmbientTemperature ambientTemperature) {
        HeatCapacitorHelper builder = HeatCapacitorHelper.forSideWithConfig(this);
        builder.addCapacitor(heatCapacitor = BasicHeatCapacitor.create(Config.HeatSmelter.HEAT_CAPACITY.get(), Config.HeatSmelter.INVERSE_CONDUCTION_COEFFICIENT.get(),
              Config.HeatSmelter.INVERSE_INSULATION_COEFFICIENT.get(), ambientTemperature, listener));
        return builder.build();
    }

    @Override
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener, IContentsListener recipeCacheListener, IContentsListener recipeCacheUnpauseListener) {
        FluidTankHelper builder = FluidTankHelper.forSideWithConfig(this);
        fluidTank = MultiFluidTank.output(MAX_FLUID,
              () -> {
                  fluidChanged = true;
                  listener.onContentsChanged();
                  onContentsChanged();
              });
        for (MultiFluidTank.Slot slot : fluidTank.getSlots()) {
            builder.addTank(slot);
        }
        return builder.build();
    }

    private boolean checkInputValidity(ItemStack item) {
        //Accept any item that has some recipe, even while the smelter is too cold to run it yet, so inputs can be
        // loaded in advance of heating up
        return findRecipe(item, false) != null;
    }

    private boolean checkFuelValidity(ItemStack item) {
        var level = getLevel();
        return level != null && ModRecipeType.findFirstSingleItemRecipe(ModRecipeTypes.TYPE_FUEL_CONVERSION, level, item) != null;
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener, IContentsListener recipeCacheListener, IContentsListener recipeCacheUnpauseListener) {
        InventorySlotHelper builder = InventorySlotHelper.forSideWithConfig(this);
        builder.addSlot(inputSlot = InputInventorySlot.at(this::checkInputValidity, recipeCacheListener, 64, 17))
            .tracksWarnings(slot -> slot.warning(WarningType.NO_MATCHING_RECIPE, getWarningCheck(RecipeError.NOT_ENOUGH_INPUT)));
        builder.addSlot(outputSlot = OutputInventorySlot.at(recipeCacheUnpauseListener, 116, 35))
            .tracksWarnings(slot -> slot.warning(WarningType.NO_SPACE_IN_OUTPUT, getWarningCheck(RecipeError.NOT_ENOUGH_OUTPUT_SPACE)));
        builder.addSlot(fuelSlot = InputInventorySlot.at(this::checkFuelValidity, recipeCacheListener, 64, 55))
            .tracksWarnings(slot -> slot.warning(WarningType.NO_MATCHING_RECIPE, getWarningCheck(RecipeError.NOT_ENOUGH_INPUT)));
        return builder.build();
    }

    @Override
    protected boolean onUpdateServer() {
        boolean sendUpdatePacket = super.onUpdateServer();
        boolean burning = burnFuel();
        HeatTransfer transfer = simulate();
        lastEnvironmentLoss = transfer.environmentTransfer();
        lastTransferLoss = transfer.adjacentTransfer();
        recipeCacheLookupMonitor.updateAndProcess();
        //Keep the synced progress in step with the temperature-scaled fractional progress: the base implementation counts
        // raw ticks, which would overflow the progress bar whenever the smelter runs slower than full speed
        if (recipeCacheLookupMonitor.getCachedRecipe(0) instanceof HeatSensitiveOneInputCachedRecipe<?, ?> cachedRecipe) {
            setOperatingTicks(cachedRecipe.getProgressTicks());
        }
        //Passively alloy the molten output in place; temperature-independent and energy-free since the metals are already molten
        tryAlloying();
        if (burning) {
            //Only set active for burning if smelting didn't already set us active
            setActive(true);
            sendUpdatePacket = true;
        }
        return sendUpdatePacket;
    }

    /**
     * Burns a single fuel item to generate heat, if the smelter can currently burn fuel.
     *
     * @return {@code true} if a fuel item was consumed.
     */
    private boolean burnFuel() {
        var level = getLevel();
        if (level == null)
            return false;

        if (canBurnFuel()) {
            ItemStack fuel = fuelSlot.getStack();
            ItemStackToHeatRecipe recipe = ModRecipeType.findFirstSingleItemRecipe(ModRecipeTypes.TYPE_FUEL_CONVERSION, level, fuel);
            if (recipe != null) {
                ItemStack itemInput = recipe.getInput().getMatchingInstance(fuel);
                if (!itemInput.isEmpty()) {
                    heatCapacitor.handleHeat(recipe.getOutput(itemInput));
                    MekanismUtils.logMismatchedStackSize(fuelSlot.shrinkStack(itemInput.getCount(), Action.EXECUTE), itemInput.getCount());
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if the smelter can currently burn fuel: it has a valid fuel item and is not already at its maximum temperature.
     */
    public boolean canBurnFuel() {
        return heatCapacitor.getTemperature() < Config.HeatSmelter.MAX_FUEL_TEMPERATURE.get() && !fuelSlot.isEmpty() && checkFuelValidity(fuelSlot.getStack());
    }

    private HeatSmelterRecipe getRecipe(ItemStack input) {
        return findRecipe(input, true);
    }

    /**
     * Finds the recipe for the given input, checking in the order: oversmelt -> melt -> normal smelt. When
     * {@code enforceTemperature} is set, heated recipes are skipped while the smelter is colder than their threshold,
     * letting a too-cold smelter fall back to plain smelting.
     */
    private HeatSmelterRecipe findRecipe(ItemStack input, boolean enforceTemperature) {
        Level level = getLevel();
        if (level == null || input.isEmpty()) {
            return null;
        }
        var oversmelt = ModRecipeType.findFirstSingleItemRecipe(ModRecipeTypes.TYPE_HEATED_SMELTING, level, input);
        if (oversmelt != null && (!enforceTemperature || oversmelt.canProcess(this)))
            return HeatSmelterRecipe.oversmelt(oversmelt);

        var melt = ModRecipeType.findFirstSingleItemRecipe(ModRecipeTypes.TYPE_HEATED_MELTING, level, input);
        if (melt != null && (!enforceTemperature || melt.canProcess(this)))
            return HeatSmelterRecipe.melt(melt);

        var smelt = MekanismRecipeType.SMELTING.getInputCache().findFirstRecipe(level, input);
        if (smelt != null)
            return HeatSmelterRecipe.smelt(smelt);

        return null;
    }

    /**
     * Passively alloys the smelter's own molten output, but only when the output tank's contents have changed since the
     * previous tick. A successful alloy operation changes the contents again (see {@link #fluidChanged}), so alloying keeps
     * going while it is happening and stops as soon as nothing further can be produced. This is temperature-independent and
     * energy-free, since the metals are already molten. At most one alloy operation is performed per tick.
     */
    private void tryAlloying() {
        if (!fluidChanged) {
            return;
        }

        Level level = getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        fluidChanged = false;
        if (fluidTank.isEmpty()) {
            return;
        }
        //Snapshot once; safe because we stop after the first operation applied this tick
        List<FluidStack> fluids = fluidTank.getFluids();
        //Fast path: if the last-applied recipe still matches, reuse it instead of re-scanning every alloy recipe
        if (lastAlloy != null && applyAlloy(lastAlloy.input1(), lastAlloy.input2(), lastAlloy.output(), fluids)) {
            return;
        }
        //Full scan: find any applicable recipe and remember it for the next tick
        for (RecipeHolder<AlloyRecipe> holder : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.TYPE_ALLOYING.value())) {
            AlloyRecipe recipe = holder.value();
            if (applyAlloy(recipe.getInput1(), recipe.getInput2(), recipe.getOutput(), fluids)) {
                lastAlloy = new AlloyCache(recipe.getInput1(), recipe.getInput2(), recipe.getOutput());
                return;
            }
        }
    }

    /**
     * If the output tank can currently satisfy the given alloy ingredients (both inputs present in sufficient amount and room
     * for the output), performs one alloy operation: drains the two inputs and adds the alloy.
     *
     * @return {@code true} if the operation was applied.
     */
    private boolean applyAlloy(FluidStackIngredient in1, FluidStackIngredient in2, FluidStackIngredient output, List<FluidStack> fluids) {
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
        //Room available once the inputs have been drained (the alloy is typically volume-neutral, but need not be)
        int projectedFree = fluidTank.getTotalNeeded() + need1 + need2;
        if (projectedFree < outAmount) {
            return false;
        }
        if (!fluidTank.containsFluid(outProbe)) {
            //A new fluid type can only occupy an empty slot; verify draining frees one up (or one already exists)
            int projectedEmpty = fluidTank.getSlots().size() - fluidTank.getFluidCount();
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
        fluidTank.extract(match1, need1, Action.EXECUTE, AutomationType.INTERNAL);
        fluidTank.extract(match2, need2, Action.EXECUTE, AutomationType.INTERNAL);
        fluidTank.insert(outProbe.copyWithAmount(outAmount), Action.EXECUTE, AutomationType.INTERNAL);
        return true;
    }

    /**
     * Finds the first fluid in the given list that matches the provided ingredient (type match only; amounts are checked by the caller).
     */
    @Nullable
    private FluidStack findMatchingFluid(FluidStackIngredient ingredient, List<FluidStack> fluids) {
        for (FluidStack fluid : fluids) {
            if (ingredient.test(fluid)) {
                return fluid;
            }
        }
        return null;
    }

    /**
     * The last alloy configuration successfully applied, so the same recipe can be reapplied without re-scanning every alloy
     * recipe each tick.
     */
    private record AlloyCache(FluidStackIngredient input1, FluidStackIngredient input2, FluidStackIngredient output) {
    }

    @Nullable
    @Override
    public HeatSmelterRecipe getRecipe(int cacheIndex) {
        return getRecipe(inputHandler.getInput());
    }

    @NotNull
    @Override
    protected RecipeCacheLookupMonitor<HeatSmelterRecipe> createNewCacheMonitor() {
        return new HeatSmelterRecipeCacheLookupMonitor(this);
    }

    /**
     * Helper functions to create a cached recipe.
     */
    private static HeatSensitiveOneInputCachedRecipe<ItemStack, HeatSmelterRecipe> itemToItem(HeatSmelterRecipe recipe,
            BooleanSupplier recheckAllErrors, IInputHandler<ItemStack> inputHandler, IOutputHandler<ItemStack> outputHandler,
            TileEntityHeatSmelter smelter) {
        return new HeatSensitiveOneInputCachedRecipe<>(recipe, recheckAllErrors, inputHandler, outputHandler, recipe::getInput, recipe::getItemOutput,
            ConstantPredicates.ITEM_EMPTY, smelter);
    }

    private static HeatSensitiveOneInputCachedRecipe<FluidStack, HeatSmelterRecipe> itemToFluid(HeatSmelterRecipe recipe,
            BooleanSupplier recheckAllErrors, IInputHandler<ItemStack> inputHandler, IOutputHandler<FluidStack> outputHandler,
            TileEntityHeatSmelter smelter) {
        return new HeatSensitiveOneInputCachedRecipe<>(recipe, recheckAllErrors, inputHandler, outputHandler, recipe::getInput, recipe::getFluidOutput,
            ConstantPredicates.FLUID_EMPTY, smelter);
    }

    @NotNull
    @Override
    public CachedRecipe<HeatSmelterRecipe> createNewCachedRecipe(HeatSmelterRecipe recipe, int cacheIndex) {
        HeatSensitiveOneInputCachedRecipe<?, HeatSmelterRecipe> cached;

        if (recipe.isItemOutput()) {
            cached = itemToItem(recipe, recheckAllRecipeErrors, inputHandler, outputHandler, this);
        } else {
            cached = itemToFluid(recipe, recheckAllRecipeErrors, inputHandler, fluidOutputHandler, this);
        }
        cached.setErrorsChanged(this::onErrorsChanged)
              .setCanHolderFunction(this::canFunction)
              .setActive(this::setActive)
              .setOnFinish(this::markForSave)
              .setOperatingTicksChanged(this::setOperatingTicks);
        return cached;
    }

    //Safe to return null. Never used by outside code, but we must have one.
    @Nullable
    @Override
    public IMekanismRecipeTypeProvider<?, HeatSmelterRecipe, ?> getRecipeType() {
        return null;
    }

    /**
     * Speed multiplier based on the smelter's current temperature. Runs linearly from zero at {@link Config.HeatSmelter#BASE_TEMPERATURE} up to one at
     * {@link Config.HeatSmelter#FULL_SPEED_TEMPERATURE}, and is clamped to a minimum of zero.
     */
    public double getSpeedFactor() {
        double temperature = heatCapacitor.getTemperature();
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
     * Number of operations that can be performed this tick, which is zero if the smelter is too cold to process.
     */
    public int getBaselineMaxOperations() {
        return getSpeedFactor() > 0 ? 1 : 0;
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableDouble.create(this::getLastTransferLoss, value -> lastTransferLoss = value));
        container.track(SyncableDouble.create(this::getLastEnvironmentLoss, value -> lastEnvironmentLoss = value));
    }

    @NotNull
    @Override
    public CompoundTag getReducedUpdateTag(@NotNull HolderLookup.Provider provider) {
        CompoundTag updateTag = super.getReducedUpdateTag(provider);
        updateTag.put(SerializationConstants.FLUID, fluidTank.serializeNBT(provider));
        return updateTag;
    }

    @Override
    public void handleUpdateTag(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        super.handleUpdateTag(tag, provider);
        NBTUtils.setCompoundIfPresent(tag, SerializationConstants.FLUID, nbt -> fluidTank.deserializeNBT(provider, nbt));
    }

    @Override
    public void onContentsChanged() {
        super.onContentsChanged();
        //The output tank's contents are rendered through the glass in-world, so changes need to reach clients even
        // when no player currently has the GUI open
        if (level != null && !level.isClientSide) {
            sendUpdatePacket();
        }
    }

    public BasicHeatCapacitor getHeatCapacitor() {
        return heatCapacitor;
    }

    public double getLastTransferLoss() {
        return lastTransferLoss;
    }

    public double getLastEnvironmentLoss() {
        return lastEnvironmentLoss;
    }
}
