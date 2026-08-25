package io.aduhtkjm.mekanismheated.content.fusedpipe;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.common.integration.energy.EnergyCompatUtils;
import mekanism.common.integration.energy.IEnergyCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import org.jetbrains.annotations.Nullable;

/**
 * Per-network cache of neighboring acceptors, shared by all transmission functions.
 * Capability instances are resolved through NeoForge {@link BlockCapabilityCache}s which
 * automatically re-resolve whenever the neighbor changes, so entries never go stale.
 */
public final class FusedAcceptorCache {

    /**
     * Maps block position (packed as `long`) to energy acceptors on each side of the block.
     */
    private final Long2ObjectMap<EnumMap<Direction, EnergyAcceptorInfo>> energyAcceptors = new Long2ObjectOpenHashMap<>();

    /**
     * Resolves the strict energy acceptor (if any) adjacent to the given position/side,
     * transparently wrapping all loaded energy compat systems (FE etc.).
     */
    @Nullable
    public IStrictEnergyHandler getEnergyAcceptor(ServerLevel level, BlockPos pos, Direction side) {
        EnumMap<Direction, EnergyAcceptorInfo> sides = energyAcceptors.get(pos.asLong());
        if (sides == null) {
            sides = new EnumMap<>(Direction.class);
            energyAcceptors.put(pos.asLong(), sides);
        }
        EnergyAcceptorInfo info = sides.get(side);
        if (info == null) {
            info = new EnergyAcceptorInfo(level, pos, side);
            sides.put(side, info);
        }
        return info.acceptor();
    }

    public void clear() {
        energyAcceptors.clear();
    }

    private static final class EnergyAcceptorInfo {

        private record CacheInfo(IEnergyCompat energyCompat, BlockCapabilityCache<?, @Nullable Direction> cache) {
        }

        private final List<CacheInfo> capabilities = new ArrayList<>();

        private EnergyAcceptorInfo(ServerLevel level, BlockPos pos, Direction side) {
            Direction opposite = side.getOpposite();
            for (IEnergyCompat energyCompat : EnergyCompatUtils.getCompats()) {
                if (energyCompat.capabilityExists()) {
                    capabilities.add(new CacheInfo(energyCompat, energyCompat.getCapability().createCache(level, pos.relative(side), opposite)));
                }
            }
        }

        @Nullable
        private IStrictEnergyHandler acceptor() {
            for (CacheInfo cacheInfo : capabilities) {
                IEnergyCompat energyCompat = cacheInfo.energyCompat();
                if (energyCompat.isUsable()) {
                    Object capability = cacheInfo.cache().getCapability();
                    if (capability != null) {
                        IStrictEnergyHandler wrapped = energyCompat.wrapAsStrictEnergyHandler(capability);
                        if (wrapped != null) {
                            return wrapped;
                        }
                    }
                }
            }
            return null;
        }
    }
}
