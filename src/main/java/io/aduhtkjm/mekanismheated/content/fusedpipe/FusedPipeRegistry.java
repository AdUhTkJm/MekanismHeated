package io.aduhtkjm.mekanismheated.content.fusedpipe;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import io.aduhtkjm.mekanismheated.tile.TileEntityFusedPipe;
import mekanism.common.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * Server-side manager of all fused pipe networks. Mirrors the responsibilities of Mekanism's
 * {@code TransmitterNetworkRegistry}, but for a single unified graph:
 * <ol>
 *     <li>disperse networks that lost a member (redistributing their buffer to the survivors)</li>
 *     <li>assign orphaned nodes by BFS-ing through adjacent fused pipes, merging any networks found</li>
 *     <li>tick every network</li>
 * </ol>
 * All state is server-only; client tiles never register themselves.
 */
public final class FusedPipeRegistry {

    private static final Set<FusedPipeNode> trackedNodes = new ObjectOpenHashSet<>();
    private static final Set<FusedPipeNode> orphanNodes = new ObjectOpenHashSet<>();
    private static final Set<FusedNetwork> networks = new ObjectOpenHashSet<>();
    private static final Set<FusedNetwork> networksToDisperse = new ObjectOpenHashSet<>();

    private FusedPipeRegistry() {
    }

    //Lifecycle API, called from TileEntityFusedPipe (server side only)

    /**
     * Starts tracking a node and queues it to join or form a network.
     */
    public static void trackOrphan(FusedPipeNode node) {
        if (trackedNodes.add(node)) {
            orphanNodes.add(node);
        }
    }

    /**
     * Stops tracking a node. If it was part of a network, that network gets dispersed so the
     * remaining nodes can reform (possibly as multiple networks). On unloads the node first
     * receives its share of the buffer via {@link FusedNetwork#distributeSharesToNodes()}.
     */
    public static void untrack(FusedPipeNode node) {
        trackedNodes.remove(node);
        orphanNodes.remove(node);
        FusedNetwork network = node.getNetwork();
        if (network != null) {
            network.removeNode(node);
            node.setNetwork(null);
            if (network.getNodes().isEmpty()) {
                networks.remove(network);
                network.acceptorCache.clear();
            } else {
                //The graph may be split now; disperse and let the survivors reform
                networksToDisperse.add(network);
            }
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        //Persist all buffers into the tiles before the world saves
        List<FusedNetwork> allNetworks = new ArrayList<>(networks);
        for (FusedNetwork network : allNetworks) {
            network.distributeSharesToNodes();
        }
        reset();
    }

    //Ticking

    public static void onServerTickPost(ServerTickEvent.Post event) {
        if (!event.getServer().tickRateManager().runsNormally()) {
            return;
        }
        disperseNetworks();
        assignOrphans();
        tickNetworks();
    }

    private static void disperseNetworks() {
        if (networksToDisperse.isEmpty()) {
            return;
        }
        List<FusedNetwork> toDisperse = new ArrayList<>(networksToDisperse);
        networksToDisperse.clear();
        for (FusedNetwork network : toDisperse) {
            if (!networks.contains(network)) {
                continue;
            }
            //Hand every valid node its share of the buffer; they will re-absorb it when reforming
            network.distributeSharesToNodes();
            List<FusedPipeNode> members = new ArrayList<>(network.getNodes());
            for (FusedPipeNode node : members) {
                node.setNetwork(null);
                if (node.isValid() && trackedNodes.contains(node)) {
                    orphanNodes.add(node);
                }
            }
            network.getNodes().clear();
            network.acceptorCache.clear();
            networks.remove(network);
        }
    }

    private static void assignOrphans() {
        if (orphanNodes.isEmpty()) {
            return;
        }
        for (FusedPipeNode start : snapshotOrphans()) {
            if (!start.isValid() || !trackedNodes.contains(start) || start.getNetwork() != null) {
                continue;
            }
            assignOrphan(start);
        }
    }

    private static List<FusedPipeNode> snapshotOrphans() {
        return new ArrayList<>(orphanNodes);
    }

    private static void assignOrphan(FusedPipeNode start) {
        OrphanPathFinder finder = new OrphanPathFinder(start);
        finder.find();

        FusedNetwork network = null;
        if (finder.networksFound.size() == 1) {
            network = finder.networksFound.iterator().next();
        } else if (finder.networksFound.size() > 1) {
            //Multiple networks meet at this node; merge them into a fresh one
            network = new FusedNetwork(UUID.randomUUID());
            networks.add(network);
            for (FusedNetwork found : finder.networksFound) {
                network.adoptFrom(found);
                found.acceptorCache.clear();
                networks.remove(found);
            }
        } else {
            network = new FusedNetwork(UUID.randomUUID());
            networks.add(network);
        }

        for (FusedPipeNode connected : finder.connectedNodes) {
            network.addNode(connected);
            orphanNodes.remove(connected);
        }
        network.clampBuffer();
    }

    private static void tickNetworks() {
        if (networks.isEmpty()) {
            return;
        }
        Iterator<FusedNetwork> iterator = networks.iterator();
        while (iterator.hasNext()) {
            FusedNetwork network = iterator.next();
            if (network.getNodes().isEmpty()) {
                iterator.remove();
                continue;
            }
            network.serverTick();
        }
    }

    /**
     * Clears all state; called on server stop.
     */
    private static void reset() {
        trackedNodes.clear();
        orphanNodes.clear();
        networksToDisperse.clear();
        networks.clear();
    }

    /**
     * Flood-fills from an orphan node through adjacent fused pipes. Orphan neighbors are absorbed
     * into {@link #connectedNodes}; already-networked neighbors contribute their network to
     * {@link #networksFound} without being traversed through.
     */
    private static final class OrphanPathFinder {

        private final Set<FusedPipeNode> connectedNodes = new ObjectOpenHashSet<>();
        private final Set<FusedNetwork> networksFound = new ObjectOpenHashSet<>();
        private final Deque<FusedPipeNode> queue = new ArrayDeque<>();

        private OrphanPathFinder(FusedPipeNode start) {
            queue.add(start);
            connectedNodes.add(start);
        }

        private void find() {
            FusedPipeNode currentNode;
            while ((currentNode = queue.poll()) != null) {
                BlockPos pos = currentNode.getBlockPos();
                ServerLevel level = currentNode.getLevel() instanceof ServerLevel serverLevel ? serverLevel : null;
                if (level == null) {
                    continue;
                }
                for (Direction side : Direction.values()) {
                    TileEntityFusedPipe neighborTile = WorldUtils.getTileEntity(TileEntityFusedPipe.class, level, pos.relative(side));
                    if (neighborTile == null || !neighborTile.isLoaded()) {
                        continue;
                    }
                    FusedPipeNode neighbor = neighborTile.getNode();
                    if (!neighbor.isValid()) {
                        continue;
                    }
                    FusedNetwork neighborNetwork = neighbor.getNetwork();
                    if (neighborNetwork != null) {
                        networksFound.add(neighborNetwork);
                    } else if (trackedNodes.contains(neighbor) && connectedNodes.add(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }
        }
    }
}
