package io.aduhtkjm.mekanismheated.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.aduhtkjm.mekanismheated.Mod;
import io.aduhtkjm.mekanismheated.block.shaker.ShakerBlock;
import io.aduhtkjm.mekanismheated.tile.TileEntityShaker;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.NonnullDefault;

@NonnullDefault
public class TileEntityShakerRenderer implements BlockEntityRenderer<TileEntityShaker> {

    public static final net.minecraft.resources.ResourceLocation MODEL_LOCATION =
          Mod.rl("block/shaker/shaker");
    public static final net.minecraft.resources.ResourceLocation GLASS_MODEL_LOCATION =
          Mod.rl("block/shaker/glass");

    private static final float SHAKE_SPEED = 0.4F;
    private static final float SHAKE_ANGLE = 6.0F;
    private static final float SHAKER_TOP_Y = 7.0F / 16.0F;
    private static final float ITEM_SCALE = 0.5F;
    private static final float DEFAULT_ITEM_MIN_Y = -0.5F;

    private final BlockRenderDispatcher blockRenderer;
    private final ItemRenderer itemRenderer;
    private final BakedModel shakerModel;
    private final BakedModel glassModel;
    private ItemStack cachedItem = ItemStack.EMPTY;
    private float cachedItemMinY = DEFAULT_ITEM_MIN_Y;

    public TileEntityShakerRenderer(BlockEntityRendererProvider.Context context) {
        this.blockRenderer = context.getBlockRenderDispatcher();
        this.itemRenderer = context.getItemRenderer();
        this.shakerModel = this.blockRenderer.getBlockModelShaper().getModelManager()
              .getModel(ModelResourceLocation.standalone(MODEL_LOCATION));
        this.glassModel = this.blockRenderer.getBlockModelShaper().getModelManager()
              .getModel(ModelResourceLocation.standalone(GLASS_MODEL_LOCATION));
    }

    @Override
    public void render(TileEntityShaker blockEntity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        BlockState blockState = blockEntity.getBlockState();
        long gameTime = blockEntity.getLevel() == null ? 0L : blockEntity.getLevel().getGameTime();
        float animationTime = gameTime + partialTick;
        float shake = blockEntity.isShaking() ? Mth.sin(animationTime * SHAKE_SPEED) * SHAKE_ANGLE : 0.0F;
        float counterShake = blockEntity.isShaking() ? Mth.cos(animationTime * SHAKE_SPEED) * SHAKE_ANGLE : 0.0F;

        poseStack.pushPose();
        applyFacingTransform(poseStack, blockState);
        renderModel(this.glassModel, blockState, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();

        poseStack.pushPose();
        applyShakerTransform(poseStack, blockState, shake, counterShake);

        renderModel(this.shakerModel, blockState, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();

        ItemStack storedItem = blockEntity.getStoredItem();
        if (!storedItem.isEmpty()) {
            int itemSeed = blockEntity.getBlockPos().hashCode();
            float itemMinY = getItemMinY(storedItem, blockEntity.getLevel(), itemSeed);
            poseStack.pushPose();
            applyShakerTransform(poseStack, blockState, shake, counterShake);
            poseStack.translate(0.5D, SHAKER_TOP_Y - ITEM_SCALE * itemMinY, 0.4375D);
            poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
            this.itemRenderer.renderStatic(
                  storedItem, ItemDisplayContext.FIXED, packedLight, packedOverlay, poseStack, bufferSource,
                  blockEntity.getLevel(), itemSeed);
            poseStack.popPose();
        }
    }

    private float getItemMinY(ItemStack itemStack, Level level, int seed) {
        if (ItemStack.matches(this.cachedItem, itemStack)) {
            return this.cachedItemMinY;
        }

        this.cachedItem = itemStack.copy();
        BakedModel itemModel = this.itemRenderer.getModel(itemStack, level, null, seed);
        this.cachedItemMinY = calculateItemMinY(itemModel);
        return this.cachedItemMinY;
    }

    private static float calculateItemMinY(BakedModel itemModel) {
        if (itemModel.isCustomRenderer()) {
            return DEFAULT_ITEM_MIN_Y;
        }

        PoseStack itemPose = new PoseStack();
        BakedModel transformedModel = itemModel.applyTransform(ItemDisplayContext.FIXED, itemPose, false);
        itemPose.translate(-0.5F, -0.5F, -0.5F);
        Matrix4f transform = itemPose.last().pose();
        RandomSource random = RandomSource.create();
        float minY = Float.POSITIVE_INFINITY;

        for (Direction direction : Direction.values()) {
            random.setSeed(42L);
            minY = findMinimumY(transformedModel.getQuads(null, direction, random), transform, minY);
        }
        random.setSeed(42L);
        minY = findMinimumY(transformedModel.getQuads(null, null, random), transform, minY);

        return Float.isFinite(minY) ? minY : DEFAULT_ITEM_MIN_Y;
    }

    private static float findMinimumY(List<BakedQuad> quads, Matrix4f transform, float minY) {
        for (BakedQuad quad : quads) {
            int[] vertices = quad.getVertices();
            for (int vertex = 0; vertex < vertices.length; vertex += 8) {
                Vector4f position = transform.transform(new Vector4f(
                      Float.intBitsToFloat(vertices[vertex]),
                      Float.intBitsToFloat(vertices[vertex + 1]),
                      Float.intBitsToFloat(vertices[vertex + 2]),
                      1.0F));
                minY = Math.min(minY, position.y());
            }
        }
        return minY;
    }

    private static void applyShakerTransform(PoseStack poseStack, BlockState blockState, float shake, float counterShake) {
        poseStack.translate(0.5D, 0.5D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(getFacingRotation(blockState.getValue(ShakerBlock.FACING))));
        poseStack.mulPose(Axis.XP.rotationDegrees(shake));
        poseStack.mulPose(Axis.ZP.rotationDegrees(counterShake));
        poseStack.translate(-0.5D, -0.5D, -0.5D);
    }

    private static void applyFacingTransform(PoseStack poseStack, BlockState blockState) {
        poseStack.translate(0.5D, 0.5D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(getFacingRotation(blockState.getValue(ShakerBlock.FACING))));
        poseStack.translate(-0.5D, -0.5D, -0.5D);
    }

    private void renderModel(BakedModel model, BlockState blockState, PoseStack poseStack,
          MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        RandomSource random = RandomSource.create(42L);
        ModelData modelData = ModelData.EMPTY;
        for (RenderType renderType : model.getRenderTypes(blockState, random, modelData)) {
            VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderTypeHelper.getEntityRenderType(renderType, false));
            this.blockRenderer.getModelRenderer().renderModel(
                  poseStack.last(), vertexConsumer, blockState, model,
                  1.0F, 1.0F, 1.0F, packedLight, packedOverlay, modelData, renderType);
        }
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
}
