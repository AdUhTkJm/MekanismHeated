package io.aduhtkjm.mekanismheated.tile;

import io.aduhtkjm.mekanismheated.Config;
import io.aduhtkjm.mekanismheated.content.ambient.ChunkAmbientTemperature;
import io.aduhtkjm.mekanismheated.recipe.AtmosphereFuelRecipe;
import io.aduhtkjm.mekanismheated.recipe.ModRecipeTypes;
import io.aduhtkjm.mekanismheated.registries.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.RelativeSide;
import mekanism.api.chemical.BasicChemicalTank;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.heat.HeatAPI;
import mekanism.api.recipes.ingredients.ChemicalStackIngredient;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.capabilities.holder.chemical.ChemicalTankHelper;
import mekanism.common.capabilities.holder.chemical.IChemicalTankHolder;
import mekanism.common.capabilities.holder.energy.EnergyContainerHelper;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.component.config.ConfigInfo;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import mekanism.common.util.EnumUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.NotNull;

/**
 * A machine that warms the ambient temperature of the chunks around it.
 *
 * <p>Every {@link Config.AtmosphereHeater#WORK_INTERVAL work cycle} (default 40 ticks) it consumes energy and fuel and
 * then raises the per-chunk ambient temperature delta (see {@link ChunkAmbientTemperature}) of the chunks around it: the
 * chunk containing the machine receives the full rise while each surrounding chunk (default 3x3 area) receives only the
 * configured outer fraction. The rise per cycle follows {@code dT = baseTempRise / 2^(T / temperatureScale)} Kelvin,
 * where {@code T} is the current effective ambient temperature (biome-based ambient plus the chunk delta), so heating
 * gradually tapers off as the ambient temperature climbs.
 *
 * <p>The base energy cost is {@code energyPerTick} FE/t over the whole cycle. {@link AtmosphereFuelRecipe}s accept a
 * single item or chemical (gas) input and reduce that consumption by a fixed FE/t amount, subtracted once per cycle.
 * All matching fuel present is consumed in full each cycle even if the total reduction exceeds the base cost; the
 * energy consumption itself simply floors at zero. Fuel is only consumed on cycles that actually run, i.e. when there
 * is enough energy stored to pay the (possibly reduced) cost.
 */
public class TileEntityAtmosphereHeater extends TileEntityConfigurableMachine {

    /** Capacity of the gas (chemical) input tank, in milli-buckets. */
    public static final long MAX_GAS = 10L * FluidType.BUCKET_VOLUME;

    private MachineEnergyContainer<TileEntityAtmosphereHeater> energyContainer;
    private IChemicalTank gasTank;

    private InputInventorySlot inputSlot;

    /** Counts ticks towards the next work cycle. */
    private int ticks;

    /** Energy-consumption reduction (FE/t) applied during the last completed work cycle, for display/debug purposes. */
    private long reduction;

    private static final int INTERVAL = Config.AtmosphereHeater.WORK_INTERVAL.get();
    private static final long BASE_COST = Config.AtmosphereHeater.ENERGY_PER_TICK.get();

    private static final List<TileEntityAtmosphereHeater.SideDefaults> SIDE_DEFAULTS = List.of(
        new TileEntityAtmosphereHeater.SideDefaults(DataType.INPUT, DataType.INPUT, DataType.INPUT), //FRONT
        new TileEntityAtmosphereHeater.SideDefaults(DataType.INPUT, DataType.INPUT, DataType.INPUT), //LEFT
        new TileEntityAtmosphereHeater.SideDefaults(DataType.INPUT, DataType.INPUT, DataType.INPUT), //RIGHT
        new TileEntityAtmosphereHeater.SideDefaults(DataType.INPUT, DataType.INPUT, DataType.INPUT), //BACK
        new TileEntityAtmosphereHeater.SideDefaults(DataType.INPUT, DataType.INPUT, DataType.INPUT), //TOP
        new TileEntityAtmosphereHeater.SideDefaults(DataType.INPUT, DataType.INPUT, DataType.INPUT) //BOTTOM
    );

    /** Default per-face {@link DataType} for the smelter's item, fluid and energy transmission. */
    private record SideDefaults(DataType item, DataType fluid, DataType energy) {}

    public TileEntityAtmosphereHeater(BlockPos pos, BlockState state) {
        super(ModBlocks.ATMOSPHERE_HEATER, pos, state);
        configComponent.setupInputConfig(TransmissionType.ITEM, inputSlot);
        configComponent.setupInputConfig(TransmissionType.CHEMICAL, gasTank);
        configComponent.setupInputConfig(TransmissionType.ENERGY, energyContainer);
        ejectorComponent = new TileComponentEjector(this);
        applySideDefaults();
    }

    /**
     * Applies {@link #SIDE_DEFAULTS} to the side config, setting each face's data type for item, fluid and heat.
     */
    private void applySideDefaults() {
        ConfigInfo itemConfig = configComponent.getConfig(TransmissionType.ITEM);
        ConfigInfo fluidConfig = configComponent.getConfig(TransmissionType.FLUID);
        ConfigInfo energyConfig = configComponent.getConfig(TransmissionType.ENERGY);
        for (int i = 0; i < SIDE_DEFAULTS.size(); i++) {
            RelativeSide side = EnumUtils.SIDES[i];
            TileEntityAtmosphereHeater.SideDefaults defaults = SIDE_DEFAULTS.get(i);
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

    @NotNull
    @Override
    protected IEnergyContainerHolder getInitialEnergyContainers(IContentsListener listener) {
        EnergyContainerHelper builder = EnergyContainerHelper.forSideWithConfig(this);
        builder.addContainer(energyContainer = MachineEnergyContainer.input(this, listener));
        return builder.build();
    }

    @NotNull
    @Override
    public IChemicalTankHolder getInitialChemicalTanks(IContentsListener listener) {
        ChemicalTankHelper builder = ChemicalTankHelper.forSideWithConfig(this);
        builder.addTank(gasTank = BasicChemicalTank.inputModern(MAX_GAS, this::isValidInputChemical, listener));
        return builder.build();
    }

    @NotNull
    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = InventorySlotHelper.forSideWithConfig(this);
        builder.addSlot(inputSlot = InputInventorySlot.at(this::isValidInputItem, listener, 75, 40));
        return builder.build();
    }

    /**
     * Checks if any atmosphere fuel recipe consumes the given item; used to validate what may enter the input slot.
     */
    private boolean isValidInputItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (AtmosphereFuelRecipe recipe : getRecipes()) {
            if (recipe.testItem(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if any atmosphere fuel recipe consumes the given chemical; used to validate what may enter the gas tank.
     */
    private boolean isValidInputChemical(ChemicalStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (AtmosphereFuelRecipe recipe : getRecipes()) {
            if (recipe.testChemical(stack)) {
                return true;
            }
        }
        return false;
    }

    private List<AtmosphereFuelRecipe> getRecipes() {
        Level level = getLevel();
        if (level == null) {
            return List.of();
        }
        List<AtmosphereFuelRecipe> recipes = new ArrayList<>();
        for (RecipeHolder<AtmosphereFuelRecipe> holder : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.TYPE_ATMOSPHERE_FUEL.value())) {
            recipes.add(holder.value());
        }
        return recipes;
    }

    @Override
    protected boolean onUpdateServer() {
        boolean sendUpdatePacket = super.onUpdateServer();
        if (level instanceof ServerLevel serverLevel) {
            if (++ticks >= INTERVAL) {
                ticks = 0;
                setActive(doWork(serverLevel));
            }
        }
        return sendUpdatePacket;
    }

    public long getEnergyConsumption() {
        return Math.max(0, BASE_COST - getReduction());
    }

    /**
     * Runs one work cycle: consumes all matching fuel, reduces the base energy cost by the fuel's total reduction
     * (floored at zero), extracts the energy and then warms the surrounding chunks' ambient temperature.
     *
     * @return {@code true} if the cycle ran.
     */
    private boolean doWork(ServerLevel serverLevel) {
        if (!canFunction()) {
            return false;
        }
        List<Runnable> fuelConsumers = new ArrayList<>();
        reduction = collectFuel(fuelConsumers);
        long cost = getEnergyConsumption();
        if (cost > 0 && energyContainer.getEnergy() < cost) {
            //Not enough energy to pay even the reduced cost; leave the fuel in place for the next cycle.
            reduction = 0;
            return false;
        }
        if (cost > 0) {
            energyContainer.extract(cost, Action.EXECUTE, AutomationType.INTERNAL);
        }
        //Fuel is consumed in full regardless of how much of its reduction ends up being used.
        fuelConsumers.forEach(Runnable::run);
        heatAmbient(serverLevel);
        return true;
    }

    /**
     * Scans the item slot and gas tank for fuel the recipes can consume and simulates consuming it.
     *
     * @param fuelConsumers list that gets one runnable per matched input that executes the actual consumption
     *
     * @return the total energy-consumption reduction (in FE/t) the matched fuel provides
     */
    private long collectFuel(List<Runnable> fuelConsumers) {
        long reduction = 0;
        ItemStack item = inputSlot.getStack();
        if (!item.isEmpty()) {
            for (AtmosphereFuelRecipe recipe : getRecipes()) {
                if (!recipe.testItem(item)) {
                    continue;
                }
                ItemStackIngredient ingredient = recipe.getItemInput().orElseThrow();
                long needed = ingredient.getNeededAmount(item);
                if (needed <= 0 || item.getCount() < needed) {
                    break;
                }
                reduction += recipe.getReduction();
                fuelConsumers.add(() -> inputSlot.shrinkStack((int) needed, Action.EXECUTE));
                break;
            }
        }
        ChemicalStack stored = gasTank.getStack();
        if (!stored.isEmpty()) {
            for (AtmosphereFuelRecipe recipe : getRecipes()) {
                if (!recipe.testChemical(stored)) {
                    continue;
                }
                ChemicalStackIngredient ingredient = recipe.getChemicalInput().orElseThrow();
                long needed = ingredient.getNeededAmount(stored);
                long available = stored.getAmount();
                if (needed <= 0 || available < needed) {
                    break;
                }
                reduction += recipe.getReduction();
                fuelConsumers.add(() -> gasTank.extract(needed, Action.EXECUTE, AutomationType.INTERNAL));
                break;
            }
        }
        return reduction;
    }

    /**
     * Raises the ambient temperature delta of the chunks around the machine: the machine's own chunk by the full rise,
     * each surrounding chunk (radius from config) by the outer fraction of the rise.
     */
    private void heatAmbient(ServerLevel serverLevel) {
        double ambient = HeatAPI.getAmbientTemp(serverLevel, worldPosition);
        double rise = Config.AtmosphereHeater.BASE_TEMP_RISE.get() / Math.pow(2, ambient / Config.AtmosphereHeater.TEMPERATURE_SCALE.get());
        double centerRise = rise * Config.AtmosphereHeater.CENTER_EFFECT.get();
        double outerRise = rise * Config.AtmosphereHeater.OUTER_EFFECT.get();
        if (centerRise == 0 && outerRise == 0) {
            return;
        }
        int radius = Config.AtmosphereHeater.CHUNK_RADIUS.get();
        ChunkPos centerChunk = new ChunkPos(worldPosition);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0) {
                    addDelta(serverLevel, centerChunk, centerRise);
                } else {
                    addDelta(serverLevel, new ChunkPos(centerChunk.x + dx, centerChunk.z + dz), outerRise);
                }
            }
        }
    }

    private static void addDelta(ServerLevel serverLevel, ChunkPos chunkPos, double rise) {
        if (rise == 0) {
            return;
        }
        ChunkAmbientTemperature.setDelta(serverLevel, chunkPos, ChunkAmbientTemperature.getDelta(serverLevel, chunkPos) + rise);
    }

    public MachineEnergyContainer<TileEntityAtmosphereHeater> getEnergyContainer() {
        return energyContainer;
    }

    public IChemicalTank getGasTank() {
        return gasTank;
    }

    public InputInventorySlot getInputSlot() {
        return inputSlot;
    }

    /** Energy-consumption reduction (FE/t) applied during the last completed work cycle. */
    public long getReduction() {
        return reduction;
    }
}
