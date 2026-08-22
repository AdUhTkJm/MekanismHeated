package io.aduhtkjm.mekanismheated.tile;

import io.aduhtkjm.mekanismheated.Config;
import io.aduhtkjm.mekanismheated.recipe.*;
import io.aduhtkjm.mekanismheated.recipe.cache.HeatSensitiveOneInputCachedRecipe;
import io.aduhtkjm.mekanismheated.recipe.lookup.monitor.HeatSmelterRecipeCacheLookupMonitor;
import io.aduhtkjm.mekanismheated.registries.ModBlocks;
import java.util.List;
import java.util.function.BooleanSupplier;

import mekanism.api.Action;
import mekanism.api.IContentsListener;
import mekanism.api.RelativeSide;
import mekanism.api.SerializationConstants;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.functions.ConstantPredicates;
import mekanism.api.heat.HeatAPI.HeatTransfer;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.api.recipes.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.api.recipes.inputs.IInputHandler;
import mekanism.api.recipes.inputs.InputHelper;
import mekanism.api.recipes.outputs.IOutputHandler;
import mekanism.api.recipes.outputs.OutputHelper;
import mekanism.common.capabilities.fluid.BasicFluidTank;
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
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.component.config.slot.InventorySlotInfo;
import mekanism.common.tile.prefab.TileEntityProgressMachine;
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
    public IExtendedFluidTank fluidTank;

    public TileEntityHeatSmelter(BlockPos pos, BlockState state) {
        super(ModBlocks.HEAT_SMELTER, pos, state, TRACKED_ERROR_TYPES, Config.HeatSmelter.BASE_SPEED.get());
        ConfigInfo itemConfig = configComponent.getConfig(TransmissionType.ITEM);
        if (itemConfig != null) {
            itemConfig.addSlotInfo(DataType.INPUT, new InventorySlotInfo(true, false, inputSlot));
            itemConfig.addSlotInfo(DataType.OUTPUT, new InventorySlotInfo(false, true, outputSlot));
            itemConfig.addSlotInfo(DataType.INPUT_OUTPUT, new InventorySlotInfo(true, true, inputSlot, outputSlot));
        }
        configComponent.setupOutputConfig(TransmissionType.FLUID, fluidTank, RelativeSide.RIGHT);
        configComponent.setupInputConfig(TransmissionType.HEAT, heatCapacitor);

        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.ITEM);
        ejectorComponent.setOutputData(configComponent, TransmissionType.FLUID);

        inputHandler = InputHelper.getInputHandler(inputSlot, RecipeError.NOT_ENOUGH_INPUT);
        outputHandler = OutputHelper.getOutputHandler(outputSlot, RecipeError.NOT_ENOUGH_OUTPUT_SPACE);
        fluidOutputHandler = OutputHelper.getOutputHandler(fluidTank, NOT_ENOUGH_FLUID_OUTPUT_SPACE_ERROR);
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
        //The tank is an output only; changes to it unpause the melting recipe cache so it can notice freed-up space
        builder.addTank(fluidTank = BasicFluidTank.output(MAX_FLUID, listener));
        return builder.build();
    }

    private boolean checkInputValidity(ItemStack item) {
        return getRecipe(item) != null;
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
        Level level = getLevel();
        if (level == null || input.isEmpty()) {
            return null;
        }
        //Check in the order: oversmelt -> melt -> normal smelt.
        var oversmelt = ModRecipeType.findFirstSingleItemRecipe(ModRecipeTypes.TYPE_HEATED_SMELTING, level, input);
        if (oversmelt != null)
            return HeatSmelterRecipe.oversmelt(oversmelt);

        var melt = ModRecipeType.findFirstSingleItemRecipe(ModRecipeTypes.TYPE_HEATED_MELTING, level, input);
        if (melt != null)
            return HeatSmelterRecipe.melt(melt);

        var smelt = MekanismRecipeType.SMELTING.getInputCache().findFirstRecipe(level, input);
        if (smelt != null)
            return HeatSmelterRecipe.smelt(smelt);

        return null;
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
