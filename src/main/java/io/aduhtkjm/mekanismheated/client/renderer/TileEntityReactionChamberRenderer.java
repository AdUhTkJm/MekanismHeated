package io.aduhtkjm.mekanismheated.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.aduhtkjm.mekanismheated.tank.MultiFluidChemicalTank;
import io.aduhtkjm.mekanismheated.tile.TileEntityReactionChamber;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mekanism.api.chemical.ChemicalStack;
import mekanism.client.render.MekanismRenderer;
import mekanism.client.render.MekanismRenderer.Model3D;
import mekanism.client.render.RenderResizableCuboid.FaceDisplay;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.RenderTypeGroup;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.system.NonnullDefault;

/**
 * Renders the contents of the reaction chamber's {@link MultiFluidChemicalTank} in-world, visible through the machine's
 * glass. It mirrors the layout of {@code GuiStackedFluidChemicalGauge}: liquids form solid bands stacked from the bottom
 * of the window upward (in tank order), while chemicals - which are generally gases - form translucent bands hanging from
 * the top of the window downward. Each band is sized by its share of the shared pool's total capacity, so the empty space
 * between the two stacks (if any) is the pool's unused headspace.
 *
 * <p>The contents are drawn across the whole block footprint, inset slightly from the faces, over the glass's vertical
 * span (y 2..14 in the {@code reaction_chamber} model). The opaque machine parts (top/bottom caps and the central pillar)
 * are baked into the terrain already, so their depth hides the parts of the content they would occlude; only what is
 * visible through the glass windows is actually seen.
 */
@NonnullDefault
public class TileEntityReactionChamberRenderer implements BlockEntityRenderer<TileEntityReactionChamber> {

    private static final float GLASS_MIN_Y = 2.0F / 16.0F;
    private static final float GLASS_MAX_Y = 14.0F / 16.0F;
    private static final float GLASS_HEIGHT = GLASS_MAX_Y - GLASS_MIN_Y;
    private static final float CONTENT_INSET = 0.5F / 16.0F;
    private static final float CONTENT_SIZE = 1.0F - 2.0F * CONTENT_INSET;

    private final BlockRenderDispatcher blockRenderer;
    private final BlockEntityRendererProvider.Context context;
    private final Map<ResourceLocation, BakedModel> fluidModelCache = new HashMap<>();

    public TileEntityReactionChamberRenderer(BlockEntityRendererProvider.Context context) {
        this.context = context;
        this.blockRenderer = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(@NotNull TileEntityReactionChamber blockEntity, float partialTick, @NotNull PoseStack poseStack,
          @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        MultiFluidChemicalTank tank = blockEntity.contentsTank;
        if (tank == null || tank.isEmpty()) {
            return;
        }
        int totalCapacity = tank.getTotalCapacity();
        if (totalCapacity <= 0) {
            return;
        }
        List<FluidStack> fluids = tank.getFluids();
        List<ChemicalStack> chemicals = tank.getChemicals();
        if (fluids.isEmpty() && chemicals.isEmpty()) {
            return;
        }

        BlockState blockState = blockEntity.getBlockState();

        //The model and the contents are both symmetric about the block's vertical axis, so no facing transform is needed.

        //Liquids stack from the bottom of the window upward, in tank order
        float yCursor = GLASS_MIN_Y;
        for (FluidStack fluid : fluids) {
            float ratio = (float) fluid.getAmount() / totalCapacity;
            if (ratio <= 0.0F) {
                continue;
            }
            float layerHeight = GLASS_HEIGHT * ratio;
            renderFluidBand(fluid, yCursor, layerHeight, blockState, poseStack, bufferSource, packedLight, packedOverlay);
            yCursor += layerHeight;
        }

        //Chemicals (generally gases) hang from the top of the window downward, in tank order
        float chemicalCursor = GLASS_MAX_Y;
        for (ChemicalStack chemical : chemicals) {
            float ratio = (float) chemical.getAmount() / totalCapacity;
            if (ratio <= 0.0F) {
                continue;
            }
            float layerHeight = GLASS_HEIGHT * ratio;
            chemicalCursor -= layerHeight;
            renderGasBand(chemical, chemicalCursor, chemicalCursor + layerHeight, ratio, blockEntity.getBlockPos(),
                  poseStack, bufferSource, packedLight, packedOverlay);
        }
    }

    private void renderFluidBand(FluidStack fluid, float yCursor, float layerHeight, BlockState blockState, PoseStack poseStack,
          MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        ResourceLocation fluidId = fluid.getFluid().builtInRegistryHolder().key().location();
        BakedModel model = fluidModelCache.computeIfAbsent(fluidId, id -> buildFluidModel(fluid));

        int tint = IClientFluidTypeExtensions.of(fluid.getFluid()).getTintColor();

        poseStack.pushPose();
        poseStack.translate(CONTENT_INSET, yCursor, CONTENT_INSET);
        poseStack.scale(CONTENT_SIZE, layerHeight, CONTENT_SIZE);
        renderModel(model, blockState, poseStack, bufferSource, packedLight, packedOverlay,
              ((tint >> 16) & 0xFF) / 255.0F, ((tint >> 8) & 0xFF) / 255.0F, (tint & 0xFF) / 255.0F);
        poseStack.popPose();
    }

    private BakedModel buildFluidModel(FluidStack fluid) {
        ResourceLocation texture = IClientFluidTypeExtensions.of(fluid.getFluid()).getStillTexture();
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(texture);
        List<BakedQuad> quads = new ArrayList<>(6);

        //6 faces with no cullface - all faces are always rendered
        for (Direction dir : Direction.values()) {
            addFace(quads, sprite, dir);
        }

        SimpleBakedModel.Builder builder = new SimpleBakedModel.Builder(
              true, false, false, ItemTransforms.NO_TRANSFORMS, ItemOverrides.EMPTY);
        quads.forEach(builder::addUnculledFace);
        return builder.particle(sprite).build(new RenderTypeGroup(RenderType.solid(), RenderType.solid()));
    }

    /**
     * Renders a single chemical (gas) band hanging from the top of the window. Only the four vertical sides are drawn: the
     * top is hidden behind the machine's cap and the bottom is a free gas boundary, and skipping the horizontal faces
     * keeps adjacent stacked gas bands from double-blending or z-fighting along their shared plane. The gas is translucent,
     * fading out as its share of the pool shrinks (like Mekanism's gaseous contents).
     */
    private void renderGasBand(ChemicalStack chemical, float yBottom, float yTop, float fillRatio, BlockPos pos, PoseStack poseStack,
          MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        TextureAtlasSprite sprite = MekanismRenderer.getChemicalTexture(chemical);
        if (sprite == null) {
            return;
        }
        Model3D model = new Model3D()
              .setTexture(sprite)
              .setSideRender(Direction.DOWN, false)
              .setSideRender(Direction.UP, false)
              .xBounds(CONTENT_INSET, CONTENT_INSET + CONTENT_SIZE)
              .yBounds(yBottom, yTop)
              .zBounds(CONTENT_INSET, CONTENT_INSET + CONTENT_SIZE);
        float alpha = Math.min(1.0F, fillRatio + 0.2F);
        int argb = MekanismRenderer.getColorARGB(chemical.getChemicalTint(), alpha);
        VertexConsumer buffer = bufferSource.getBuffer(Sheets.translucentCullBlockSheet());
        Camera camera = context.getBlockEntityRenderDispatcher().camera;
        MekanismRenderer.renderObject(model, poseStack, buffer, argb, packedLight, packedOverlay, FaceDisplay.FRONT, camera, pos);
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
     * The four corners of the unit cube face (0..16) with the given outward direction, ordered counter-clockwise when
     * viewed from outside so the face is front-facing and survives the back-face culling of {@link RenderType#solid()}.
     * Each corner is {x, y, z, u, v}.
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

    private void renderModel(BakedModel model, BlockState blockState, PoseStack poseStack, MultiBufferSource bufferSource,
          int packedLight, int packedOverlay, float red, float green, float blue) {
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderTypeHelper.getEntityRenderType(RenderType.solid(), false));
        this.blockRenderer.getModelRenderer().renderModel(
              poseStack.last(), vertexConsumer, blockState, model,
              red, green, blue, packedLight, packedOverlay, ModelData.EMPTY, RenderType.solid());
    }
}
