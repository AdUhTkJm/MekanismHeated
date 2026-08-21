package io.aduhtkjm.mekanismheated.tile;

import io.aduhtkjm.mekanismheated.Config;
import io.aduhtkjm.mekanismheated.recipe.lookup.monitor.HeatSmelterRecipeCacheLookupMonitor;
import io.aduhtkjm.mekanismheated.recipes.ItemStackToHeatRecipe;
import io.aduhtkjm.mekanismheated.recipes.ModRecipeType;
import io.aduhtkjm.mekanismheated.registries.ModBlocks;
import java.util.List;
import mekanism.api.Action;
import mekanism.api.IContentsListener;
import mekanism.api.heat.HeatAPI.HeatTransfer;
import mekanism.api.recipes.ItemStackToItemStackRecipe;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.api.recipes.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.api.recipes.cache.OneInputCachedRecipe;
import mekanism.api.recipes.inputs.IInputHandler;
import mekanism.api.recipes.inputs.InputHelper;
import mekanism.api.recipes.outputs.IOutputHandler;
import mekanism.api.recipes.outputs.OutputHelper;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.client.recipe_viewer.type.RecipeViewerRecipeType;
import mekanism.common.capabilities.heat.BasicHeatCapacitor;
import mekanism.common.capabilities.heat.CachedAmbientTemperature;
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
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TileEntityHeatSmelter extends TileEntityProgressMachine<ItemStackToItemStackRecipe>
      implements IRecipeLookupHandler<ItemStackToItemStackRecipe> {

    private static final List<RecipeError> TRACKED_ERROR_TYPES = List.of(
          RecipeError.NOT_ENOUGH_ENERGY,
          RecipeError.NOT_ENOUGH_INPUT,
          RecipeError.NOT_ENOUGH_OUTPUT_SPACE,
          RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT
    );
    public static final double HEAT_CAPACITY = 100;
    public static final double INVERSE_CONDUCTION_COEFFICIENT = 5;
    public static final double INVERSE_INSULATION_COEFFICIENT = 10;
    public static final double MAX_FUEL_TEMPERATURE = 1_000;

    protected final IInputHandler<@NotNull ItemStack> inputHandler;
    protected final IOutputHandler<@NotNull ItemStack> outputHandler;

    private BasicHeatCapacitor heatCapacitor;

    private double lastEnvironmentLoss;
    private double lastTransferLoss;

    InputInventorySlot inputSlot;
    InputInventorySlot fuelSlot;
    OutputInventorySlot outputSlot;

    public TileEntityHeatSmelter(BlockPos pos, BlockState state) {
        super(ModBlocks.HEAT_SMELTER, pos, state, TRACKED_ERROR_TYPES, Config.BASE_SPEED.get());
        ConfigInfo itemConfig = configComponent.getConfig(TransmissionType.ITEM);
        if (itemConfig != null) {
            itemConfig.addSlotInfo(DataType.INPUT, new InventorySlotInfo(true, false, inputSlot));
            itemConfig.addSlotInfo(DataType.OUTPUT, new InventorySlotInfo(false, true, outputSlot));
            itemConfig.addSlotInfo(DataType.INPUT_OUTPUT, new InventorySlotInfo(true, true, inputSlot, outputSlot));
        }

        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.ITEM);

        inputHandler = InputHelper.getInputHandler(inputSlot, RecipeError.NOT_ENOUGH_INPUT);
        outputHandler = OutputHelper.getOutputHandler(outputSlot, RecipeError.NOT_ENOUGH_OUTPUT_SPACE);
    }

    @NotNull
    @Override
    protected IHeatCapacitorHolder getInitialHeatCapacitors(IContentsListener listener, IContentsListener recipeCacheListener, IContentsListener recipeCacheUnpauseListener,
          CachedAmbientTemperature ambientTemperature) {
        HeatCapacitorHelper builder = HeatCapacitorHelper.forSide(facingSupplier);
        builder.addCapacitor(heatCapacitor = BasicHeatCapacitor.create(HEAT_CAPACITY, INVERSE_CONDUCTION_COEFFICIENT, INVERSE_INSULATION_COEFFICIENT, ambientTemperature, listener));
        return builder.build();
    }

    private boolean checkInputValidity(ItemStack item) {
        var recipe = getRecipe(0);
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
        builder.addSlot(fuelSlot = InputInventorySlot.at(this::checkFuelValidity, recipeCacheListener, 64, 60))
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
        return heatCapacitor.getTemperature() < MAX_FUEL_TEMPERATURE && !fuelSlot.isEmpty() && checkFuelValidity(fuelSlot.getStack());
    }

    @Nullable
    @Override
    public ItemStackToItemStackRecipe getRecipe(int cacheIndex) {
        return MekanismRecipeType.SMELTING.getInputCache().findFirstRecipe(getLevel(), inputHandler.getInput());
    }

    @NotNull
    @Override
    protected RecipeCacheLookupMonitor<ItemStackToItemStackRecipe> createNewCacheMonitor() {
        return new HeatSmelterRecipeCacheLookupMonitor(this);
    }

    @NotNull
    @Override
    public CachedRecipe<ItemStackToItemStackRecipe> createNewCachedRecipe(@NotNull ItemStackToItemStackRecipe recipe, int cacheIndex) {
        return OneInputCachedRecipe.itemToItem(recipe, recheckAllRecipeErrors, inputHandler, outputHandler)
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
     * Number of game ticks required to complete the current recipe at the smelter's current temperature.
     */
    public int getTicksRequiredForTemperature() {
        double speedFactor = getSpeedFactor();
        if (speedFactor <= 0) {
            return Integer.MAX_VALUE;
        }
        //Clamp to Integer.MAX_VALUE so that a barely-positive speed factor can never overflow the int return type
        return (int) Math.clamp(Math.ceil(getTicksRequired() / speedFactor), 1, Integer.MAX_VALUE);
    }

    /**
     * Number of operations that can be performed this tick, which is zero if the smelter is too cold to process.
     */
    public int getBaselineMaxOperations() {
        return getSpeedFactor() > 0 ? 1 : 0;
    }

    @Override
    public double getScaledProgress() {
        return getOperatingTicks() / (double) getTicksRequiredForTemperature();
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
