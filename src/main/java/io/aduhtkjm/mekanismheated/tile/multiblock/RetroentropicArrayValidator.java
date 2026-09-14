package io.aduhtkjm.mekanismheated.tile.multiblock;

import io.aduhtkjm.mekanismheated.registries.ModBlocks;
import mekanism.common.lib.math.voxel.VoxelCuboid;
import mekanism.common.lib.multiblock.CuboidStructureValidator;
import mekanism.common.lib.multiblock.FormationProtocol;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A 3x4x3 cuboid with an inner 1x2x1 empty space. The default CuboidStructureValidator already does that for us.
 */
public class RetroentropicArrayValidator extends CuboidStructureValidator<RetroentropicArrayData> {

    public static final VoxelCuboid SHAPE = new VoxelCuboid(3, 4, 3);

    public RetroentropicArrayValidator() {
        super(SHAPE, SHAPE);
    }

    @Override
    protected FormationProtocol.CasingType getCasingType(BlockState state) {
        return state.getBlock() == ModBlocks.RETROENTROPIC_ARRAY_CASING.get() ? FormationProtocol.CasingType.FRAME : FormationProtocol.CasingType.INVALID;
    }
}
