package io.aduhtkjm.mekanismheated.tank;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalTank;
import mekanism.api.fluid.IExtendedFluidTank;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.NonnullDefault;

/**
 * A tank that can hold multiple different fluids and chemicals simultaneously, constrained only by a total capacity
 * rather than per-type capacity. Internally uses a fixed set of slots, each capable of holding one fluid <em>or</em> one
 * chemical, while enforcing that the combined amount across all slots (measured in mB) does not exceed the total capacity.
 *
 * <p>
 * Each slot is exposed both as a fluid tank ({@link IExtendedFluidTank}) and as a chemical tank ({@link IChemicalTank}). A
 * slot will only accept a fluid while it is empty or already holds that same fluid, and will only accept a chemical while
 * it is empty or already holds that same chemical. This lets the same pool of slots be shared between the two content
 * types: up to {@link #getSlotCount()} distinct substances (fluid and/or chemical) can be stored at once, regardless of
 * how many of them are fluids versus chemicals.
 * </p>
 *
 * @see MultiFluidTank the fluid-only analogue used by the heat smelter
 */
public class MultiFluidChemicalTank {

    public static final int DEFAULT_SLOT_COUNT = 16;

    private final int totalCapacity;
    private final List<Entry> entries;
    private final List<FluidView> fluidViews;
    private final List<ChemicalView> chemicalViews;
    @Nullable
    private final IContentsListener listener;

    private MultiFluidChemicalTank(int totalCapacity, int slotCount, @Nullable IContentsListener listener) {
        this.totalCapacity = totalCapacity;
        this.listener = listener;
        this.entries = new ArrayList<>(slotCount);
        this.fluidViews = new ArrayList<>(slotCount);
        this.chemicalViews = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            Entry entry = new Entry();
            entries.add(entry);
            fluidViews.add(new FluidView(entry));
            chemicalViews.add(new ChemicalView(entry));
        }
    }

    public static MultiFluidChemicalTank create(int totalCapacity, int slotCount, @Nullable IContentsListener listener) {
        if (totalCapacity < 0) {
            throw new IllegalArgumentException("Capacity must be at least zero");
        }
        if (slotCount < 1) {
            throw new IllegalArgumentException("Slot count must be at least 1");
        }
        return new MultiFluidChemicalTank(totalCapacity, slotCount, listener);
    }

    public static MultiFluidChemicalTank create(int totalCapacity, @Nullable IContentsListener listener) {
        return create(totalCapacity, DEFAULT_SLOT_COUNT, listener);
    }

    public int getTotalCapacity() {
        return totalCapacity;
    }

    public int getSlotCount() {
        return entries.size();
    }

    /**
     * @return The total amount (in mB) currently stored across all slots, fluids and chemicals combined. Always at most
     *         {@link #getTotalCapacity()}.
     */
    public int getTotalAmount() {
        long total = 0;
        for (Entry entry : entries) {
            total += entry.amount();
        }
        //Safe to narrow: the total can never exceed totalCapacity, which is an int
        return (int) total;
    }

    public int getTotalNeeded() {
        return Math.max(0, totalCapacity - getTotalAmount());
    }

    public boolean isEmpty() {
        for (Entry entry : entries) {
            if (!entry.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * The number of slots that currently hold anything (fluid or chemical).
     */
    public int getUsedSlotCount() {
        int count = 0;
        for (Entry entry : entries) {
            if (!entry.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    public int getFluidCount() {
        int count = 0;
        for (Entry entry : entries) {
            if (entry.isFluid()) {
                count++;
            }
        }
        return count;
    }

    public int getChemicalCount() {
        int count = 0;
        for (Entry entry : entries) {
            if (entry.isChemical()) {
                count++;
            }
        }
        return count;
    }

    @NotNull
    public List<IExtendedFluidTank> getFluidViews() {
        return Collections.unmodifiableList(fluidViews);
    }

    @NotNull
    public List<IChemicalTank> getChemicalViews() {
        return Collections.unmodifiableList(chemicalViews);
    }

    /**
     * Returns a list of all non-empty fluid stacks currently stored.
     */
    @NotNull
    public List<FluidStack> getFluids() {
        List<FluidStack> result = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.isFluid()) {
                result.add(entry.fluid);
            }
        }
        return result;
    }

    /**
     * Returns a list of all non-empty chemical stacks currently stored.
     */
    @NotNull
    public List<ChemicalStack> getChemicals() {
        List<ChemicalStack> result = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.isChemical()) {
                result.add(entry.chemical);
            }
        }
        return result;
    }

    @NotNull
    public FluidStack insert(@NotNull FluidStack stack, Action action, AutomationType automationType) {
        if (stack.isEmpty()) {
            return stack;
        }
        int needed = getTotalNeeded();
        if (needed <= 0) {
            return stack;
        }
        //Prefer a slot already holding this fluid, otherwise the first fully-empty slot
        Entry target = null;
        for (Entry entry : entries) {
            if (FluidStack.isSameFluidSameComponents(entry.fluid, stack)) {
                target = entry;
                break;
            }
        }
        if (target == null) {
            for (Entry entry : entries) {
                if (entry.isEmpty()) {
                    target = entry;
                    break;
                }
            }
        }
        //If every slot is occupied by a different type (or a chemical) there is nowhere to put this fluid
        if (target == null) {
            return stack;
        }
        int toAdd = Math.min(stack.getAmount(), needed);
        if (action.execute()) {
            if (target.isEmpty()) {
                target.fluid = stack.copyWithAmount(toAdd);
            } else {
                target.fluid = target.fluid.copyWithAmount(target.fluid.getAmount() + toAdd);
            }
            onContentsChanged();
        }
        return stack.copyWithAmount(stack.getAmount() - toAdd);
    }

    @NotNull
    public ChemicalStack insert(@NotNull ChemicalStack stack, Action action, AutomationType automationType) {
        if (stack.isEmpty()) {
            return stack;
        }
        int needed = getTotalNeeded();
        if (needed <= 0) {
            return stack;
        }
        //Prefer a slot already holding this chemical, otherwise the first fully-empty slot
        Entry target = null;
        for (Entry entry : entries) {
            if (ChemicalStack.isSameChemical(entry.chemical, stack)) {
                target = entry;
                break;
            }
        }
        if (target == null) {
            for (Entry entry : entries) {
                if (entry.isEmpty()) {
                    target = entry;
                    break;
                }
            }
        }
        //If every slot is occupied by a different type (or a fluid) there is nowhere to put this chemical
        if (target == null) {
            return stack;
        }
        long toAdd = Math.min(stack.getAmount(), needed);
        if (action.execute()) {
            if (target.isEmpty()) {
                target.chemical = stack.copyWithAmount(toAdd);
            } else {
                target.chemical = target.chemical.copyWithAmount(target.chemical.getAmount() + toAdd);
            }
            onContentsChanged();
        }
        return stack.copyWithAmount(stack.getAmount() - toAdd);
    }

    @NotNull
    public FluidStack extract(@NotNull FluidStack filter, int amount, Action action, AutomationType automationType) {
        if (filter.isEmpty() || amount < 1) {
            return FluidStack.EMPTY;
        }
        for (Entry entry : entries) {
            if (entry.isFluid() && FluidStack.isSameFluidSameComponents(entry.fluid, filter)) {
                int toExtract = Math.min(entry.fluid.getAmount(), amount);
                FluidStack ret = entry.fluid.copyWithAmount(toExtract);
                if (action.execute()) {
                    entry.removeFluid(toExtract);
                    onContentsChanged();
                }
                return ret;
            }
        }
        return FluidStack.EMPTY;
    }

    @NotNull
    public ChemicalStack extract(@NotNull ChemicalStack filter, long amount, Action action, AutomationType automationType) {
        if (filter.isEmpty() || amount < 1) {
            return ChemicalStack.EMPTY;
        }
        for (Entry entry : entries) {
            if (entry.isChemical() && ChemicalStack.isSameChemical(entry.chemical, filter)) {
                long toExtract = Math.min(entry.chemical.getAmount(), amount);
                ChemicalStack ret = entry.chemical.copyWithAmount(toExtract);
                if (action.execute()) {
                    entry.removeChemical(toExtract);
                    onContentsChanged();
                }
                return ret;
            }
        }
        return ChemicalStack.EMPTY;
    }

    /**
     * Extracts from the first slot holding any fluid, regardless of type.
     */
    @NotNull
    public FluidStack extractFirstFluid(int amount, Action action, AutomationType automationType) {
        if (amount < 1) {
            return FluidStack.EMPTY;
        }
        for (Entry entry : entries) {
            if (entry.isFluid()) {
                int toExtract = Math.min(entry.fluid.getAmount(), amount);
                FluidStack ret = entry.fluid.copyWithAmount(toExtract);
                if (action.execute()) {
                    entry.removeFluid(toExtract);
                    onContentsChanged();
                }
                return ret;
            }
        }
        return FluidStack.EMPTY;
    }

    /**
     * Extracts from the first slot holding any chemical, regardless of type.
     */
    @NotNull
    public ChemicalStack extractFirstChemical(long amount, Action action, AutomationType automationType) {
        if (amount < 1) {
            return ChemicalStack.EMPTY;
        }
        for (Entry entry : entries) {
            if (entry.isChemical()) {
                long toExtract = Math.min(entry.chemical.getAmount(), amount);
                ChemicalStack ret = entry.chemical.copyWithAmount(toExtract);
                if (action.execute()) {
                    entry.removeChemical(toExtract);
                    onContentsChanged();
                }
                return ret;
            }
        }
        return ChemicalStack.EMPTY;
    }

    public boolean containsFluid(FluidStack stack) {
        for (Entry entry : entries) {
            if (FluidStack.isSameFluidSameComponents(entry.fluid, stack)) {
                return true;
            }
        }
        return false;
    }

    public boolean containsChemical(ChemicalStack stack) {
        for (Entry entry : entries) {
            if (ChemicalStack.isSameChemical(entry.chemical, stack)) {
                return true;
            }
        }
        return false;
    }

    public int getAmountOfFluid(FluidStack stack) {
        for (Entry entry : entries) {
            if (FluidStack.isSameFluidSameComponents(entry.fluid, stack)) {
                return entry.fluid.getAmount();
            }
        }
        return 0;
    }

    public long getAmountOfChemical(ChemicalStack stack) {
        for (Entry entry : entries) {
            if (ChemicalStack.isSameChemical(entry.chemical, stack)) {
                return entry.chemical.getAmount();
            }
        }
        return 0;
    }

    public void setEmpty() {
        boolean wasEmpty = isEmpty();
        for (Entry entry : entries) {
            entry.fluid = FluidStack.EMPTY;
            entry.chemical = ChemicalStack.EMPTY;
        }
        if (!wasEmpty) {
            onContentsChanged();
        }
    }

    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag nbt = new CompoundTag();
        List<FluidStack> fluids = getFluids();
        if (!fluids.isEmpty()) {
            ListTag list = new ListTag();
            for (FluidStack stack : fluids) {
                list.add(stack.save(provider));
            }
            nbt.put("fluids", list);
        }
        List<ChemicalStack> chemicals = getChemicals();
        if (!chemicals.isEmpty()) {
            ListTag list = new ListTag();
            for (ChemicalStack stack : chemicals) {
                list.add(stack.save(provider));
            }
            nbt.put("chemicals", list);
        }
        return nbt;
    }

    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        setEmpty();
        if (nbt.contains("fluids", Tag.TAG_LIST)) {
            ListTag list = nbt.getList("fluids", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                FluidStack stack = FluidStack.parseOptional(provider, list.getCompound(i));
                if (!stack.isEmpty()) {
                    insert(stack, Action.EXECUTE, AutomationType.INTERNAL);
                }
            }
        }
        if (nbt.contains("chemicals", Tag.TAG_LIST)) {
            ListTag list = nbt.getList("chemicals", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                ChemicalStack stack = ChemicalStack.parseOptional(provider, list.getCompound(i));
                if (!stack.isEmpty()) {
                    insert(stack, Action.EXECUTE, AutomationType.INTERNAL);
                }
            }
        }
        onContentsChanged();
    }

    private void onContentsChanged() {
        if (listener != null) {
            listener.onContentsChanged();
        }
    }

    /**
     * The raw storage for a single slot. A slot holds at most one substance: either a non-empty fluid or a non-empty
     * chemical, but never both. The two {@link FluidView}/{@link ChemicalView} wrappers for this slot share this state.
     */
    private static final class Entry {

        private FluidStack fluid = FluidStack.EMPTY;
        private ChemicalStack chemical = ChemicalStack.EMPTY;

        boolean isEmpty() {
            return fluid.isEmpty() && chemical.isEmpty();
        }

        boolean isFluid() {
            return !fluid.isEmpty();
        }

        boolean isChemical() {
            return !chemical.isEmpty();
        }

        /**
         * The amount of substance (in mB) currently held by this slot, or zero if empty.
         */
        long amount() {
            return isFluid() ? fluid.getAmount() : chemical.getAmount();
        }

        void removeFluid(int amount) {
            if (amount >= fluid.getAmount()) {
                fluid = FluidStack.EMPTY;
            } else {
                fluid = fluid.copyWithAmount(fluid.getAmount() - amount);
            }
        }

        void removeChemical(long amount) {
            if (amount >= chemical.getAmount()) {
                chemical = ChemicalStack.EMPTY;
            } else {
                chemical = chemical.copyWithAmount(chemical.getAmount() - amount);
            }
        }
    }

    /**
     * Fluid-side view of a single slot. Behaves as a normal single-fluid tank for the purposes of Mekanism's fluid handler
     * system, except that it reports {@link #isEmpty()} as {@code false} while the slot holds a chemical (i.e. it is
     * occupied), and it will only accept a fluid while the slot is empty or already holds that same fluid. The effective
     * insert capacity is governed by the parent tank's total remaining capacity.
     */
    @NonnullDefault
    public class FluidView implements IExtendedFluidTank {

        private final Entry entry;

        FluidView(Entry entry) {
            this.entry = entry;
        }

        @Override
        public FluidStack getFluid() {
            return entry.fluid;
        }

        @Override
        public int getCapacity() {
            return totalCapacity;
        }

        @Override
        public int getFluidAmount() {
            return entry.fluid.getAmount();
        }

        @Override
        public int getNeeded() {
            return MultiFluidChemicalTank.this.getTotalNeeded();
        }

        @Override
        public boolean isEmpty() {
            //Occupied by a chemical counts as "not empty" so the fluid handler does not try to place a fluid here
            return entry.isEmpty();
        }

        @Override
        public boolean isFluidEqual(FluidStack other) {
            return FluidStack.isSameFluidSameComponents(entry.fluid, other);
        }

        @Override
        public void setStack(FluidStack stack) {
            setStackUnchecked(stack);
        }

        @Override
        public void setStackUnchecked(FluidStack stack) {
            if (!stack.isEmpty() && entry.isChemical()) {
                throw new IllegalStateException("Cannot set a fluid stack on a slot that is holding a chemical");
            }
            entry.fluid = stack.copy();
            onContentsChanged();
        }

        @Override
        public FluidStack insert(FluidStack stack, Action action, AutomationType automationType) {
            if (stack.isEmpty()) {
                return stack;
            }
            //Only accept while the slot is empty or holds the same fluid (reject if it holds a chemical or a different fluid)
            if (!entry.isEmpty() && !isFluidEqual(stack)) {
                return stack;
            }
            int needed = MultiFluidChemicalTank.this.getTotalNeeded();
            if (needed <= 0) {
                return stack;
            }
            int toAdd = Math.min(stack.getAmount(), needed);
            if (action.execute()) {
                if (entry.isEmpty()) {
                    entry.fluid = stack.copyWithAmount(toAdd);
                } else {
                    entry.fluid = entry.fluid.copyWithAmount(entry.fluid.getAmount() + toAdd);
                }
                onContentsChanged();
            }
            return stack.copyWithAmount(stack.getAmount() - toAdd);
        }

        @Override
        public FluidStack extract(int amount, Action action, AutomationType automationType) {
            if (entry.fluid.isEmpty() || amount < 1) {
                return FluidStack.EMPTY;
            }
            int toExtract = Math.min(entry.fluid.getAmount(), amount);
            FluidStack ret = entry.fluid.copyWithAmount(toExtract);
            if (action.execute()) {
                entry.removeFluid(toExtract);
                onContentsChanged();
            }
            return ret;
        }

        @Override
        public int setStackSize(int amount, Action action) {
            if (entry.fluid.isEmpty()) {
                return 0;
            }
            if (amount <= 0) {
                if (action.execute()) {
                    entry.fluid = FluidStack.EMPTY;
                    onContentsChanged();
                }
                return 0;
            }
            //Cap at the current amount plus whatever the shared tank still has room for
            int maxAllowed = entry.fluid.getAmount() + MultiFluidChemicalTank.this.getTotalNeeded();
            int clamped = Math.min(amount, maxAllowed);
            if (clamped == entry.fluid.getAmount() || action.simulate()) {
                return clamped;
            }
            entry.fluid = entry.fluid.copyWithAmount(clamped);
            onContentsChanged();
            return clamped;
        }

        @Override
        public CompoundTag serializeNBT(HolderLookup.Provider provider) {
            //The parent tank is responsible for real serialization; this is only here to satisfy the interface
            CompoundTag nbt = new CompoundTag();
            if (!entry.fluid.isEmpty()) {
                nbt.put("stored", entry.fluid.save(provider));
            }
            return nbt;
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
            //The parent tank is responsible for real serialization; this is only here to satisfy the interface
            if (nbt.contains("stored", Tag.TAG_COMPOUND)) {
                entry.fluid = FluidStack.parseOptional(provider, nbt.getCompound("stored"));
            } else {
                entry.fluid = FluidStack.EMPTY;
            }
        }

        @Override
        public void onContentsChanged() {
            MultiFluidChemicalTank.this.onContentsChanged();
        }

        @Override
        public boolean isFluidValid(FluidStack stack) {
            return true;
        }
    }

    /**
     * Chemical-side view of a single slot. Behaves as a normal single-chemical tank for the purposes of Mekanism's chemical
     * handler system, except that it reports {@link #isEmpty()} as {@code false} while the slot holds a fluid (i.e. it is
     * occupied), and it will only accept a chemical while the slot is empty or already holds that same chemical. The
     * effective insert capacity is governed by the parent tank's total remaining capacity.
     */
    @NonnullDefault
    public class ChemicalView implements IChemicalTank {

        private final Entry entry;

        ChemicalView(Entry entry) {
            this.entry = entry;
        }

        @Override
        public ChemicalStack getStack() {
            return entry.chemical;
        }

        @Override
        public long getCapacity() {
            return totalCapacity;
        }

        @Override
        public long getStored() {
            return entry.chemical.getAmount();
        }

        @Override
        public long getNeeded() {
            return MultiFluidChemicalTank.this.getTotalNeeded();
        }

        @Override
        public boolean isEmpty() {
            //Occupied by a fluid counts as "not empty" so the chemical handler does not try to place a chemical here
            return entry.isEmpty();
        }

        @Override
        public boolean isTypeEqual(ChemicalStack other) {
            return ChemicalStack.isSameChemical(entry.chemical, other);
        }

        @Override
        public boolean isValid(ChemicalStack stack) {
            return true;
        }

        @Override
        public void setStack(ChemicalStack stack) {
            setStackUnchecked(stack);
        }

        @Override
        public void setStackUnchecked(ChemicalStack stack) {
            if (!stack.isEmpty() && entry.isFluid()) {
                throw new IllegalStateException("Cannot set a chemical stack on a slot that is holding a fluid");
            }
            entry.chemical = stack.copy();
            onContentsChanged();
        }

        @Override
        public ChemicalStack insert(ChemicalStack stack, Action action, AutomationType automationType) {
            if (stack.isEmpty()) {
                return stack;
            }
            //Only accept while the slot is empty or holds the same chemical (reject if it holds a fluid or a different chemical)
            if (!entry.isEmpty() && !isTypeEqual(stack)) {
                return stack;
            }
            int needed = MultiFluidChemicalTank.this.getTotalNeeded();
            if (needed <= 0) {
                return stack;
            }
            long toAdd = Math.min(stack.getAmount(), needed);
            if (action.execute()) {
                if (entry.isEmpty()) {
                    entry.chemical = stack.copyWithAmount(toAdd);
                } else {
                    entry.chemical = entry.chemical.copyWithAmount(entry.chemical.getAmount() + toAdd);
                }
                onContentsChanged();
            }
            return stack.copyWithAmount(stack.getAmount() - toAdd);
        }

        @Override
        public ChemicalStack extract(long amount, Action action, AutomationType automationType) {
            if (entry.chemical.isEmpty() || amount < 1) {
                return ChemicalStack.EMPTY;
            }
            long toExtract = Math.min(entry.chemical.getAmount(), amount);
            ChemicalStack ret = entry.chemical.copyWithAmount(toExtract);
            if (action.execute()) {
                entry.removeChemical(toExtract);
                onContentsChanged();
            }
            return ret;
        }

        @Override
        public long setStackSize(long amount, Action action) {
            if (entry.chemical.isEmpty()) {
                return 0;
            }
            if (amount <= 0) {
                if (action.execute()) {
                    entry.chemical = ChemicalStack.EMPTY;
                    onContentsChanged();
                }
                return 0;
            }
            //Cap at the current amount plus whatever the shared tank still has room for
            long maxAllowed = entry.chemical.getAmount() + MultiFluidChemicalTank.this.getTotalNeeded();
            long clamped = Math.min(amount, maxAllowed);
            if (clamped == entry.chemical.getAmount() || action.simulate()) {
                return clamped;
            }
            entry.chemical = entry.chemical.copyWithAmount(clamped);
            onContentsChanged();
            return clamped;
        }

        @Override
        public CompoundTag serializeNBT(HolderLookup.Provider provider) {
            //The parent tank is responsible for real serialization; this is only here to satisfy the interface
            CompoundTag nbt = new CompoundTag();
            if (!entry.chemical.isEmpty()) {
                nbt.put("stored", entry.chemical.save(provider));
            }
            return nbt;
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
            //The parent tank is responsible for real serialization; this is only here to satisfy the interface
            if (nbt.contains("stored", Tag.TAG_COMPOUND)) {
                entry.chemical = ChemicalStack.parseOptional(provider, nbt.getCompound("stored"));
            } else {
                entry.chemical = ChemicalStack.EMPTY;
            }
        }

        @Override
        public void onContentsChanged() {
            MultiFluidChemicalTank.this.onContentsChanged();
        }
    }
}
