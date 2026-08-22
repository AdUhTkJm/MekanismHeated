package io.aduhtkjm.mekanismheated.block.shaker;

import io.aduhtkjm.mekanismheated.registries.ModBlocks;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.common.capabilities.energy.MachineEnergyContainer;
import mekanism.common.capabilities.holder.energy.EnergyContainerHelper;
import mekanism.common.capabilities.holder.energy.IEnergyContainerHolder;
import mekanism.common.capabilities.holder.slot.IInventorySlotHolder;
import mekanism.common.capabilities.holder.slot.InventorySlotHelper;
import mekanism.common.inventory.container.MekanismContainer;
import mekanism.common.inventory.container.sync.SyncableInt;
import mekanism.common.inventory.slot.EnergyInventorySlot;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.prefab.TileEntityConfigurableMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Powered inventory and processing logic for the Shaker.
 *
 * <p>The Shaker deliberately does not hard-code a transformation recipe yet. It consumes energy to
 * shake one input item at a time and transfers that item to the output. This gives the block a
 * useful, deterministic machine workflow while leaving room for recipe-backed shaking later.</p>
 */
public class ShakerBlockEntity extends TileEntityConfigurableMachine {

    public static final int PROCESSING_TICKS = 40;
    public static final long ENERGY_PER_TICK = 40;
    public static final long MAX_ENERGY = 100_000;

    private MachineEnergyContainer<ShakerBlockEntity> energyContainer;
    private InputInventorySlot inputSlot;
    private OutputInventorySlot outputSlot;
    private EnergyInventorySlot energySlot;
    private int operatingTicks;

    public ShakerBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlocks.SHAKER, pos, blockState);
        configComponent.setupItemIOConfig(inputSlot, outputSlot, energySlot);
        configComponent.setupInputConfig(TransmissionType.ENERGY, energyContainer);

        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(configComponent, TransmissionType.ITEM);
    }

    @Override
    protected IEnergyContainerHolder getInitialEnergyContainers(IContentsListener listener) {
        EnergyContainerHelper builder = EnergyContainerHelper.forSideWithConfig(this);
        builder.addContainer(energyContainer = MachineEnergyContainer.input(this, listener));
        return builder.build();
    }

    @Override
    protected IInventorySlotHolder getInitialInventory(IContentsListener listener) {
        InventorySlotHelper builder = InventorySlotHelper.forSideWithConfig(this);
        builder.addSlot(inputSlot = InputInventorySlot.at(listener, 64, 17));
        builder.addSlot(outputSlot = OutputInventorySlot.at(listener, 116, 35));
        builder.addSlot(energySlot = EnergyInventorySlot.fillOrConvert(energyContainer, this::getLevel, listener, 64, 53));
        return builder.build();
    }

    @Override
    protected boolean onUpdateServer() {
        boolean sendUpdatePacket = super.onUpdateServer();
        energySlot.fillContainerOrConvert();

        boolean canOperate = canOperate();
        if (canOperate && energyContainer.getEnergy() >= energyContainer.getEnergyPerTick()) {
            long extracted = energyContainer.extract(energyContainer.getEnergyPerTick(), Action.EXECUTE, AutomationType.INTERNAL);
            if (extracted >= energyContainer.getEnergyPerTick()) {
                operatingTicks++;
                setActive(true);
                sendUpdatePacket = true;
                if (operatingTicks >= PROCESSING_TICKS) {
                    completeOperation();
                    operatingTicks = 0;
                }
            } else {
                resetProcessing();
            }
        } else {
            resetProcessing();
        }
        return sendUpdatePacket;
    }

    private boolean canOperate() {
        if (inputSlot == null || outputSlot == null || inputSlot.isEmpty()) {
            return false;
        }
        ItemStack input = inputSlot.getStack();
        ItemStack output = outputSlot.getStack();
        return output.isEmpty()
              || ItemStack.isSameItemSameComponents(input, output) && output.getCount() < outputSlot.getLimit(output);
    }

    private void completeOperation() {
        ItemStack processed = inputSlot.extractItem(1, Action.EXECUTE, AutomationType.INTERNAL);
        if (processed.isEmpty()) {
            return;
        }

        ItemStack remainder = outputSlot.insertItem(processed, Action.EXECUTE, AutomationType.INTERNAL);
        if (!remainder.isEmpty()) {
            // Keep the operation lossless if another inventory update filled the output between
            // the check above and the actual transfer.
            inputSlot.insertItem(remainder, Action.EXECUTE, AutomationType.INTERNAL);
        }
    }

    private void resetProcessing() {
        if (operatingTicks != 0) {
            operatingTicks = 0;
        }
        setActive(false);
    }

    public boolean isShaking() {
        return getActive();
    }

    public ItemStack getStoredItem() {
        return inputSlot == null ? ItemStack.EMPTY : inputSlot.getStack();
    }

    /**
     * Kept as a small compatibility helper for callers that used the old hand-insertion API.
     */
    public void setStoredItem(ItemStack itemStack) {
        if (inputSlot != null) {
            inputSlot.setStack(itemStack.copy());
        }
    }

    public MachineEnergyContainer<ShakerBlockEntity> getEnergyContainer() {
        return energyContainer;
    }

    public int getOperatingTicks() {
        return operatingTicks;
    }

    public double getScaledProgress() {
        return operatingTicks / (double) PROCESSING_TICKS;
    }

    @Override
    public void onContentsChanged() {
        super.onContentsChanged();
        // The item is also rendered on the physical shaker platform, so changes need to reach
        // clients even when no player currently has the GUI open.
        if (level != null && !level.isClientSide) {
            sendUpdatePacket();
        }
    }

    @Override
    public void addContainerTrackers(MekanismContainer container) {
        super.addContainerTrackers(container);
        container.track(SyncableInt.create(this::getOperatingTicks, value -> operatingTicks = value));
    }
}
