package io.aduhtkjm.mekanismheated.content.fusedpipe;

import io.aduhtkjm.mekanismheated.tile.TileEntityFusedPipe;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BooleanSupplier;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.heat.IHeatHandler;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.integration.energy.EnergyCompatUtils;
import mekanism.common.integration.energy.IEnergyCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Per-network cache of neighboring acceptors.
 * <p>
 * Instead of a position-keyed map, this keeps flat lists of acceptor entries that are rebuilt only
 * when something relevant changes: network membership, side configuration, the redstone state, or a
 * neighbor gaining/losing a capability (NeoForge fires our invalidation callback for the latter).
 * Between rebuilds the per-tick cost is a plain list walk with no lookups and no side checks.
 * <p>
 * Entries are only created when the neighbor actually exposes the corresponding capability from
 * the relevant side; same-network pipes are excluded. The underlying {@link BlockCapabilityCache}s
 * are registered with invalidation callbacks so that a machine placed or removed later triggers a
 * rebuild, at which point the capability check is re-evaluated.
 */
public final class FusedAcceptorCache {

    /**
     * Per-side bundle of all loaded energy-compat capability caches. Only created when the neighbor
     * exposes at least one energy capability; whenever any of the underlying capabilities changes,
     * {@link #invalidate()} fires through the invalidation callbacks.
     */
    private static final class EnergyAdaptor {

        private record CompatCache(IEnergyCompat compat, BlockCapabilityCache<?, @Nullable Direction> cache) {
        }

        /**
         * Compatibility layers for each different type of energy. <p>
         *
         * E.g. FE, RF, EU etc.
         */
        private final List<CompatCache> caches = new ArrayList<>();

        private EnergyAdaptor(ServerLevel level, BlockPos pos, Direction context, Runnable invalidationListener) {
            //Finds all compatibility layers that we need to use.
            for (IEnergyCompat energyCompat : EnergyCompatUtils.getCompats()) {
                if (energyCompat.capabilityExists()) {
                    caches.add(new CompatCache(energyCompat,
                          energyCompat.getCapability().createCache(level, pos, context, ALWAYS_VALID, invalidationListener)));
                }
            }
        }

        /**
         * Converts other energy interfaces into the one that Mekanism uses natively.
         */
        @Nullable
        private IStrictEnergyHandler resolve() {
            for (CompatCache cache : caches) {
                IEnergyCompat energyCompat = cache.compat();
                if (!energyCompat.isUsable()) {
                    continue;
                }
                Object capability = cache.cache().getCapability();
                if (capability != null) {
                    IStrictEnergyHandler wrapped = energyCompat.wrapAsStrictEnergyHandler(capability);
                    if (wrapped != null) {
                        return wrapped;
                    }
                }
            }
            return null;
        }
    }

    private static final BooleanSupplier ALWAYS_VALID = () -> true;

    /**
     * A neighboring handler the network may push energy into.
     */
    public record EnergyTarget(EnergyAdaptor adaptor) {

        @Nullable
        public IStrictEnergyHandler resolve() {
            return adaptor.resolve();
        }
    }

    /**
     * A neighboring handler the network may actively drain energy from. Remembers the origin node
     * because pull rates are tier dependent.
     */
    public record EnergySource(FusedPipeNode origin, EnergyAdaptor adaptor) {

        @Nullable
        public IStrictEnergyHandler resolve() {
            return adaptor.resolve();
        }
    }

    /**
     * A neighboring capability the network may push fluids/chemicals/items into.
     */
    public record TankTarget<H>(BlockCapabilityCache<H, @Nullable Direction> cache) {

        @Nullable
        public H resolve() {
            return cache.getCapability();
        }
    }

    /**
     * A neighboring capability the network may actively drain fluids/chemicals/items from.
     */
    public record TankSource<H>(FusedPipeNode origin, BlockCapabilityCache<H, @Nullable Direction> cache) {

        @Nullable
        public H resolve() {
            return cache.getCapability();
        }
    }

    /**
     * A neighboring heat handler. Heat is different from energy/fluid/chemical: it flows based on
     * temperature difference to ALL adjacent blocks with IHeatHandler capability, regardless of
     * the side's connection type (NONE, NORMAL, PULL, PUSH all conduct heat).
     */
    public record HeatAcceptor(BlockCapabilityCache<IHeatHandler, @Nullable Direction> cache) {

        @Nullable
        public IHeatHandler resolve() {
            return cache.getCapability();
        }
    }

    private final List<EnergyTarget> energyTargets = new ArrayList<>();
    private final List<EnergySource> energySources = new ArrayList<>();
    private final List<TankTarget<IFluidHandler>> fluidTargets = new ArrayList<>();
    private final List<TankSource<IFluidHandler>> fluidSources = new ArrayList<>();
    private final List<TankTarget<IChemicalHandler>> chemicalTargets = new ArrayList<>();
    private final List<TankSource<IChemicalHandler>> chemicalSources = new ArrayList<>();
    private final List<TankTarget<IItemHandler>> itemTargets = new ArrayList<>();
    private final List<TankSource<IItemHandler>> itemSources = new ArrayList<>();
    private final List<HeatAcceptor> heatAcceptors = new ArrayList<>();
    private boolean dirty = true;

    /**
     * Marks the acceptor lists as outdated; they are rebuilt on their next use.
     */
    public void invalidate() {
        dirty = true;
        clearLists();
    }

    public List<EnergyTarget> getEnergyTargets() {
        return energyTargets;
    }

    public List<EnergySource> getEnergySources() {
        return energySources;
    }

    public List<TankTarget<IFluidHandler>> getFluidTargets() {
        return fluidTargets;
    }

    public List<TankSource<IFluidHandler>> getFluidSources() {
        return fluidSources;
    }

    public List<TankTarget<IChemicalHandler>> getChemicalTargets() {
        return chemicalTargets;
    }

    public List<TankSource<IChemicalHandler>> getChemicalSources() {
        return chemicalSources;
    }

    public List<TankTarget<IItemHandler>> getItemTargets() {
        return itemTargets;
    }

    public List<TankSource<IItemHandler>> getItemSources() {
        return itemSources;
    }

    public List<HeatAcceptor> getHeatAcceptors() {
        return heatAcceptors;
    }

    /**
     * Rebuilds all acceptor lists if anything changed since the last build. Entries are only added
     * when the neighbor actually exposes the corresponding capability from the relevant side;
     * same-network pipes are always excluded. Heat acceptors are NOT gated by connection type —
     * heat flows to all adjacent blocks with IHeatHandler capability.
     */
    public void rebuildIfNeeded(Collection<FusedPipeNode> nodes) {
        if (!dirty) {
            return;
        }
        dirty = false;
        clearLists();
        for (FusedPipeNode node : nodes) {
            if (!(node.getLevel() instanceof ServerLevel level)) {
                continue;
            }
            BlockPos pos = node.getBlockPos();
            for (Direction side : Direction.values()) {
                BlockPos neighborPos = pos.relative(side);
                Direction context = side.getOpposite();
                //Skip same-network pipes — they share buffers with us and would be circular
                if (isSameNetworkPipe(level, neighborPos, node)) {
                    continue;
                }
                boolean sends = node.canSendTo(side);
                boolean pullsEnergy = node.pullsEnergyFrom(side);
                //Only add entries when the neighbor actually exposes the capability from this side
                if (sends || pullsEnergy) {
                    boolean hasEnergy = false;
                    for (BlockCapability<?, @Nullable Direction> energyCap : EnergyCompatUtils.getLoadedEnergyCapabilities()) {
                        if (level.getCapability(energyCap, neighborPos, context) != null) {
                            hasEnergy = true;
                            break;
                        }
                    }
                    if (hasEnergy) {
                        //One adaptor per side so NORMAL sides (both send and pull) share it
                        EnergyAdaptor adaptor = new EnergyAdaptor(level, neighborPos, context, this::invalidate);
                        if (sends) {
                            energyTargets.add(new EnergyTarget(adaptor));
                        }
                        if (pullsEnergy) {
                            energySources.add(new EnergySource(node, adaptor));
                        }
                    }
                }
                if (sends) {
                    if (level.getCapability(Capabilities.FLUID.block(), neighborPos, context) != null) {
                        addTarget(fluidTargets, Capabilities.FLUID.block(), level, neighborPos, context);
                    }
                    if (level.getCapability(Capabilities.CHEMICAL.block(), neighborPos, context) != null) {
                        addTarget(chemicalTargets, Capabilities.CHEMICAL.block(), level, neighborPos, context);
                    }
                    if (level.getCapability(Capabilities.ITEM.block(), neighborPos, context) != null) {
                        addTarget(itemTargets, Capabilities.ITEM.block(), level, neighborPos, context);
                    }
                }
                if (node.pullsFluidFrom(side) && level.getCapability(Capabilities.FLUID.block(), neighborPos, context) != null) {
                    addSource(fluidSources, node, Capabilities.FLUID.block(), level, neighborPos, context);
                }
                if (node.pullsChemicalFrom(side) && level.getCapability(Capabilities.CHEMICAL.block(), neighborPos, context) != null) {
                    addSource(chemicalSources, node, Capabilities.CHEMICAL.block(), level, neighborPos, context);
                }
                if (node.pullsItemsFrom(side) && level.getCapability(Capabilities.ITEM.block(), neighborPos, context) != null) {
                    addSource(itemSources, node, Capabilities.ITEM.block(), level, neighborPos, context);
                }
                //Heat: always add if heat is enabled on this node — heat flows regardless of connection type
                if (node.isEnabled(FusedFunction.HEAT) && level.getCapability(Capabilities.HEAT, neighborPos, context) != null) {
                    addHeatAcceptor(level, neighborPos, context);
                }
            }
        }
    }

    /**
     * Returns true if the block at {@code pos} is a fused pipe belonging to the same network as
     * the node being rebuilt. Same-network pipes share buffers and must not be added as targets or
     * sources — that would create circular references and waste ticks on no-op resolves.
     */
    private boolean isSameNetworkPipe(ServerLevel level, BlockPos pos, FusedPipeNode node) {
        FusedNetwork network = node.getNetwork();
        if (network == null) {
            return false;
        }
        if (level.getBlockEntity(pos) instanceof TileEntityFusedPipe tile) {
            return tile.getNode().getNetwork() == network;
        }
        return false;
    }

    private void clearLists() {
        energyTargets.clear();
        energySources.clear();
        fluidTargets.clear();
        fluidSources.clear();
        chemicalTargets.clear();
        chemicalSources.clear();
        itemTargets.clear();
        itemSources.clear();
        heatAcceptors.clear();
    }

    private <H> void addTarget(List<TankTarget<H>> targets, BlockCapability<H, @Nullable Direction> capability,
          ServerLevel level, BlockPos pos, Direction context) {
        targets.add(new TankTarget<>(BlockCapabilityCache.create(capability, level, pos, context, ALWAYS_VALID, this::invalidate)));
    }

    private <H> void addSource(List<TankSource<H>> sources, FusedPipeNode origin, BlockCapability<H, @Nullable Direction> capability,
          ServerLevel level, BlockPos pos, Direction context) {
        sources.add(new TankSource<>(origin, BlockCapabilityCache.create(capability, level, pos, context, ALWAYS_VALID, this::invalidate)));
    }

    private void addHeatAcceptor(ServerLevel level, BlockPos pos, Direction context) {
        heatAcceptors.add(new HeatAcceptor(BlockCapabilityCache.create(Capabilities.HEAT, level, pos, context, ALWAYS_VALID, this::invalidate)));
    }
}
