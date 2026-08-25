package io.aduhtkjm.mekanismheated.content.fusedpipe;

import io.aduhtkjm.mekanismheated.tile.TileEntityFusedPipe;
import mekanism.common.lib.transmitter.ConnectionType;
import mekanism.common.tier.CableTier;
import mekanism.common.tier.ConductorTier;
import mekanism.common.tier.PipeTier;
import mekanism.common.tier.TransporterTier;
import mekanism.common.tier.TubeTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
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
        this.network = network;
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

    //Configuration delegation

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
     * @return The chemical capacity this node contributes to the network's chemical tank.
     */
    public long getChemicalCapacity() {
        if (!tile.getConfig().isEnabled(FusedFunction.CHEMICAL)) {
            return 0L;
        }
        return TubeTier.get(tile.getConfig().getTier(FusedFunction.CHEMICAL)).getTubeCapacity();
    }

    public double getHeatCapacity() {
        if (!tile.getConfig().isEnabled(FusedFunction.HEAT)) {
            return 0D;
        }
        return ConductorTier.get(tile.getConfig().getTier(FusedFunction.HEAT)).getHeatCapacity();
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

    @NotNull
    @Override
    public String toString() {
        return "FusedPipeNode{" + tile.getBlockPos() + ", network=" + (network == null ? "none" : network.getUUID()) + "}";
    }
}
