package io.aduhtkjm.mekanismheated.tile.multiblock;

import io.aduhtkjm.mekanismheated.Config;
import io.aduhtkjm.mekanismheated.content.upgrade.HeatedUpgrades;
import io.aduhtkjm.mekanismheated.content.upgrade.IHeatedUpgradeMultiblockData;
import io.aduhtkjm.mekanismheated.recipe.BasicFractionationRecipe;
import io.aduhtkjm.mekanismheated.recipe.FractionationRecipe;
import io.aduhtkjm.mekanismheated.recipe.FractionationRecipe.BankOutput;
import io.aduhtkjm.mekanismheated.recipe.ModRecipeTypes;
import io.aduhtkjm.mekanismheated.recipe.PassiveFractionationRecipe;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.SerializationConstants;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.functions.ConstantPredicates;
import mekanism.api.heat.HeatAPI;
import mekanism.api.recipes.vanilla_input.SingleFluidRecipeInput;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.capabilities.fluid.VariableCapacityFluidTank;
import mekanism.common.capabilities.heat.VariableHeatCapacitor;
import mekanism.common.inventory.container.sync.dynamic.ContainerSync;
import mekanism.common.lib.multiblock.MultiblockData;
import mekanism.common.lib.multiblock.Structure;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.NBTUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Multiblock data for a Thermal Fractionation Tower.
 *
 * <p>The interior is divided vertically by distillation tray layers: the space below the lowest tray forms the shared
 * feed sump ({@link #inputTank}), and every compartment directly above a tray forms one output bank. Banks are indexed
 * from the bottom of the tower.</p>
 */
public class FractionationMultiblockData extends MultiblockData implements IHeatedUpgradeMultiblockData {

    /**
     * Display cap for the GUI temperature bar, in Kelvin.
     */
    public static final double MAX_DISPLAY_TEMPERATURE = 3_000;

    @ContainerSync
    public BasicFluidTank inputTank;
    @ContainerSync
    public VariableHeatCapacitor heatCapacitor;
    @ContainerSync
    public double lastEnvironmentLoss;

    /** Output banks, ordered bottom to top. */
    private final List<IExtendedFluidTank> banks = new ArrayList<>();
    private final IntList bankCapacities = new IntArrayList();

    private int sumpCapacity;
    /** Layout geometry: y-range of the tower and the sorted y-levels of full distillation tray layers. */
    private int boundsMinY;
    private int boundsMaxY;
    private int[] trayLayers = new int[0];
    private double biomeAmbientTemp;
    private double progress;
    private boolean processing;

    // Scale trackers used to throttle update packets, mirroring the thermal evaporation plant
    public float prevInputScale;
    public float[] prevBankScales = new float[0];

    /**
     * Adjacent heat exchange with the blocks touching the tower's shell. The neighbour list is rebuilt lazily whenever
     * the structure changes.
     */
    private final MultiblockHeatTransfer heatTransfer = new MultiblockHeatTransfer(this);

    public FractionationMultiblockData(BlockEntity tile) {
        super(tile);
        //Fall back to the ambient temperature at the controller position; recalculated for the whole structure
        //in {@link #onCreated} (including any per-chunk ambient temperature delta).
        biomeAmbientTemp = HeatAPI.getAmbientTemp(tile.getLevel(), tile.getBlockPos());
        inputTank = VariableCapacityFluidTank.input(this, this::getSumpCapacity, ConstantPredicates.alwaysTrue(), createSaveAndComparator(this));
        //Note: The capacitor must also be registered in the heat capacitors list, otherwise the multiblock exposes
        // no heat handler at all and neither the valves nor internal conduction see any temperature integration
        heatCapacitors.add(heatCapacitor = VariableHeatCapacitor.create(Config.Fractionation.HEAT_CAPACITY_PER_HEIGHT.get() * 3, () -> biomeAmbientTemp, this));
        fluidTanks.add(inputTank);
    }

    /**
     * (Re)builds the layout: tower y-range, tray layer positions, feed sump capacity and the output banks. Called
     * server-side during formation validation and client-side when reading the update tag.
     *
     * @param trayLayersIn Sorted (ascending) y-levels of full distillation tray layers.
     */
    public void configureBanks(int boundsMinYIn, int boundsMaxYIn, int[] trayLayersIn, int sumpCapacityIn, int[] bankCapacitiesIn) {
        this.boundsMinY = boundsMinYIn;
        this.boundsMaxY = boundsMaxYIn;
        this.trayLayers = trayLayersIn.clone();
        this.sumpCapacity = sumpCapacityIn;
        this.bankCapacities.clear();
        this.banks.clear();
        this.bankCapacities.addElements(0, bankCapacitiesIn);
        this.prevBankScales = new float[bankCapacitiesIn.length];
        //Re-add the sump first so that the tank indices stay stable for the multiblock cache
        fluidTanks.clear();
        fluidTanks.add(inputTank);
        for (int i = 0; i < bankCapacitiesIn.length; i++) {
            int index = i;
            banks.add(VariableCapacityFluidTank.output(() -> bankCapacities.getInt(index), ConstantPredicates.alwaysTrue(), this));
            fluidTanks.add(banks.get(index));
        }
    }

    public int getSumpCapacity() {
        return sumpCapacity;
    }

    /**
     * Resolves which tank a valve at the given y-level interfaces with, following the physical layering of the tower:
     * levels below the lowest tray belong to the feed sump, each level above a tray belongs to that tray's output bank,
     * and levels occupied by distillation trays themselves expose no tank at all. Valves in the bottommost (floor) row
     * count as sump level, and the top row counts as part of the highest bank.
     *
     * @return the tank for that level, or {@code null} if the level is outside the tower or on a tray layer.
     */
    @Nullable
    public IExtendedFluidTank getTankForLevel(int y) {
        if (!isFormed() || y < boundsMinY || y > boundsMaxY) {
            return null;
        }
        if (trayLayers.length == 0) {
            //No trays: the entire interior is a single feed sump
            return inputTank;
        }
        for (int i = 0; i < trayLayers.length; i++) {
            if (y == trayLayers[i]) {
                //A valve embedded in a tray layer has no fluid access
                return null;
            }
            if (y < trayLayers[i]) {
                //Below this tray: either the sump or the bank of the previous tray
                return i == 0 ? inputTank : banks.get(i - 1);
            }
        }
        //Above the highest tray: its output bank
        return banks.get(trayLayers.length - 1);
    }

    /** @return unmodifiable view of the output banks, ordered bottom to top. */
    @NotNull
    public List<IExtendedFluidTank> getOutputBanks() {
        return Collections.unmodifiableList(banks);
    }

    public int getBankCount() {
        return banks.size();
    }

    @Override
    public void onCreated(Level world) {
        super.onCreated(world);
        biomeAmbientTemp = calculateAverageAmbientTemperature(world);
        double baseHeatCapacity = Config.Fractionation.HEAT_CAPACITY_PER_HEIGHT.get() * height();
        heatCapacitor.setHeatCapacity(baseHeatCapacity, true);
        if (!isRemote()) {
            //The capacity was just derived from the tower's height, so re-apply the heat upgrades on top of it. The
            //client is skipped: its capacity arrives through the update tag and the container sync instead.
            HeatedUpgrades.applyTo(heatCapacitor, baseHeatCapacity, HeatedUpgrades.multipliersFor(this));
        }
        heatTransfer.invalidate();
    }

    @Override
    public void remove(Level world, Structure oldStructure) {
        super.remove(world, oldStructure);
        //Drop the cached neighbour capabilities so a torn down tower does not keep them alive
        heatTransfer.invalidate();
    }

    @Override
    public boolean tick(Level world) {
        boolean needsPacket = super.tick(world);
        // external heat dissipation
        lastEnvironmentLoss = simulateEnvironment();
        // adjacent heat conduction to neighboring blocks
        simulateAdjacent();
        // update temperature
        updateHeatCapacitors(null);
        needsPacket |= processRecipes(world);
        needsPacket |= updateScales();
        return needsPacket;
    }

    @Override
    public double simulateEnvironment() {
        double currentTemperature = getTemperature();
        double heatCapacity = heatCapacitor.getHeatCapacity();
        if (Math.abs(currentTemperature - biomeAmbientTemp) < 0.001) {
            heatCapacitor.handleHeat(biomeAmbientTemp * heatCapacity - heatCapacitor.getHeat());
        } else {
            double incr = Config.Fractionation.HEAT_DISSIPATION.get() * Math.sqrt(Math.abs(currentTemperature - biomeAmbientTemp));
            if (currentTemperature > biomeAmbientTemp) {
                incr = -incr;
            }
            heatCapacitor.handleHeat(heatCapacity * incr);
            if (incr < 0) {
                return -incr;
            }
        }
        return 0;
    }

    /**
     * Transfers heat to all adjacent blocks that expose an {@code IHeatHandler}. Only blocks adjacent to heat-exposing
     * tiles (valves) are considered, and only cooler ones: hotter neighbours initiate their own transfer when they
     * simulate.
     */
    @Override
    public double simulateAdjacent() {
        return heatTransfer.simulateAdjacent();
    }

    public double getTemperature() {
        return heatCapacitor.getTemperature();
    }

    public boolean isProcessing() {
        return processing;
    }

    /**
     * Runs fractionation recipes.
     *
     * <p>While the feed sump holds fluid the matching input-based recipe is used and that fluid is consumed; while the sump
     * is empty, passive generation takes over and a {@link PassiveFractionationRecipe} produces its banked outputs from the
     * environment, consuming nothing. Both obey the same temperature window and speed-scaling rules.</p>
     *
     * @return {@code true} if the processing state changed and an update packet should be sent.
     */
    private boolean processRecipes(Level level) {
        boolean wasProcessing = processing;
        FluidStack current = inputTank.getFluid();
        FractionationRecipe recipe;
        if (current.isEmpty()) {
            // Passive generation only runs on an empty sump. Passive recipes take no input, but getRecipeFor short-circuits
            // empty inputs (returning nothing), so fetch the type's recipes directly and use the first complete one.
            recipe = level.getRecipeManager()
                  .getAllRecipesFor(ModRecipeTypes.TYPE_FRACTIONATING_PASSIVE.value())
                  .stream()
                  .filter(holder -> !holder.value().isIncomplete())
                  .map(RecipeHolder::value)
                  .findFirst()
                  .orElse(null);
        } else {
            recipe = level.getRecipeManager()
                  .getRecipeFor(ModRecipeTypes.TYPE_FRACTIONATING.value(), new SingleFluidRecipeInput(current), level)
                  .map(RecipeHolder::value)
                  .orElse(null);
        }
        if (recipe == null || recipe.getMinTemperature() > getTemperature() || recipe.getMaxTemperature() < getTemperature()) {
            return wasProcessing != (processing = false);
        }
        // Speed scales linearly from zero ops at min temperature up to nominal speed at base temperature.
        progress += processingRate(recipe);
        int operations = (int) progress;
        if (operations <= 0) {
            processing = false;
            return wasProcessing;
        }
        int performed = 0;
        while (performed < operations && operate(recipe)) {
            performed++;
        }
        progress -= performed;
        processing = performed > 0;
        return wasProcessing != processing;
    }

    /**
     * Linear processing rate in operations per tick: zero at the minimum temperature, nominal at the base temperature, and
     * zero again above the maximum temperature.
     */
    private double processingRate(FractionationRecipe recipe) {
        double span = recipe.getBaseTemperature() - recipe.getMinTemperature();
        double temperature = getTemperature();
        return span <= 0 ? 1 : temperature > recipe.getMaxTemperature() ? 0 : Math.clamp((temperature - recipe.getMinTemperature()) / span, 0, 1);
    }

    /**
     * Dispatches a single operation to the concrete recipe: passive recipes only fill the output banks, while input-based
     * recipes also consume their matching input fluid from the sump.
     *
     * @return {@code true} if the operation was performed.
     */
    private boolean operate(FractionationRecipe recipe) {
        if (recipe instanceof PassiveFractionationRecipe passive)
            return performPassiveOperation(passive);

        if (recipe instanceof BasicFractionationRecipe input)
            return performOperation(input);

        return false;
    }

    /**
     * Attempts one operation of an input-based recipe: consumes the matching sump fluid and deposits the banked outputs.
     *
     * @return {@code true} if the operation was performed.
     */
    private boolean performOperation(BasicFractionationRecipe recipe) {
        FluidStack current = inputTank.getFluid();
        FluidStack required = recipe.getInput().getMatchingInstance(current);
        if (required.isEmpty() || required.getAmount() > inputTank.getFluidAmount())
            return false;

        if (!canDeposit(recipe.getOutputs()))
            return false;

        inputTank.extract(required.getAmount(), Action.EXECUTE, AutomationType.INTERNAL);
        deposit(recipe.getOutputs());
        return true;
    }

    /**
     * Attempts one operation of a passive recipe: deposits the banked outputs, consuming nothing from the sump.
     *
     * @return {@code true} if the operation was performed.
     */
    private boolean performPassiveOperation(PassiveFractionationRecipe recipe) {
        if (!canDeposit(recipe.getOutputs()))
            return false;

        deposit(recipe.getOutputs());
        return true;
    }

    /**
     * Simulates depositing every output first, so an operation never advances (or consumes input) unless the full set of
     * outputs can be produced. <p>
     *
     * However, if the tower does not have enough output tanks, this is allowed. All extra output will be discarded.
     */
    private boolean canDeposit(List<BankOutput> outputs) {
        for (BankOutput output : outputs) {
            if (output.bank() >= banks.size())
                return true;

            if (!banks.get(output.bank()).insert(output.stack().copy(), Action.SIMULATE, AutomationType.INTERNAL).isEmpty())
                return false;
        }
        return true;
    }

    /**
     * Commits the banked outputs. Only call after {@link #canDeposit(List)} has returned {@code true}.
     */
    private void deposit(List<BankOutput> outputs) {
        for (BankOutput output : outputs) {
            if (output.bank() >= banks.size())
                return;

            banks.get(output.bank()).insert(output.stack().copy(), Action.EXECUTE, AutomationType.INTERNAL);
        }
    }

    private boolean updateScales() {
        boolean changed = false;
        float inputScale = MekanismUtils.getScale(prevInputScale, inputTank);
        if (!Mth.equal(inputScale, prevInputScale)) {
            changed = true;
            prevInputScale = inputScale;
        }
        int bankCount = banks.size();
        if (prevBankScales.length != bankCount) {
            prevBankScales = new float[bankCount];
            changed = true;
        }
        for (int i = 0; i < bankCount; i++) {
            float scale = MekanismUtils.getScale(prevBankScales[i], banks.get(i));
            if (!Mth.equal(scale, prevBankScales[i])) {
                changed = true;
                prevBankScales[i] = scale;
            }
        }
        return changed;
    }

    @Override
    public void readUpdateTag(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        super.readUpdateTag(tag, provider);
        NBTUtils.setFluidStackIfPresent(provider, tag, SerializationConstants.FLUID, fluid -> inputTank.setStackUnchecked(fluid));
        if (tag.contains("bank_capacities", Tag.TAG_INT_ARRAY)) {
            int[] capacities = tag.getIntArray("bank_capacities");
            int sumpCap = tag.contains("sump_capacity", Tag.TAG_ANY_NUMERIC) ? tag.getInt("sump_capacity") : 0;
            int minY = tag.contains("bounds_min_y", Tag.TAG_ANY_NUMERIC) ? tag.getInt("bounds_min_y") : 0;
            int maxY = tag.contains("bounds_max_y", Tag.TAG_ANY_NUMERIC) ? tag.getInt("bounds_max_y") : 0;
            int[] trays = tag.contains("tray_layers", Tag.TAG_INT_ARRAY) ? tag.getIntArray("tray_layers") : new int[0];
            configureBanks(minY, maxY, trays, sumpCap, capacities);
            ListTag bankFluids = tag.getList("bank_fluids", Tag.TAG_COMPOUND);
            for (int i = 0; i < bankFluids.size() && i < banks.size(); i++) {
                banks.get(i).setStackUnchecked(FluidStack.parseOptional(provider, bankFluids.getCompound(i)));
            }
        }
    }

    @Override
    public void writeUpdateTag(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        super.writeUpdateTag(tag, provider);
        tag.put(SerializationConstants.FLUID, inputTank.getFluid().saveOptional(provider));
        tag.putInt("sump_capacity", sumpCapacity);
        tag.putInt("bounds_min_y", boundsMinY);
        tag.putInt("bounds_max_y", boundsMaxY);
        tag.putIntArray("tray_layers", trayLayers);
        tag.putIntArray("bank_capacities", bankCapacities.toIntArray());
        ListTag bankFluids = new ListTag(banks.size());
        for (IExtendedFluidTank bank : banks) {
            bankFluids.add(bank.getFluid().saveOptional(provider));
        }
        tag.put("bank_fluids", bankFluids);
    }

    @Override
    protected int getMultiblockRedstoneLevel() {
        return MekanismUtils.redstoneLevelFromContents(inputTank.getFluidAmount(), inputTank.getCapacity());
    }
}
