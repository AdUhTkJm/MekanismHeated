package io.aduhtkjm.mekanismheated.tank;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.aduhtkjm.mekanismheated.Mod;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
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
 * A fluid tank that can hold multiple different fluids simultaneously, constrained only by a total capacity
 * rather than per-fluid capacity. Internally uses a fixed set of slots, each capable of holding one fluid type,
 * while enforcing that the combined amount across all slots does not exceed the total capacity.
 */
public class MultiFluidTank {

    public static final int DEFAULT_SLOT_COUNT = 16;

    private final int totalCapacity;
    private final List<Slot> slots;
    @Nullable
    private final IContentsListener listener;

    private MultiFluidTank(int totalCapacity, int slotCount, @Nullable IContentsListener listener) {
        this.totalCapacity = totalCapacity;
        this.listener = listener;
        this.slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            slots.add(new Slot());
        }
    }

    public static MultiFluidTank output(int totalCapacity, int slotCount, @Nullable IContentsListener listener) {
        if (totalCapacity < 0) {
            throw new IllegalArgumentException("Capacity must be at least zero");
        }
        if (slotCount < 1) {
            throw new IllegalArgumentException("Slot count must be at least 1");
        }
        return new MultiFluidTank(totalCapacity, slotCount, listener);
    }

    public static MultiFluidTank output(int totalCapacity, @Nullable IContentsListener listener) {
        return output(totalCapacity, DEFAULT_SLOT_COUNT, listener);
    }

    public int getTotalCapacity() {
        return totalCapacity;
    }

    public int getTotalAmount() {
        int total = 0;
        for (Slot slot : slots) {
            total += slot.stored.getAmount();
        }
        return total;
    }

    public int getTotalNeeded() {
        return Math.max(0, totalCapacity - getTotalAmount());
    }

    public boolean isEmpty() {
        for (Slot slot : slots) {
            if (!slot.stored.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @NotNull
    public List<Slot> getSlots() {
        return Collections.unmodifiableList(slots);
    }

    /**
     * Returns a list of all non-empty fluid stacks currently stored.
     */
    @NotNull
    public List<FluidStack> getFluids() {
        List<FluidStack> result = new ArrayList<>();
        for (Slot slot : slots) {
            if (!slot.stored.isEmpty()) {
                result.add(slot.stored);
            }
        }
        return result;
    }

    public int getFluidCount() {
        int count = 0;
        for (Slot slot : slots) {
            if (!slot.stored.isEmpty()) {
                count++;
            }
        }
        return count;
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
        int toAdd = Math.min(stack.getAmount(), needed);
        if (action.execute()) {
            Slot target = null;
            for (Slot slot : slots) {
                if (FluidStack.isSameFluidSameComponents(slot.stored, stack)) {
                    target = slot;
                    break;
                }
            }
            if (target == null) {
                for (Slot slot : slots) {
                    if (slot.stored.isEmpty()) {
                        target = slot;
                        break;
                    }
                }
            }
            if (target != null) {
                if (target.stored.isEmpty()) {
                    target.stored = stack.copyWithAmount(toAdd);
                } else {
                    target.stored = target.stored.copyWithAmount(target.stored.getAmount() + toAdd);
                }
                onContentsChanged();
            }
        }
        return stack.copyWithAmount(stack.getAmount() - toAdd);
    }

    @NotNull
    public FluidStack extract(@NotNull FluidStack filter, int amount, Action action, AutomationType automationType) {
        if (filter.isEmpty() || amount < 1) {
            return FluidStack.EMPTY;
        }
        for (Slot slot : slots) {
            if (FluidStack.isSameFluidSameComponents(slot.stored, filter)) {
                int toExtract = Math.min(slot.stored.getAmount(), amount);
                if (action.execute()) {
                    if (toExtract >= slot.stored.getAmount()) {
                        slot.stored = FluidStack.EMPTY;
                    } else {
                        slot.stored = slot.stored.copyWithAmount(slot.stored.getAmount() - toExtract);
                    }
                    onContentsChanged();
                }
                return slot.stored.copyWithAmount(toExtract);
            }
        }
        return FluidStack.EMPTY;
    }

    @NotNull
    public FluidStack extractFirst(int amount, Action action, AutomationType automationType) {
        if (amount < 1) {
            return FluidStack.EMPTY;
        }
        for (Slot slot : slots) {
            if (!slot.stored.isEmpty()) {
                int toExtract = Math.min(slot.stored.getAmount(), amount);
                if (action.execute()) {
                    if (toExtract >= slot.stored.getAmount()) {
                        slot.stored = FluidStack.EMPTY;
                    } else {
                        slot.stored = slot.stored.copyWithAmount(slot.stored.getAmount() - toExtract);
                    }
                    onContentsChanged();
                }
                return slot.stored.copyWithAmount(toExtract);
            }
        }
        return FluidStack.EMPTY;
    }

    public boolean containsFluid(FluidStack stack) {
        for (Slot slot : slots) {
            if (FluidStack.isSameFluidSameComponents(slot.stored, stack)) {
                return true;
            }
        }
        return false;
    }

    public int getAmountOf(FluidStack stack) {
        for (Slot slot : slots) {
            if (FluidStack.isSameFluidSameComponents(slot.stored, stack)) {
                return slot.stored.getAmount();
            }
        }
        return 0;
    }

    public void setEmpty() {
        boolean wasEmpty = isEmpty();
        for (Slot slot : slots) {
            slot.stored = FluidStack.EMPTY;
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
        onContentsChanged();
    }

    private void onContentsChanged() {
        if (listener != null) {
            listener.onContentsChanged();
        }
    }

    /**
     * A single fluid slot within the {@link MultiFluidTank}. Each slot holds at most one fluid type.
     * Implements {@link IExtendedFluidTank} for integration with Mekanism's fluid handler system.
     * The effective insert capacity is governed by the parent tank's total remaining capacity.
     */
    @NonnullDefault
    public class Slot implements IExtendedFluidTank {

        private FluidStack stored = FluidStack.EMPTY;

        @Override
        public FluidStack getFluid() {
            return stored;
        }

        @Override
        public int getCapacity() {
            return totalCapacity;
        }

        @Override
        public int getFluidAmount() {
            return stored.getAmount();
        }

        @Override
        public boolean isEmpty() {
            return stored.isEmpty();
        }

        @Override
        public boolean isFluidEqual(FluidStack other) {
            return FluidStack.isSameFluidSameComponents(stored, other);
        }

        @Override
        public int getNeeded() {
            return MultiFluidTank.this.getTotalNeeded();
        }

        @Override
        public void setStack(FluidStack stack) {
            setStackUnchecked(stack);
        }

        @Override
        public void setStackUnchecked(FluidStack stack) {
            stored = stack.copy();
            onContentsChanged();
        }

        @Override
        public FluidStack insert(FluidStack stack, Action action, AutomationType automationType) {
            //Only accept if this slot is empty or holds the same fluid
            if (stack.isEmpty()) {
                return stack;
            }
            if (!stored.isEmpty() && !isFluidEqual(stack)) {
                return stack;
            }
            int needed = MultiFluidTank.this.getTotalNeeded();
            if (needed <= 0) {
                return stack;
            }
            int toAdd = Math.min(stack.getAmount(), needed);
            if (action.execute()) {
                if (stored.isEmpty()) {
                    stored = stack.copyWithAmount(toAdd);
                } else {
                    stored = stored.copyWithAmount(stored.getAmount() + toAdd);
                }
                onContentsChanged();
            }
            return stack.copyWithAmount(stack.getAmount() - toAdd);
        }

        @Override
        public FluidStack extract(int amount, Action action, AutomationType automationType) {
            if (stored.isEmpty() || amount < 1) {
                return FluidStack.EMPTY;
            }
            int toExtract = Math.min(stored.getAmount(), amount);
            FluidStack ret = stored.copyWithAmount(toExtract);
            if (action.execute()) {
                if (toExtract >= stored.getAmount()) {
                    stored = FluidStack.EMPTY;
                } else {
                    stored = stored.copyWithAmount(stored.getAmount() - toExtract);
                }
                onContentsChanged();
            }
            return ret;
        }

        @Override
        public int setStackSize(int amount, Action action) {
            if (stored.isEmpty()) {
                return 0;
            }
            if (amount <= 0) {
                if (action.execute()) {
                    stored = FluidStack.EMPTY;
                    onContentsChanged();
                }
                return 0;
            }
            //Cap at total remaining + current amount in this slot
            int maxAllowed = stored.getAmount() + MultiFluidTank.this.getTotalNeeded();
            int clamped = Math.min(amount, maxAllowed);
            if (clamped == stored.getAmount() || action.simulate()) {
                return clamped;
            }
            stored = stored.copyWithAmount(clamped);
            onContentsChanged();
            return clamped;
        }

        @Override
        public CompoundTag serializeNBT(HolderLookup.Provider provider) {
            //Serialization is handled by the parent MultiFluidTank
            CompoundTag nbt = new CompoundTag();
            if (!stored.isEmpty()) {
                nbt.put("stored", stored.save(provider));
            }
            return nbt;
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
            //Deserialization is handled by the parent MultiFluidTank
            if (nbt.contains("stored", Tag.TAG_COMPOUND)) {
                stored = FluidStack.parseOptional(provider, nbt.getCompound("stored"));
            } else {
                stored = FluidStack.EMPTY;
            }
        }

        @Override
        public void onContentsChanged() {
            MultiFluidTank.this.onContentsChanged();
        }

        @Override
        public boolean isFluidValid(FluidStack stack) {
            return true;
        }
    }
}
