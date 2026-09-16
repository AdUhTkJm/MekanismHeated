package io.aduhtkjm.mekanismheated.tile.multiblock;

import io.aduhtkjm.mekanismheated.Config;
import io.aduhtkjm.mekanismheated.content.upgrade.HeatedUpgrades;
import io.aduhtkjm.mekanismheated.content.upgrade.IHeatedUpgradeMultiblockData;
import io.aduhtkjm.mekanismheated.recipe.HeatSmelterRecipe;
import io.aduhtkjm.mekanismheated.tile.HeatSmelterLogic;
import io.aduhtkjm.mekanismheated.tile.TileEntityHeatSmelter;
import io.aduhtkjm.mekanismheated.tank.MultiFluidTank;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.heat.HeatAPI;
import mekanism.api.heat.HeatAPI.HeatTransfer;
import mekanism.common.capabilities.heat.BasicHeatCapacitor;
import mekanism.common.capabilities.heat.VariableHeatCapacitor;
import mekanism.common.inventory.slot.BasicInventorySlot;
import mekanism.common.lib.multiblock.MultiblockData;
import mekanism.common.lib.multiblock.Structure;
import mekanism.common.util.NBTUtils;
import mekanism.common.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Shared "brain" for a formed Large Heat Smelter. Holds the (fixed) item slots and the volume-scaled fluid tank +
 * heat capacitor, and drives the fuel → heat → smelting / alloying logic each tick from the master block.
 *
 * <p>Because the smelting recipe is temperature-gated via the tile-bound
 * {@code HeatSmelterRecipeCacheLookupMonitor}, this data replicates that processing in a self-contained loop using
 * {@link HeatSmelterLogic} rather than the {@code RecipeCacheLookupMonitor}. The single-block and multiblock paths
 * therefore share identical math.</p>
 *
 * <p>While formed, the smelter processes recipes in parallel: each completed cycle performs one recipe operation per
 * member block of the structure at once (limited by available input items and output space), producing that many
 * times the output in the same time a single smelter would take. The batch's total heat cost scales with the square
 * root of the operation count (capped at {@link Config.HeatSmelter#MAX_HEAT_MULTIPLIER}), so a large smelter is more
 * heat-efficient than the equivalent number of separate smelters.</p>
 */
public class LargeHeatSmelterData extends MultiblockData implements IHeatedUpgradeMultiblockData {

    private final BasicInventorySlot inputSlot;
    private final BasicInventorySlot outputSlot;
    private final BasicInventorySlot fuelSlot;
    private final MultiFluidTank fluidTank;
    private final VariableHeatCapacitor heatCapacitor;

    /**
     * Adjacent heat exchange with the blocks touching the structure. Without this the shared capacitor would only ever
     * lose heat to the environment, never to a colder neighbour, because {@link MultiblockData} has no
     * {@code getAdjacent} implementation and our heat-transfer mixin only lets the hotter side push heat.
     */
    private final MultiblockHeatTransfer heatTransfer = new MultiblockHeatTransfer(this);

    private double biomeAmbientTemp;
    private double progress;
    private boolean processing;
    private boolean fluidChanged;
    @Nullable
    private HeatSmelterLogic.AlloyConfig lastAlloy;
    private double lastEnvironmentLoss;
    private double lastTransferLoss;

    public LargeHeatSmelterData(BlockEntity tile) {
        super(tile);
        // Fall back to the ambient temperature at the controller position; recalculated for the whole structure
        // in {@link #onCreated} (including any per-chunk ambient temperature delta).
        biomeAmbientTemp = HeatAPI.getAmbientTemp(tile.getLevel(), tile.getBlockPos());
        IContentsListener listener = createSaveAndComparator();
        //Use the same GUI positions as the standalone smelter so the reused GUI lays out identically
        inputSlot = BasicInventorySlot.at(listener, 64, 17);
        outputSlot = BasicInventorySlot.at(listener, 116, 35);
        fuelSlot = BasicInventorySlot.at(listener, 64, 55);
        IContentsListener fluidListener = () -> {
            fluidChanged = !isRemote();
            listener.onContentsChanged();
        };
        // The initial capacities are for a single block; they are scaled up by {@link #configure(int)} as soon as the
        // structure's volume is known. On the server that is when the formation protocol calls setShape, and on the
        // client it is when the synced volume arrives (see {@link #setVolume(int)}).
        fluidTank = MultiFluidTank.output(TileEntityHeatSmelter.MAX_FLUID, fluidListener);
        // The heat capacitor must be registered in the heat capacitors list, otherwise the multiblock exposes no
        // heat handler at all and nothing can add or receive heat
        heatCapacitor = VariableHeatCapacitor.create(Config.HeatSmelter.HEAT_CAPACITY.get(), () -> biomeAmbientTemp, listener);
        inventorySlots.add(inputSlot);
        inventorySlots.add(outputSlot);
        inventorySlots.add(fuelSlot);
        fluidTanks.addAll(fluidTank.getSlots());
        heatCapacitors.add(heatCapacitor);
    }

    /**
     * Scales the fluid tank and heat capacitor capacities by the structure's volume. Safe to call multiple times
     * (idempotent) and called on both the server (during formation) and the client (on update-tag load).
     *
     * @param volume the volume (L*W*H) of the formed structure
     */
    public void configure(int volume) {
        fluidTank.setTotalCapacity(TileEntityHeatSmelter.MAX_FLUID * volume);
        double baseHeatCapacity = Config.HeatSmelter.HEAT_CAPACITY.get() * volume;
        heatCapacitor.setHeatCapacity(baseHeatCapacity, true);
        if (!isRemote()) {
            //The capacity was just derived from the structure's volume, so re-apply the heat upgrades on top of it. The
            //client is skipped: its capacity arrives through the update tag and the container sync instead.
            HeatedUpgrades.applyTo(heatCapacitor, baseHeatCapacity, HeatedUpgrades.multipliersFor(this));
        }
    }

    /**
     * Keeps the volume-scaled capacities in step with the synced volume. Volume is a container-synced field, so this is
     * the hook the client uses to learn the structure's size: update-tag loading calls it via {@code super.readUpdateTag},
     * and every member block's open container calls it as well. Without this, a GUI opened on a non-master member would
     * read the shared tank's (large) contents against the unscaled single-block capacity, and the fluid gauge would draw
     * above its own top.
     */
    @Override
    public void setVolume(int volume) {
        if (getVolume() != volume) {
            super.setVolume(volume);
            //A zero volume means the structure is not laid out yet; the heat capacitor must never have a capacity of zero
            if (volume > 0) {
                configure(volume);
            }
        }
    }

    public BasicInventorySlot getInputSlot() {
        return inputSlot;
    }

    public BasicInventorySlot getOutputSlot() {
        return outputSlot;
    }

    public BasicInventorySlot getFuelSlot() {
        return fuelSlot;
    }

    public MultiFluidTank getFluidTank() {
        return fluidTank;
    }

    public VariableHeatCapacitor getHeatCapacitor() {
        return heatCapacitor;
    }

    public double getTemperature() {
        return heatCapacitor.getTemperature();
    }

    public double getProgress() {
        return progress;
    }

    public void setProgress(double progress) {
        this.progress = progress;
    }

    public double getLastTransferLoss() {
        return lastTransferLoss;
    }

    public void setLastTransferLoss(double lastTransferLoss) {
        this.lastTransferLoss = lastTransferLoss;
    }

    public double getLastEnvironmentLoss() {
        return lastEnvironmentLoss;
    }

    public void setLastEnvironmentLoss(double lastEnvironmentLoss) {
        this.lastEnvironmentLoss = lastEnvironmentLoss;
    }

    @Override
    public double simulateAdjacent() {
        return heatTransfer.simulateAdjacent();
    }

    @Override
    public boolean tick(Level world) {
        boolean needsPacket = super.tick(world);
        boolean burning = burnFuel();
        HeatTransfer transfer = simulate();
        lastEnvironmentLoss = transfer.environmentTransfer();
        lastTransferLoss = transfer.adjacentTransfer();
        updateHeatCapacitors(null);
        needsPacket |= processRecipes(world);
        tryAlloying();
        if (burning) {
            needsPacket = true;
        }
        return needsPacket;
    }

    private boolean burnFuel() {
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            return false;
        }
        int consumed = HeatSmelterLogic.burnFuel(level, heatCapacitor.getTemperature(), heatCapacitor, fuelSlot.getStack());
        if (consumed > 0) {
            fuelSlot.extractItem(consumed, Action.EXECUTE, AutomationType.INTERNAL);
            return true;
        }
        return false;
    }

    private boolean processRecipes(Level world) {
        boolean wasProcessing = processing;
        if (world == null || world.isClientSide) {
            return wasProcessing != (processing = false);
        }
        ItemStack input = inputSlot.getStack();
        double temperature = heatCapacitor.getTemperature();
        HeatSmelterRecipe recipe = HeatSmelterLogic.findRecipeFor(world, input, temperature, true);
        double speed = HeatSmelterLogic.speedFactor(temperature);
        int required = Config.HeatSmelter.BASE_SPEED.get();
        if (recipe == null || speed <= 0) {
            if (recipe == null) {
                //No valid input; discard any accumulated progress
                progress = 0;
            }
            return wasProcessing != (processing = false);
        }
        // The large smelter performs one recipe operation per member block at once, limited by the input items
        // actually available and by the output space. The batch's heat cost scales with the square root of the
        // operation count (capped), making it more heat-efficient than the equivalent number of separate smelters.
        int operations = Math.min(HeatSmelterLogic.parallelOperations(getVolume(), input.getCount()),
              maxOutputOperations(recipe, input));
        if (operations <= 0) {
            return wasProcessing != (processing = false);
        }
        // Pay this tick's share of the recipe's total heat cost (scaled by the parallel heat multiplier); if the
        // capacitor cannot cover it, the smelter stalls and the recipe idles (without advancing) until enough heat
        // is available again
        double heatForTick = HeatSmelterLogic.heatForTick(
              recipe.getHeatConsumed() * HeatSmelterLogic.heatMultiplier(operations), required, speed);
        if (heatForTick > 0 && heatCapacitor.getHeat() < heatForTick) {
            return wasProcessing != (processing = false);
        }
        progress += speed;
        if (heatForTick > 0) {
            heatCapacitor.handleHeat(-heatForTick);
        }
        int performed = 0;
        int maxCycles = (int) (progress / required);
        while (performed < maxCycles && operate(recipe, input, operations)) {
            performed++;
            progress -= required;
        }
        processing = performed > 0;
        return wasProcessing != processing;
    }

    /**
     * The number of recipe operations the output containers can currently accept, given each operation's output size.
     * Used to shrink the batch (and with it the heat cost) when the output side is running low on room.
     */
    private int maxOutputOperations(HeatSmelterRecipe recipe, ItemStack input) {
        if (recipe.isItemOutput()) {
            ItemStack perOperation = recipe.getItemOutput(input);
            int perOperationCount = perOperation.getCount();
            if (perOperationCount <= 0) {
                return 0;
            }
            ItemStack probe = perOperation.copyWithCount(perOperation.getMaxStackSize());
            ItemStack remainder = outputSlot.insertItem(probe, Action.SIMULATE, AutomationType.INTERNAL);
            int free = probe.getCount() - remainder.getCount();
            return free / perOperationCount;
        }
        FluidStack perOperation = recipe.getFluidOutput(input);
        if (perOperation.isEmpty() || perOperation.getAmount() <= 0) {
            return 0;
        }
        FluidStack probe = perOperation.copyWithAmount(Integer.MAX_VALUE);
        FluidStack remainder = fluidTank.insert(probe, Action.SIMULATE, AutomationType.INTERNAL);
        long free = (long) Integer.MAX_VALUE - remainder.getAmount();
        return (int) Math.min(Integer.MAX_VALUE, free / perOperation.getAmount());
    }

    private boolean operate(HeatSmelterRecipe recipe, ItemStack input, int operations) {
        if (input.isEmpty() || !recipe.test(input)) {
            return false;
        }
        //Never process more than the input slot actually holds (e.g. near the end of the input stack)
        int count = Math.min(operations, input.getCount());
        if (count <= 0) {
            return false;
        }
        if (recipe.isItemOutput()) {
            ItemStack perOperation = recipe.getItemOutput(input);
            ItemStack output = perOperation.copyWithCount(perOperation.getCount() * count);
            if (!outputSlot.insertItem(output, Action.SIMULATE, AutomationType.INTERNAL).isEmpty()) {
                return false;
            }
            inputSlot.extractItem(count, Action.EXECUTE, AutomationType.INTERNAL);
            outputSlot.insertItem(output, Action.EXECUTE, AutomationType.INTERNAL);
        } else {
            FluidStack perOperation = recipe.getFluidOutput(input);
            FluidStack output = perOperation.copyWithAmount(perOperation.getAmount() * count);
            if (!fluidTank.insert(output, Action.SIMULATE, AutomationType.INTERNAL).isEmpty()) {
                return false;
            }
            inputSlot.extractItem(count, Action.EXECUTE, AutomationType.INTERNAL);
            fluidTank.insert(output, Action.EXECUTE, AutomationType.INTERNAL);
        }
        return true;
    }

    private void tryAlloying() {
        if (!fluidChanged) {
            return;
        }
        Level level = getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        fluidChanged = false;
        lastAlloy = HeatSmelterLogic.tryAlloyOnce(level, fluidTank, lastAlloy);
    }

    @Override
    public void onCreated(Level world) {
        biomeAmbientTemp = calculateAverageAmbientTemperature(world);
        //The containers restored from the multiblock cache carry the capacities they were saved with, and applying the
        //cache overwrote the volume scaling that the validator's postcheck applied before it. Re-apply it (a no-op
        //for an unchanged volume; setHeatCapacity keeps the stored heat in step with the ambient baseline) so that the
        //ambient baseline and the clamping done by super line up with the current structure size again.
        configure(getVolume());
        super.onCreated(world);
        heatTransfer.invalidate();
        //Absorb each standalone member block's containers into the shared brain, then empty them so the per-block
        //containers become dormant. A member that still references the multiblock cache has already handed its contents
        //over to that cache, whose contents were restored into the shared containers above, so it is left untouched:
        //absorbing it as well would count its share a second time.
        double absorbedHeat = 0;
        for (BlockPos pos : locations) {
            BlockEntity tile = WorldUtils.getTileEntity(world, pos);
            if (!(tile instanceof TileEntityHeatSmelter smelter) || smelter.getCacheID() != null) {
                continue;
            }
            inputSlot.insertItem(smelter.getInputSlot().getStack().copy(), Action.EXECUTE, AutomationType.INTERNAL);
            outputSlot.insertItem(smelter.getOutputSlot().getStack().copy(), Action.EXECUTE, AutomationType.INTERNAL);
            fuelSlot.insertItem(smelter.getFuelSlot().getStack().copy(), Action.EXECUTE, AutomationType.INTERNAL);
            smelter.getInputSlot().setStackUnchecked(ItemStack.EMPTY);
            smelter.getOutputSlot().setStackUnchecked(ItemStack.EMPTY);
            smelter.getFuelSlot().setStackUnchecked(ItemStack.EMPTY);
            for (FluidStack fluid : smelter.getFluidTank().getFluids()) {
                fluidTank.insert(fluid.copy(), Action.EXECUTE, AutomationType.INTERNAL);
            }
            smelter.getFluidTank().setEmpty();
            //A member's heat includes its own ambient baseline, which the volume-scaled shared capacitor already
            //accounts for once, so only the energy it stores above its ambient is transferred
            BasicHeatCapacitor memberCapacitor = smelter.getHeatCapacitor();
            double memberAmbient = HeatAPI.getAmbientTemp(world, pos);
            absorbedHeat += memberCapacitor.getHeat() - memberCapacitor.getHeatCapacity() * memberAmbient;
            //Leave the member's capacitor at its ambient baseline, matching its emptied item and fluid containers
            memberCapacitor.setHeat(memberCapacitor.getHeatCapacity() * memberAmbient);
        }
        if (absorbedHeat != 0) {
            heatCapacitor.setHeat(heatCapacitor.getHeat() + absorbedHeat);
        }
    }

    @Override
    public void readUpdateTag(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        //Note: super.readUpdateTag syncs the volume, which re-configures the volume-scaled capacities via setVolume
        super.readUpdateTag(tag, provider);
        NBTUtils.setCompoundIfPresent(tag, "smelter_fluid", nbt -> fluidTank.deserializeNBT(provider, nbt));
        NBTUtils.setCompoundIfPresent(tag, "smelter_heat", nbt -> heatCapacitor.deserializeNBT(provider, nbt));
        inputSlot.deserializeNBT(provider, tag.getCompound("smelter_input"));
        outputSlot.deserializeNBT(provider, tag.getCompound("smelter_output"));
        fuelSlot.deserializeNBT(provider, tag.getCompound("smelter_fuel"));
    }

    @Override
    public void writeUpdateTag(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider provider) {
        super.writeUpdateTag(tag, provider);
        tag.put("smelter_fluid", fluidTank.serializeNBT(provider));
        tag.put("smelter_heat", heatCapacitor.serializeNBT(provider));
        CompoundTag inputTag = inputSlot.serializeNBT(provider);
        if (!inputTag.isEmpty()) {
            tag.put("smelter_input", inputTag);
        }
        CompoundTag outputTag = outputSlot.serializeNBT(provider);
        if (!outputTag.isEmpty()) {
            tag.put("smelter_output", outputTag);
        }
        CompoundTag fuelTag = fuelSlot.serializeNBT(provider);
        if (!fuelTag.isEmpty()) {
            tag.put("smelter_fuel", fuelTag);
        }
    }

    @Override
    public void remove(Level world, Structure oldStructure) {
        super.remove(world, oldStructure);
        // Drop the cached neighbour capabilities so a torn down structure does not keep them alive
        heatTransfer.invalidate();
        lastAlloy = null;
        progress = 0;
        processing = false;
        fluidChanged = false;
    }
}
