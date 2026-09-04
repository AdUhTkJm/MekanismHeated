package io.aduhtkjm.mekanismheated.tile;

import io.aduhtkjm.mekanismheated.Config;
import io.aduhtkjm.mekanismheated.integration.jei.ModRecipeViewerTypes;
import io.aduhtkjm.mekanismheated.recipe.CondenserRecipe;
import io.aduhtkjm.mekanismheated.recipe.ModRecipeTypes;
import io.aduhtkjm.mekanismheated.recipe.cache.CondenserCachedRecipe;
import io.aduhtkjm.mekanismheated.recipe.lookup.monitor.CondenserRecipeCacheLookupMonitor;
import io.aduhtkjm.mekanismheated.registries.ModBlocks;
import java.util.List;
import mekanism.api.IContentsListener;
import mekanism.api.RelativeSide;
import mekanism.api.SerializationConstants;
import mekanism.api.heat.HeatAPI.HeatTransfer;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.api.recipes.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.api.recipes.inputs.IInputHandler;
import mekanism.api.recipes.inputs.InputHelper;
import mekanism.api.recipes.outputs.IOutputHandler;
import mekanism.api.recipes.outputs.OutputHelper;
import mekanism.common.capabilities.heat.BasicHeatCapacitor;
import mekanism.common.capabilities.heat.CachedAmbientTemperature;
import mekanism.common.capabilities.fluid.BasicFluidTank;
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
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.recipe.lookup.IRecipeLookupHandler;
import mekanism.common.recipe.lookup.monitor.RecipeCacheLookupMonitor;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.component.config.slot.InventorySlotInfo;
import mekanism.common.tile.prefab.TileEntityProgressMachine;
import mekanism.common.util.EnumUtils;
import mekanism.common.util.NBTUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TileEntityCondenser extends TileEntityProgressMachine<CondenserRecipe> implements IRecipeLookupHandler<CondenserRecipe> {

    public static final RecipeError NOT_ENOUGH_FLUID_INPUT_ERROR = RecipeError.create();

    private static final List<RecipeError> TRACKED_ERROR_TYPES = List.of(
          RecipeError.NOT_ENOUGH_INPUT,
          NOT_ENOUGH_FLUID_INPUT_ERROR,
          RecipeError.NOT_ENOUGH_OUTPUT_SPACE,
          RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT
    );

    public static final int MAX_FLUID = Config.Condenser.FLUID_CAPACITY.get() * FluidType.BUCKET_VOLUME;

    /**
     * Default per-face data type for each transmission type the condenser supports. Indexed to match the order of
     * {@link RelativeSide} ({@link EnumUtils#SIDES}): FRONT, LEFT, RIGHT, BACK, TOP, BOTTOM. Players can still override
     * any face via the side config GUI.
     */
    private static final List<SideDefaults> SIDE_DEFAULTS = List.of(
          new SideDefaults(DataType.NONE, DataType.NONE, DataType.INPUT), //FRONT
          new SideDefaults(DataType.NONE, DataType.INPUT, DataType.INPUT), //LEFT
          new SideDefaults(DataType.OUTPUT, DataType.NONE, DataType.INPUT), //RIGHT
          new SideDefaults(DataType.NONE, DataType.NONE, DataType.INPUT), //BACK
          new SideDefaults(DataType.NONE, DataType.NONE, DataType.INPUT), //TOP
          new SideDefaults(DataType.NONE, DataType.NONE, DataType.INPUT) //BOTTOM
    );

    /** Default per-face {@link DataType} for the condenser's item, fluid and heat transmission. */
    private record SideDefaults(DataType item, DataType fluid, DataType heat) {}

    protected final IInputHandler<@NotNull FluidStack> fluidInputHandler;
    protected final IInputHandler<@NotNull ItemStack> itemInputHandler;
    protected final IOutputHandler<@NotNull ItemStack> outputHandler;

    private BasicHeatCapacitor heatCapacitor;
    public BasicFluidTank inputFluidTank;

    public InputInventorySlot inputSlot;
    public OutputInventorySlot outputSlot;

    private double lastEnvironmentLoss;
    private double lastTransferLoss;

    public TileEntityCondenser(BlockPos pos, BlockState state) {
        super(ModBlocks.CONDENSER, pos, state, TRACKED_ERROR_TYPES, Config.Condenser.BASE_SPEED.get());
        ConfigInfo itemConfig = configComponent.getConfig(TransmissionType.ITEM);
        if (itemConfig != null) {
            itemConfig.addSlotInfo(DataType.INPUT, new InventorySlotInfo(true, false, inputSlot));
            itemConfig.addSlotInfo(DataType.OUTPUT, new InventorySlotInfo(false, true, outputSlot));
            itemConfig.addSlotInfo(DataType.INPUT_OUTPUT, new InventorySlotInfo(true, true, inputSlot, outputSlot));
        }
        configComponent.setupInputConfig(TransmissionType.FLUID, inputFluidTank);
        configComponent.setupInputConfig(TransmissionType.HEAT, heatCapacitor);
        applySideDefaults();

        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.ITEM);

        fluidInputHandler = InputHelper.getInputHandler(inputFluidTank, NOT_ENOUGH_FLUID_INPUT_ERROR);
        itemInputHandler = InputHelper.getInputHandler(inputSlot, RecipeError.NOT_ENOUGH_INPUT);
        outputHandler = OutputHelper.getOutputHandler(outputSlot, RecipeError.NOT_ENOUGH_OUTPUT_SPACE);
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
    protected IHeatCapacitorHolder getInitialHeatCapacitors(IContentsListener listener, IContentsListener recipeCacheListener, IContentsListener recipeCacheUnpauseListener,
          CachedAmbientTemperature ambientTemperature) {
        HeatCapacitorHelper builder = HeatCapacitorHelper.forSideWithConfig(this);
        builder.addCapacitor(heatCapacitor = BasicHeatCapacitor.create(Config.Condenser.HEAT_CAPACITY.get(), Config.Condenser.INVERSE_CONDUCTION_COEFFICIENT.get(),
              Config.Condenser.INVERSE_INSULATION_COEFFICIENT.get(), ambientTemperature, listener));
        return builder.build();
    }

    @Override
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener, IContentsListener recipeCacheListener, IContentsListener recipeCacheUnpauseListener) {
        FluidTankHelper builder = FluidTankHelper.forSideWithConfig(this);
        builder.addTank(inputFluidTank = BasicFluidTank.input(MAX_FLUID, this::containsRecipeFluid, recipeCacheListener));
        return builder.build();
    }

    private boolean containsRecipeFluid(FluidStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Level level = getLevel();
        if (level == null) {
            return false;
        }
        for (CondenserRecipe recipe : getCondenserRecipes(level)) {
            if (recipe.getFluidInput().testType(stack)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsRecipeItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Level level = getLevel();
        if (level == null) {
            return false;
        }
        for (CondenserRecipe recipe : getCondenserRecipes(level)) {
            if (recipe.hasItemInput() && recipe.getItemInput().orElseThrow().test(stack)) {
                return true;
            }
        }
        return false;
    }

    private List<CondenserRecipe> getCondenserRecipes(Level level) {
        return level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.TYPE_CONDENSING.value())
              .stream()
              .map(RecipeHolder::value)
              .toList();
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener, IContentsListener recipeCacheListener, IContentsListener recipeCacheUnpauseListener) {
        InventorySlotHelper builder = InventorySlotHelper.forSideWithConfig(this);
        builder.addSlot(inputSlot = InputInventorySlot.at(this::containsRecipeItem, recipeCacheListener, 65, 35))
              .tracksWarnings(slot -> slot.warning(WarningType.NO_MATCHING_RECIPE, getWarningCheck(RecipeError.NOT_ENOUGH_INPUT)));
        builder.addSlot(outputSlot = OutputInventorySlot.at(recipeCacheUnpauseListener, 116, 35))
              .tracksWarnings(slot -> slot.warning(WarningType.NO_SPACE_IN_OUTPUT, getWarningCheck(RecipeError.NOT_ENOUGH_OUTPUT_SPACE)));
        return builder.build();
    }

    @Override
    protected boolean onUpdateServer() {
        boolean sendUpdatePacket = super.onUpdateServer();
        HeatTransfer transfer = simulate();
        lastEnvironmentLoss = transfer.environmentTransfer();
        lastTransferLoss = transfer.adjacentTransfer();
        recipeCacheLookupMonitor.updateAndProcess();
        if (recipeCacheLookupMonitor.getCachedRecipe(0) instanceof CondenserCachedRecipe cachedRecipe) {
            setOperatingTicks(cachedRecipe.getProgressTicks());
        }
        return sendUpdatePacket;
    }

    /**
     * Speed multiplier based on the condenser's current temperature. The condenser works faster when colder:
     * 100% speed at FULL_SPEED_TEMPERATURE (cold end), 0% at MAX_TEMPERATURE (hot end), linear between.
     */
    public double getSpeedFactor() {
        double temperature = heatCapacitor.getTemperature();
        double max = Config.Condenser.MAX_TEMPERATURE.get();
        double full = Config.Condenser.FULL_SPEED_TEMPERATURE.get();
        double range = max - full;
        if (range <= 0) {
            return temperature < max ? 1 : 0;
        }
        return Math.clamp((max - temperature) / range, 0, 1);
    }

    /**
     * Number of operations that can be performed this tick, which is zero if the condenser is too hot to process.
     */
    public int getBaselineMaxOperations() {
        return getSpeedFactor() > 0 ? 1 : 0;
    }

    /**
     * Finds the recipe to use for the given inputs. When two recipes match the same fluid but differ in whether they
     * consume an item, the recipe consuming an item is preferred.
     */
    @Nullable
    public static CondenserRecipe findFirstCondenserRecipe(@Nullable Level level, FluidStack fluid, ItemStack item) {
        if (level == null || fluid.isEmpty()) {
            return null;
        }
        CondenserRecipe itemlessMatch = null;
        for (RecipeHolder<CondenserRecipe> holder : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.TYPE_CONDENSING.value())) {
            CondenserRecipe recipe = holder.value();
            if (!recipe.test(fluid, item)) {
                continue;
            }
            if (recipe.hasItemInput()) {
                return recipe;
            }
            if (itemlessMatch == null) {
                itemlessMatch = recipe;
            }
        }
        return itemlessMatch;
    }

    @Nullable
    @Override
    public CondenserRecipe getRecipe(int cacheIndex) {
        return findFirstCondenserRecipe(getLevel(), fluidInputHandler.getInput(), itemInputHandler.getInput());
    }

    @NotNull
    @Override
    protected RecipeCacheLookupMonitor<CondenserRecipe> createNewCacheMonitor() {
        return new CondenserRecipeCacheLookupMonitor(this);
    }

    @NotNull
    @Override
    public CachedRecipe<CondenserRecipe> createNewCachedRecipe(@NotNull CondenserRecipe recipe, int cacheIndex) {
        CondenserCachedRecipe cached = new CondenserCachedRecipe(recipe, recheckAllRecipeErrors, fluidInputHandler, itemInputHandler, outputHandler, this);
        cached.setErrorsChanged(this::onErrorsChanged)
              .setCanHolderFunction(this::canFunction)
              .setActive(this::setActive)
              .setOnFinish(this::markForSave)
              .setOperatingTicksChanged(this::setOperatingTicks);
        return cached;
    }

    @Nullable
    @Override
    public IMekanismRecipeTypeProvider<?, CondenserRecipe, ?> getRecipeType() {
        return null;
    }

    @NotNull
    @Override
    public IRecipeViewerRecipeType<CondenserRecipe> recipeViewerType() {
        return ModRecipeViewerTypes.CONDENSING;
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
        updateTag.put(SerializationConstants.FLUID, inputFluidTank.serializeNBT(provider));
        return updateTag;
    }

    @Override
    public void handleUpdateTag(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        super.handleUpdateTag(tag, provider);
        NBTUtils.setCompoundIfPresent(tag, SerializationConstants.FLUID, nbt -> inputFluidTank.deserializeNBT(provider, nbt));
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
