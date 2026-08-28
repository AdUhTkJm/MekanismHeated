package io.aduhtkjm.mekanismheated.content.fusedpipe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

import io.aduhtkjm.mekanismheated.Mod;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.SerializationConstants;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.energy.IEnergyContainer;
import mekanism.api.energy.IStrictEnergyHandler;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.api.functions.ConstantPredicates;
import mekanism.api.heat.HeatAPI;
import mekanism.api.heat.IHeatCapacitor;
import mekanism.api.heat.IHeatHandler;
import mekanism.api.math.MathUtils;
import mekanism.common.capabilities.chemical.VariableCapacityChemicalTank;
import mekanism.common.capabilities.energy.VariableCapacityEnergyContainer;
import mekanism.common.capabilities.fluid.VariableCapacityFluidTank;
import mekanism.common.capabilities.heat.VariableHeatCapacitor;
import mekanism.common.content.network.distribution.ChemicalHandlerTarget;
import mekanism.common.content.network.distribution.EnergyAcceptorTarget;
import mekanism.common.content.network.distribution.FluidHandlerTarget;
import mekanism.common.util.ChemicalUtil;
import mekanism.common.util.EmitUtils;
import mekanism.common.util.FluidUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The unified network of a region of fused pipes. Unlike Mekanism's per-type networks there is a
 * single graph for every transmission function; disabled functions simply contribute nothing.
 * <p>
 * Energy, fluid, chemicals and heat use network-wide buffers whose capacity is the sum of the
 * per-node capacities; tanks hold at most a single type at a time (vanilla transmitter parity).
 * Items keep their state in the network item buffer.
 * <p>
 * Persistence is handled by {@link FusedNetworkSavedData}: the full buffer state is serialised
 * once per network (not per node) into world-level saved data, keyed by the network's UUID.
 * Each tile entity only persists its network UUID. On load, the first node to join the network
 * restores the buffers from saved data. Per-node shares are only used during network dispersal
 * and chunk unloads where the network graph must be split.
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
    private final VariableHeatCapacitor heatCapacitor;
    private final List<IHeatCapacitor> heatCapacitorsView;
    private final List<ItemStack> itemBuffer = new ArrayList<>();
    private int itemCount;
    private int itemBufferCapacity;
    private boolean itemCapacityDirty = true;
    private final ItemHandler itemHandler = new ItemHandler();
    public final FusedAcceptorCache acceptorCache = new FusedAcceptorCache();
    private long lastSavedTime = -1;

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
        heatCapacitor = VariableHeatCapacitor.create(0,
              this::getTotalHeatConduction, this::getTotalHeatInsulation,
              () -> (double) HeatAPI.AMBIENT_TEMP, dirtyListener);
        heatCapacitorsView = Collections.singletonList(heatCapacitor);
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
     * <p>
     * When the very first node joins, the network attempts to restore its buffer state from
     * the world-level {@link FusedNetworkSavedData}.
     */
    public void addNode(FusedPipeNode node) {
        if (nodes.add(node)) {
            node.setNetwork(this);
            acceptorCache.invalidate();
            itemCapacityDirty = true;
            //First node: try to restore from saved data
            if (nodes.size() == 1) {
                Level level = node.getLevel();
                if (level instanceof ServerLevel serverLevel) {
                    loadFromSavedData(serverLevel, level.registryAccess());
                }
            }
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
            if (node.isEnabled(FusedFunction.HEAT)) {
                double share = node.takeSavedHeat();
                if (share > 0) {
                    heatCapacitor.handleHeat(share);
                }
            }
            if (node.isEnabled(FusedFunction.ITEM)) {
                for (ItemStack stack : node.takeSavedItems()) {
                    insertIntoBuffer(stack);
                }
            }
            updateHeatCapacity();
        }
    }

    public void removeNode(FusedPipeNode node) {
        nodes.remove(node);
        acceptorCache.invalidate();
        itemCapacityDirty = true;
        updateHeatCapacity();
    }

    /**
     * Called after one or more pipes in this network were upgraded in place with an alloy, so
     * network-wide cached values (heat capacity, item buffer capacity) are recomputed from the
     * nodes' new tiers. The energy/fluid/chemical container capacities are lazy and pick the new
     * tiers up on their own.
     */
    public void onPipeUpgraded() {
        itemCapacityDirty = true;
        updateHeatCapacity();
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
        itemCapacityDirty = true;
        long theirEnergy = other.getEnergy();
        if (theirEnergy > 0L) {
            energyContainer.setEnergy(MathUtils.addClamped(energyContainer.getEnergy(), theirEnergy));
            other.energyContainer.setEmpty();
        }
        absorbFluid(other.fluidTank.getFluid());
        other.fluidTank.setEmpty();
        absorbChemical(other.chemicalTank.getStack());
        other.chemicalTank.setEmpty();
        double theirHeat = other.heatCapacitor.getHeat();
        if (theirHeat > 0) {
            heatCapacitor.handleHeat(theirHeat);
            other.heatCapacitor.setHeat(0);
        }
        for (ItemStack stack : other.itemBuffer) {
            insertIntoBuffer(stack);
        }
        other.itemBuffer.clear();
        other.itemCount = 0;
        updateHeatCapacity();
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
        if (heatCapacitor.getHeat() > getTotalHeatCapacity()) {
            heatCapacitor.setHeat(getTotalHeatCapacity());
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

    public double getTotalHeatCapacity() {
        double capacity = 0;
        for (FusedPipeNode node : nodes) {
            capacity += node.getHeatCapacity();
        }
        return capacity;
    }

    private double getTotalHeatConduction() {
        double max = 0;
        for (FusedPipeNode node : nodes) {
            max = Math.max(max, node.getHeatConduction());
        }
        return max;
    }

    private double getTotalHeatInsulation() {
        double max = 0;
        for (FusedPipeNode node : nodes) {
            max = Math.max(max, node.getHeatInsulation());
        }
        return max;
    }

    private void updateHeatCapacity() {
        heatCapacitor.setHeatCapacity(getTotalHeatCapacity(), false);
    }

    public double getHeat() {
        return heatCapacitor.getHeat();
    }

    @NotNull
    public List<IHeatCapacitor> getHeatCapacitors(@Nullable Direction side) {
        return heatCapacitorsView;
    }

    /**
     * Serialises the network's buffer state into the world-level {@link FusedNetworkSavedData},
     * keyed by this network's UUID. Called during background saves so that tile entities only
     * need to persist their network UUID rather than distributing shares to every node.
     * The {@code lastSavedTime} guard ensures this only happens once per game tick even
     * if multiple tiles in the same network call it.
     */
    public void saveToSavedData(@NotNull ServerLevel level, @NotNull HolderLookup.Provider provider) {
        long now = level.getGameTime();
        if (now == lastSavedTime) {
            return;
        }
        lastSavedTime = now;
        CompoundTag tag = new CompoundTag();
        //Energy
        tag.putLong(SerializationConstants.ENERGY, energyContainer.getEnergy());
        //Fluid
        if (!fluidTank.isEmpty()) {
            tag.put(SerializationConstants.FLUID, fluidTank.getFluid().save(provider));
        }
        //Chemical
        if (!chemicalTank.isEmpty()) {
            tag.put(SerializationConstants.BOXED_CHEMICAL, chemicalTank.getStack().save(provider));
        }
        //Heat
        if (heatCapacitor.getHeat() > 0) {
            tag.putDouble(SerializationConstants.HEAT_STORED, heatCapacitor.getHeat());
        }
        //Items
        if (!itemBuffer.isEmpty()) {
            ListTag itemTag = new ListTag();
            for (ItemStack stack : itemBuffer) {
                itemTag.add(stack.save(provider));
            }
            tag.put("Items", itemTag);
        }
        Mod.LOGGER.debug("network saved: {}", tag);
        FusedNetworkSavedData.get(level).putNetwork(uuid, tag);
    }

    /**
     * Restores the network's buffer state from the world-level {@link FusedNetworkSavedData}.
     * If no saved data exists for this UUID, the buffers remain empty.
     */
    public void loadFromSavedData(@NotNull ServerLevel level, @NotNull HolderLookup.Provider provider) {
        CompoundTag tag = FusedNetworkSavedData.get(level).consumeNetwork(uuid);
        Mod.LOGGER.debug("network loaded: {}", tag);
        if (tag == null) {
            return;
        }
        //Energy
        energyContainer.setEnergy(tag.getLong(SerializationConstants.ENERGY));
        //Fluid
        if (tag.contains(SerializationConstants.FLUID, Tag.TAG_COMPOUND)) {
            fluidTank.setStack(FluidStack.parseOptional(provider, tag.getCompound(SerializationConstants.FLUID)));
        }
        //Chemical
        if (tag.contains(SerializationConstants.BOXED_CHEMICAL, Tag.TAG_COMPOUND)) {
            chemicalTank.setStack(ChemicalStack.parseOptional(provider, tag.getCompound(SerializationConstants.BOXED_CHEMICAL)));
        }
        //Heat
        if (tag.contains(SerializationConstants.HEAT_STORED, Tag.TAG_DOUBLE)) {
            heatCapacitor.setHeat(tag.getDouble(SerializationConstants.HEAT_STORED));
        }
        //Items
        itemBuffer.clear();
        itemCount = 0;
        if (tag.contains("Items", Tag.TAG_LIST)) {
            ListTag itemTag = tag.getList("Items", Tag.TAG_COMPOUND);
            for (int i = 0; i < itemTag.size(); i++) {
                ItemStack stack = ItemStack.parseOptional(provider, itemTag.getCompound(i));
                if (!stack.isEmpty()) {
                    itemBuffer.add(stack);
                    itemCount += stack.getCount();
                }
            }
        }
    }

    /**
     * Removes this network's entry from the world-level {@link FusedNetworkSavedData}.
     */
    public void removeFromSavedData(@NotNull ServerLevel level) {
        FusedNetworkSavedData.get(level).removeNetwork(uuid);
    }

    /**
     * Distributes the current buffers equally among the nodes that have each function enabled and
     * empties the network buffers; used when chunks unload or a network disperses.
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
                energyEligible.get(index).setSavedEnergy(share);
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
                    fluidEligible.get(index).setSavedFluid(fluid.copyWithAmount((int) share));
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
                    chemicalEligible.get(index).setSavedChemical(chemical.copyWithAmount(share));
                }
            }
            chemicalTank.setEmpty();
        }
        //Heat
        List<FusedPipeNode> heatEligible = eligibleNodes(FusedFunction.HEAT);
        if (heatCapacitor.getHeat() > 0 && !heatEligible.isEmpty()) {
            double total = heatCapacitor.getHeat();
            double base = total / heatEligible.size();
            for (FusedPipeNode node : heatEligible) {
                node.setSavedHeat(base);
            }
            heatCapacitor.setHeat(0);
        }
        //Items
        List<FusedPipeNode> itemEligible = eligibleNodes(FusedFunction.ITEM);
        if (!itemBuffer.isEmpty() && !itemEligible.isEmpty()) {
            for (FusedPipeNode node : itemEligible) {
                node.setSavedItems(new ArrayList<>());
            }
            for (int index = 0; index < itemBuffer.size(); index++) {
                FusedPipeNode node = itemEligible.get(index % itemEligible.size());
                node.getSavedItems().add(itemBuffer.get(index));
            }
            itemBuffer.clear();
            itemCount = 0;
        }
    }

    /**
     * Items transfer instantly — no travel speed. Pull from PULL sources into the buffer, then
     * emit from the buffer to NORMAL/PUSH targets. Persistence is handled by
     * {@link FusedNetworkSavedData} during background saves.
     */
    public IItemHandler getItemHandler() {
        return itemHandler;
    }

    private int getItemBufferCapacity() {
        if (itemCapacityDirty) {
            itemBufferCapacity = 0;
            for (FusedAcceptorCache.TankSource<IItemHandler> entry : acceptorCache.getItemSources()) {
                itemBufferCapacity += entry.origin().getItemPullAmount();
            }
            itemCapacityDirty = false;
        }
        return itemBufferCapacity;
    }

    private void pullItems() {
        for (FusedAcceptorCache.TankSource<IItemHandler> entry : acceptorCache.getItemSources()) {
            int pullRate = entry.origin().getItemPullAmount();
            if (pullRate <= 0) {
                continue;
            }
            IItemHandler source = entry.resolve();
            if (source == null) {
                continue;
            }
            for (int slot = 0; slot < source.getSlots(); slot++) {
                if (pullRate <= 0) {
                    break;
                }
                int capacity = getItemBufferCapacity();
                if (itemCount >= capacity) {
                    break;
                }
                int space = capacity - itemCount;
                ItemStack stackInSlot = source.getStackInSlot(slot);
                if (stackInSlot.isEmpty()) {
                    continue;
                }
                int extract = Math.min(pullRate, Math.min(space, stackInSlot.getMaxStackSize()));
                ItemStack extracted = source.extractItem(slot, extract, true);
                if (extracted.isEmpty()) {
                    continue;
                }
                int actuallyExtracted = extracted.getCount();
                source.extractItem(slot, actuallyExtracted, false);
                insertIntoBuffer(stackInSlot.copyWithCount(actuallyExtracted));
                pullRate -= actuallyExtracted;
            }
        }
    }

    private void emitItems() {
        if (itemBuffer.isEmpty()) {
            return;
        }
        List<FusedAcceptorCache.TankTarget<IItemHandler>> targets = acceptorCache.getItemTargets();
        Iterator<ItemStack> iterator = itemBuffer.iterator();
        while (iterator.hasNext()) {
            ItemStack stack = iterator.next();
            if (stack.isEmpty()) {
                iterator.remove();
                continue;
            }
            for (FusedAcceptorCache.TankTarget<IItemHandler> entry : targets) {
                IItemHandler acceptor = entry.resolve();
                if (acceptor == null || stack.isEmpty()) {
                    continue;
                }
                for (int slot = 0; slot < acceptor.getSlots(); slot++) {
                    if (stack.isEmpty()) {
                        break;
                    }
                    ItemStack remaining = acceptor.insertItem(slot, stack, true);
                    int accepted = stack.getCount() - remaining.getCount();
                    if (accepted > 0) {
                        ItemStack toInsert = stack.copyWithCount(accepted);
                        acceptor.insertItem(slot, toInsert, false);
                        stack.shrink(accepted);
                        itemCount -= accepted;
                    }
                }
            }
            if (stack.isEmpty()) {
                iterator.remove();
            }
        }
    }

    /**
     * Returns the remaining part of the stack that isn't inserted.
     */
    private ItemStack insertIntoBuffer(@NotNull ItemStack stack) {
        int capacity = getItemBufferCapacity();
        if (itemCount >= capacity) {
            return stack;
        }
        int remaining = capacity - itemCount;
        //Try to merge with existing stacks first
        for (ItemStack existing : itemBuffer) {
            if (remaining <= 0) {
                break;
            }
            if (ItemStack.isSameItemSameComponents(existing, stack) && existing.getCount() < existing.getMaxStackSize()) {
                int space = Math.min(existing.getMaxStackSize() - existing.getCount(), remaining);
                int toAdd = Math.min(space, stack.getCount());
                existing.grow(toAdd);
                stack.shrink(toAdd);
                remaining -= toAdd;
                itemCount += toAdd;
                if (stack.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }
        }
        //Add as new stack
        if (!stack.isEmpty() && remaining > 0) {
            int toAdd = Math.min(remaining, stack.getCount());
            if (toAdd < stack.getCount()) {
                itemBuffer.add(stack.copyWithCount(toAdd));
            } else {
                itemBuffer.add(stack.copy());
            }
            stack.shrink(toAdd);
            itemCount += toAdd;
        }
        return stack.isEmpty() ? ItemStack.EMPTY : stack;
    }

    /**
     * Simple IItemHandler backed by the item buffer. Machines can push items into the pipe;
     * the network then emits them to targets on the next tick.
     */
    private class ItemHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return itemBuffer.size();
        }

        @NotNull
        @Override
        public ItemStack getStackInSlot(int slot) {
            return itemBuffer.get(slot);
        }

        @NotNull
        @Override
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            return insertIntoBuffer(stack.copy());
        }

        @NotNull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= itemBuffer.size()) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = itemBuffer.get(slot);
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int extracted = Math.min(amount, stack.getCount());
            ItemStack result = stack.copyWithCount(extracted);
            if (!simulate) {
                stack.shrink(extracted);
                itemCount -= extracted;
                if (stack.isEmpty()) {
                    itemBuffer.remove(slot);
                }
            }
            return result;
        }

        @Override
        public int getSlotLimit(int slot) {
            if (slot < 0 || slot >= itemBuffer.size()) {
                return 0;
            }
            return itemBuffer.get(slot).getMaxStackSize();
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return true;
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
            double heatShare = node.takeSavedHeat();
            if (heatShare > 0) {
                heatCapacitor.handleHeat(heatShare);
            }
            for (ItemStack stack : node.takeSavedItems()) {
                insertIntoBuffer(stack);
            }
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
        pullItems();
        emitEnergy();
        emitFluids();
        emitChemicals();
        emitItems();
        simulateHeat();
    }

    //Heat

    /**
     * Network-wide heat simulation. The network acts as a single heat object: one temperature,
     * one capacity. Heat flows to all adjacent blocks with IHeatHandler capability, regardless
     * of the side's connection type. Environment loss is also computed here.
     * <p>
     * Uses the 2-phase approach: Phase 1 calculates transfers via {@code handleHeat} (which queues
     * into the capacitor's accumulator), Phase 2 commits via {@code update}.
     */
    private void simulateHeat() {
        double totalCapacity = getTotalHeatCapacity();
        if (totalCapacity <= 0) {
            return;
        }
        double myTemp = heatCapacitor.getTemperature();

        //Phase 1: simulate adjacent transfers
        for (FusedAcceptorCache.HeatAcceptor entry : acceptorCache.getHeatAcceptors()) {
            IHeatHandler sink = entry.resolve();
            if (sink == null) {
                continue;
            }
            double sinkTemp = sink.getTotalTemperature();
            double invConduction = sink.getTotalInverseConduction() + heatCapacitor.getInverseConduction();
            double tempToTransfer = (myTemp - sinkTemp) / invConduction;
            double heatToTransfer = tempToTransfer * totalCapacity;
            heatCapacitor.handleHeat(-heatToTransfer);
            sink.handleHeat(heatToTransfer);
        }

        //Environment loss: 6 sides to air
        double ambientTemp = HeatAPI.AMBIENT_TEMP;
        double invConductionEnv = HeatAPI.AIR_INVERSE_COEFFICIENT + heatCapacitor.getInverseInsulation() + heatCapacitor.getInverseConduction();
        double tempToTransferEnv = (myTemp - ambientTemp) / invConductionEnv;
        double heatToTransferEnv = tempToTransferEnv * totalCapacity;
        heatCapacitor.handleHeat(-heatToTransferEnv * 6);

        //Phase 2: commit
        heatCapacitor.update();
    }

    //Energy

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
            FluidStack bufferWithFallback = fluidTank.getFluid();
            FluidStack received;
            if (bufferWithFallback.isEmpty()) {
                received = acceptor.drain(availablePull, IFluidHandler.FluidAction.SIMULATE);
            } else {
                received = acceptor.drain(bufferWithFallback.copyWithAmount(availablePull), IFluidHandler.FluidAction.SIMULATE);
            }
            if (!received.isEmpty() && fluidTank.insert(received, Action.SIMULATE, AutomationType.INTERNAL).isEmpty()) {
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
