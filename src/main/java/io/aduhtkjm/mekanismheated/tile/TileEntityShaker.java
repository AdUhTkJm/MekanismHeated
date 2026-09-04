package io.aduhtkjm.mekanismheated.tile;

import io.aduhtkjm.mekanismheated.Config;
import io.aduhtkjm.mekanismheated.integration.jei.ModRecipeViewerTypes;
import io.aduhtkjm.mekanismheated.recipe.ModRecipeTypes;
import io.aduhtkjm.mekanismheated.recipe.ShakerRecipe;
import io.aduhtkjm.mekanismheated.recipe.cache.ShakerCachedRecipe;
import io.aduhtkjm.mekanismheated.registries.ModBlocks;
import java.util.Arrays;
import java.util.List;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.RelativeSide;
import mekanism.api.SerializationConstants;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.api.recipes.cache.CachedRecipe.OperationTracker.RecipeError;
import mekanism.api.recipes.inputs.IInputHandler;
import mekanism.api.recipes.inputs.InputHelper;
import mekanism.api.recipes.outputs.IOutputHandler;
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
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.inventory.warning.WarningTracker.WarningType;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.client.recipe_viewer.type.IRecipeViewerRecipeType;
import mekanism.common.recipe.IMekanismRecipeTypeProvider;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
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

/**
 * Powered inventory and processing logic for the Shaker.
 *
 * <p>The Shaker shakes one input item at a time, optionally consuming fluid from its input tank, and produces 1-3 item
 * stacks. When two recipes match the same item but differ in whether they consume fluid, the fluid consuming recipe
 * takes precedence.</p>
 */
public class TileEntityShaker extends TileEntityProgressMachine<ShakerRecipe> {

    /** Error for the fluid input tank, separate from the item input's error so their warnings do not cross-talk. */
    public static final RecipeError NOT_ENOUGH_FLUID_INPUT_ERROR = RecipeError.create();

    private static final List<RecipeError> TRACKED_ERROR_TYPES = List.of(
          RecipeError.NOT_ENOUGH_ENERGY,
          RecipeError.NOT_ENOUGH_INPUT,
          NOT_ENOUGH_FLUID_INPUT_ERROR,
          RecipeError.NOT_ENOUGH_OUTPUT_SPACE,
          RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT
    );

    /** Capacity of the fluid input tank, in milli-buckets. */
    public static final int MAX_FLUID = 10 * FluidType.BUCKET_VOLUME;

    /**
     * Default per-face data type for each transmission type the shaker supports. Indexed to match the order of
     * {@link RelativeSide} ({@link EnumUtils#SIDES}): FRONT, LEFT, RIGHT, BACK, TOP, BOTTOM. Players can still override
     * any face via the side config GUI.
     */
    private static final List<SideDefaults> SIDE_DEFAULTS = List.of(
          new SideDefaults(DataType.INPUT, DataType.INPUT, DataType.INPUT), //FRONT
          new SideDefaults(DataType.INPUT, DataType.INPUT, DataType.INPUT), //LEFT
          new SideDefaults(DataType.OUTPUT, DataType.INPUT, DataType.INPUT), //RIGHT
          new SideDefaults(DataType.INPUT, DataType.INPUT, DataType.INPUT), //BACK
          new SideDefaults(DataType.INPUT, DataType.INPUT, DataType.INPUT), //TOP
          new SideDefaults(DataType.INPUT, DataType.INPUT, DataType.INPUT) //BOTTOM
    );

    /** Default per-face {@link DataType} for the shaker's item, fluid and energy transmission. */
    private record SideDefaults(DataType item, DataType fluid, DataType energy) {}

    protected final IInputHandler<@NotNull ItemStack> inputHandler;
    protected final IInputHandler<@NotNull FluidStack> fluidInputHandler;
    protected final IOutputHandler<@NotNull List<ItemStack>> outputHandler;

    private MachineEnergyContainer<TileEntityShaker> energyContainer;
    public BasicFluidTank inputFluidTank;

    InputInventorySlot inputSlot;
    FluidInventorySlot fluidSlot;
    OutputInventorySlot[] outputSlots;
    EnergyInventorySlot energySlot;

    public TileEntityShaker(BlockPos pos, BlockState blockState) {
        super(ModBlocks.SHAKER, pos, blockState, TRACKED_ERROR_TYPES, Config.Shaker.BASE_SPEED.get());
        configComponent.setupItemIOConfig(Arrays.asList(inputSlot, fluidSlot), Arrays.asList(outputSlots), energySlot, false);
        configComponent.setupInputConfig(TransmissionType.FLUID, inputFluidTank);
        configComponent.setupInputConfig(TransmissionType.ENERGY, energyContainer);
        applySideDefaults();

        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.ITEM);

        inputHandler = InputHelper.getInputHandler(inputSlot, RecipeError.NOT_ENOUGH_INPUT);
        fluidInputHandler = InputHelper.getInputHandler(inputFluidTank, NOT_ENOUGH_FLUID_INPUT_ERROR);
        outputHandler = new MultiSlotOutputHandler(Arrays.asList(outputSlots), RecipeError.NOT_ENOUGH_OUTPUT_SPACE);
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
        builder.addContainer(energyContainer = MachineEnergyContainer.input(this, listener));
        return builder.build();
    }

    @Override
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener, IContentsListener recipeCacheListener, IContentsListener recipeCacheUnpauseListener) {
        FluidTankHelper builder = FluidTankHelper.forSideWithConfig(this);
        builder.addTank(inputFluidTank = BasicFluidTank.input(MAX_FLUID, this::containsRecipeFluid, recipeCacheListener));
        return builder.build();
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener, IContentsListener recipeCacheListener, IContentsListener recipeCacheUnpauseListener) {
        outputSlots = new OutputInventorySlot[3];

        InventorySlotHelper builder = InventorySlotHelper.forSideWithConfig(this);
        builder.addSlot(inputSlot = InputInventorySlot.at(this::containsRecipeItem, recipeCacheListener, 64, 17))
              .tracksWarnings(slot -> slot.warning(WarningType.NO_MATCHING_RECIPE, getWarningCheck(RecipeError.NOT_ENOUGH_INPUT)));
        //Slot for filling the input tank from buckets or other fluid containers
        builder.addSlot(fluidSlot = FluidInventorySlot.fill(inputFluidTank, listener, 26, 35));
        fluidSlot.setSlotType(ContainerSlotType.INPUT);
        for (int i = 0; i < outputSlots.length; i++) {
            int x = 116, y = 17 + 18 * i;
            builder.addSlot(outputSlots[i] = OutputInventorySlot.at(recipeCacheUnpauseListener, x, y))
                  .tracksWarnings(slot -> slot.warning(WarningType.NO_SPACE_IN_OUTPUT, getWarningCheck(RecipeError.NOT_ENOUGH_OUTPUT_SPACE)));
        }
        builder.addSlot(energySlot = EnergyInventorySlot.fillOrConvert(energyContainer, this::getLevel, listener, 64, 53));
        return builder.build();
    }

    @Override
    protected boolean onUpdateServer() {
        boolean sendUpdatePacket = super.onUpdateServer();
        energySlot.fillContainerOrConvert();
        fluidSlot.fillTank();
        recipeCacheLookupMonitor.updateAndProcess();
        return sendUpdatePacket;
    }

    /**
     * Checks if any shaking recipe consumes the given fluid; used to validate what may enter the input tank.
     */
    private boolean containsRecipeFluid(FluidStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Level level = getLevel();
        if (level == null) {
            return false;
        }
        for (ShakerRecipe recipe : getShakerRecipes(level)) {
            if (recipe.hasFluidInput() && recipe.getFluidInput().orElseThrow().testType(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if any shaking recipe uses the given item as its input; used to validate what may enter the input slot.
     */
    private boolean containsRecipeItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Level level = getLevel();
        if (level == null) {
            return false;
        }
        for (ShakerRecipe recipe : getShakerRecipes(level)) {
            if (recipe.getInput().test(stack)) {
                return true;
            }
        }
        return false;
    }

    private List<ShakerRecipe> getShakerRecipes(Level level) {
        return level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.TYPE_SHAKING.value())
              .stream()
              .map(RecipeHolder::value)
              .toList();
    }

    /**
     * Finds the recipe to use for the given inputs.
     *
     * <p>When two recipes match the same item but differ in whether they consume fluid, the recipe consuming fluid is
     * preferred; the fluid-less variant only gets used while the tank cannot supply the fluid version.</p>
     */
    @Nullable
    public static ShakerRecipe findFirstShakerRecipe(@Nullable Level level, ItemStack item, FluidStack fluid) {
        if (level == null || item.isEmpty()) {
            return null;
        }
        ShakerRecipe fluidlessMatch = null;
        for (RecipeHolder<ShakerRecipe> holder : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.TYPE_SHAKING.value())) {
            ShakerRecipe recipe = holder.value();
            if (!recipe.test(item, fluid)) {
                continue;
            }
            if (recipe.hasFluidInput()) {
                //Prefer recipes that consume fluid over otherwise identical recipes that don't
                return recipe;
            }
            if (fluidlessMatch == null) {
                fluidlessMatch = recipe;
            }
        }
        return fluidlessMatch;
    }

    public boolean isShaking() {
        return getActive();
    }

    public ItemStack getStoredItem() {
        return inputSlot == null ? ItemStack.EMPTY : inputSlot.getStack();
    }

    public MachineEnergyContainer<TileEntityShaker> getEnergyContainer() {
        return energyContainer;
    }

    @Nullable
    @Override
    public ShakerRecipe getRecipe(int cacheIndex) {
        return findFirstShakerRecipe(getLevel(), inputHandler.getInput(), fluidInputHandler.getInput());
    }

    @NotNull
    @Override
    public CachedRecipe<ShakerRecipe> createNewCachedRecipe(@NotNull ShakerRecipe recipe, int cacheIndex) {
        return new ShakerCachedRecipe(recipe, recheckAllRecipeErrors, inputHandler, fluidInputHandler, outputHandler, this)
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
    public IMekanismRecipeTypeProvider<?, ShakerRecipe, ?> getRecipeType() {
        return null;
    }

    @NotNull
    @Override
    public IRecipeViewerRecipeType<ShakerRecipe> recipeViewerType() {
        return ModRecipeViewerTypes.SHAKING;
    }

    @Override
    public void onContentsChanged() {
        super.onContentsChanged();
        // The item is also rendered on the physical shaker platform, so changes need to reach
        // clients even when no player currently has the GUI open.
        if (level != null && !level.isClientSide) {
            sendUpdatePacket();
        }
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

    /**
     * Output handler that spreads a recipe's 1-3 outputs across the shaker's three output slots, one stack per slot.
     */
    private record MultiSlotOutputHandler(List<IInventorySlot> slots, RecipeError notEnoughSpace) implements IOutputHandler<@NotNull List<ItemStack>> {
        @Override
        public void handleOutput(@NotNull List<ItemStack> toOutput, int operations) {
            if (operations == 0 || toOutput.isEmpty()) {
                //This should not happen
                return;
            }
            for (int i = 0; i < toOutput.size() && i < slots.size(); i++) {
                ItemStack output = toOutput.get(i);
                if (output.isEmpty()) {
                    continue;
                }
                ItemStack toInsert = operations > 1 ? output.copyWithCount(output.getCount() * operations) : output.copy();
                slots.get(i).insertItem(toInsert, Action.EXECUTE, AutomationType.INTERNAL);
            }
        }

        @Override
        public void calculateOperationsCanSupport(CachedRecipe.OperationTracker tracker, List<ItemStack> toOutput) {
            for (int i = 0; i < toOutput.size() && i < slots.size() && tracker.shouldContinueChecking(); i++) {
                calculateOperationsCanSupport(tracker, slots.get(i), toOutput.get(i));
            }
        }

        private void calculateOperationsCanSupport(CachedRecipe.OperationTracker tracker, IInventorySlot slot, ItemStack toOutput) {
            //If our output is empty, we have nothing to add, so we treat it as being able to fit all
            if (!toOutput.isEmpty()) {
                //Make a copy of the stack we are outputting with its maximum size
                ItemStack maxOutput = toOutput.copyWithCount(toOutput.getMaxStackSize());
                ItemStack remainder = slot.insertItem(maxOutput, Action.SIMULATE, AutomationType.INTERNAL);
                int amountUsed = maxOutput.getCount() - remainder.getCount();
                //Divide the amount we can actually use by the amount one output operation is equal to
                int operations = amountUsed / toOutput.getCount();
                tracker.updateOperations(operations);
                if (operations == 0) {
                    if (amountUsed == 0 && slot.getLimit(slot.getStack()) - slot.getCount() > 0) {
                        tracker.addError(RecipeError.INPUT_DOESNT_PRODUCE_OUTPUT);
                    } else {
                        tracker.addError(notEnoughSpace);
                    }
                }
            }
        }
    }
}
