package io.aduhtkjm.mekanismheated.tile.multiblock;

import io.aduhtkjm.mekanismheated.Config;
import io.aduhtkjm.mekanismheated.Mod;
import io.aduhtkjm.mekanismheated.recipe.ModRecipeTypes;
import io.aduhtkjm.mekanismheated.recipe.RetroentropicArrayRecipe;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.heat.HeatAPI;
import mekanism.common.capabilities.heat.BasicHeatCapacitor;
import mekanism.common.inventory.container.sync.dynamic.ContainerSync;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.lib.multiblock.MultiblockData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * A machine that works only when temperatuer <0K. This needs rapidly switching the cooler, or just making the cooler
 * run on 1MJ/t.
 */
public class RetroentropicArrayData extends MultiblockData {
    /**
     * The amount of game days backtracked by the retroentropic array. <p>
     *
     * This is merely a plot-relevant description; it is actually nothing more than a progress bar.
     */
    @ContainerSync
    private int backtracked;
    /**
     * Duration of {@link #cachedRecipe} in ticks. Kept as a separate synced field because the recipe itself is only ever
     * resolved server-side, while the GUI and Jade need the duration to render the progress.
     */
    @ContainerSync
    private int duration;
    @ContainerSync
    public BasicHeatCapacitor heatCapacitor;
    public InputInventorySlot inputSlot;
    public OutputInventorySlot outputSlot;
    @ContainerSync
    private double lastEnvironmentLoss;
    @ContainerSync
    private double lastTransferLoss;
    @ContainerSync
    private boolean processing;
    @Nullable
    private RetroentropicArrayRecipe cachedRecipe = null;

    private double biomeAmbientTemp;

    public RetroentropicArrayData(BlockEntity tile) {
        super(tile);
        biomeAmbientTemp = HeatAPI.getAmbientTemp(tile.getLevel(), tile.getBlockPos());
        IContentsListener listener = createSaveAndComparator();

        double capacity = Config.RetroentropicArray.HEAT_CAPACITY.get();
        double invCdt = Config.RetroentropicArray.INVERSE_CONDUCTION_COEFFICIENT.get();
        double invIns = Config.RetroentropicArray.INVERSE_INSULATION_COEFFICIENT.get();
        heatCapacitor = BasicHeatCapacitor.create(capacity, invCdt, invIns, () -> biomeAmbientTemp, listener);
        // Containers have to be registered in the inherited lists, otherwise they are invisible to
        // capabilities, the multiblock cache and the GUI (a field alone exposes nothing).
        heatCapacitors.add(heatCapacitor);
        inventorySlots.add(inputSlot = InputInventorySlot.at(listener, 64, 35));
        inventorySlots.add(outputSlot = OutputInventorySlot.at(listener, 116, 35));
    }

    @Override
    public void onCreated(Level world) {
        biomeAmbientTemp = calculateAverageAmbientTemperature(world);
    }

    @Override
    public boolean tick(Level world) {
        boolean needsPacket = super.tick(world);
        HeatAPI.HeatTransfer transfer = simulate();
        lastEnvironmentLoss = transfer.environmentTransfer();
        lastTransferLoss = transfer.adjacentTransfer();
        Mod.LOGGER.info("env = {}, transfer = {}", lastEnvironmentLoss, lastTransferLoss);
        updateHeatCapacitors(null);
        needsPacket |= processRecipes(world);
        return needsPacket;
    }

    private boolean processRecipes(Level level) {
        boolean wasProcessing = processing;
        processing = false;

        ItemStack input = inputSlot.getStack();
        // The retroentropic array only works below 0 Kelvin.
        if (!input.isEmpty() && heatCapacitor.getTemperature() < 0) {
            if (cachedRecipe == null || !cachedRecipe.test(input)) {
                //The input changed, so restart the process from scratch and look the recipe up again.
                backtracked = 0;
                cachedRecipe = level.getRecipeManager()
                      .getRecipeFor(ModRecipeTypes.TYPE_RETROENTROPIC_ARRAY.value(), new SingleRecipeInput(input), level)
                      .map(RecipeHolder::value)
                      .orElse(null);
                duration = cachedRecipe == null ? 0 : cachedRecipe.getDuration();
            }
            if (cachedRecipe != null) {
                processing = true;
                if (backtracked < cachedRecipe.getDuration()) {
                    backtracked++;
                } else if (operate(input, cachedRecipe)) {
                    backtracked = 0;
                }
            }
        } else {
            // No input, or not cold enough: drop any cached recipe and reset the progress.
            cachedRecipe = null;
            backtracked = 0;
            duration = 0;
        }
        return wasProcessing != processing;
    }

    /**
     * @return {@code true} if one operation was performed and the progress should be reset.
     */
    private boolean operate(ItemStack input, RetroentropicArrayRecipe recipe) {
        int inputAmt = (int) recipe.getInput().getNeededAmount(input);
        if (inputAmt <= 0) {
            return false;
        }
        var output = recipe.getOutput(input.copyWithCount(inputAmt));
        if (output.isEmpty()) {
            return false;
        }
        // Too less items to extract.
        if (inputSlot.extractItem(inputAmt, Action.SIMULATE, AutomationType.INTERNAL).getCount() != inputAmt) {
            return false;
        }
        // Too many items to insert.
        if (!outputSlot.insertItem(output, Action.SIMULATE, AutomationType.INTERNAL).isEmpty()) {
            return false;
        }

        inputSlot.extractItem(inputAmt, Action.EXECUTE, AutomationType.INTERNAL);
        outputSlot.insertItem(output, Action.EXECUTE, AutomationType.INTERNAL);
        return true;
    }

    public int getBacktracked() {
        return backtracked;
    }

    /**
     * @return the duration of the current operation in ticks, or {@code 0} if no recipe is cached.
     */
    public int getDuration() {
        return duration;
    }

    /**
     * @return the progress of the current operation in the range {@code [0, 1]}, for the GUI progress bar.
     */
    public double getScaledProgress() {
        int duration = getDuration();
        return duration <= 0 ? 0 : Math.clamp((double) backtracked / duration, 0, 1);
    }

    public boolean isProcessing() {
        return processing;
    }

    /**
     * @return the current temperature of the array in Kelvin.
     */
    public double getTemperature() {
        return heatCapacitor.getTemperature();
    }

    public double getLastEnvironmentLoss() {
        return lastEnvironmentLoss;
    }

    public void setLastEnvironmentLoss(double lastEnvironmentLoss) {
        this.lastEnvironmentLoss = lastEnvironmentLoss;
    }

    public double getLastTransferLoss() {
        return lastTransferLoss;
    }

    public void setLastTransferLoss(double lastTransferLoss) {
        this.lastTransferLoss = lastTransferLoss;
    }
}
