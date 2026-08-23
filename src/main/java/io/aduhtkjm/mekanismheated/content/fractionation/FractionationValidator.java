package io.aduhtkjm.mekanismheated.content.fractionation;

import io.aduhtkjm.mekanismheated.Config;
import io.aduhtkjm.mekanismheated.ModLang;
import io.aduhtkjm.mekanismheated.block.fractionation.DistillationTrayBlock;
import io.aduhtkjm.mekanismheated.registries.ModBlocks;
import io.aduhtkjm.mekanismheated.tile.multiblock.TileEntityThermalFractionationController;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.Arrays;
import java.util.EnumSet;
import mekanism.common.MekanismLang;
import mekanism.common.content.blocktype.BlockType;
import mekanism.common.lib.math.voxel.VoxelCuboid;
import mekanism.common.lib.math.voxel.VoxelCuboid.CuboidSide;
import mekanism.common.lib.math.voxel.VoxelCuboid.WallRelative;
import mekanism.common.lib.multiblock.CuboidStructureValidator;
import mekanism.common.lib.multiblock.FormationProtocol;
import mekanism.common.lib.multiblock.FormationProtocol.CasingType;
import mekanism.common.lib.multiblock.FormationProtocol.FormationResult;
import mekanism.common.lib.multiblock.FormationProtocol.StructureRequirement;
import mekanism.common.lib.multiblock.StructureHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

public class FractionationValidator extends CuboidStructureValidator<FractionationMultiblockData> {

    private static final VoxelCuboid MIN_CUBOID = new VoxelCuboid(4, 3, 4);
    private static final VoxelCuboid MAX_CUBOID = new VoxelCuboid(4, 18, 4);

    private boolean foundController = false;

    /**
     * Number of distillation trays per interior y-level, tracked while validating the inner nodes.
     */
    private final Int2IntMap trayCounts = new Int2IntArrayMap();

    @Override
    protected FormationResult validateFrame(FormationProtocol<FractionationMultiblockData> ctx, BlockPos pos, BlockState state, CasingType type, boolean needsFrame) {
        boolean controller = structure.getTile(pos) instanceof TileEntityThermalFractionationController;
        if (foundController && controller) {
            //Ensure we don't allow ignoring the failure as if there are multiple in the corners which are ignored spots
            // it is possible then we will form with multiple controllers
            return FormationResult.fail(MekanismLang.MULTIBLOCK_INVALID_CONTROLLER_CONFLICT, pos, true);
        }
        foundController |= controller;
        return super.validateFrame(ctx, pos, state, type, needsFrame);
    }

    @Override
    protected StructureRequirement getStructureRequirement(BlockPos pos) {
        WallRelative relative = cuboid.getWallRelative(pos);
        if (pos.getY() == cuboid.getMaxPos().getY()) {
            if (relative.isOnCorner()) {
                return StructureRequirement.IGNORED;
            } else if (!relative.isOnEdge()) {
                return StructureRequirement.INNER;
            } else {
                return StructureRequirement.OTHER;
            }
        }
        return super.getStructureRequirement(pos);
    }

    @Override
    protected CasingType getCasingType(BlockState state) {
        Block block = state.getBlock();
        if (BlockType.is(block, ModBlocks.THERMAL_FRACTIONATION_CASING_TYPE)) {
            return CasingType.FRAME;
        } else if (BlockType.is(block, ModBlocks.THERMAL_FRACTIONATION_VALVE_TYPE)) {
            return CasingType.VALVE;
        } else if (BlockType.is(block, ModBlocks.THERMAL_FRACTIONATION_CONTROLLER_TYPE)) {
            return CasingType.OTHER;
        }
        return CasingType.INVALID;
    }

    @Override
    protected boolean validateInner(BlockState state, Long2ObjectMap<ChunkAccess> chunkMap, BlockPos pos) {
        if (state.isAir()) {
            return true;
        }
        if (state.getBlock() instanceof DistillationTrayBlock) {
            //Note: pos is a mutable position reused by the caller, so only read from it
            trayCounts.put(pos.getY(), trayCounts.get(pos.getY()) + 1);
            return true;
        }
        return false;
    }

    @Override
    public boolean precheck() {
        cuboid = StructureHelper.fetchCuboid(structure, MIN_CUBOID, MAX_CUBOID, EnumSet.complementOf(EnumSet.of(CuboidSide.TOP)), 8);
        trayCounts.clear();
        foundController = false;
        return cuboid != null;
    }

    @Override
    public FormationResult postcheck(FractionationMultiblockData structure, Long2ObjectMap<ChunkAccess> chunkMap) {
        if (!foundController) {
            return FormationResult.fail(MekanismLang.MULTIBLOCK_INVALID_NO_CONTROLLER);
        }
        int traysPerLayer = (cuboid.length() - 2) * (cuboid.width() - 2);
        BlockPos min = cuboid.getMinPos();
        BlockPos max = cuboid.getMaxPos();
        int[] trayLayers = trayCounts.keySet().toIntArray();
        Arrays.sort(trayLayers);
        for (int trayLayer : trayLayers) {
            if (trayCounts.get(trayLayer) != traysPerLayer) {
                //Report the first interior spot of that layer as the failure location
                return FormationResult.fail(ModLang.MULTIBLOCK_INVALID_INCOMPLETE_TRAY_LAYER, new BlockPos(min.getX() + 1, trayLayer, min.getZ() + 1));
            }
        }
        FormationResult layoutResult = validateLayout(trayLayers, min.getY(), max.getY());
        if (!layoutResult.isFormed()) {
            return layoutResult;
        }
        applyLayout(structure, trayLayers, min.getY(), max.getY());
        return FormationResult.SUCCESS;
    }

    /**
     * Validates that every compartment formed by the trays has at least one open interior layer: the lowest tray needs
     * sump space below it, consecutive trays need a gap between them, and no tray may sit directly below the top opening.
     */
    private FormationResult validateLayout(int[] trayLayers, int minY, int maxY) {
        for (int i = 0; i < trayLayers.length; i++) {
            int y = trayLayers[i];
            if (y == maxY) {
                //A tray directly below the opening leaves no space to collect its fraction
                return FormationResult.fail(ModLang.MULTIBLOCK_INVALID_TRAY_TOP, interiorPos(y));
            }
            if (i == 0 ? y - (minY + 1) < 1 : y - trayLayers[i - 1] < 2) {
                return FormationResult.fail(ModLang.MULTIBLOCK_INVALID_TRAY_SPACING, interiorPos(i == 0 ? minY + 1 : y));
            }
        }
        return FormationResult.SUCCESS;
    }

    private BlockPos interiorPos(int y) {
        return new BlockPos(cuboid.getMinPos().getX() + 1, y, cuboid.getMinPos().getZ() + 1);
    }

    /**
     * Computes the sump/bank capacities from the tray layout and configures the layout (bounds, tray levels and
     * capacities) on the multiblock data.
     *
     * @param trayLayers Sorted y-levels of full distillation tray layers.
     */
    private void applyLayout(FractionationMultiblockData structure, int[] trayLayers, int minY, int maxY) {
        int fluidPerLayer = Config.Fractionation.FLUID_PER_LAYER.get();
        int[] bankCapacities = new int[trayLayers.length];
        for (int i = 0; i < trayLayers.length; i++) {
            int boundary = i + 1 < trayLayers.length ? trayLayers[i + 1] : maxY + 1;
            bankCapacities[i] = (boundary - (trayLayers[i] + 1)) * fluidPerLayer;
        }
        int sumpHeight = trayLayers.length == 0 ? maxY - (minY + 1) + 1 : trayLayers[0] - (minY + 1);
        structure.configureBanks(minY, maxY, trayLayers, sumpHeight * fluidPerLayer, bankCapacities);
    }
}
