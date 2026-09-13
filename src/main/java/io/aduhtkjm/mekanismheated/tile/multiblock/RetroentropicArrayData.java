package io.aduhtkjm.mekanismheated.tile.multiblock;

import io.aduhtkjm.mekanismheated.Config;
import mekanism.api.IContentsListener;
import mekanism.api.heat.HeatAPI;
import mekanism.common.capabilities.heat.BasicHeatCapacitor;
import mekanism.common.lib.multiblock.MultiblockData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class RetroentropicArrayData extends MultiblockData {
    /**
     * The amount of game days backtracked by the retroentropic array. <p>
     *
     * This is merely a plot-relevant description; it is actually nothing more than a progress bar.
     */
    private int backtracked;
    public BasicHeatCapacitor heatCapacitor;
    private double biomeAmbientTemp;

    public RetroentropicArrayData(BlockEntity tile) {
        super(tile);
        biomeAmbientTemp = HeatAPI.getAmbientTemp(tile.getLevel(), tile.getBlockPos());
        IContentsListener listener = createSaveAndComparator();

        double capacity = Config.RetroentropicArray.HEAT_CAPACITY.get();
        double invCdt = Config.RetroentropicArray.INVERSE_CONDUCTION_COEFFICIENT.get();
        double invIns = Config.RetroentropicArray.INVERSE_INSULATION_COEFFICIENT.get();
        heatCapacitor = BasicHeatCapacitor.create(capacity, invCdt, invIns, () -> biomeAmbientTemp, listener);
    }

    @Override
    public void onCreated(Level world) {
        biomeAmbientTemp = calculateAverageAmbientTemperature(world);
    }
}
