package io.aduhtkjm.mekanismheated.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.aduhtkjm.mekanismheated.Mod;
import io.aduhtkjm.mekanismheated.block.temperaturecontroller.TemperatureControllerBlock;
import io.aduhtkjm.mekanismheated.tile.TileEntityTemperatureController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.lwjgl.system.NonnullDefault;

/**
 * Draws the 6x16 pixel window on the Temperature Controller's front face, one flat quad per row: the bottom
 * {@code displayLevel} rows are lit with the temperature colour ramp (green at the bottom through to red at the top)
 * and the rest are the shade of the panel around the window.
 *
 * <p>The number of lit rows is computed on the server — only the server sees the per-chunk ambient temperature delta —
 * and arrives through the reduced update tag, so this class holds no state and reads nothing but the synced level.
 *
 * <p>Each row is a plain coloured quad, but the texture it samples is not incidental: the quads use the block atlas
 * sheet, whose window pixels in {@code textures/block/temperature_controller/front.png} are pure white, so the row
 * colour comes through unchanged while the quad still picks up the block's own lighting from the lightmap. The block
 * atlas is also the one thing that has to be used here — a render type with a texture of its own would not be flushed
 * with the other block entities and would be drawn at the end of the frame instead.
 */
@NonnullDefault
public class TileEntityTemperatureControllerRenderer implements BlockEntityRenderer<TileEntityTemperatureController> {

    /**
     * The block's front texture, whose window is the flat white the row colours are multiplied onto.
     */
    public static final ResourceLocation FRONT_TEXTURE = Mod.rl("block/temperature_controller/front");

    /**
     * The window's bounds within the front texture, in texture pixels: {@code x = 5..10} inclusive for all of
     * {@code y = 0..15}. In block coordinates that is {@code 5/16} to {@code 11/16} across and the full height.
     */
    private static final float WINDOW_MIN_X = 5.0F / 16.0F;
    private static final float WINDOW_MAX_X = 11.0F / 16.0F;
    /**
     * A point inside the white window, as a fraction of the texture, used for every vertex: the window is uniform, so
     * one sample is all a flat quad needs. It stays clear of the window's edge whatever the atlas resolution is.
     */
    private static final float WINDOW_U = 0.5F;
    private static final float WINDOW_V = 0.5F;
    /**
     * Just outside the block's front plane ({@code z = 0} for the unrotated north face), so the strip covers the
     * window texture instead of z-fighting with it.
     */
    private static final float STRIP_Z = -0.002F;
    /**
     * Rows that are not lit: the shade of the panel around the window in the front texture, so an empty window reads
     * as a window that is off rather than as a hole.
     */
    private static final int UNLIT_COLOR = 0xFF565656;
    /**
     * The colour of a lit row, indexed by row from the bottom. Hue sweeps from 120 degrees (green, cold) to 0 degrees
     * (red, hot) in 8 degree steps at full saturation and value — a plain green-to-red RGB interpolation would muddy
     * through olive instead of passing through yellow and orange.
     */
    private static final int[] ROW_COLORS = {
          0xFF00FF00, //  0: 120
          0xFF22FF00, //  1: 112
          0xFF44FF00, //  2: 104
          0xFF66FF00, //  3:  96
          0xFF88FF00, //  4:  88
          0xFFAAFF00, //  5:  80
          0xFFCCFF00, //  6:  72
          0xFFEEFF00, //  7:  64
          0xFFFFEE00, //  8:  56
          0xFFFFCC00, //  9:  48
          0xFFFFAA00, // 10:  40
          0xFFFF8800, // 11:  32
          0xFFFF6600, // 12:  24
          0xFFFF4400, // 13:  16
          0xFFFF2200, // 14:   8
          0xFFFF0000  // 15:   0
    };

    public TileEntityTemperatureControllerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TileEntityTemperatureController blockEntity, float partialTick, PoseStack poseStack,
          MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BlockState blockState = blockEntity.getBlockState();
        int level = blockEntity.getDisplayLevel();
        //Looked up per frame rather than cached: the atlas and its sprite positions are rebuilt from scratch whenever
        //the resources are reloaded, which would leave a cached sprite pointing into the old atlas.
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(FRONT_TEXTURE);
        float u = sprite.getU(WINDOW_U);
        float v = sprite.getV(WINDOW_V);

        poseStack.pushPose();
        applyFacingTransform(poseStack, blockState);
        VertexConsumer vertexConsumer = bufferSource.getBuffer(Sheets.cutoutBlockSheet());
        PoseStack.Pose pose = poseStack.last();
        for (int row = 0; row < TileEntityTemperatureController.DISPLAY_ROWS; row++) {
            int color = row < level ? ROW_COLORS[row] : UNLIT_COLOR;
            float minY = (float) row / TileEntityTemperatureController.DISPLAY_ROWS;
            float maxY = (float) (row + 1) / TileEntityTemperatureController.DISPLAY_ROWS;
            //Counter-clockwise seen from outside the front face, which is the winding the block baker gives a north
            //face — the sheet culls back faces, so the wrong order would make the strip invisible from the front.
            addVertex(vertexConsumer, pose, WINDOW_MIN_X, minY, color, u, v, packedLight, packedOverlay);
            addVertex(vertexConsumer, pose, WINDOW_MIN_X, maxY, color, u, v, packedLight, packedOverlay);
            addVertex(vertexConsumer, pose, WINDOW_MAX_X, maxY, color, u, v, packedLight, packedOverlay);
            addVertex(vertexConsumer, pose, WINDOW_MAX_X, minY, color, u, v, packedLight, packedOverlay);
        }
        poseStack.popPose();
    }

    private static void addVertex(VertexConsumer vertexConsumer, PoseStack.Pose pose, float x, float y, int color,
          float u, float v, int packedLight, int packedOverlay) {
        vertexConsumer.addVertex(pose, x, y, STRIP_Z)
              .setColor(color)
              .setUv(u, v)
              .setOverlay(packedOverlay)
              .setLight(packedLight)
              //The unrotated front face points north, i.e. along -Z; the pose turns the normal along with the quad.
              .setNormal(pose, 0.0F, 0.0F, -1.0F);
    }

    /**
     * Rotates the whole block so that {@code z = 0} is the face the player sees, the same transformation the blockstate
     * JSON applies to the model. The renderer bypasses that rotation, so it has to apply it itself.
     */
    private static void applyFacingTransform(PoseStack poseStack, BlockState blockState) {
        poseStack.translate(0.5D, 0.5D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(getFacingRotation(blockState.getValue(TemperatureControllerBlock.FACING))));
        poseStack.translate(-0.5D, -0.5D, -0.5D);
    }

    private static float getFacingRotation(Direction facing) {
        return switch (facing) {
            // Block model JSON Y rotations use the opposite sign from Axis.YP rotations.
            case EAST -> -90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };
    }

    @Override
    public AABB getRenderBoundingBox(TileEntityTemperatureController blockEntity) {
        //The strip sits a fraction outside the block, so inflate slightly and let floating point drift in the frustum
        //planes never cull it while it is exactly on screen.
        return BlockEntityRenderer.super.getRenderBoundingBox(blockEntity).inflate(0.1D);
    }
}
