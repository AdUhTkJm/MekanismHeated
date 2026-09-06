package io.aduhtkjm.mekanismheated.tile;

import io.aduhtkjm.mekanismheated.Config;
import io.aduhtkjm.mekanismheated.recipe.ModRecipeTypes;
import io.aduhtkjm.mekanismheated.recipe.ReactionChamberRecipe;
import io.aduhtkjm.mekanismheated.recipe.ReactionChamberRecipeInput;
import io.aduhtkjm.mekanismheated.registries.ModBlocks;
import io.aduhtkjm.mekanismheated.tank.MultiFluidChemicalTank;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.RelativeSide;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.heat.HeatAPI.HeatTransfer;
import mekanism.api.recipes.ingredients.ChemicalStackIngredient;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.api.recipes.ingredients.InputIngredient;
import mekanism.common.capabilities.heat.BasicHeatCapacitor;
import mekanism.common.capabilities.heat.CachedAmbientTemperature;
import mekanism.common.capabilities.holder.chemical.ChemicalTankHelper;
import mekanism.common.capabilities.holder.chemical.IChemicalTankHolder;
import mekanism.common.capabilities.holder.fluid.FluidTankHelper;
import mekanism.common.capabilities.holder.fluid.IFluidTankHolder;
import mekanism.common.capabilities.holder.heat.HeatCapacitorHelper;
import mekanism.common.capabilities.holder.heat.IHeatCapacitorHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.component.config.slot.InventorySlotInfo;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import mekanism.common.util.EnumUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A machine that automatically runs its {@link ReactionChamberRecipe}s on a fixed cadence.
 *
 * <p>The chamber's contents are a single item input slot, a single item output slot and a {@link MultiFluidChemicalTank} that
 * mixes fluids and chemicals in one shared pool. Every {@code reactionInterval} ticks (and immediately whenever the contents
 * change) it executes its recipes: each recipe whose temperature window contains the chamber's current heat-capacitor
 * temperature is applied once, and the list is re-scanned so that one recipe's output can feed another's input, until no
 * recipe that has not yet reacted this execution can react (or the per-execution operation cap is hit). A recipe that reacts
 * in an execution will not react again in that same execution; it only runs on a later one. Inputs are consumed from the
 * slots/tank, outputs are inserted into them, and anything that does not fit is silently discarded.
 */
public class TileEntityReactionChamber extends TileEntityConfigurableMachine {

    /** Capacity of the shared fluid/chemical pool, in milli-buckets. */
    public static final int MAX_CAPACITY = (int) Math.min(Integer.MAX_VALUE,
          (long) Config.ReactionChamber.CAPACITY.get() * FluidType.BUCKET_VOLUME);

    /** Update-tag key carrying the serialized {@link MultiFluidChemicalTank}, so clients can render the contents in-world. */
    private static final String CONTENTS_TAG = "reaction_contents";

    /**
     * Default per-face data type for each transmission type the chamber supports. Indexed to match the order of
     * {@link RelativeSide} ({@link EnumUtils#SIDES}): FRONT, LEFT, RIGHT, BACK, TOP, BOTTOM. Players can still override any
     * face via the side config. The mixed fluid/chemical pool is both an input and an output, so its faces are INPUT_OUTPUT.
     */
    private static final List<SideDefaults> SIDE_DEFAULTS = List.of(
          new SideDefaults(DataType.INPUT, DataType.INPUT_OUTPUT, DataType.INPUT_OUTPUT, DataType.INPUT), //FRONT
          new SideDefaults(DataType.INPUT, DataType.INPUT_OUTPUT, DataType.INPUT_OUTPUT, DataType.INPUT), //LEFT
          new SideDefaults(DataType.OUTPUT, DataType.INPUT_OUTPUT, DataType.INPUT_OUTPUT, DataType.INPUT), //RIGHT
          new SideDefaults(DataType.INPUT, DataType.INPUT_OUTPUT, DataType.INPUT_OUTPUT, DataType.INPUT), //BACK
          new SideDefaults(DataType.INPUT, DataType.INPUT_OUTPUT, DataType.INPUT_OUTPUT, DataType.INPUT), //TOP
          new SideDefaults(DataType.INPUT, DataType.INPUT_OUTPUT, DataType.INPUT_OUTPUT, DataType.INPUT) //BOTTOM
    );

    /** Default per-face {@link DataType} for the chamber's item, fluid, chemical and heat transmission. */
    private record SideDefaults(DataType item, DataType fluid, DataType chemical, DataType heat) {}

    /** Set whenever a slot or the tank changes; an execution is triggered while it is set. Cleared once nothing can react. */
    private boolean needsReaction;
    /** Counts ticks since the last periodic execution. */
    private int tickCount;
    /** Set when the shared pool's contents change; the next server tick forwards them to clients for the in-world render. */
    private boolean needsSync;

    private BasicHeatCapacitor heatCapacitor;
    private double lastEnvironmentLoss;
    private double lastTransferLoss;

    InputInventorySlot inputSlot;
    OutputInventorySlot outputSlot;
    public MultiFluidChemicalTank contentsTank;

    public TileEntityReactionChamber(BlockPos pos, BlockState state) {
        super(ModBlocks.REACTION_CHAMBER, pos, state);
        ConfigInfo itemConfig = configComponent.getConfig(TransmissionType.ITEM);
        if (itemConfig != null) {
            itemConfig.addSlotInfo(DataType.INPUT, new InventorySlotInfo(true, false, inputSlot));
            itemConfig.addSlotInfo(DataType.OUTPUT, new InventorySlotInfo(false, true, outputSlot));
            itemConfig.addSlotInfo(DataType.INPUT_OUTPUT, new InventorySlotInfo(true, true, inputSlot, outputSlot));
        }
        ConfigInfo fluidConfig = configComponent.getConfig(TransmissionType.FLUID);
        if (fluidConfig != null) {
            List<IExtendedFluidTank> fluidViews = new ArrayList<>(contentsTank.getFluidViews());
            fluidConfig.addSlotInfo(DataType.INPUT, TileComponentConfig.createInfo(TransmissionType.FLUID, true, false, fluidViews));
            fluidConfig.addSlotInfo(DataType.OUTPUT, TileComponentConfig.createInfo(TransmissionType.FLUID, false, true, fluidViews));
            fluidConfig.addSlotInfo(DataType.INPUT_OUTPUT, TileComponentConfig.createInfo(TransmissionType.FLUID, true, true, fluidViews));
            fluidConfig.setCanEject(false);
        }
        ConfigInfo chemicalConfig = configComponent.getConfig(TransmissionType.CHEMICAL);
        if (chemicalConfig != null) {
            List<IChemicalTank> chemicalViews = new ArrayList<>(contentsTank.getChemicalViews());
            chemicalConfig.addSlotInfo(DataType.INPUT, TileComponentConfig.createInfo(TransmissionType.CHEMICAL, true, false, chemicalViews));
            chemicalConfig.addSlotInfo(DataType.OUTPUT, TileComponentConfig.createInfo(TransmissionType.CHEMICAL, false, true, chemicalViews));
            chemicalConfig.addSlotInfo(DataType.INPUT_OUTPUT, TileComponentConfig.createInfo(TransmissionType.CHEMICAL, true, true, chemicalViews));
            chemicalConfig.setCanEject(false);
        }
        configComponent.setupInputConfig(TransmissionType.HEAT, heatCapacitor);
        applySideDefaults();

        ejectorComponent = new TileComponentEjector(this);
        //Only items auto-eject; the mixed pool is deliberately left in place so products can keep reacting
        ejectorComponent.setOutputData(configComponent, TransmissionType.ITEM);
    }

    @Override
    protected void presetVariables() {
        super.presetVariables();
        //The tank must exist before the base constructor asks for our holders. `this` is used as the content listener so that
        // any change to the pool (e.g. a pipe feeding it) triggers an immediate reaction attempt.
        contentsTank = MultiFluidChemicalTank.create(MAX_CAPACITY, this);
    }

    /**
     * Applies {@link #SIDE_DEFAULTS} to the side config, setting each face's data type for item, fluid, chemical and heat.
     */
    private void applySideDefaults() {
        ConfigInfo itemConfig = configComponent.getConfig(TransmissionType.ITEM);
        ConfigInfo fluidConfig = configComponent.getConfig(TransmissionType.FLUID);
        ConfigInfo chemicalConfig = configComponent.getConfig(TransmissionType.CHEMICAL);
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
            if (chemicalConfig != null) {
                chemicalConfig.setDataType(defaults.chemical(), side);
            }
            if (heatConfig != null) {
                heatConfig.setDataType(defaults.heat(), side);
            }
        }
    }

    @NotNull
    @Override
    public IChemicalTankHolder getInitialChemicalTanks(IContentsListener listener) {
        ChemicalTankHelper builder = ChemicalTankHelper.forSideWithConfig(this);
        for (IChemicalTank view : contentsTank.getChemicalViews()) {
            builder.addTank(view);
        }
        return builder.build();
    }

    @NotNull
    @Override
    protected IFluidTankHolder getInitialFluidTanks(IContentsListener listener) {
        FluidTankHelper builder = FluidTankHelper.forSideWithConfig(this);
        for (IExtendedFluidTank view : contentsTank.getFluidViews()) {
            builder.addTank(view);
        }
        return builder.build();
    }

    @NotNull
    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = InventorySlotHelper.forSideWithConfig(this);
        builder.addSlot(inputSlot = InputInventorySlot.at(this::isValidInputItem, listener, 26, 32));
        builder.addSlot(outputSlot = OutputInventorySlot.at(listener, 134, 32));
        return builder.build();
    }

    @NotNull
    @Override
    protected IHeatCapacitorHolder getInitialHeatCapacitors(IContentsListener listener, CachedAmbientTemperature ambientTemperature) {
        HeatCapacitorHelper builder = HeatCapacitorHelper.forSideWithConfig(this);
        builder.addCapacitor(heatCapacitor = BasicHeatCapacitor.create(Config.ReactionChamber.HEAT_CAPACITY.get(),
              Config.ReactionChamber.INVERSE_CONDUCTION_COEFFICIENT.get(), Config.ReactionChamber.INVERSE_INSULATION_COEFFICIENT.get(),
              ambientTemperature, listener));
        return builder.build();
    }

    /**
     * Accepts any item that is the input ingredient of at least one (complete) reaction recipe, regardless of temperature or
     * of whether the other required inputs are present yet, so items can be loaded in advance of heating up.
     */
    private boolean isValidInputItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Level level = getLevel();
        if (level == null) {
            return false;
        }
        for (RecipeHolder<ReactionChamberRecipe> holder : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.TYPE_REACTION.value())) {
            ReactionChamberRecipe recipe = holder.value();
            if (!recipe.isIncomplete() && recipe.hasItemInput() && recipe.getItemInput().orElseThrow().testType(stack)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean onUpdateServer() {
        boolean sendUpdatePacket = super.onUpdateServer();
        HeatTransfer transfer = simulate();
        lastEnvironmentLoss = transfer.environmentTransfer();
        lastTransferLoss = transfer.adjacentTransfer();
        //React on the periodic interval, and immediately whenever the contents changed since the last tick.
        if (needsReaction || ++tickCount >= Config.ReactionChamber.REACTION_INTERVAL.get()) {
            tickCount = 0;
            setActive(runReactions());
        } else if (getActive()) {
            setActive(false);
        }
        //Batch content changes into a single update packet per tick for the in-world render
        if (needsSync) {
            needsSync = false;
            sendUpdatePacket();
        }
        return sendUpdatePacket;
    }

    /**
     * Executes the chamber's recipes, applying each eligible recipe at most once (or until the per-execution operation cap is
     * hit).
     *
     * <p>Each pass walks every recipe in order and applies a single operation to each recipe that has not yet reacted this
     * execution, is complete, and whose temperature window contains the current temperature. A pass that applied at least one
     * operation is followed by another, so one recipe's output can feed another's input even when the consuming recipe comes
     * first in the list; the scan stops once a full pass reacts nothing new. A recipe that reacts is recorded and skipped for
     * the rest of this execution, so it only runs again on a later (interval- or content-triggered) execution. Successful
     * operations change the contents (see {@link #onContentsChanged()}), which keeps {@link #needsReaction} set during the
     * loop; it is cleared once we stop so the machine idles until the next content change or interval.
     *
     * @return {@code true} if at least one operation was applied.
     */
    private boolean runReactions() {
        Level level = getLevel();
        if (level == null || level.isClientSide || !canFunction()) {
            needsReaction = false;
            return false;
        }
        if (inputSlot.isEmpty() && contentsTank.isEmpty()) {
            needsReaction = false;
            return false;
        }
        List<ReactionChamberRecipe> recipes = new ArrayList<>();
        for (RecipeHolder<ReactionChamberRecipe> holder : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.TYPE_REACTION.value())) {
            recipes.add(holder.value());
        }
        if (recipes.isEmpty()) {
            needsReaction = false;
            return false;
        }
        int maxOperations = Config.ReactionChamber.MAX_OPERATIONS.get();
        int operations = 0;
        boolean processed = false;
        //A recipe that reacts is skipped for the rest of this execution so it only runs again on a later one. Tracked by
        // identity, since value-equal but distinct recipes must each be allowed to react.
        Set<ReactionChamberRecipe> reacted = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean progressed;
        do {
            progressed = false;
            for (ReactionChamberRecipe recipe : recipes) {
                if (reacted.contains(recipe)) {
                    continue;
                }
                if (recipe.isIncomplete()) {
                    continue;
                }
                if (!recipe.temperatureAllows(heatCapacitor.getTemperature())) {
                    continue;
                }
                if (applyRecipe(recipe)) {
                    reacted.add(recipe);
                    progressed = true;
                    processed = true;
                    if (++operations >= maxOperations) {
                        //Safety: let the next periodic execution pick up where we left off.
                        needsReaction = false;
                        return processed;
                    }
                }
            }
        } while (progressed);
        needsReaction = false;
        return processed;
    }

    /**
     * Applies a single operation of the given recipe against the chamber's current contents: consumes the required item, fluid
     * and chemical inputs, then inserts the produced item, fluids and chemicals, silently discarding anything that does not
     * fit.
     *
     * @return {@code true} if the operation changed the chamber's contents (a reaction that produces exactly what it consumed
     *         is treated as not having made progress so the scan can terminate).
     */
    private boolean applyRecipe(ReactionChamberRecipe recipe) {
        List<FluidStack> fluids = contentsTank.getFluids();
        List<ChemicalStack> chemicals = contentsTank.getChemicals();
        ItemStack inputItem = inputSlot.getStack();
        ReactionChamberRecipeInput input = new ReactionChamberRecipeInput(inputItem, fluids, chemicals);
        if (!recipe.test(input)) {
            return false;
        }
        ContentState before = captureContent(inputItem, outputSlot.getStack(), fluids, chemicals);

        int itemUse = 0;
        if (recipe.hasItemInput()) {
            itemUse = (int) recipe.getItemInput().orElseThrow().getNeededAmount(inputItem);
            if (itemUse < 1) {
                return false;
            }
        }
        int[] fluidAssignment = matchRequirements(recipe.getFluidInputs(), fluids);
        if (fluidAssignment == null) {
            return false;
        }
        int[] chemicalAssignment = matchRequirements(recipe.getChemicalInputs(), chemicals);
        if (chemicalAssignment == null) {
            return false;
        }

        if (itemUse > 0) {
            inputSlot.shrinkStack(itemUse, Action.EXECUTE);
        }
        List<FluidStackIngredient> fluidInputs = recipe.getFluidInputs();
        for (int i = 0; i < fluidInputs.size(); i++) {
            FluidStack matched = fluids.get(fluidAssignment[i]);
            int amount = (int) fluidInputs.get(i).getNeededAmount(matched);
            contentsTank.extract(matched.copy(), amount, Action.EXECUTE, AutomationType.INTERNAL);
        }
        List<ChemicalStackIngredient> chemicalInputs = recipe.getChemicalInputs();
        for (int i = 0; i < chemicalInputs.size(); i++) {
            ChemicalStack matched = chemicals.get(chemicalAssignment[i]);
            long amount = chemicalInputs.get(i).getNeededAmount(matched);
            contentsTank.extract(matched.copy(), amount, Action.EXECUTE, AutomationType.INTERNAL);
        }

        recipe.getItemOutput().ifPresent(out -> {
            if (!out.isEmpty()) {
                outputSlot.insertItem(out, Action.EXECUTE, AutomationType.INTERNAL);
            }
        });
        for (FluidStack out : recipe.getFluidOutputs()) {
            contentsTank.insert(out, Action.EXECUTE, AutomationType.INTERNAL);
        }
        for (ChemicalStack out : recipe.getChemicalOutputs()) {
            contentsTank.insert(out, Action.EXECUTE, AutomationType.INTERNAL);
        }

        ContentState after = captureContent(inputSlot.getStack(), outputSlot.getStack(), contentsTank.getFluids(), contentsTank.getChemicals());
        return !before.sameAs(after);
    }

    /**
     * Assigns each required ingredient to a distinct available stack that satisfies it, using the same bipartite matching the
     * recipes use (Kuhn's algorithm), so consumption matches {@link ReactionChamberRecipe#test(ReactionChamberRecipeInput)}.
     *
     * @return For each required ingredient, the index into {@code available} it is satisfied by, or {@code null} if the
     *         requirements cannot all be satisfied.
     */
    private static <T> int[] matchRequirements(List<? extends InputIngredient<T>> required, List<T> available) {
        int requiredCount = required.size();
        if (requiredCount == 0) {
            return new int[0];
        }
        int availableCount = available.size();
        if (availableCount < requiredCount) {
            return null;
        }
        int[] requiredToAvailable = new int[requiredCount];
        Arrays.fill(requiredToAvailable, -1);
        int[] availableToRequired = new int[availableCount];
        Arrays.fill(availableToRequired, -1);
        for (int r = 0; r < requiredCount; r++) {
            boolean[] seen = new boolean[availableCount];
            if (!augment(r, required, available, requiredToAvailable, availableToRequired, seen)) {
                return null;
            }
        }
        return requiredToAvailable;
    }

    private static <T> boolean augment(int requirement, List<? extends InputIngredient<T>> required, List<T> available, int[] requiredToAvailable,
          int[] availableToRequired, boolean[] seen) {
        InputIngredient<T> ingredient = required.get(requirement);
        for (int a = 0; a < available.size(); a++) {
            if (seen[a] || !ingredient.test(available.get(a))) {
                continue;
            }
            seen[a] = true;
            if (availableToRequired[a] == -1 || augment(availableToRequired[a], required, available, requiredToAvailable, availableToRequired, seen)) {
                requiredToAvailable[requirement] = a;
                availableToRequired[a] = requirement;
                return true;
            }
        }
        return false;
    }

    /**
     * A snapshot of the chamber's full contents, used to detect whether an operation actually changed anything.
     */
    private record ContentState(ItemStack input, ItemStack output, List<FluidStack> fluids, List<ChemicalStack> chemicals) {

        boolean sameAs(ContentState other) {
            return stacksEqual(input, other.input) && stacksEqual(output, other.output)
                  && fluidListsEqual(fluids, other.fluids) && chemicalListsEqual(chemicals, other.chemicals);
        }

        private static boolean stacksEqual(ItemStack a, ItemStack b) {
            return a.getCount() == b.getCount() && ItemStack.isSameItemSameComponents(a, b);
        }

        private static boolean fluidListsEqual(List<FluidStack> a, List<FluidStack> b) {
            if (a.size() != b.size()) {
                return false;
            }
            for (int i = 0; i < a.size(); i++) {
                FluidStack first = a.get(i);
                FluidStack second = b.get(i);
                if (first.getAmount() != second.getAmount() || !FluidStack.isSameFluidSameComponents(first, second)) {
                    return false;
                }
            }
            return true;
        }

        private static boolean chemicalListsEqual(List<ChemicalStack> a, List<ChemicalStack> b) {
            if (a.size() != b.size()) {
                return false;
            }
            for (int i = 0; i < a.size(); i++) {
                ChemicalStack first = a.get(i);
                ChemicalStack second = b.get(i);
                if (first.getAmount() != second.getAmount() || !ChemicalStack.isSameChemical(first, second)) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Captures a deep copy of the given item slots and tank lists so it is not mutated by subsequent operations.
     */
    private ContentState captureContent(ItemStack input, ItemStack output, List<FluidStack> fluids, List<ChemicalStack> chemicals) {
        List<FluidStack> fluidCopies = new ArrayList<>(fluids.size());
        for (FluidStack fluid : fluids) {
            fluidCopies.add(fluid.copy());
        }
        List<ChemicalStack> chemicalCopies = new ArrayList<>(chemicals.size());
        for (ChemicalStack chemical : chemicals) {
            chemicalCopies.add(chemical.copy());
        }
        return new ContentState(input.copy(), output.copy(), fluidCopies, chemicalCopies);
    }

    @Override
    public void onContentsChanged() {
        super.onContentsChanged();
        //Note: State updates only matter on the server; the client simply updates its copy of the contents.
        if (level != null && !level.isClientSide) {
            needsReaction = true;
            //The pool's contents are rendered through the glass in-world, so changes need to reach clients even when no
            // player currently has the GUI open. Batching into a single packet per tick avoids flooding while a batch of
            // reactions mutates the pool several times in one tick.
            needsSync = true;
        }
    }

    @NotNull
    @Override
    public CompoundTag getReducedUpdateTag(@NotNull HolderLookup.Provider provider) {
        CompoundTag updateTag = super.getReducedUpdateTag(provider);
        updateTag.put(CONTENTS_TAG, contentsTank.serializeNBT(provider));
        return updateTag;
    }

    @Override
    public void handleUpdateTag(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        super.handleUpdateTag(tag, provider);
        if (tag.contains(CONTENTS_TAG, Tag.TAG_COMPOUND)) {
            contentsTank.deserializeNBT(provider, tag.getCompound(CONTENTS_TAG));
        }
    }

    public BasicHeatCapacitor getHeatCapacitor() {
        return heatCapacitor;
    }

    public double getTemperature() {
        return heatCapacitor.getTemperature();
    }

    public double getLastTransferLoss() {
        return lastTransferLoss;
    }

    public double getLastEnvironmentLoss() {
        return lastEnvironmentLoss;
    }
}
