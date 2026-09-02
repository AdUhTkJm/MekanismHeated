package io.aduhtkjm.mekanismheated.mixin;

import mekanism.api.heat.IHeatHandler;
import mekanism.common.capabilities.heat.ITileHeatHandler;
import mekanism.common.util.EnumUtils;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Re-enable Mekanism's calorimetry-based adjacent heat transfer, which is commented out upstream in
 * {@link ITileHeatHandler#simulateAdjacent()}. The stock implementation transfers relative to the
 * ambient temperature; this instead transfers relative to the equilibrium (calorimetry) temperature
 * between this handler and the adjacent sink.
 *
 * <p>Targeting the interface (rather than a concrete class) applies this to every {@link ITileHeatHandler}
 * implementor: all Mekanism tiles, heat-pipe transmitters, the entangloporter, and multiblocks.
 */
@Mixin(ITileHeatHandler.class)
public interface MixinITileHeatHandler extends ITileHeatHandler {

    @Override
    default double simulateAdjacent() {
        double adjacentTransfer = 0;
        for (Direction side : EnumUtils.DIRECTIONS) {
            IHeatHandler sink = getAdjacent(side);
            if (sink != null) {
                double temp = getTotalTemperature(side);
                double sinkTemp = sink.getTotalTemperature();
                if (temp <= sinkTemp) {
                    //If our temperature is lower than (or equal to) the sink, skip: the sink handles the
                    // transfer when it simulates, which also avoids the transfer happening twice per tick.
                    continue;
                }
                double heatCapacity = getTotalHeatCapacity(side);
                double sinkHeatCapacity = sink.getTotalHeatCapacity();
                //Calculate the target temperature using calorimetry
                double finalTemp = (temp * heatCapacity + sinkTemp * sinkHeatCapacity) / (heatCapacity + sinkHeatCapacity);
                double invConduction = sink.getTotalInverseConduction() + getTotalInverseConductionCoefficient(side);
                double tempToTransfer = (temp - finalTemp) / invConduction;
                double heatToTransfer = tempToTransfer * heatCapacity;
                handleHeat(-heatToTransfer, side);
                //Note: Our sinks in mek are "lazy" but they will update the next tick if needed
                sink.handleHeat(heatToTransfer);
                adjacentTransfer = incrementAdjacentTransfer(adjacentTransfer, tempToTransfer, side);
            }
        }
        return adjacentTransfer;
    }
}
