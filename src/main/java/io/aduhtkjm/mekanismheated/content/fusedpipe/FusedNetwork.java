package io.aduhtkjm.mekanismheated.content.fusedpipe;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.functions.ConstantPredicates;
import mekanism.api.math.MathUtils;
import mekanism.common.capabilities.energy.VariableCapacityEnergyContainer;
import mekanism.common.content.network.distribution.EnergyAcceptorTarget;
import mekanism.common.util.EmitUtils;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The unified network of a region of fused pipes. Unlike Mekanism's per-type networks there is a
 * single graph for every transmission function; disabled functions simply contribute nothing.
 * <p>
 * Energy (and later fluid/chemical) uses a network-wide buffer whose capacity is the sum of the
 * per-node capacities. Heat and items keep their state on the nodes themselves.
 */
public class FusedNetwork {

    //For emit utils
    private static final Void ENERGY = null;

    private final UUID uuid;
    private final Set<FusedPipeNode> nodes = new ObjectLinkedOpenHashSet<>();
    private final VariableCapacityEnergyContainer energyContainer;
    private final List<IEnergyContainer> energyContainersView;
    public final FusedAcceptorCache acceptorCache = new FusedAcceptorCache();

    public FusedNetwork(UUID uuid) {
        this.uuid = uuid;
        LongSupplier capacity = this::getEnergyCapacity;
        IContentsListener dirtyListener = this::markDirty;
        energyContainer = VariableCapacityEnergyContainer.create(capacity, ConstantPredicates.alwaysTrue(),
              ConstantPredicates.alwaysTrue(), dirtyListener);
        energyContainersView = Collections.singletonList(energyContainer);
    }

    public UUID getUUID() {
        return uuid;
    }

    public Set<FusedPipeNode> getNodes() {
        return nodes;
    }

    //Membership

    /**
     * Adds a node to this network and absorbs its saved share into the buffer.
     */
    public void addNode(FusedPipeNode node) {
        if (nodes.add(node)) {
            node.setNetwork(this);
            long share = node.takeSavedEnergy();
            if (share > 0L) {
                energyContainer.setEnergy(MathUtils.addClamped(energyContainer.getEnergy(), share));
            }
        }
    }

    public void removeNode(FusedPipeNode node) {
        nodes.remove(node);
        acceptorCache.clear();
    }

    /**
     * Moves all nodes and the buffer of another network into this one.
     */
    public void adoptFrom(FusedNetwork other) {
        for (FusedPipeNode node : other.nodes) {
            node.setNetwork(this);
            nodes.add(node);
        }
        other.nodes.clear();
        long theirBuffer = other.getEnergy();
        if (theirBuffer > 0L) {
            energyContainer.setEnergy(MathUtils.addClamped(energyContainer.getEnergy(), theirBuffer));
            other.energyContainer.setEmpty();
        }
    }

    public void clampBuffer() {
        if (!energyContainer.isEmpty() && energyContainer.getEnergy() > getEnergyCapacity()) {
            energyContainer.setEnergy(getEnergyCapacity());
        }
    }

    //Buffer

    public long getEnergyCapacity() {
        long capacity = 0L;
        for (FusedPipeNode node : nodes) {
            capacity = MathUtils.addClamped(capacity, node.getEnergyCapacity());
        }
        return capacity;
    }

    public long getEnergy() {
        return energyContainer.getEnergy();
    }

    @NotNull
    public List<IEnergyContainer> getEnergyContainers(@Nullable Direction side) {
        return energyContainersView;
    }

    /**
     * Distributes the current buffer equally among all nodes so that each tile can persist its
     * share; used when a chunk unloads or the whole network disperses.
     */
    public void distributeSharesToNodes() {
        int count = nodes.size();
        if (count == 0 || energyContainer.isEmpty()) {
            return;
        }
        long total = energyContainer.getEnergy();
        long base = total / count;
        long remainder = total % count;
        int index = 0;
        for (FusedPipeNode node : nodes) {
            long share = base + (index < remainder ? 1L : 0L);
            node.setSavedEnergy(MathUtils.addClamped(node.getSavedEnergy(), share));
            index++;
        }
        energyContainer.setEmpty();
    }

    /**
     * Takes back the distributed shares of every node except the keeper, returning them to the
     * buffer; used when a single chunk unloads so only that node keeps a claim.
     */
    public void reclaimSharesExcept(FusedPipeNode keeper) {
        for (FusedPipeNode node : nodes) {
            if (node != keeper) {
                long share = node.takeSavedEnergy();
                if (share > 0L) {
                    energyContainer.setEnergy(MathUtils.addClamped(energyContainer.getEnergy(), share));
                }
            }
        }
    }

    private void markDirty() {
        //Mark a single tile dirty so that changes to the network buffer are persisted eventually
        for (FusedPipeNode node : nodes) {
            node.getTile().setChanged();
            break;
        }
    }

    //Ticking

    public void serverTick() {
        pullFromAcceptors();
        emitEnergy();
    }

    private void pullFromAcceptors() {
        for (FusedPipeNode node : nodes) {
            ServerLevel level = node.getLevel() instanceof ServerLevel serverLevel ? serverLevel : null;
            if (level == null) {
                continue;
            }
            for (Direction side : Direction.values()) {
                if (!node.canAcceptFrom(side)) {
                    continue;
                }
                long availablePull = Math.min(node.getEnergyPullRate(), energyContainer.getNeeded());
                if (availablePull <= 0L) {
                    continue;
                }
                IStrictEnergyHandler acceptor = acceptorCache.getEnergyAcceptor(level, node.getBlockPos(), side);
                if (acceptor == null) {
                    continue;
                }
                long received = acceptor.extractEnergy(availablePull, Action.SIMULATE);
                if (received > 0L && energyContainer.insert(received, Action.SIMULATE, AutomationType.INTERNAL) == 0L) {
                    //If we received some energy and are able to insert it all, actually transfer it
                    long remainder = energyContainer.insert(received, Action.EXECUTE, AutomationType.INTERNAL);
                    long extracted = acceptor.extractEnergy(received - remainder, Action.EXECUTE);
                    if (extracted > 0L) {
                        energyContainer.insert(extracted, Action.EXECUTE, AutomationType.INTERNAL);
                    }
                }
            }
        }
    }

    private void emitEnergy() {
        if (nodes.isEmpty()) {
            return;
        }
        long energyToSend = energyContainer.getEnergy();
        if (energyToSend <= 0L) {
            return;
        }
        EnergyAcceptorTarget target = null;
        for (FusedPipeNode node : nodes) {
            ServerLevel level = node.getLevel() instanceof ServerLevel serverLevel ? serverLevel : null;
            if (level == null) {
                continue;
            }
            for (Direction side : Direction.values()) {
                if (!node.canSendTo(side)) {
                    continue;
                }
                IStrictEnergyHandler acceptor = acceptorCache.getEnergyAcceptor(level, node.getBlockPos(), side);
                if (acceptor != null && acceptor.insertEnergy(energyToSend, Action.SIMULATE) < energyToSend) {
                    if (target == null) {
                        target = new EnergyAcceptorTarget(nodes.size());
                    }
                    target.addHandler(acceptor);
                }
            }
        }
        if (target != null && target.getHandlerCount() > 0) {
            long sent = EmitUtils.sendToAcceptors(target, energyToSend, ENERGY);
            if (sent > 0L) {
                energyContainer.extract(sent, Action.EXECUTE, AutomationType.INTERNAL);
            }
        }
    }

    @Override
    public String toString() {
        return "FusedNetwork{" + uuid + ", " + nodes.size() + " nodes}";
    }
}
