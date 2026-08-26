package io.aduhtkjm.mekanismheated.content.fusedpipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.functions.ConstantPredicates;
import mekanism.api.math.MathUtils;
import mekanism.common.capabilities.chemical.VariableCapacityChemicalTank;
import mekanism.common.capabilities.energy.VariableCapacityEnergyContainer;
import mekanism.common.capabilities.fluid.VariableCapacityFluidTank;
import mekanism.common.content.network.distribution.ChemicalHandlerTarget;
import mekanism.common.content.network.distribution.EnergyAcceptorTarget;
import mekanism.common.content.network.distribution.FluidHandlerTarget;
import mekanism.common.util.ChemicalUtil;
import mekanism.common.util.EmitUtils;
import mekanism.common.util.FluidUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The unified network of a region of fused pipes. Unlike Mekanism's per-type networks there is a
 * single graph for every transmission function; disabled functions simply contribute nothing.
 * <p>
 * Energy, fluid and chemicals use network-wide buffers whose capacity is the sum of the per-node
 * capacities; both tanks hold at most a single type at a time (vanilla transmitter parity).
 * Heat and items keep their state on the nodes themselves.
 */
public class FusedNetwork {

    //For emit utils
    private static final Void ENERGY = null;

    private final UUID uuid;
    private final Set<FusedPipeNode> nodes = new ObjectLinkedOpenHashSet<>();
    private final VariableCapacityEnergyContainer energyContainer;
    private final List<IEnergyContainer> energyContainersView;
    public final VariableCapacityFluidTank fluidTank;
    private final List<IExtendedFluidTank> fluidTanksView;
    public final IChemicalTank chemicalTank;
    private final List<IChemicalTank> chemicalTanksView;
    public final FusedAcceptorCache acceptorCache = new FusedAcceptorCache();

    public FusedNetwork(UUID uuid) {
        this.uuid = uuid;
        LongSupplier capacity = this::getEnergyCapacity;
        IntSupplier fluidCapacity = this::getFluidCapacityAsInt;
        LongSupplier chemicalCapacity = this::getChemicalCapacity;
        IContentsListener dirtyListener = this::markDirty;
        energyContainer = VariableCapacityEnergyContainer.create(capacity, ConstantPredicates.alwaysTrue(),
              ConstantPredicates.alwaysTrue(), dirtyListener);
        energyContainersView = Collections.singletonList(energyContainer);
        fluidTank = VariableCapacityFluidTank.create(fluidCapacity, ConstantPredicates.alwaysTrueBi(),
              ConstantPredicates.alwaysTrueBi(), ConstantPredicates.alwaysTrue(), dirtyListener);
        fluidTanksView = Collections.singletonList(fluidTank);
        chemicalTank = VariableCapacityChemicalTank.createAllValid(chemicalCapacity, dirtyListener);
        chemicalTanksView = Collections.singletonList(chemicalTank);
    }

    public UUID getUUID() {
        return uuid;
    }

    public Set<FusedPipeNode> getNodes() {
        return nodes;
    }

    //Membership

    /**
     * Adds a node to this network and absorbs its saved shares into the buffers. Shares of
     * functions that are disabled on the node stay parked on it until the function is enabled.
     */
    public void addNode(FusedPipeNode node) {
        if (nodes.add(node)) {
            node.setNetwork(this);
            acceptorCache.invalidate();
            if (node.isEnabled(FusedFunction.ENERGY)) {
                long share = node.takeSavedEnergy();
                if (share > 0L) {
                    energyContainer.setEnergy(MathUtils.addClamped(energyContainer.getEnergy(), share));
                }
            }
            if (node.isEnabled(FusedFunction.FLUID)) {
                absorbFluid(node.takeSavedFluid());
            }
            if (node.isEnabled(FusedFunction.CHEMICAL)) {
                absorbChemical(node.takeSavedChemical());
            }
        }
    }

    public void removeNode(FusedPipeNode node) {
        nodes.remove(node);
        acceptorCache.invalidate();
    }

    /**
     * Moves all nodes and the buffers of another network into this one.
     */
    public void adoptFrom(FusedNetwork other) {
        for (FusedPipeNode node : other.nodes) {
            node.setNetwork(this);
            nodes.add(node);
        }
        other.nodes.clear();
        acceptorCache.invalidate();
        long theirEnergy = other.getEnergy();
        if (theirEnergy > 0L) {
            energyContainer.setEnergy(MathUtils.addClamped(energyContainer.getEnergy(), theirEnergy));
            other.energyContainer.setEmpty();
        }
        absorbFluid(other.fluidTank.getFluid());
        other.fluidTank.setEmpty();
        absorbChemical(other.chemicalTank.getStack());
        other.chemicalTank.setEmpty();
    }

    public void clampBuffer() {
        if (!energyContainer.isEmpty() && energyContainer.getEnergy() > getEnergyCapacity()) {
            energyContainer.setEnergy(getEnergyCapacity());
        }
        if (!fluidTank.isEmpty()) {
            int capacity = getFluidCapacityAsInt();
            if (fluidTank.getFluidAmount() > capacity) {
                MekanismUtils.logMismatchedStackSize(fluidTank.setStackSize(capacity, Action.EXECUTE), capacity);
            }
        }
        if (!chemicalTank.isEmpty()) {
            long capacity = getChemicalCapacity();
            if (chemicalTank.getStored() > capacity) {
                MekanismUtils.logMismatchedStackSize(chemicalTank.setStackSize(capacity, Action.EXECUTE), capacity);
            }
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

    public int getFluidCapacityAsInt() {
        return MathUtils.clampToInt(getFluidCapacity());
    }

    public long getFluidCapacity() {
        long capacity = 0L;
        for (FusedPipeNode node : nodes) {
            capacity = MathUtils.addClamped(capacity, node.getFluidCapacity());
        }
        return capacity;
    }

    @NotNull
    public List<IExtendedFluidTank> getFluidTanks(@Nullable Direction side) {
        return fluidTanksView;
    }

    public long getChemicalCapacity() {
        long capacity = 0L;
        for (FusedPipeNode node : nodes) {
            capacity = MathUtils.addClamped(capacity, node.getChemicalCapacity());
        }
        return capacity;
    }

    @NotNull
    public List<IChemicalTank> getChemicalTanks(@Nullable Direction side) {
        return chemicalTanksView;
    }

    /**
     * Distributes the current buffers equally among the nodes that have each function enabled, so
     * that every tile can persist its share; used when chunks unload or a network disperses.
     */
    public void distributeSharesToNodes() {
        if (nodes.isEmpty()) {
            return;
        }
        //Energy
        List<FusedPipeNode> energyEligible = eligibleNodes(FusedFunction.ENERGY);
        if (!energyContainer.isEmpty() && !energyEligible.isEmpty()) {
            long total = energyContainer.getEnergy();
            long base = total / energyEligible.size();
            long remainder = total % energyEligible.size();
            for (int index = 0; index < energyEligible.size(); index++) {
                long share = base + (index < remainder ? 1L : 0L);
                FusedPipeNode node = energyEligible.get(index);
                node.setSavedEnergy(MathUtils.addClamped(node.getSavedEnergy(), share));
            }
            energyContainer.setEmpty();
        }
        //Fluid
        List<FusedPipeNode> fluidEligible = eligibleNodes(FusedFunction.FLUID);
        if (!fluidTank.isEmpty() && !fluidEligible.isEmpty()) {
            FluidStack fluid = fluidTank.getFluid();
            long total = fluid.getAmount();
            long base = total / fluidEligible.size();
            long remainder = total % fluidEligible.size();
            for (int index = 0; index < fluidEligible.size(); index++) {
                long share = base + (index < remainder ? 1L : 0L);
                if (share > 0L) {
                    FusedPipeNode node = fluidEligible.get(index);
                    //Fluid amounts are int-bounded so the share always fits
                    FluidStack part = fluid.copyWithAmount((int) share);
                    node.setSavedFluid(node.getSavedFluid().isEmpty() ? part : sumFluids(node.getSavedFluid(), part));
                }
            }
            fluidTank.setEmpty();
        }
        //Chemical
        List<FusedPipeNode> chemicalEligible = eligibleNodes(FusedFunction.CHEMICAL);
        if (!chemicalTank.isEmpty() && !chemicalEligible.isEmpty()) {
            ChemicalStack chemical = chemicalTank.getStack();
            long total = chemical.getAmount();
            long base = total / chemicalEligible.size();
            long remainder = total % chemicalEligible.size();
            for (int index = 0; index < chemicalEligible.size(); index++) {
                long share = base + (index < remainder ? 1L : 0L);
                if (share > 0L) {
                    FusedPipeNode node = chemicalEligible.get(index);
                    ChemicalStack part = chemical.copyWithAmount(share);
                    node.setSavedChemical(node.getSavedChemical().isEmpty() ? part : sumChemicals(node.getSavedChemical(), part));
                }
            }
            chemicalTank.setEmpty();
        }
    }

    @NotNull
    private List<FusedPipeNode> eligibleNodes(FusedFunction function) {
        List<FusedPipeNode> eligible = new ArrayList<>();
        for (FusedPipeNode node : nodes) {
            if (node.isEnabled(function)) {
                eligible.add(node);
            }
        }
        return eligible;
    }

    @NotNull
    private static FluidStack sumFluids(@NotNull FluidStack a, @NotNull FluidStack b) {
        return a.copyWithAmount(a.getAmount() + b.getAmount());
    }

    @NotNull
    private static ChemicalStack sumChemicals(@NotNull ChemicalStack a, @NotNull ChemicalStack b) {
        return a.copyWithAmount(a.getAmount() + b.getAmount());
    }

    /**
     * Takes back the distributed shares of every node except the keeper, returning them to the
     * buffers; used when a single chunk unloads so only that node keeps a claim.
     */
    public void reclaimSharesExcept(FusedPipeNode keeper) {
        for (FusedPipeNode node : nodes) {
            if (node == keeper) {
                continue;
            }
            long share = node.takeSavedEnergy();
            if (share > 0L) {
                energyContainer.setEnergy(MathUtils.addClamped(energyContainer.getEnergy(), share));
            }
            absorbFluid(node.takeSavedFluid());
            absorbChemical(node.takeSavedChemical());
        }
    }

    private void absorbFluid(@NotNull FluidStack fluid) {
        if (!fluid.isEmpty()) {
            if (fluidTank.isEmpty()) {
                fluidTank.setStack(fluid.copy());
            } else if (fluidTank.isFluidEqual(fluid)) {
                int amount = fluid.getAmount();
                MekanismUtils.logMismatchedStackSize(fluidTank.growStack(amount, Action.EXECUTE), amount);
            }
            //Mismatching fluids cannot happen for shares that came out of this same network;
            // if they somehow do, the content is dropped like vanilla does on incompatible merges
        }
    }

    private void absorbChemical(@NotNull ChemicalStack chemical) {
        if (!chemical.isEmpty()) {
            if (chemicalTank.isEmpty()) {
                chemicalTank.setStack(chemical.copy());
            } else if (chemicalTank.isTypeEqual(chemical)) {
                long amount = chemical.getAmount();
                MekanismUtils.logMismatchedStackSize(chemicalTank.growStack(amount, Action.EXECUTE), amount);
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
        acceptorCache.rebuildIfNeeded(nodes);
        pullEnergy();
        pullFluids();
        pullChemicals();
        emitEnergy();
        emitFluids();
        emitChemicals();
    }

    private void pullEnergy() {
        for (FusedAcceptorCache.EnergySource entry : acceptorCache.getEnergySources()) {
            long availablePull = Math.min(entry.origin().getEnergyPullRate(), energyContainer.getNeeded());
            if (availablePull <= 0L) {
                continue;
            }
            IStrictEnergyHandler acceptor = entry.resolve();
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

    private void pullFluids() {
        for (FusedAcceptorCache.TankSource<IFluidHandler> entry : acceptorCache.getFluidSources()) {
            int availablePull = Math.min(entry.origin().getFluidPullRate(), fluidTank.getNeeded());
            if (availablePull <= 0) {
                continue;
            }
            IFluidHandler acceptor = entry.resolve();
            if (acceptor == null) {
                continue;
            }
            //If the network holds a fluid already, only try to drain that same type
            FluidStack bufferWithFallback = fluidTank.getFluid();
            FluidStack received;
            if (bufferWithFallback.isEmpty()) {
                received = acceptor.drain(availablePull, IFluidHandler.FluidAction.SIMULATE);
            } else {
                received = acceptor.drain(bufferWithFallback.copyWithAmount(availablePull), IFluidHandler.FluidAction.SIMULATE);
            }
            if (!received.isEmpty() && fluidTank.insert(received, Action.SIMULATE, AutomationType.INTERNAL).isEmpty()) {
                //We are able to insert it all, actually transfer it
                fluidTank.insert(acceptor.drain(received.copy(), IFluidHandler.FluidAction.EXECUTE), Action.EXECUTE, AutomationType.INTERNAL);
            }
        }
    }

    private void pullChemicals() {
        for (FusedAcceptorCache.TankSource<IChemicalHandler> entry : acceptorCache.getChemicalSources()) {
            long availablePull = Math.min(entry.origin().getChemicalPullRate(), chemicalTank.getNeeded());
            if (availablePull <= 0L) {
                continue;
            }
            IChemicalHandler acceptor = entry.resolve();
            if (acceptor == null) {
                continue;
            }
            ChemicalStack bufferWithFallback = chemicalTank.getStack();
            ChemicalStack received;
            if (bufferWithFallback.isEmpty()) {
                received = acceptor.extractChemical(availablePull, Action.SIMULATE);
            } else {
                received = acceptor.extractChemical(bufferWithFallback.copyWithAmount(availablePull), Action.SIMULATE);
            }
            if (!received.isEmpty() && chemicalTank.insert(received, Action.SIMULATE, AutomationType.INTERNAL).isEmpty()) {
                chemicalTank.insert(acceptor.extractChemical(received, Action.EXECUTE), Action.EXECUTE, AutomationType.INTERNAL);
            }
        }
    }

    private void emitEnergy() {
        long energyToSend = energyContainer.getEnergy();
        if (energyToSend <= 0L) {
            return;
        }
        List<FusedAcceptorCache.EnergyTarget> targets = acceptorCache.getEnergyTargets();
        EnergyAcceptorTarget target = null;
        for (FusedAcceptorCache.EnergyTarget entry : targets) {
            IStrictEnergyHandler acceptor = entry.resolve();
            if (acceptor != null && acceptor.insertEnergy(energyToSend, Action.SIMULATE) < energyToSend) {
                if (target == null) {
                    target = new EnergyAcceptorTarget(targets.size());
                }
                target.addHandler(acceptor);
            }
        }
        if (target != null && target.getHandlerCount() > 0) {
            long sent = EmitUtils.sendToAcceptors(target, energyToSend, ENERGY);
            if (sent > 0L) {
                energyContainer.extract(sent, Action.EXECUTE, AutomationType.INTERNAL);
            }
        }
    }

    private void emitFluids() {
        if (fluidTank.isEmpty()) {
            return;
        }
        FluidStack fluidToSend = fluidTank.getFluid();
        List<FusedAcceptorCache.TankTarget<IFluidHandler>> candidates = acceptorCache.getFluidTargets();
        FluidHandlerTarget target = null;
        for (FusedAcceptorCache.TankTarget<IFluidHandler> entry : candidates) {
            IFluidHandler acceptor = entry.resolve();
            if (acceptor != null && FluidUtils.canFill(acceptor, fluidToSend)) {
                if (target == null) {
                    target = new FluidHandlerTarget(candidates.size());
                }
                target.addHandler(acceptor);
            }
        }
        if (target != null && target.getHandlerCount() > 0) {
            int sent = EmitUtils.sendToAcceptors(target, fluidToSend.getAmount(), fluidToSend);
            if (sent > 0) {
                MekanismUtils.logMismatchedStackSize(fluidTank.shrinkStack(sent, Action.EXECUTE), sent);
            }
        }
    }

    private void emitChemicals() {
        if (chemicalTank.isEmpty()) {
            return;
        }
        ChemicalStack chemicalToSend = chemicalTank.getStack();
        List<FusedAcceptorCache.TankTarget<IChemicalHandler>> candidates = acceptorCache.getChemicalTargets();
        ChemicalHandlerTarget target = null;
        for (FusedAcceptorCache.TankTarget<IChemicalHandler> entry : candidates) {
            IChemicalHandler acceptor = entry.resolve();
            if (acceptor != null && ChemicalUtil.canInsert(acceptor, chemicalToSend)) {
                if (target == null) {
                    target = new ChemicalHandlerTarget(candidates.size());
                }
                target.addHandler(acceptor);
            }
        }
        if (target != null && target.getHandlerCount() > 0) {
            long sent = EmitUtils.sendToAcceptors(target, chemicalToSend.getAmount(), chemicalToSend);
            if (sent > 0L) {
                MekanismUtils.logMismatchedStackSize(chemicalTank.shrinkStack(sent, Action.EXECUTE), sent);
            }
        }
    }

    @Override
    public String toString() {
        return "FusedNetwork{" + uuid + ", " + nodes.size() + " nodes}";
    }
}
