package io.aduhtkjm.mekanismheated.tile.multiblock;

import io.aduhtkjm.mekanismheated.registries.ModBlocks;
import mekanism.common.lib.multiblock.CuboidStructureValidator;
import mekanism.common.lib.multiblock.FormationProtocol;
import net.minecraft.world.level.block.state.BlockState;

public class RetroentropicArrayValidator extends CuboidStructureValidator<RetroentropicArrayData> {
    @Override
    protected FormationProtocol.CasingType getCasingType(BlockState state) {
        return state.getBlock() == ModBlocks.HEAT_SMELTER.get() ? FormationProtocol.CasingType.FRAME : FormationProtocol.CasingType.INVALID;
    }
}
