package io.aduhtkjm.mekanismheated.content.fusedpipe;

import java.util.ArrayList;
import java.util.List;
import io.aduhtkjm.mekanismheated.tile.TileEntityFusedPipe;
import mekanism.api.chemical.ChemicalStack;
import mekanism.common.lib.transmitter.ConnectionType;
import net.minecraft.world.item.ItemStack;
import mekanism.common.tier.CableTier;
import mekanism.common.tier.ConductorTier;
import mekanism.common.tier.PipeTier;
import mekanism.common.tier.TransporterTier;
import mekanism.common.tier.TubeTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The per-position content object of a fused pipe. Holds all mutable transmission state that is
 * not owned by the {@link FusedNetwork} (saved shares for persistence) and delegates configuration
 * queries to its tile.
 */
public class FusedPipeNode {

    private final TileEntityFusedPipe tile;

    @Nullable
    private FusedNetwork network;

    /**
     * Share of the network's energy buffer that was distributed to this node when it unloaded or
     * when its network dispersed, so that buffered energy survives chunk unloads.
     */
    private long savedEnergy;

    /**
     * Share of the network's fluid tank distributed to this node, same purpose as {@link #savedEnergy}.
     */
    @NotNull
    private FluidStack savedFluid = FluidStack.EMPTY;

    /**
     * Share of the network's chemical tank distributed to this node, same purpose as {@link #savedEnergy}.
     */
    @NotNull
    private ChemicalStack savedChemical = ChemicalStack.EMPTY;

    /**
     * Share of the network's heat buffer distributed to this node, same purpose as {@link #savedEnergy}.
     */
    private double savedHeat;

    /**
     * Share of the network's item buffer distributed to this node, same purpose as {@link #savedEnergy}.
     */
    @NotNull
    private List<ItemStack> savedItems = new ArrayList<>();

    public FusedPipeNode(TileEntityFusedPipe tile) {
        this.tile = tile;
    }

    public TileEntityFusedPipe getTile() {
        return tile;
    }

    public BlockPos getBlockPos() {
        return tile.getBlockPos();
    }

    @Nullable
    public Level getLevel() {
        return tile.getLevel();
    }

    public boolean isValid() {
        return !tile.isRemoved() && tile.isLoaded();
    }

    @Nullable
    public FusedNetwork getNetwork() {
        return network;
    }

    public void setNetwork(@Nullable FusedNetwork network) {
        FusedNetwork old = this.network;
        this.network = network;
        if (old != network) {
            tile.invalidateTransmittedCapabilities();
        }
    }

    //Shares

    public long takeSavedEnergy() {
        long energy = savedEnergy;
        savedEnergy = 0L;
        return energy;
    }

    public void setSavedEnergy(long energy) {
        this.savedEnergy = Math.max(0L, energy);
    }

    public long getSavedEnergy() {
        return savedEnergy;
    }

    @NotNull
    public FluidStack takeSavedFluid() {
        FluidStack fluid = savedFluid;
        savedFluid = FluidStack.EMPTY;
        return fluid;
    }

    public void setSavedFluid(@NotNull FluidStack fluid) {
        this.savedFluid = fluid.isEmpty() ? FluidStack.EMPTY : fluid;
    }

    @NotNull
    public FluidStack getSavedFluid() {
        return savedFluid;
    }

    @NotNull
    public ChemicalStack takeSavedChemical() {
        ChemicalStack chemical = savedChemical;
        savedChemical = ChemicalStack.EMPTY;
        return chemical;
    }

    public void setSavedChemical(@NotNull ChemicalStack chemical) {
        this.savedChemical = chemical.isEmpty() ? ChemicalStack.EMPTY : chemical;
    }

    @NotNull
    public ChemicalStack getSavedChemical() {
        return savedChemical;
    }

    public double takeSavedHeat() {
        double heat = savedHeat;
        savedHeat = 0;
        return heat;
    }

    public void setSavedHeat(double heat) {
        this.savedHeat = Math.max(0, heat);
    }

    public double getSavedHeat() {
        return savedHeat;
    }

    @NotNull
    public List<ItemStack> takeSavedItems() {
        List<ItemStack> items = savedItems;
        savedItems = new ArrayList<>();
        return items;
    }

    public void setSavedItems(@NotNull List<ItemStack> items) {
        this.savedItems = items;
    }

    @NotNull
    public List<ItemStack> getSavedItems() {
        return savedItems;
    }

    //Configuration delegation

    public boolean isEnabled(FusedFunction function) {
        return tile.getConfig().isEnabled(function);
    }

    /**
     * @return The capacity this node contributes to the network's energy buffer,
     * zero if the energy function is disabled.
     */
    public long getEnergyCapacity() {
        if (!tile.getConfig().isEnabled(FusedFunction.ENERGY)) {
            return 0L;
        }
        return CableTier.get(tile.getConfig().getTier(FusedFunction.ENERGY)).getCableCapacity();
    }

    /**
     * @return The maximum amount of energy this node may pull through a pull side per tick.
     */
    public long getEnergyPullRate() {
        return getEnergyCapacity();
    }

    /**
     * @return The fluid capacity this node contributes to the network's fluid tank in mB.
     */
    public long getFluidCapacity() {
        if (!tile.getConfig().isEnabled(FusedFunction.FLUID)) {
            return 0L;
        }
        return PipeTier.get(tile.getConfig().getTier(FusedFunction.FLUID)).getPipeCapacity();
    }

    /**
     * @return The maximum amount of fluid in mB this node may pull through a pull side per tick.
     */
    public int getFluidPullRate() {
        if (!tile.getConfig().isEnabled(FusedFunction.FLUID)) {
            return 0;
        }
        return PipeTier.get(tile.getConfig().getTier(FusedFunction.FLUID)).getPipePullAmount();
    }

    /**
     * @return The chemical capacity this node contributes to the network's chemical tank.
     */
    public long getChemicalCapacity() {
        if (!tile.getConfig().isEnabled(FusedFunction.CHEMICAL)) {
            return 0L;
        }
        return TubeTier.get(tile.getConfig().getTier(FusedFunction.CHEMICAL)).getTubeCapacity();
    }

    /**
     * @return The maximum amount of chemical this node may pull through a pull side per tick.
     */
    public long getChemicalPullRate() {
        if (!tile.getConfig().isEnabled(FusedFunction.CHEMICAL)) {
            return 0L;
        }
        return TubeTier.get(tile.getConfig().getTier(FusedFunction.CHEMICAL)).getTubePullAmount();
    }

    /**
     * @return The heat capacity this node contributes to the network's heat buffer.
     */
    public double getHeatCapacity() {
        if (!tile.getConfig().isEnabled(FusedFunction.HEAT)) {
            return 0D;
        }
        return ConductorTier.get(tile.getConfig().getTier(FusedFunction.HEAT)).getHeatCapacity();
    }

    /**
     * @return The inverse conduction coefficient this node contributes to the network.
     */
    public double getHeatConduction() {
        if (!tile.getConfig().isEnabled(FusedFunction.HEAT)) {
            return 0D;
        }
        return ConductorTier.get(tile.getConfig().getTier(FusedFunction.HEAT)).getInverseConduction();
    }

    /**
     * @return The inverse insulation coefficient this node contributes to the network.
     */
    public double getHeatInsulation() {
        if (!tile.getConfig().isEnabled(FusedFunction.HEAT)) {
            return 0D;
        }
        return ConductorTier.get(tile.getConfig().getTier(FusedFunction.HEAT)).getInverseConductionInsulation();
    }

    public int getItemPullAmount() {
        if (!tile.getConfig().isEnabled(FusedFunction.ITEM)) {
            return 0;
        }
        return TransporterTier.get(tile.getConfig().getTier(FusedFunction.ITEM)).getPullAmount();
    }

    public int getItemSpeed() {
        if (!tile.getConfig().isEnabled(FusedFunction.ITEM)) {
            return 0;
        }
        return TransporterTier.get(tile.getConfig().getTier(FusedFunction.ITEM)).getSpeed();
    }

    //Connection helpers (shared side config)

    @Nullable
    public ConnectionType getConnectionTypeRaw(Direction side) {
        return tile.getConnectionTypeRaw(side);
    }

    public boolean canSendTo(Direction side) {
        ConnectionType type = tile.getConnectionTypeRaw(side);
        return type != null && type.canSendTo() && !tile.isRedstoneActivated();
    }

    public boolean canAcceptFrom(Direction side) {
        ConnectionType type = tile.getConnectionTypeRaw(side);
        return type != null && type.canAccept() && !tile.isRedstoneActivated();
    }

    /**
     * Pull sides actively drain their neighbors (vanilla transmitter semantics); NORMAL sides only
     * accept pushes.
     */
    public boolean isPullSide(Direction side) {
        ConnectionType type = tile.getConnectionTypeRaw(side);
        return type == ConnectionType.PULL && !tile.isRedstoneActivated();
    }

    /**
     * Whether the network may actively drain energy from this side. Energy deliberately follows
     * Mekanism cable behaviour: NORMAL sides also pull automatically, no PULL configuration needed.
     */
    public boolean pullsEnergyFrom(Direction side) {
        return canAcceptFrom(side);
    }

    /**
     * Fluid pulling requires an explicitly configured PULL side (vanilla mechanical pipe behaviour).
     */
    public boolean pullsFluidFrom(Direction side) {
        return isPullSide(side);
    }

    /**
     * Chemical pulling requires an explicitly configured PULL side (vanilla pressurized tube behaviour).
     */
    public boolean pullsChemicalFrom(Direction side) {
        return isPullSide(side);
    }

    /**
     * Item pulling requires an explicitly configured PULL side (vanilla logistical transporter behaviour).
     */
    public boolean pullsItemsFrom(Direction side) {
        return isPullSide(side);
    }

    @NotNull
    @Override
    public String toString() {
        return "FusedPipeNode{" + tile.getBlockPos() + ", network=" + (network == null ? "none" : network.getUUID()) + "}";
    }
}
