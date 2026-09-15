package io.aduhtkjm.mekanismheated.tile.multiblock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import io.aduhtkjm.mekanismheated.util.CapabilityWatch;
import mekanism.api.heat.IHeatHandler;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.lib.multiblock.MultiblockData;
import mekanism.common.tile.base.TileEntityMekanism;
import mekanism.common.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import org.jetbrains.annotations.Nullable;

/**
 * Shared adjacent-heat-transfer simulation for {@link MultiblockData}.
 *
 * <p>{@link MultiblockData} implements {@code ITileHeatHandler}, but leaves {@code getAdjacent(Direction)} returning
 * {@code null}, so the default {@code simulateAdjacent()} can never find a neighbour: a formed multiblock exchanges no
 * heat with the blocks around it at all. On top of that, our {@code MixinITileHeatHandler} restores calorimetry-based
 * transfer, which is one-way: only the hotter side pushes heat into the colder one. A machine that has to be <em>cooled</em>
 * (the Retroentropic Array, or a Large Heat Smelter between bursts) is the hotter side in that exchange, so it must
 * actively run the simulation itself instead of relying on the neighbour.</p>
 *
 * <p>Because a multiblock's "outside" is the union of the faces of all of its member blocks, the neighbours cannot be
 * obtained from a single position. This helper keeps one {@link BlockCapabilityCache} per adjacent heat handler, and runs
 * the same calorimetry exchange as the tile implementation: both sides are brought to a weighted final temperature and
 * the resulting temperature difference is the transfer reported for the loss readouts.</p>
 *
 * <p>Only blocks outside the structure are considered, and only from member tiles that expose heat (so a block tucked
 * behind a casing without a heat capacitor is unreachable). Owned by one multiblock data instance; call
 * {@link #invalidate()} whenever the structure (re)forms or is removed. Neighbour changes are picked up on their own:
 * a {@link BlockCapabilityCache} covers positions that already expose heat, and a {@link CapabilityWatch} covers the
 * ones that do not, so a machine placed next to the structure — or one whose heat side the player reconfigures — is
 * still noticed.</p>
 */
public class MultiblockHeatTransfer {

    private static final BooleanSupplier ALWAYS_VALID = () -> true;

    private final MultiblockData data;
    private final List<BlockCapabilityCache<IHeatHandler, @Nullable Direction>> acceptors = new ArrayList<>();
    /**
     * Watches every adjacent position that currently exposes no heat handler. Unlike a member tile, a multiblock data
     * instance never receives block updates, so without this a heat machine placed next to the structure would never be
     * found (the cache for that position does not exist yet, and nothing else would rebuild the list).
     */
    private final CapabilityWatch watchers = new CapabilityWatch(this::invalidate);
    private boolean dirty = true;

    public MultiblockHeatTransfer(MultiblockData data) {
        this.data = data;
    }

    /**
     * Marks the cached neighbour list as stale so it is rebuilt on the next simulation. Safe to call at any time,
     * including from a capability invalidation callback while the list is being iterated.
     */
    public void invalidate() {
        dirty = true;
    }

    /**
     * Transfers heat from this multiblock into every adjacent, colder {@link IHeatHandler}. Hotter neighbours are skipped:
     * they start their own transfer when they simulate, which also stops a transfer from happening twice per tick.
     *
     * @return the summed temperature difference that was transferred, for the transfer-loss readout.
     */
    public double simulateAdjacent() {
        rebuildIfNeeded();
        if (acceptors.isEmpty()) {
            return 0;
        }
        double adjacentTransfer = 0;
        double totalCapacity = data.getTotalHeatCapacity(null);
        double myTemp = data.getTotalTemperature(null);
        for (BlockCapabilityCache<IHeatHandler, @Nullable Direction> cache : acceptors) {
            IHeatHandler sink = cache.getCapability();
            if (sink == null) {
                continue;
            }
            double sinkTemp = sink.getTotalTemperature();
            if (myTemp <= sinkTemp) {
                continue;
            }
            double sinkHeatCapacity = sink.getTotalHeatCapacity();
            double invConduction = sink.getTotalInverseConduction() + data.getTotalInverseConductionCoefficient(null);
            double finalTemp = (myTemp * totalCapacity + sinkTemp * sinkHeatCapacity) / (totalCapacity + sinkHeatCapacity);
            double tempToTransfer = (myTemp - finalTemp) / invConduction;
            double heatToTransfer = tempToTransfer * totalCapacity;
            // Note: handleHeat only queues the transfer; the owning data must flush it with updateHeatCapacitors afterwards
            data.handleHeat(-heatToTransfer, null);
            sink.handleHeat(heatToTransfer);
            adjacentTransfer += tempToTransfer;
        }
        return adjacentTransfer;
    }

    /**
     * Rebuilds the neighbour list if it is stale. Only positions outside the structure that are adjacent to a member tile
     * exposing heat are collected, and duplicates (two member tiles touching the same neighbour) are removed. Positions
     * without a heat handler are watched instead of cached, so they still trigger a rebuild when one shows up later.
     */
    private void rebuildIfNeeded() {
        if (!dirty) {
            return;
        }
        Level world = data.getLevel();
        if (!(world instanceof ServerLevel level)) {
            // Nothing to do while the level is not available; keep the list dirty so it is rebuilt once it is.
            return;
        }
        dirty = false;
        acceptors.clear();
        watchers.clear();
        Set<BlockPos> seen = new HashSet<>();
        for (BlockPos pos : data.locations) {
            BlockEntity tile = WorldUtils.getTileEntity(level, pos);
            if (!(tile instanceof TileEntityMekanism mekTile) || !mekTile.canHandleHeat()) {
                continue;
            }
            for (Direction side : Direction.values()) {
                BlockPos neighborPos = pos.relative(side);
                if (data.isKnownLocation(neighborPos) || !seen.add(neighborPos)) {
                    continue;
                }
                Direction context = side.getOpposite();
                if (level.getCapability(Capabilities.HEAT, neighborPos, context) != null) {
                    acceptors.add(BlockCapabilityCache.create(Capabilities.HEAT, level, neighborPos, context, ALWAYS_VALID, this::invalidate));
                } else {
                    // Nothing to transfer heat to (yet). Watch the position so a machine placed there, or a neighbour
                    // switching heat on for this side, marks the list stale even though there is no cache to invalidate.
                    watchers.watch(level, neighborPos);
                }
            }
        }
    }
}
