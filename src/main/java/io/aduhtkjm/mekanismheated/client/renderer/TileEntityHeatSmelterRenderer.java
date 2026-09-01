package io.aduhtkjm.mekanismheated.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.aduhtkjm.mekanismheated.block.heatsmelter.HeatSmelterBlock;
import io.aduhtkjm.mekanismheated.tile.TileEntityHeatSmelter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.RenderTypeGroup;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.fluids.FluidStack;
import org.lwjgl.system.NonnullDefault;

@NonnullDefault
public class TileEntityHeatSmelterRenderer implements BlockEntityRenderer<TileEntityHeatSmelter> {

    private static final float GLASS_MIN_Y = 2.0F / 16.0F;
    private static final float GLASS_MAX_Y = 14.0F / 16.0F;
    private static final float FLUID_INSET = 0.5F / 16.0F;
    private static final float FLUID_SIZE = 1.0F - 2.0F * FLUID_INSET;

    private final BlockRenderDispatcher blockRenderer;
    private final Map<ResourceLocation, FluidModelEntry> modelCache = new HashMap<>();

    public TileEntityHeatSmelterRenderer(BlockEntityRendererProvider.Context context) {
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(TileEntityHeatSmelter blockEntity, float partialTick, PoseStack poseStack,
          MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (blockEntity.fluidTank == null || blockEntity.fluidTank.isEmpty()) {
            return;
        }
        int totalCapacity = blockEntity.fluidTank.getTotalCapacity();
        if (totalCapacity <= 0) {
            return;
        }
        List<FluidStack> fluids = blockEntity.fluidTank.getFluids();
        if (fluids.isEmpty()) {
            return;
        }

        BlockState blockState = blockEntity.getBlockState();
        float glassHeight = GLASS_MAX_Y - GLASS_MIN_Y;

        poseStack.pushPose();
        applyFacingTransform(poseStack, blockState);

        //Render each fluid as a layer stacked from the bottom
        float yCursor = GLASS_MIN_Y;
        for (FluidStack fluid : fluids) {
            float ratio = (float) fluid.getAmount() / totalCapacity;
            if (ratio <= 0.0F) {
                continue;
            }
            float layerHeight = glassHeight * ratio;

            ResourceLocation fluidId = fluid.getFluid().builtInRegistryHolder().key().location();
            FluidModelEntry entry = modelCache.computeIfAbsent(fluidId, id -> buildFluidModel(fluid));

            int tint = IClientFluidTypeExtensions.of(fluid.getFluid()).getTintColor();

            poseStack.pushPose();
            poseStack.translate(FLUID_INSET, yCursor, FLUID_INSET);
            poseStack.scale(FLUID_SIZE, layerHeight, FLUID_SIZE);
            renderModel(entry.model, entry.renderType, blockState, poseStack, bufferSource, packedLight, packedOverlay,
                  ((tint >> 16) & 0xFF) / 255.0F, ((tint >> 8) & 0xFF) / 255.0F, (tint & 0xFF) / 255.0F);
            poseStack.popPose();

            yCursor += layerHeight;
        }

        poseStack.popPose();
    }

    private FluidModelEntry buildFluidModel(FluidStack fluid) {
        ResourceLocation texture = IClientFluidTypeExtensions.of(fluid.getFluid()).getStillTexture();
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(texture);
        List<BakedQuad> quads = new ArrayList<>(6);

        //6 faces with no cullface — all faces are always rendered
        for (Direction dir : Direction.values()) {
            addFace(quads, sprite, dir);
        }

        SimpleBakedModel.Builder builder = new SimpleBakedModel.Builder(
              true, false, false, ItemTransforms.NO_TRANSFORMS, ItemOverrides.EMPTY);
        quads.forEach(builder::addUnculledFace);
        return new FluidModelEntry(builder.particle(sprite).build(new RenderTypeGroup(RenderType.solid(), RenderType.solid())), RenderType.solid());
    }

    private static void addFace(List<BakedQuad> quads, TextureAtlasSprite sprite, Direction dir) {
        int[] vertices = new int[32];
        for (int i = 0; i < 4; i++) {
            int[] corner = faceCorners(dir)[i];
            packVertex(vertices, i * 8, sprite, corner[0], corner[1], corner[2], corner[3], corner[4]);
        }
        quads.add(new BakedQuad(vertices, 0, dir, sprite, false));
    }

    /**
     * The four corners of the unit cube face (0..16) with the given outward direction, ordered
     * counter-clockwise when viewed from outside so the face is front-facing and survives the
     * back-face culling of {@link RenderType#solid()}. Each corner is {x, y, z, u, v}.
     */
    private static int[][] faceCorners(Direction dir) {
        return switch (dir) {
            case DOWN -> new int[][]{{0, 0, 0, 0, 0}, {16, 0, 0, 16, 0}, {16, 0, 16, 16, 16}, {0, 0, 16, 0, 16}};
            case UP -> new int[][]{{0, 16, 16, 0, 16}, {16, 16, 16, 16, 16}, {16, 16, 0, 16, 0}, {0, 16, 0, 0, 0}};
            case NORTH -> new int[][]{{0, 0, 0, 0, 0}, {0, 16, 0, 0, 16}, {16, 16, 0, 16, 16}, {16, 0, 0, 16, 0}};
            case SOUTH -> new int[][]{{0, 0, 16, 0, 0}, {16, 0, 16, 16, 0}, {16, 16, 16, 16, 16}, {0, 16, 16, 0, 16}};
            case WEST -> new int[][]{{0, 0, 0, 0, 0}, {0, 0, 16, 16, 0}, {0, 16, 16, 16, 16}, {0, 16, 0, 0, 16}};
            case EAST -> new int[][]{{16, 0, 0, 0, 0}, {16, 16, 0, 0, 16}, {16, 16, 16, 16, 16}, {16, 0, 16, 16, 0}};
        };
    }

    private static void packVertex(int[] vertices, int offset, TextureAtlasSprite sprite,
          float x, float y, float z, float u, float v) {
        vertices[offset] = Float.floatToRawIntBits(x / 16.0F);
        vertices[offset + 1] = Float.floatToRawIntBits(y / 16.0F);
        vertices[offset + 2] = Float.floatToRawIntBits(z / 16.0F);
        vertices[offset + 3] = -1;
        vertices[offset + 4] = Float.floatToRawIntBits(sprite.getU(u / 16.0F));
        vertices[offset + 5] = Float.floatToRawIntBits(sprite.getV(v / 16.0F));
    }

    private void renderModel(BakedModel model, RenderType renderType, BlockState blockState, PoseStack poseStack,
          MultiBufferSource bufferSource, int packedLight, int packedOverlay,
          float red, float green, float blue) {
        ModelData modelData = ModelData.EMPTY;
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderTypeHelper.getEntityRenderType(renderType, false));
        this.blockRenderer.getModelRenderer().renderModel(
              poseStack.last(), vertexConsumer, blockState, model,
              red, green, blue, packedLight, packedOverlay, modelData, renderType);
    }

    private static void applyFacingTransform(PoseStack poseStack, BlockState blockState) {
        poseStack.translate(0.5D, 0.5D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(getFacingRotation(blockState.getValue(HeatSmelterBlock.FACING))));
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

    private record FluidModelEntry(BakedModel model, RenderType renderType) {
    }
}
