package io.aduhtkjm.mekanismheated.tile;

import io.aduhtkjm.mekanismheated.Config;
import io.aduhtkjm.mekanismheated.integration.jei.ModRecipeViewerTypes;
import io.aduhtkjm.mekanismheated.recipe.ModRecipeTypes;
import io.aduhtkjm.mekanismheated.recipe.QuenchingRecipe;
import io.aduhtkjm.mekanismheated.recipe.cache.QuenchingCachedRecipe;
import io.aduhtkjm.mekanismheated.registries.ModBlocks;
import java.util.List;
import mekanism.api.IContentsListener;
import mekanism.api.RelativeSide;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.api.recipes.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.api.recipes.inputs.IInputHandler;
import mekanism.api.recipes.inputs.InputHelper;
import mekanism.api.recipes.outputs.IOutputHandler;
import mekanism.api.recipes.outputs.OutputHelper;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.holder.energy.EnergyContainerHelper;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.fluid.FluidTankHelper;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.container.slot.ContainerSlotType;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.FluidInventorySlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.warning.WarningTracker.WarningType;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.recipe.IMekanismRecipeTypeProvider;
import mekanism.common.recipe.lookup.IRecipeLookupHandler;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.component.config.slot.InventorySlotInfo;
import mekanism.common.tile.prefab.TileEntityProgressMachine;
import mekanism.common.util.EnumUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Powered inventory and processing logic for the Quenching Enrichment Chamber.
 *
 * <p>The chamber quenches one input item together with fluid from its input tank, producing a fluid in its output tank.
 * Unlike the other machines in this mod it involves no heat at all: it draws {@link Config.QuenchingEnrichmentChamber#ENERGY_PER_TICK}
 * Joules per tick while processing, and each recipe takes {@link Config.QuenchingEnrichmentChamber#PROCESSING_TIME} ticks
 * (before upgrades).</p>
 */
public class TileEntityQuenchingEnrichmentChamber extends TileEntityProgressMachine<QuenchingRecipe> implements IRecipeLookupHandler<QuenchingRecipe> {

    /** Error for the fluid input tank, separate from the item input's error so their warnings do not cross-talk. */
    public static final RecipeError NOT_ENOUGH_FLUID_INPUT_ERROR = RecipeError.create();
    /** Error for the fluid output tank, separate from the item input's error so their warnings do not cross-talk. */
    public static final RecipeError NOT_ENOUGH_FLUID_OUTPUT_SPACE_ERROR = RecipeError.create();

    private static final List<RecipeError> TRACKED_ERROR_TYPES = List.of(
          RecipeError.NOT_ENOUGH_ENERGY,
          RecipeError.NOT_ENOUGH_INPUT,
          NOT_ENOUGH_FLUID_INPUT_ERROR,
          NOT_ENOUGH_FLUID_OUTPUT_SPACE_ERROR,
          RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT
    );

    /** Capacity of the fluid input tank, in milli-buckets. */
    public static final int MAX_INPUT_FLUID = Config.QuenchingEnrichmentChamber.INPUT_FLUID_CAPACITY.get() * FluidType.BUCKET_VOLUME;
    /** Capacity of the fluid output tank, in milli-buckets. */
    public static final int MAX_OUTPUT_FLUID = Config.QuenchingEnrichmentChamber.OUTPUT_FLUID_CAPACITY.get() * FluidType.BUCKET_VOLUME;

    /**
     * Default per-face data type for each transmission type the chamber supports. Indexed to match the order of
     * {@link RelativeSide} ({@link EnumUtils#SIDES}): FRONT, LEFT, RIGHT, BACK, TOP, BOTTOM. Players can still override
     * any face via the side config GUI.
     */
    private static final List<SideDefaults> SIDE_DEFAULTS = List.of(
          new SideDefaults(DataType.INPUT, DataType.INPUT, DataType.INPUT), //FRONT
          new SideDefaults(DataType.INPUT, DataType.INPUT, DataType.INPUT), //LEFT
          new SideDefaults(DataType.INPUT, DataType.OUTPUT, DataType.INPUT), //RIGHT
          new SideDefaults(DataType.INPUT, DataType.INPUT, DataType.INPUT), //BACK
          new SideDefaults(DataType.INPUT, DataType.INPUT, DataType.INPUT), //TOP
          new SideDefaults(DataType.INPUT, DataType.INPUT, DataType.INPUT) //BOTTOM
    );

    /** Default per-face {@link DataType} for the chamber's item, fluid and energy transmission. */
    private record SideDefaults(DataType item, DataType fluid, DataType energy) {}

    protected final IInputHandler<@NotNull ItemStack> itemInputHandler;
    protected final IInputHandler<@NotNull FluidStack> fluidInputHandler;
    protected final IOutputHandler<@NotNull FluidStack> fluidOutputHandler;

    private MachineEnergyContainer<TileEntityQuenchingEnrichmentChamber> energyContainer;
    public BasicFluidTank inputFluidTank;
    public BasicFluidTank outputFluidTank;

    InputInventorySlot inputSlot;

    public TileEntityQuenchingEnrichmentChamber(BlockPos pos, BlockState state) {
        super(ModBlocks.QUENCHING_ENRICHMENT_CHAMBER, pos, state, TRACKED_ERROR_TYPES, Config.QuenchingEnrichmentChamber.PROCESSING_TIME.get());
        ConfigInfo itemConfig = configComponent.getConfig(TransmissionType.ITEM);
        if (itemConfig != null) {
            itemConfig.addSlotInfo(DataType.INPUT, new InventorySlotInfo(true, false, inputSlot));
        }
        configComponent.setupIOConfig(TransmissionType.FLUID, inputFluidTank, outputFluidTank, RelativeSide.RIGHT);
        configComponent.setupInputConfig(TransmissionType.ENERGY, energyContainer);
        applySideDefaults();

        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.FLUID);

        itemInputHandler = InputHelper.getInputHandler(inputSlot, RecipeError.NOT_ENOUGH_INPUT);
        fluidInputHandler = InputHelper.getInputHandler(inputFluidTank, NOT_ENOUGH_FLUID_INPUT_ERROR);
        fluidOutputHandler = OutputHelper.getOutputHandler(outputFluidTank, NOT_ENOUGH_FLUID_OUTPUT_SPACE_ERROR);
    }

    /**
     * Applies {@link #SIDE_DEFAULTS} to the side config, setting each face's data type for item, fluid and energy.
     */
    private void applySideDefaults() {
        ConfigInfo itemConfig = configComponent.getConfig(TransmissionType.ITEM);
        ConfigInfo fluidConfig = configComponent.getConfig(TransmissionType.FLUID);
        ConfigInfo energyConfig = configComponent.getConfig(TransmissionType.ENERGY);
        for (int i = 0; i < SIDE_DEFAULTS.size(); i++) {
            RelativeSide side = EnumUtils.SIDES[i];
            SideDefaults defaults = SIDE_DEFAULTS.get(i);
            if (itemConfig != null) {
                itemConfig.setDataType(defaults.item(), side);
            }
            if (fluidConfig != null) {
                fluidConfig.setDataType(defaults.fluid(), side);
            }
            if (energyConfig != null) {
                energyConfig.setDataType(defaults.energy(), side);
            }
        }
    }

    @Override
    protected IEnergyContainerHolder getInitialEnergyContainers(IContentsListener listener, IContentsListener recipeCacheListener, IContentsListener recipeCacheUnpauseListener) {
        EnergyContainerHelper builder = EnergyContainerHelper.forSideWithConfig(this);
        //Use the unpause listener rather than the plain one: running out of energy pauses the cached recipe (see
        //CachedRecipe#pausedForErrors), and it is only resumed when a container that can resolve the error changes.
        //Without this, adding energy to an already-loaded machine would never wake the recipe back up.
        builder.addContainer(energyContainer = MachineEnergyContainer.input(this, recipeCacheUnpauseListener));
        return builder.build();
    }

    @Override
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener, IContentsListener recipeCacheListener, IContentsListener recipeCacheUnpauseListener) {
        FluidTankHelper builder = FluidTankHelper.forSideWithConfig(this);
        builder.addTank(inputFluidTank = BasicFluidTank.input(MAX_INPUT_FLUID, this::containsRecipeFluid, recipeCacheListener));
        builder.addTank(outputFluidTank = BasicFluidTank.output(MAX_OUTPUT_FLUID, recipeCacheUnpauseListener));
        return builder.build();
    }

    /**
     * Checks if any quenching recipe consumes the given fluid; used to validate what may enter the input tank.
     */
    private boolean containsRecipeFluid(FluidStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Level level = getLevel();
        if (level == null) {
            return false;
        }
        for (RecipeHolder<QuenchingRecipe> holder : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.TYPE_QUENCHING.value())) {
            if (holder.value().getFluidInput().testType(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if any quenching recipe uses the given item as its input; used to validate what may enter the input slot.
     */
    private boolean containsRecipeItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Level level = getLevel();
        if (level == null) {
            return false;
        }
        for (RecipeHolder<QuenchingRecipe> holder : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.TYPE_QUENCHING.value())) {
            if (holder.value().getItemInput().test(stack)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener, IContentsListener recipeCacheListener, IContentsListener recipeCacheUnpauseListener) {
        InventorySlotHelper builder = InventorySlotHelper.forSideWithConfig(this);
        builder.addSlot(inputSlot = InputInventorySlot.at(this::containsRecipeItem, recipeCacheListener, 64, 40))
              .tracksWarnings(slot -> slot.warning(WarningType.NO_MATCHING_RECIPE, getWarningCheck(RecipeError.NOT_ENOUGH_INPUT)));
        return builder.build();
    }

    @Override
    protected boolean onUpdateServer() {
        boolean sendUpdatePacket = super.onUpdateServer();
        recipeCacheLookupMonitor.updateAndProcess();
        return sendUpdatePacket;
    }

    /**
     * Finds the recipe to use for the given inputs.
     *
     * @return The first recipe whose item and fluid inputs both match, or {@code null} if there is none.
     */
    @Nullable
    public static QuenchingRecipe findFirstQuenchingRecipe(@Nullable Level level, ItemStack item, FluidStack fluid) {
        if (level == null || item.isEmpty() || fluid.isEmpty()) {
            return null;
        }
        for (RecipeHolder<QuenchingRecipe> holder : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.TYPE_QUENCHING.value())) {
            QuenchingRecipe recipe = holder.value();
            if (recipe.test(item, fluid)) {
                return recipe;
            }
        }
        return null;
    }

    @Nullable
    @Override
    public QuenchingRecipe getRecipe(int cacheIndex) {
        return findFirstQuenchingRecipe(getLevel(), itemInputHandler.getInput(), fluidInputHandler.getInput());
    }

    @NotNull
    @Override
    public CachedRecipe<QuenchingRecipe> createNewCachedRecipe(@NotNull QuenchingRecipe recipe, int cacheIndex) {
        return new QuenchingCachedRecipe(recipe, recheckAllRecipeErrors, itemInputHandler, fluidInputHandler, fluidOutputHandler)
              .setErrorsChanged(this::onErrorsChanged)
              .setCanHolderFunction(this::canFunction)
              .setActive(this::setActive)
              .setEnergyRequirements(energyContainer::getEnergyPerTick, energyContainer)
              .setRequiredTicks(this::getTicksRequired)
              .setOnFinish(this::markForSave)
              .setOperatingTicksChanged(this::setOperatingTicks);
    }

    //Safe to return null. Never used by outside code, but we must have one.
    @Nullable
    @Override
    public IMekanismRecipeTypeProvider<?, QuenchingRecipe, ?> getRecipeType() {
        return null;
    }

    @NotNull
    @Override
    public IRecipeViewerRecipeType<QuenchingRecipe> recipeViewerType() {
        return ModRecipeViewerTypes.QUENCHING;
    }

    public MachineEnergyContainer<TileEntityQuenchingEnrichmentChamber> getEnergyContainer() {
        return energyContainer;
    }
}
