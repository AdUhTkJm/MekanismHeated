package io.aduhtkjm.mekanismheated.content.fractionation;

import io.aduhtkjm.mekanismheated.Config;
import io.aduhtkjm.mekanismheated.recipe.FractionationRecipe;
import io.aduhtkjm.mekanismheated.recipe.FractionationRecipe.BankOutput;
import io.aduhtkjm.mekanismheated.recipe.ModRecipeTypes;
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
import mekanism.common.inventory.slot.FluidInventorySlot;
import mekanism.common.lib.multiblock.MultiblockData;
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

/**
 * Multiblock data for a Thermal Fractionation Tower.
 *
 * <p>The interior is divided vertically by distillation tray layers: the space below the lowest tray forms the shared
 * feed sump ({@link #inputTank}), and every compartment directly above a tray forms one output bank. Banks are indexed
 * from the bottom of the tower.</p>
 */
public class FractionationMultiblockData extends MultiblockData {

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
    private double biomeAmbientTemp;
    private double progress;
    private boolean processing;

    // Scale trackers used to throttle update packets, mirroring the thermal evaporation plant
    public float prevInputScale;
    public float[] prevBankScales = new float[0];

    public FractionationMultiblockData(BlockEntity tile) {
        super(tile);
        biomeAmbientTemp = HeatAPI.AMBIENT_TEMP;
        inputTank = VariableCapacityFluidTank.input(this, this::getSumpCapacity, ConstantPredicates.alwaysTrue(), createSaveAndComparator(this));
        heatCapacitor = VariableHeatCapacitor.create(Config.Fractionation.HEAT_CAPACITY_PER_HEIGHT.get() * 3, () -> biomeAmbientTemp, this);
        fluidTanks.add(inputTank);
        inventorySlots.add(FluidInventorySlot.fill(inputTank, this, 28, 12));
        inventorySlots.add(FluidInventorySlot.drain(inputTank, this, 28, 50));
    }

    /**
     * (Re)builds the feed sump capacity and the output banks. Called server-side during formation validation and
     * client-side when reading the update tag.
     */
    public void configureBanks(int sumpCapacityIn, int[] bankCapacitiesIn) {
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
        heatCapacitor.setHeatCapacity(Config.Fractionation.HEAT_CAPACITY_PER_HEIGHT.get() * height(), true);
    }

    @Override
    public boolean tick(Level world) {
        boolean needsPacket = super.tick(world);
        // external heat dissipation
        lastEnvironmentLoss = simulateEnvironment();
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

    public double getTemperature() {
        return heatCapacitor.getTemperature();
    }

    public boolean isProcessing() {
        return processing;
    }

    /**
     * Runs fractionation recipes: consumes matching fluid from the sump and deposits the outputs into their target banks.
     *
     * @return {@code true} if the processing state changed and an update packet should be sent.
     */
    private boolean processRecipes(Level world) {
        boolean wasProcessing = processing;
        FluidStack current = inputTank.getFluid();
        FractionationRecipe recipe = null;
        if (!current.isEmpty()) {
            recipe = world.getRecipeManager()
                  .getRecipeFor(ModRecipeTypes.TYPE_FRACTIONATING.value(), new SingleFluidRecipeInput(current), world)
                  .map(RecipeHolder::value)
                  .orElse(null);
        }
        if (recipe == null || recipe.getMinTemperature() > getTemperature()) {
            processing = false;
            return wasProcessing != processing;
        }
        //Speed scales linearly from zero ops at min temperature up to nominal speed at base temperature,
        // and keeps scaling proportionally beyond it
        double span = recipe.getBaseTemperature() - recipe.getMinTemperature();
        double rate = span <= 0 ? 1 : Math.max(0, (getTemperature() - recipe.getMinTemperature()) / span);
        progress += rate;
        int operations = (int) progress;
        if (operations <= 0) {
            processing = false;
            return wasProcessing != processing;
        }
        int performed = 0;
        while (performed < operations && performOperation(recipe)) {
            performed++;
        }
        progress -= performed;
        processing = performed > 0;
        return wasProcessing != processing;
    }

    /**
     * Attempts to run a single operation of the given recipe.
     *
     * @return {@code true} if the operation was performed.
     */
    private boolean performOperation(FractionationRecipe recipe) {
        FluidStack current = inputTank.getFluid();
        FluidStack required = recipe.getInput().getMatchingInstance(current);
        if (required.isEmpty() || required.getAmount() > inputTank.getFluidAmount()) {
            return false;
        }
        List<BankOutput> outputs = recipe.getOutputs();
        //Simulate all deposits first so we never consume input without being able to produce the outputs
        for (BankOutput output : outputs) {
            if (output.bank() >= banks.size()) {
                return false;
            }
            if (!banks.get(output.bank()).insert(output.stack().copy(), Action.SIMULATE, AutomationType.INTERNAL).isEmpty()) {
                return false;
            }
        }
        inputTank.extract(required.getAmount(), Action.EXECUTE, AutomationType.INTERNAL);
        for (BankOutput output : outputs) {
            banks.get(output.bank()).insert(output.stack().copy(), Action.EXECUTE, AutomationType.INTERNAL);
        }
        return true;
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
            configureBanks(sumpCap, capacities);
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
