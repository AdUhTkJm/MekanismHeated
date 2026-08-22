package io.aduhtkjm.mekanismheated.tile;

import io.aduhtkjm.mekanismheated.Config;
import io.aduhtkjm.mekanismheated.recipe.ItemStackToHeatRecipe;
import io.aduhtkjm.mekanismheated.recipe.ModRecipeType;
import io.aduhtkjm.mekanismheated.recipe.cache.HeatSensitiveOneInputCachedRecipe;
import io.aduhtkjm.mekanismheated.recipe.lookup.monitor.HeatSmelterRecipeCacheLookupMonitor;
import io.aduhtkjm.mekanismheated.registries.ModBlocks;
import java.util.List;
import mekanism.api.Action;
import mekanism.api.IContentsListener;
import mekanism.api.RelativeSide;
import mekanism.api.SerializationConstants;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.heat.HeatAPI.HeatTransfer;
import mekanism.api.recipes.ItemStackToItemStackRecipe;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.api.recipes.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.api.recipes.inputs.IInputHandler;
import mekanism.api.recipes.inputs.InputHelper;
import mekanism.api.recipes.outputs.IOutputHandler;
import mekanism.api.recipes.outputs.OutputHelper;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.client.recipe_viewer.type.RecipeViewerRecipeType;
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
import mekanism.common.recipe.lookup.cache.InputRecipeCache.SingleItem;
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
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TileEntityHeatSmelter extends TileEntityProgressMachine<ItemStackToItemStackRecipe>
      implements IRecipeLookupHandler<ItemStackToItemStackRecipe> {

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
    private static final String FLUID_PROGRESS_KEY = "fluid_progress";

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
        super(ModBlocks.HEAT_SMELTER, pos, state, TRACKED_ERROR_TYPES, Config.BASE_SPEED.get());
        ConfigInfo itemConfig = configComponent.getConfig(TransmissionType.ITEM);
        if (itemConfig != null) {
            itemConfig.addSlotInfo(DataType.INPUT, new InventorySlotInfo(true, false, inputSlot));
            itemConfig.addSlotInfo(DataType.OUTPUT, new InventorySlotInfo(false, true, outputSlot));
            itemConfig.addSlotInfo(DataType.INPUT_OUTPUT, new InventorySlotInfo(true, true, inputSlot, outputSlot));
        }
        configComponent.setupOutputConfig(TransmissionType.FLUID, fluidTank, RelativeSide.RIGHT);

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
        HeatCapacitorHelper builder = HeatCapacitorHelper.forSide(facingSupplier);
        builder.addCapacitor(heatCapacitor = BasicHeatCapacitor.create(Config.HEAT_CAPACITY.get(), Config.INVERSE_CONDUCTION_COEFFICIENT.get(),
              Config.INVERSE_INSULATION_COEFFICIENT.get(), ambientTemperature, listener));
        return builder.build();
    }

    @NotNull
    @Override
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener, IContentsListener recipeCacheListener, IContentsListener recipeCacheUnpauseListener) {
        FluidTankHelper builder = FluidTankHelper.forSideWithConfig(this);
        //The tank is an output only; changes to it unpause the melting recipe cache so it can notice freed-up space
        builder.addTank(fluidTank = BasicFluidTank.output(MAX_FLUID, listener));
        return builder.build();
    }

    private boolean checkInputValidity(ItemStack item) {
        // We can't use getRecipe() here, since the input slot is still empty when we're testing.
        var recipe = getSmelterRecipe(item);
        if (recipe == null || getLevel() == null)
            return false;

        return recipe.matches(new SingleRecipeInput(item), getLevel());
    }

    private boolean checkFuelValidity(ItemStack item) {
        return ModRecipeType.findFirstFuelConversion(getLevel(), item) != null;
    }

    @NotNull
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
        if (recipeCacheLookupMonitor.getCachedRecipe(0) instanceof HeatSensitiveOneInputCachedRecipe recipe) {
            setOperatingTicks(recipe.getProgressTicks());
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
        if (canBurnFuel()) {
            ItemStack fuel = fuelSlot.getStack();
            ItemStackToHeatRecipe recipe = ModRecipeType.findFirstFuelConversion(getLevel(), fuel);
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
        return heatCapacitor.getTemperature() < Config.MAX_FUEL_TEMPERATURE.get() && !fuelSlot.isEmpty() && checkFuelValidity(fuelSlot.getStack());
    }

    private ItemStackToItemStackRecipe getSmelterRecipe(ItemStack item) {
        return MekanismRecipeType.SMELTING.getInputCache().findFirstRecipe(getLevel(), item);
    }

    @Nullable
    @Override
    public ItemStackToItemStackRecipe getRecipe(int cacheIndex) {
        return getSmelterRecipe(inputHandler.getInput());
    }

    @NotNull
    @Override
    protected RecipeCacheLookupMonitor<ItemStackToItemStackRecipe> createNewCacheMonitor() {
        return new HeatSmelterRecipeCacheLookupMonitor(this);
    }

    @NotNull
    @Override
    public CachedRecipe<ItemStackToItemStackRecipe> createNewCachedRecipe(@NotNull ItemStackToItemStackRecipe recipe, int cacheIndex) {
        return HeatSensitiveOneInputCachedRecipe.itemToItem(recipe, recheckAllRecipeErrors, inputHandler, outputHandler, this)
              .setErrorsChanged(this::onErrorsChanged)
              .setCanHolderFunction(this::canFunction)
              .setActive(this::setActive)
              .setOnFinish(this::markForSave)
              .setOperatingTicksChanged(this::setOperatingTicks);
    }

    @NotNull
    @Override
    public IMekanismRecipeTypeProvider<SingleRecipeInput, ItemStackToItemStackRecipe, SingleItem<ItemStackToItemStackRecipe>> getRecipeType() {
        return MekanismRecipeType.SMELTING;
    }

    /**
     * Speed multiplier based on the smelter's current temperature. Runs linearly from zero at {@link Config#BASE_TEMPERATURE} up to one at
     * {@link Config#FULL_SPEED_TEMPERATURE}, and is clamped to a minimum of zero.
     */
    public double getSpeedFactor() {
        double temperature = heatCapacitor.getTemperature();
        double base = Config.BASE_TEMPERATURE.get();
        double full = Config.FULL_SPEED_TEMPERATURE.get();
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
    public IRecipeViewerRecipeType<ItemStackToItemStackRecipe> recipeViewerType() {
        return RecipeViewerRecipeType.SMELTING;
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
