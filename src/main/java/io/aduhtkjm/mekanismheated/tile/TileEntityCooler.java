package io.aduhtkjm.mekanismheated.tile;

import io.aduhtkjm.mekanismheated.Config;
import io.aduhtkjm.mekanismheated.registries.ModBlocks;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.RelativeSide;
import mekanism.api.SerializationConstants;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.capabilities.heat.BasicHeatCapacitor;
import mekanism.common.capabilities.heat.CachedAmbientTemperature;
import mekanism.common.capabilities.holder.energy.EnergyContainerHelper;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.heat.HeatCapacitorHelper;
import mekanism.common.capabilities.holder.heat.IHeatCapacitorHolder;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableLong;
import mekanism.common.registries.MekanismDataComponents;
import mekanism.common.tile.base.TileEntityMekanism;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.system.NonnullDefault;

@NonnullDefault
public class TileEntityCooler extends TileEntityMekanism {

    public static final long BASE_USAGE = 100;
    private long energyUsed = 0;

    private static final String TAG_ENERGY_USAGE = "targetTemperature";

    @SuppressWarnings("all") // nullable
    private MachineEnergyContainer<TileEntityCooler> energyContainer;
    @SuppressWarnings("all") // nullable
    private BasicHeatCapacitor hotCapacitor;
    @SuppressWarnings("all") // nullable
    private BasicHeatCapacitor coldCapacitor;

    public TileEntityCooler(BlockPos pos, BlockState state) {
        super(ModBlocks.COOLER, pos, state);
    }

    @Override
    protected IEnergyContainerHolder getInitialEnergyContainers(IContentsListener listener) {
        EnergyContainerHelper builder = EnergyContainerHelper.forSide(facingSupplier);
        builder.addContainer(energyContainer = MachineEnergyContainer.input(this, listener));
        energyContainer.setMaxEnergy(Config.Cooler.MAX_ENERGY.get());
        return builder.build();
    }

    @Override
    protected IHeatCapacitorHolder getInitialHeatCapacitors(IContentsListener listener, CachedAmbientTemperature ambient) {
        HeatCapacitorHelper builder = HeatCapacitorHelper.forSide(facingSupplier);
        double capacity = Config.Cooler.HEAT_CAPACITY.get();
        double invCdt = Config.Cooler.INVERSE_CONDUCTION_COEFFICIENT.get();
        double invIns = Config.Cooler.INVERSE_INSULATION_COEFFICIENT.get();
        hotCapacitor = BasicHeatCapacitor.create(capacity, invCdt, invIns, ambient, listener);
        coldCapacitor = BasicHeatCapacitor.create(capacity, invCdt, invIns, ambient, listener);
        builder.addCapacitor(hotCapacitor, RelativeSide.LEFT, RelativeSide.RIGHT);
        builder.addCapacitor(coldCapacitor, RelativeSide.FRONT, RelativeSide.BACK, RelativeSide.TOP, RelativeSide.BOTTOM);
        return builder.build();
    }

    @Override
    protected boolean onUpdateServer() {
        boolean sendUpdatePacket = super.onUpdateServer();
        long toUse = 0;
        if (canFunction()) {
            toUse = energyContainer.extract(energyContainer.getEnergyPerTick(), Action.SIMULATE, AutomationType.INTERNAL);
            if (toUse > 0) {
                double heat = toUse * getCop();
                coldCapacitor.handleHeat(-heat);
                hotCapacitor.handleHeat(heat);
                energyContainer.extract(toUse, Action.EXECUTE, AutomationType.INTERNAL);
            }
        }
        setActive(toUse > 0);
        energyUsed = toUse;
        simulate();
        return sendUpdatePacket;
    }

    /**
     * Effective heat pump coefficient of performance (COP), i.e. heat moved per joule of energy
     * consumed, at the current cold-side temperature.
     *
     * <p>The COP scales linearly with the cold side's absolute temperature, equal to the configured
     * {@link Config.Cooler#EFFICIENCY} at the ambient temperature the cold side starts at. A
     * refrigerator's ideal (Carnot) COP is proportional to the cold reservoir's absolute
     * temperature, so this makes cooling progressively harder as the cold side gets colder: the
     * cooling rate tends to zero as the cold side approaches 0K, which acts as an asymptote the
     * cold side can get arbitrarily close to but never below.
     */
    public double getCop() {
        double coldTemperature = coldCapacitor.getTemperature();
        if (coldTemperature <= 0)
            return 0;

        return Config.Cooler.EFFICIENCY.get() * coldTemperature / ambientTemperature.getAsDouble();
    }

    public long getEnergyUsed() {
        return energyUsed;
    }

    public double getHotTemperature() {
        return hotCapacitor.getTemperature();
    }

    public double getColdTemperature() {
        return coldCapacitor.getTemperature();
    }

    public MachineEnergyContainer<TileEntityCooler> getEnergyContainer() {
        return energyContainer;
    }

    public void setEnergyUsageFromPacket(long energyUsage) {
        energyContainer.setEnergyPerTick(energyUsage);
        markForSave();
    }

    @Override
    public CompoundTag getConfigurationData(HolderLookup.Provider provider, Player player) {
        CompoundTag data = super.getConfigurationData(provider, player);
        data.putLong(SerializationConstants.ENERGY_USAGE, energyContainer.getEnergyPerTick());
        return data;
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableLong.create(this::getEnergyUsed, value -> energyUsed = value));
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        builder.set(MekanismDataComponents.ENERGY_USAGE, energyContainer.getEnergyPerTick());
        super.collectImplicitComponents(builder);
    }
    
    @Override
    public void saveAdditional( CompoundTag nbt,  HolderLookup.Provider provider) {
        super.saveAdditional(nbt, provider);
        nbt.putLong(TAG_ENERGY_USAGE, energyUsed);
    }

    @Override
    public void loadAdditional( CompoundTag nbt,  HolderLookup.Provider provider) {
        super.loadAdditional(nbt, provider);
        energyUsed = nbt.getLong(TAG_ENERGY_USAGE);
    }
}
