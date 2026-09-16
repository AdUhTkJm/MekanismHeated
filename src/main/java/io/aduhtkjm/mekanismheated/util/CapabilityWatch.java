package io.aduhtkjm.mekanismheated.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.ICapabilityInvalidationListener;

/**
 * Keeps raw capability invalidation listeners registered for a set of block positions.
 *
 * <p>A {@link BlockCapabilityCache} only tells a listener about changes at a position if the cache
 * was created while <em>something</em> already exposed the queried capability there — NeoForge only
 * invokes the listeners of caches which have queried the position since the last invalidation. A
 * position that currently exposes nothing therefore has nobody listening, and a capability
 * <em>appearing</em> there later is never noticed. That happens in two situations:</p>
 * <ul>
 *     <li>a machine is placed (its block entity calls {@code clearRemoved()}, which invalidates the
 *     capabilities at its position), and</li>
 *     <li>a machine reconfigures which capabilities it exposes on a side. This is what happens when
 *     the player toggles a side in the GUI: no block changes, but the machine calls
 *     {@code Level#invalidateCapabilities(BlockPos)} for its own position.</li>
 * </ul>
 *
 * <p>This helper registers a listener that does not care <em>which</em> capability changed, so the
 * owner can rebuild its cached neighbour list whenever anything around it may have become relevant.
 * Listeners are held by the level with a weak reference, so this instance keeps a strong reference
 * to every registration; {@link #clear()} drops them again when the position set is rebuilt.</p>
 *
 * <p><strong>The callback must not touch the level.</strong> It can run while a chunk is being
 * unloaded or while a block entity is in the middle of being removed, so it should do nothing more
 * than flag the owner's state as dirty.</p>
 */
public final class CapabilityWatch {

    private final Runnable onInvalidated;
    /**
     * The positions currently watched. Doubles as the duplicate guard within one build cycle, so
     * several owners sharing a neighbour cannot register listeners for it twice.
     */
    private final Set<BlockPos> watchedPositions = new HashSet<>();
    /**
     * Strong references to the live registrations. The level only holds listeners weakly, so without
     * keeping our own reference they would be collected — and silently stop firing — as soon as the
     * garbage collector runs. Dropped again by {@link #clear()} when the position set is rebuilt.
     */
    private final List<Registration> registrations = new ArrayList<>();

    /**
     * @param onInvalidated invoked whenever one of the watched positions may have changed its
     *                      capabilities. Called at most once per position between rebuilds.
     */
    public CapabilityWatch(Runnable onInvalidated) {
        this.onInvalidated = onInvalidated;
    }

    /**
     * Starts watching the given position. Watching the same position twice within one build cycle is
     * a no-op, so several owners sharing a neighbour cannot register duplicate listeners.
     */
    public void watch(ServerLevel level, BlockPos pos) {
        if (!watchedPositions.add(pos)) {
            return;
        }
        Registration registration = new Registration();
        registrations.add(registration);
        level.registerCapabilityListener(pos, registration);
    }

    /**
     * Stops watching every registered position. The level has no way to unregister a listener, but it
     * drops any listener whose callback returns {@code false}, so superseded registrations are disarmed
     * and then released here. This has to be called before re-arming to avoid duplicate registrations.
     */
    public void clear() {
        for (Registration registration : registrations) {
            // The level only notices a disarmed registration once it invalidates that position again.
            // Until then it may still call us, so make sure a stale registration cannot flag the owner
            // as dirty after the list it belonged to has already been rebuilt.
            registration.disarmed = true;
        }
        registrations.clear();
        watchedPositions.clear();
    }

    /**
     * A single registration, kept alive by {@link #registrations}.
     */
    private final class Registration implements ICapabilityInvalidationListener {

        private boolean disarmed;

        @Override
        public boolean onInvalidate() {
            if (disarmed) {
                return false;
            }
            onInvalidated.run();
            // The notification already flagged our data as stale, and whoever rebuilds will arm a
            // fresh watcher. Dropping this registration keeps stale listeners from piling up in the
            // level while the rebuild is pending.
            return false;
        }
    }
}
