package io.aduhtkjm.mekanismheated.tile.multiblock;

import io.aduhtkjm.mekanismheated.Config;
import io.aduhtkjm.mekanismheated.recipe.ModRecipeTypes;
import io.aduhtkjm.mekanismheated.recipe.RetroentropicArrayRecipe;
import mekanism.api.Action;
import mekanism.api.AutomationType;
import mekanism.api.IContentsListener;
import mekanism.api.heat.HeatAPI;
import mekanism.api.recipes.cache.CachedRecipe;
import mekanism.common.capabilities.heat.BasicHeatCapacitor;
import mekanism.common.inventory.container.sync.dynamic.ContainerSync;
import mekanism.common.inventory.slot.InputInventorySlot;
import mekanism.common.inventory.slot.OutputInventorySlot;
import mekanism.common.lib.multiblock.MultiblockData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
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
    private int backtracked;
    @ContainerSync
    public BasicHeatCapacitor heatCapacitor;
    @ContainerSync
    public InputInventorySlot inputSlot;
    @ContainerSync
    private double lastEnvironmentLoss;
    private double lastTransferLoss;
    private boolean processing;
    @Nullable
    RetroentropicArrayRecipe cachedRecipe = null;

    public OutputInventorySlot outputSlot;

    private double biomeAmbientTemp;

    public RetroentropicArrayData(BlockEntity tile) {
        super(tile);
        biomeAmbientTemp = HeatAPI.getAmbientTemp(tile.getLevel(), tile.getBlockPos());
        IContentsListener listener = createSaveAndComparator();

        double capacity = Config.RetroentropicArray.HEAT_CAPACITY.get();
        double invCdt = Config.RetroentropicArray.INVERSE_CONDUCTION_COEFFICIENT.get();
        double invIns = Config.RetroentropicArray.INVERSE_INSULATION_COEFFICIENT.get();
        heatCapacitor = BasicHeatCapacitor.create(capacity, invCdt, invIns, () -> biomeAmbientTemp, listener);
        inputSlot = InputInventorySlot.at(listener, 64, 35);
        outputSlot = OutputInventorySlot.at(listener, 116, 35);
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
        updateHeatCapacitors(null);
        needsPacket |= processRecipes(world);
        return needsPacket;
    }

    private boolean processRecipes(Level level) {
        boolean wasProcessing = processing;

        ItemStack input = inputSlot.getStack();
        if (input.isEmpty())
            return wasProcessing != (processing = false);

        // The retroentropic array only works below <0 Kelvin.
        if (heatCapacitor.getTemperature() >= 0)
            return wasProcessing != (processing = false);

        if (cachedRecipe == null || !cachedRecipe.test(input)) {
            var recipe = level.getRecipeManager().getRecipeFor(
                ModRecipeTypes.TYPE_RETROENTROPIC_ARRAY.value(), new SingleRecipeInput(input), level
            );
            if (recipe.isEmpty())
                return wasProcessing != (processing = false);
            cachedRecipe = recipe.get().value();
        }

        if (++backtracked < cachedRecipe.getDuration())
            return wasProcessing == (processing = true);

        backtracked = 0;
        operate(input, cachedRecipe);
        return wasProcessing != processing;
    }

    private void operate(ItemStack input, RetroentropicArrayRecipe recipe) {
        int inputAmt = (int) recipe.getInput().getNeededAmount(input);
        var output = recipe.getOutput(ItemStack.EMPTY);
        // Too less items to extract.
        if (inputSlot.extractItem(inputAmt, Action.SIMULATE, AutomationType.INTERNAL).getCount() != inputAmt)
            return;
        // Too many items to insert.
        if (!outputSlot.insertItem(output, Action.SIMULATE, AutomationType.INTERNAL).isEmpty())
            return;

        inputSlot.extractItem(inputAmt, Action.EXECUTE, AutomationType.INTERNAL);
        outputSlot.insertItem(output, Action.EXECUTE, AutomationType.INTERNAL);
    }

    public int getBacktracked() {
        return backtracked;
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
