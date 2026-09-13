package io.aduhtkjm.mekanismheated.tile.multiblock;

import io.aduhtkjm.mekanismheated.registries.ModBlocks;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import mekanism.common.lib.math.voxel.VoxelCuboid;
import mekanism.common.lib.multiblock.CuboidStructureValidator;
import mekanism.common.lib.multiblock.FormationProtocol.CasingType;
import mekanism.common.lib.multiblock.FormationProtocol.FormationResult;
import mekanism.common.lib.multiblock.FormationProtocol.StructureRequirement;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Validates a solid cuboid of {@code heat_smelter} blocks between 2x2x2 and 6x6x6. Because the structure is solid
 * (no hollow interior), every block is a {@code heat_smelter}: all positions are treated as frame/casing and must
 * be a heat smelter block.
 */
public class LargeHeatSmelterValidator extends CuboidStructureValidator<LargeHeatSmelterData> {

    private static final VoxelCuboid MIN_CUBOID = new VoxelCuboid(2, 2, 2);
    private static final VoxelCuboid MAX_CUBOID = new VoxelCuboid(6, 6, 6);

    public LargeHeatSmelterValidator() {
        super(MIN_CUBOID, MAX_CUBOID);
    }

    @Override
    protected StructureRequirement getStructureRequirement(BlockPos pos) {
        // Must be frame everywhere.
        return StructureRequirement.FRAME;
    }

    @Override
    protected CasingType getCasingType(BlockState state) {
        return state.getBlock() == ModBlocks.HEAT_SMELTER.get() ? CasingType.FRAME : CasingType.INVALID;
    }

    @Override
    public FormationResult postcheck(LargeHeatSmelterData structure, Long2ObjectMap<ChunkAccess> chunkMap) {
        FormationResult result = super.postcheck(structure, chunkMap);
        if (result.isFormed()) {
            // Scale the shared fluid tank and heat capacitor to the formed structure's volume
            structure.configure(structure.getVolume());
        }
        return result;
    }
}
