package io.aduhtkjm.mekanismheated.content.fusedpipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BooleanSupplier;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.integration.energy.EnergyCompatUtils;
import mekanism.common.integration.energy.IEnergyCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Per-network cache of neighboring acceptors.
 * <p>
 * Instead of a position-keyed map, this keeps flat lists of acceptor entries that are rebuilt only
 * when something relevant changes: network membership, side configuration, the redstone state, or a
 * neighbor losing/regaining a capability (NeoForge fires our invalidation callback for the latter).
 * Between rebuilds the per-tick cost is a plain list walk with no lookups and no side checks.
 * <p>
 * Block positions are needed only while building the lists, to create the underlying
 * {@link BlockCapabilityCache}s pointing at the neighbors; afterwards only the handlers matter.
 * Capability caches persist even while their neighbor currently exposes nothing, so machines
 * placed later are picked up without any rebuild.
 */
public final class FusedAcceptorCache {

    /**
     * Per-side bundle of all loaded energy-compat capability caches. Persists even while no
     * capability resolves, so a machine placed later is picked up immediately; whenever any of
     * the underlying capabilities changes, {@link #invalidate()} fires through the invalidation
     * callbacks.
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
     * A neighboring capability the network may push fluids/chemicals into.
     */
    public record TankTarget<H>(BlockCapabilityCache<H, @Nullable Direction> cache) {

        @Nullable
        public H resolve() {
            return cache.getCapability();
        }
    }

    /**
     * A neighboring capability the network may actively drain fluids/chemicals from.
     */
    public record TankSource<H>(FusedPipeNode origin, BlockCapabilityCache<H, @Nullable Direction> cache) {

        @Nullable
        public H resolve() {
            return cache.getCapability();
        }
    }

    private final List<EnergyTarget> energyTargets = new ArrayList<>();
    private final List<EnergySource> energySources = new ArrayList<>();
    private final List<TankTarget<IFluidHandler>> fluidTargets = new ArrayList<>();
    private final List<TankSource<IFluidHandler>> fluidSources = new ArrayList<>();
    private final List<TankTarget<IChemicalHandler>> chemicalTargets = new ArrayList<>();
    private final List<TankSource<IChemicalHandler>> chemicalSources = new ArrayList<>();
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

    /**
     * Rebuilds all acceptor lists if anything changed since the last build.
     */
    public void rebuildIfNeeded(Collection<FusedPipeNode> nodes) {
        if (!dirty) {
            return;
        }
        dirty = false;
        for (FusedPipeNode node : nodes) {
            if (!(node.getLevel() instanceof ServerLevel level)) {
                continue;
            }
            BlockPos pos = node.getBlockPos();
            for (Direction side : Direction.values()) {
                BlockPos neighborPos = pos.relative(side);
                Direction context = side.getOpposite();
                boolean sends = node.canSendTo(side);
                boolean pullsEnergy = node.pullsEnergyFrom(side);
                if (sends || pullsEnergy) {
                    //One adaptor per side so NORMAL sides (both send and pull) share it
                    EnergyAdaptor adaptor = new EnergyAdaptor(level, neighborPos, context, this::invalidate);
                    if (sends) {
                        energyTargets.add(new EnergyTarget(adaptor));
                    }
                    if (pullsEnergy) {
                        energySources.add(new EnergySource(node, adaptor));
                    }
                    addTarget(fluidTargets, Capabilities.FLUID.block(), level, neighborPos, context);
                    addTarget(chemicalTargets, Capabilities.CHEMICAL.block(), level, neighborPos, context);
                }
                if (node.pullsFluidFrom(side)) {
                    addSource(fluidSources, node, Capabilities.FLUID.block(), level, neighborPos, context);
                }
                if (node.pullsChemicalFrom(side)) {
                    addSource(chemicalSources, node, Capabilities.CHEMICAL.block(), level, neighborPos, context);
                }
            }
        }
    }

    private void clearLists() {
        energyTargets.clear();
        energySources.clear();
        fluidTargets.clear();
        fluidSources.clear();
        chemicalTargets.clear();
        chemicalSources.clear();
    }

    private <H> void addTarget(List<TankTarget<H>> targets, BlockCapability<H, @Nullable Direction> capability,
          ServerLevel level, BlockPos pos, Direction context) {
        targets.add(new TankTarget<>(BlockCapabilityCache.create(capability, level, pos, context, ALWAYS_VALID, this::invalidate)));
    }

    private <H> void addSource(List<TankSource<H>> sources, FusedPipeNode origin, BlockCapability<H, @Nullable Direction> capability,
          ServerLevel level, BlockPos pos, Direction context) {
        sources.add(new TankSource<>(origin, BlockCapabilityCache.create(capability, level, pos, context, ALWAYS_VALID, this::invalidate)));
    }
}
