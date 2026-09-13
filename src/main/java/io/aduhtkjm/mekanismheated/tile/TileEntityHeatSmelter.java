package io.aduhtkjm.mekanismheated.tile;

import io.aduhtkjm.mekanismheated.Config;
import io.aduhtkjm.mekanismheated.recipe.*;
import io.aduhtkjm.mekanismheated.recipe.cache.HeatSensitiveOneInputCachedRecipe;
import io.aduhtkjm.mekanismheated.recipe.lookup.monitor.HeatSmelterRecipeCacheLookupMonitor;
import io.aduhtkjm.mekanismheated.registries.ModBlocks;
import io.aduhtkjm.mekanismheated.tank.MultiFluidTank;
import io.aduhtkjm.mekanismheated.tile.multiblock.LargeHeatSmelterData;
import io.aduhtkjm.mekanismheated.tile.multiblock.ModLargeHeatSmelter;
import io.aduhtkjm.mekanismheated.tile.prefab.TileEntityProgressMultiblockMachine;
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
import mekanism.common.lib.multiblock.MultiblockManager;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.recipe.IMekanismRecipeTypeProvider;
import mekanism.common.recipe.lookup.IRecipeLookupHandler;
import mekanism.common.recipe.lookup.monitor.RecipeCacheLookupMonitor;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.component.config.slot.InventorySlotInfo;
import mekanism.common.util.EnumUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.NBTUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TileEntityHeatSmelter
      extends TileEntityProgressMultiblockMachine<LargeHeatSmelterData, HeatSmelterRecipe>
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
    public static final int MAX_FLUID = Config.HeatSmelter.FLUID_CAPACITY.get() * FluidType.BUCKET_VOLUME;

    /**
     * Default per-face data type for each transmission type the smelter supports. Indexed to match the order of
     * {@link RelativeSide} ({@link EnumUtils#SIDES}): FRONT, LEFT, RIGHT, BACK, TOP, BOTTOM. Players can still override
     * any face via the side config GUI.
     */
    private static final List<SideDefaults> SIDE_DEFAULTS = List.of(
          new SideDefaults(DataType.INPUT, DataType.OUTPUT, DataType.INPUT), //FRONT
          new SideDefaults(DataType.INPUT, DataType.OUTPUT, DataType.INPUT), //LEFT
          new SideDefaults(DataType.OUTPUT, DataType.OUTPUT, DataType.INPUT), //RIGHT
          new SideDefaults(DataType.INPUT, DataType.OUTPUT, DataType.INPUT), //BACK
          new SideDefaults(DataType.INPUT, DataType.OUTPUT, DataType.INPUT), //TOP
          new SideDefaults(DataType.INPUT, DataType.OUTPUT, DataType.INPUT) //BOTTOM
    );

    /** Default per-face {@link DataType} for the smelter's item, fluid and heat transmission. */
    private record SideDefaults(DataType item, DataType fluid, DataType heat) {}

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
    private HeatSmelterLogic.AlloyConfig lastAlloy;

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
            List<IExtendedFluidTank> slotTanks = new ArrayList<>(fluidTank.getSlots());
            fluidConfig.addSlotInfo(DataType.OUTPUT, TileComponentConfig.createInfo(TransmissionType.FLUID, false, true, slotTanks));
        }
        configComponent.setupInputConfig(TransmissionType.HEAT, heatCapacitor);
        applySideDefaults();

        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.ITEM);
        ejectorComponent.setOutputData(configComponent, TransmissionType.FLUID);

        inputHandler = InputHelper.getInputHandler(inputSlot, RecipeError.NOT_ENOUGH_INPUT);
        outputHandler = OutputHelper.getOutputHandler(outputSlot, RecipeError.NOT_ENOUGH_OUTPUT_SPACE);
        fluidOutputHandler = new IOutputHandler<>() {
            @Override
            public void handleOutput(@NotNull FluidStack toOutput, int operations) {
                fluidTank.insert(toOutput.copyWithAmount(toOutput.getAmount() * operations), Action.EXECUTE, AutomationType.INTERNAL);
            }

            @Override
            public void calculateOperationsCanSupport(@NotNull OperationTracker tracker, @NotNull FluidStack toOutput) {
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

    /**
     * Applies {@link #SIDE_DEFAULTS} to the side config, setting each face's data type for item, fluid and heat.
     */
    private void applySideDefaults() {
        ConfigInfo itemConfig = configComponent.getConfig(TransmissionType.ITEM);
        ConfigInfo fluidConfig = configComponent.getConfig(TransmissionType.FLUID);
        ConfigInfo heatConfig = configComponent.getConfig(TransmissionType.HEAT);
        for (int i = 0; i < SIDE_DEFAULTS.size(); i++) {
            RelativeSide side = EnumUtils.SIDES[i];
            SideDefaults defaults = SIDE_DEFAULTS.get(i);
            if (itemConfig != null) {
                itemConfig.setDataType(defaults.item(), side);
            }
            if (fluidConfig != null) {
                fluidConfig.setDataType(defaults.fluid(), side);
            }
            if (heatConfig != null) {
                heatConfig.setDataType(defaults.heat(), side);
            }
        }
    }

    @NotNull
    @Override
    public LargeHeatSmelterData createMultiblock() {
        return new LargeHeatSmelterData(this);
    }

    @NotNull
    @Override
    public MultiblockManager<LargeHeatSmelterData> getManager() {
        return ModLargeHeatSmelter.LARGE_HEAT_SMELTER_MANAGER;
    }

    @NotNull
    @Override
    protected IHeatCapacitorHolder getInitialHeatCapacitors(IContentsListener listener, IContentsListener recipeCacheListener, IContentsListener recipeCacheUnpauseListener,
          CachedAmbientTemperature ambientTemperature) {
        HeatCapacitorHelper builder = HeatCapacitorHelper.forSideWithConfig(this);
        builder.addCapacitor(heatCapacitor = BasicHeatCapacitor.create(Config.HeatSmelter.HEAT_CAPACITY.get(), Config.HeatSmelter.INVERSE_CONDUCTION_COEFFICIENT.get(),
              Config.HeatSmelter.INVERSE_INSULATION_COEFFICIENT.get(), ambientTemperature, listener));
        IHeatCapacitorHolder standalone = builder.build();
        //While formed, expose the shared brain's heat capacitor; otherwise the per-block one
        return side -> getMultiblock().isFormed() ? getMultiblock().getHeatCapacitors(side) : standalone.getHeatCapacitors(side);
    }

    @Override
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener, IContentsListener recipeCacheListener, IContentsListener recipeCacheUnpauseListener) {
        FluidTankHelper builder = FluidTankHelper.forSideWithConfig(this);
        fluidTank = MultiFluidTank.output(MAX_FLUID,
              () -> {
                  //State updates happens only on server side.
                  if (!isRemote()) {
                      fluidChanged = true;
                  }
                  listener.onContentsChanged();
                  onContentsChanged();
              });
        for (MultiFluidTank.Slot slot : fluidTank.getSlots()) {
            builder.addTank(slot);
        }
        IFluidTankHolder standalone = builder.build();
        //While formed, expose the shared brain's fluid tanks; otherwise the per-block ones
        return side -> getMultiblock().isFormed() ? getMultiblock().getFluidTanks(side) : standalone.getTanks(side);
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
        IInventorySlotHolder standalone = builder.build();
        //While formed, expose the shared brain's item slots; otherwise the per-block ones
        return side -> getMultiblock().isFormed() ? getMultiblock().getInventorySlots(side) : standalone.getInventorySlots(side);
    }

    @Override
    protected boolean onUpdateServer() {
        boolean sendUpdatePacket = super.onUpdateServer();
        //Tick the multiblock structure every tick (formed or not) so formation is detected and the shared brain runs
        boolean multiblockPacket = tickMultiblock(getMultiblock());
        //The per-block machine logic only runs while unformed; once formed the shared brain handles all processing
        if (!getMultiblock().isFormed()) {
            boolean burning = burnFuel();
            HeatTransfer transfer = simulate();
            lastEnvironmentLoss = transfer.environmentTransfer();
            lastTransferLoss = transfer.adjacentTransfer();
            recipeCacheLookupMonitor.updateAndProcess();
            //Keep the synced progress in step with the temperature-scaled fractional progress: the base implementation counts
            // raw ticks, which would overflow the progress bar whenever the smelter runs slower than full speed
            if (recipeCacheLookupMonitor.getCachedRecipe(0) instanceof HeatSensitiveOneInputCachedRecipe<?> cachedRecipe) {
                setOperatingTicks(cachedRecipe.getProgressTicks());
            }
            //Passively alloy the molten output in place; temperature-independent and energy-free since the metals are already molten
            tryAlloying();
            if (burning) {
                //Only set active for burning if smelting didn't already set us active
                setActive(true);
                sendUpdatePacket = true;
            }
        }
        return sendUpdatePacket | multiblockPacket;
    }

    /**
     * Burns a single fuel item to generate heat, if the smelter can currently burn fuel.
     *
     * @return {@code true} if a fuel item was consumed.
     */
    private boolean burnFuel() {
        int consumed = HeatSmelterLogic.burnFuel(getLevel(), heatCapacitor.getTemperature(), heatCapacitor, fuelSlot.getStack());
        if (consumed > 0) {
            MekanismUtils.logMismatchedStackSize(fuelSlot.shrinkStack(consumed, Action.EXECUTE), consumed);
            return true;
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
        return HeatSmelterLogic.findRecipeFor(getLevel(), input, heatCapacitor.getTemperature(), enforceTemperature);
    }

    /**
     * Checks whether the given recipe is still the one the smelter would select for its current input at its current
     * temperature. Recipe selection switches between plain smelting and temperature-gated heated recipes as the smelter's
     * temperature crosses a recipe's threshold, so this lets the cached recipe detect when it has become stale and needs to
     * be re-selected.
     */
    public boolean isRecipeStillEffective(HeatSmelterRecipe recipe) {
        return isSameRecipe(findRecipe(inputHandler.getInput(), true), recipe);
    }

    /**
     * Checks whether two {@link HeatSmelterRecipe}s wrap the same underlying recipe, comparing the active branch and the
     * recipe it holds. The wrapped recipes are stable references from the recipe manager, so reference equality suffices.
     */
    private static boolean isSameRecipe(@Nullable HeatSmelterRecipe a, HeatSmelterRecipe b) {
        if (a == null) {
            return false;
        }
        if (a.isOversmelt()) {
            return b.isOversmelt() && a.getOversmelt() == b.getOversmelt();
        }
        if (a.isMelt()) {
            return b.isMelt() && a.getMelt() == b.getMelt();
        }
        return a.isSmelt() && b.isSmelt() && a.getSmelt() == b.getSmelt();
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
        lastAlloy = HeatSmelterLogic.tryAlloyOnce(level, fluidTank, lastAlloy);
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
    private static HeatSensitiveOneInputCachedRecipe<ItemStack> itemToItem(HeatSmelterRecipe recipe,
            BooleanSupplier recheckAllErrors, IInputHandler<ItemStack> inputHandler, IOutputHandler<ItemStack> outputHandler,
            TileEntityHeatSmelter smelter) {
        return new HeatSensitiveOneInputCachedRecipe<>(recipe, recheckAllErrors, inputHandler, outputHandler, recipe::getInput, recipe::getItemOutput,
            ConstantPredicates.ITEM_EMPTY, smelter);
    }

    private static HeatSensitiveOneInputCachedRecipe<FluidStack> itemToFluid(HeatSmelterRecipe recipe,
            BooleanSupplier recheckAllErrors, IInputHandler<ItemStack> inputHandler, IOutputHandler<FluidStack> outputHandler,
            TileEntityHeatSmelter smelter) {
        return new HeatSensitiveOneInputCachedRecipe<>(recipe, recheckAllErrors, inputHandler, outputHandler, recipe::getInput, recipe::getFluidOutput,
            ConstantPredicates.FLUID_EMPTY, smelter);
    }

    @NotNull
    @Override
    public CachedRecipe<HeatSmelterRecipe> createNewCachedRecipe(HeatSmelterRecipe recipe, int cacheIndex) {
        HeatSensitiveOneInputCachedRecipe<?> cached;

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
    @SuppressWarnings("all")
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
        return HeatSmelterLogic.speedFactor(heatCapacitor.getTemperature());
    }

    /**
     * Checks whether the smelter's stored heat can pay this tick's share of the recipe's total heat cost. The total cost
     * is spread proportionally over the recipe's processing ticks (see {@link HeatSmelterLogic#heatForTick}), so slower
     * (colder) processing pays less per tick. If it cannot be paid, processing stalls: the recipe idles without advancing
     * until enough heat is available again.
     */
    public boolean canProcessHeat(HeatSmelterRecipe recipe) {
        double needed = HeatSmelterLogic.heatForTick(recipe.getHeatConsumed(), ticksRequired, getSpeedFactor());
        return needed <= 0 || heatCapacitor.getHeat() >= needed;
    }

    /**
     * Draws this tick's share of the given recipe's total heat cost from the heat capacitor, cooling the smelter down.
     */
    public void consumeHeat(double totalHeat) {
        double needed = HeatSmelterLogic.heatForTick(totalHeat, ticksRequired, getSpeedFactor());
        if (needed > 0) {
            heatCapacitor.handleHeat(-needed);
        }
    }

    /**
     * Number of operations that can be performed this tick, which is zero if the smelter is too cold to process.
     */
    public int getBaselineMaxOperations() {
        return getSpeedFactor() > 0 ? 1 : 0;
    }

    @Override
    public void addContainerTrackers(@NotNull MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableDouble.create(this::getLastTransferLoss, value -> lastTransferLoss = value));
        container.track(SyncableDouble.create(this::getLastEnvironmentLoss, value -> lastEnvironmentLoss = value));
        //While formed, sync the shared brain's live values to the client so the GUI shows real multiblock state
        // (the per-block values above are dormant once formed). The setters write into the client-side brain instance.
        container.track(SyncableDouble.create(() -> getMultiblock().getProgress(), value -> getMultiblock().setProgress(value)));
        container.track(SyncableDouble.create(() -> getMultiblock().getLastTransferLoss(), value -> getMultiblock().setLastTransferLoss(value)));
        container.track(SyncableDouble.create(() -> getMultiblock().getLastEnvironmentLoss(), value -> getMultiblock().setLastEnvironmentLoss(value)));
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

    public InputInventorySlot getInputSlot() {
        return inputSlot;
    }

    public InputInventorySlot getFuelSlot() {
        return fuelSlot;
    }

    public OutputInventorySlot getOutputSlot() {
        return outputSlot;
    }

    public MultiFluidTank getFluidTank() {
        return fluidTank;
    }

    /**
     * The fluid tank the GUI should display: the shared brain's tank while formed, otherwise this block's own tank.
     */
    public MultiFluidTank getDisplayFluidTank() {
        return getMultiblock().isFormed() ? getMultiblock().getFluidTank() : fluidTank;
    }

    /**
     * The scaled (0-1) recipe progress the GUI should display: the shared brain's progress while formed, otherwise this
     * block's own progress.
     */
    public double getDisplayScaledProgress() {
        LargeHeatSmelterData multiblock = getMultiblock();
        return multiblock.isFormed() ? multiblock.getProgress() / (double) Config.HeatSmelter.BASE_SPEED.get() : getScaledProgress();
    }

    /**
     * The temperature the GUI should display: the shared brain's temperature while formed, otherwise this block's own.
     */
    public double getDisplayTemperature() {
        return getMultiblock().isFormed() ? getMultiblock().getTemperature() : heatCapacitor.getTemperature();
    }

    /**
     * The last heat transferred to/from neighbors, from the shared brain while formed, otherwise this block's own.
     */
    public double getDisplayLastTransferLoss() {
        return getMultiblock().isFormed() ? getMultiblock().getLastTransferLoss() : lastTransferLoss;
    }

    /**
     * The last heat lost to the environment, from the shared brain while formed, otherwise this block's own.
     */
    public double getDisplayLastEnvironmentLoss() {
        return getMultiblock().isFormed() ? getMultiblock().getLastEnvironmentLoss() : lastEnvironmentLoss;
    }

    public double getLastTransferLoss() {
        return lastTransferLoss;
    }

    public double getLastEnvironmentLoss() {
        return lastEnvironmentLoss;
    }
}
