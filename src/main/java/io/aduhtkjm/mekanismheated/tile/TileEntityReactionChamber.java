package io.aduhtkjm.mekanismheated.tile;

import io.aduhtkjm.mekanismheated.Config;
import io.aduhtkjm.mekanismheated.recipe.ModRecipeTypes;
import io.aduhtkjm.mekanismheated.recipe.ReactionChamberRecipe;
import io.aduhtkjm.mekanismheated.recipe.ReactionChamberRecipeInput;
import io.aduhtkjm.mekanismheated.registries.ModBlocks;
import io.aduhtkjm.mekanismheated.tank.MultiFluidChemicalTank;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A machine that automatically runs its {@link ReactionChamberRecipe}s, each on its own schedule.
 *
 * <p>The chamber's contents are a single item input slot, a single item output slot and a {@link MultiFluidChemicalTank} that
 * mixes fluids and chemicals in one shared pool. A recipe reacts as soon as its inputs are available and its temperature window
 * contains the chamber's current heat-capacitor temperature: it consumes its inputs and inserts its outputs in a single tick,
 * and then goes on cooldown for its {@linkplain ReactionChamberRecipe#getDuration() duration}, during which it cannot react
 * again. Recipes therefore run at independent rates rather than the whole chamber working on one fixed interval. Inputs are
 * consumed from the slots/tank, outputs are inserted into them, and anything that does not fit is silently discarded.
 * Cooldowns are not saved, so a chamber that gets reloaded starts with every recipe ready to react.
 *
 * <p>To keep idle ticks cheap the chamber only walks the recipes that matter to it: the ones its current contents can
 * satisfy, plus the ones that are still cooling down. That watched list is rebuilt only when it can have changed - when the
 * contents change, or when a reload replaces the recipes - so an idle tick just decrements a few cooldowns, and a chamber with
 * nothing in it does nothing at all (which also freezes its cooldowns until there is something to work with again). A recipe
 * that is ready but cannot react with the current contents (the temperature is out of its window, or another recipe took the
 * shared inputs first) is left waiting instead of being re-tested every tick. A recipe that is no longer watched at all is
 * simply absent from the list, and only gets looked at again by a rebuild.
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

    /** Set whenever a slot or the tank changes; the watched recipes are rebuilt on the next tick while it is set. */
    private boolean needsReaction;
    /** Set when the shared pool's contents change; the next server tick forwards them to clients for the in-world render. */
    private boolean needsSync;

    /**
     * The recipes the chamber is watching: those its current contents can satisfy, plus any that are still cooling down. Kept
     * short and rebuilt only when it can have changed (see {@link #rebuildPendingReactions(RecipeManager)}), so that an idle
     * tick only has to walk it instead of the whole recipe list.
     */
    private final List<PendingReaction> pendingReactions = new ArrayList<>();

    /**
     * The recipe manager {@link #pendingReactions} was built from, or {@code null} before the first build. A different
     * instance means a data or tag reload replaced the recipes, so the watched entries - which hold recipe instances - have to
     * be rebuilt around the new ones.
     */
    @Nullable
    private RecipeManager trackedRecipeManager;

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
        setActive(tickReactions());
        //Batch content changes into a single update packet per tick for the in-world render
        if (needsSync) {
            needsSync = false;
            sendUpdatePacket();
        }
        return sendUpdatePacket;
    }

    /**
     * Advances every recipe the chamber is watching by one tick: recipes that are cooling down move one tick closer to being
     * ready, and every recipe that is ready reacts if the chamber's temperature is within its window.
     *
     * <p>The watched list is rebuilt first whenever the contents changed on the previous tick or a reload replaced the
     * recipes; that rebuild is also what makes a recipe which was waiting on its inputs eligible again.
     *
     * @return {@code true} if the chamber should render as active, i.e. it reacted or still has a cooldown running.
     */
    private boolean tickReactions() {
        Level level = getLevel();
        if (level == null || level.isClientSide || !canFunction()) {
            //No world to react in, and a redstone-disabled chamber freezes instead of running its cooldowns down
            return false;
        }
        if (inputSlot.isEmpty() && contentsTank.isEmpty()) {
            //Nothing to react with: no recipe can match, so skip the whole update. Cooldowns resume where they left off when
            //contents arrive again, which also rebuilds the watched list and drops whatever no longer matches.
            return false;
        }
        RecipeManager recipeManager = level.getRecipeManager();
        if (needsReaction || recipeManager != trackedRecipeManager) {
            rebuildPendingReactions(recipeManager);
            needsReaction = false;
        }
        boolean processed = false;
        boolean coolingDown = false;
        boolean changed = false;
        for (PendingReaction reaction : pendingReactions) {
            //A recipe whose cooldown runs out on this tick is ready to react on this same tick
            if (reaction.cooldown > 0 && --reaction.cooldown > 0) {
                coolingDown = true;
                continue;
            }
            if (reaction.waiting) {
                //Ready, but the current contents cannot satisfy this recipe and they have not changed since we last checked
                continue;
            }
            ReactionChamberRecipe recipe = reaction.recipe.value();
            if (!recipe.temperatureAllows(heatCapacitor.getTemperature())) {
                //Unlike the inputs, the temperature drifts whether or not the contents change, so it is re-checked every tick
                //and the recipe reacts as soon as the chamber is hot (or cold) enough
                continue;
            }
            if (applyRecipe(recipe)) {
                reaction.cooldown = recipe.getDuration();
                processed = true;
                coolingDown = true;
                changed = true;
            } else {
                //Either another recipe took the shared input first on this tick, or this recipe produces exactly what it
                //consumed. Wait for the contents to change again rather than re-testing it every tick.
                reaction.waiting = true;
            }
        }
        //Reacting changed the contents, so the next tick has to rebuild the watched list to pick up the recipes those new
        //contents unlock. Note that this deliberately overwrites the flag our own operations set: a recipe that consumed its
        //inputs without changing anything net must not count as a content change, or it would react again on every tick.
        needsReaction = changed;
        return processed || coolingDown;
    }

    /**
     * Rebuilds {@link #pendingReactions} for the given recipe manager and the chamber's current contents.
     *
     * <p>The rebuilt list holds every complete recipe whose inputs the current contents satisfy, plus every recipe that was
     * already watched and is still cooling down. Keeping the latter even when its inputs are gone means a recipe which briefly
     * loses them to another recipe cannot react again early: its cooldown always runs to the end, so a recipe never runs more
     * often than once per its duration. Every other recipe is left out of the list - one that cannot react and has finished
     * cooling down simply is not watched again until the contents change.
     */
    private void rebuildPendingReactions(RecipeManager recipeManager) {
        List<RecipeHolder<ReactionChamberRecipe>> recipes = new ArrayList<>(
              recipeManager.getAllRecipesFor(ModRecipeTypes.TYPE_REACTION.value()));
        //Recipes compete for the shared pool, so this order decides who gets a limited input first. Sorting by id makes that
        //independent of the order in which the recipe manager happens to have loaded the recipes.
        recipes.sort(Comparator.comparing(RecipeHolder<ReactionChamberRecipe>::id));
        Map<ResourceLocation, PendingReaction> tracked = new HashMap<>(pendingReactions.size());
        for (PendingReaction reaction : pendingReactions) {
            tracked.put(reaction.recipe.id(), reaction);
        }
        pendingReactions.clear();
        ReactionChamberRecipeInput input = createInput();
        for (RecipeHolder<ReactionChamberRecipe> holder : recipes) {
            ReactionChamberRecipe recipe = holder.value();
            if (recipe.isIncomplete()) {
                continue;
            }
            PendingReaction previous = tracked.get(holder.id());
            if (previous != null && previous.cooldown > 0) {
                //Rebind to the current holder, as a reload may have replaced the instances the watched entries point at, and
                //shorten the cooldown if the recipe's duration shrank with it. Re-testing the recipe here is what wakes a
                //waiting entry back up.
                pendingReactions.add(new PendingReaction(holder, Math.min(previous.cooldown, recipe.getDuration()),
                      !recipe.test(input)));
            } else if (recipe.test(input)) {
                pendingReactions.add(new PendingReaction(holder, 0, false));
            }
        }
        trackedRecipeManager = recipeManager;
    }

    /**
     * @return The chamber's current contents, gathered into the input the recipes are matched against.
     */
    private ReactionChamberRecipeInput createInput() {
        return new ReactionChamberRecipeInput(inputSlot.getStack(), contentsTank.getFluids(), contentsTank.getChemicals());
    }

    /**
     * Applies a single operation of the given recipe against the chamber's current contents: consumes the required item, fluid
     * and chemical inputs, then inserts the produced item, fluids and chemicals, silently discarding anything that does not
     * fit.
     *
     * @return {@code true} if the operation changed the chamber's contents (a reaction that produces exactly what it consumed
     *         is treated as not having made progress, so the chamber leaves it waiting until the contents change).
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
     * A recipe the chamber is watching, together with how long it still has to wait before it may react again.
     */
    private static final class PendingReaction {

        private final RecipeHolder<ReactionChamberRecipe> recipe;
        /** Ticks left before the recipe may react; zero when it is ready to react now. */
        private int cooldown;
        /**
         * Set while the recipe is ready but cannot react with the current contents, so that it is not re-tested every tick.
         * A rebuild, which happens whenever the contents change, re-checks it and clears this again.
         */
        private boolean waiting;

        private PendingReaction(RecipeHolder<ReactionChamberRecipe> recipe, int cooldown, boolean waiting) {
            this.recipe = recipe;
            this.cooldown = cooldown;
            this.waiting = waiting;
        }
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
