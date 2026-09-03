package io.aduhtkjm.mekanismheated.client;

import io.aduhtkjm.mekanismheated.Mod;
import io.aduhtkjm.mekanismheated.block.BlockFusedPipe;
import mekanism.api.tier.BaseTier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.RenderShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;
import org.lwjgl.system.NonnullDefault;

/**
 * Supplies the correct per-tier break/hit particles for the fused pipe.
 *
 * <p>The pipe is a single block whose blockstate picks a multipart model per tier. Vanilla resolves
 * a block's break particle icon through {@code BlockModelShaper.getTexture}, which for a multipart
 * model always returns the icon of the <em>first</em> selector ({@code fused_pipe/core} = basic
 * tier). That is why an advanced/elite/ultimate pipe still burst into basic-tier particles.
 *
 * <p>These hooks take over particle spawning for the block and feed each
 * {@link TerrainParticle} the sprite of the pipe's actual displayed tier, taken straight from the
 * block atlas (the same {@code <tier>_fused_pipe} texture the tier's core model declares as its
 * particle). Placement and motion mirror the vanilla logic, so only the texture differs.
 */
@NonnullDefault
@OnlyIn(Dist.CLIENT)
public class FusedPipeBlockExtensions implements IClientBlockExtensions {

    @Override
    public boolean addDestroyEffects(BlockState state, Level level, BlockPos pos, ParticleEngine manager) {
        SpriteSet sprite = tierSpriteSet(state);
        ClientLevel clientLevel = (ClientLevel) level;
        VoxelShape shape = state.getShape(level, pos);
        shape.forAllBoxes((x1, y1, z1, x2, y2, z2) -> {
            double sx = Math.min(1.0, x2 - x1);
            double sy = Math.min(1.0, y2 - y1);
            double sz = Math.min(1.0, z2 - z1);
            int i = Math.max(2, Mth.ceil(sx / 0.25));
            int j = Math.max(2, Mth.ceil(sy / 0.25));
            int k = Math.max(2, Mth.ceil(sz / 0.25));
            for (int l = 0; l < i; l++) {
                for (int i1 = 0; i1 < j; i1++) {
                    for (int j1 = 0; j1 < k; j1++) {
                        double fx = ((double) l + 0.5) / i;
                        double fy = ((double) i1 + 0.5) / j;
                        double fz = ((double) j1 + 0.5) / k;
                        manager.add(new TerrainParticle(
                                clientLevel,
                                pos.getX() + fx * sx + x1,
                                pos.getY() + fy * sy + y1,
                                pos.getZ() + fz * sz + z1,
                                fx - 0.5, fy - 0.5, fz - 0.5,
                                state, pos
                        ).setSpriteFromAge(sprite));
                    }
                }
            }
        });
        return true;
    }

    @Override
    public boolean addHitEffects(BlockState state, Level level, HitResult target, ParticleEngine manager) {
        if (!(target instanceof BlockHitResult blockHit)
                || state.getRenderShape() == RenderShape.INVISIBLE
                || !state.shouldSpawnTerrainParticles()) {
            return false;
        }
        ClientLevel clientLevel = (ClientLevel) level;
        BlockPos pos = blockHit.getBlockPos();
        Direction side = blockHit.getDirection();
        AABB bounds = state.getShape(level, pos).bounds();
        RandomSource random = clientLevel.random;
        double x = pos.getX() + random.nextDouble() * (bounds.maxX - bounds.minX - 0.2F) + 0.1F + bounds.minX;
        double y = pos.getY() + random.nextDouble() * (bounds.maxY - bounds.minY - 0.2F) + 0.1F + bounds.minY;
        double z = pos.getZ() + random.nextDouble() * (bounds.maxZ - bounds.minZ - 0.2F) + 0.1F + bounds.minZ;
        if (side == Direction.DOWN) {
            y = pos.getY() + bounds.minY - 0.1F;
        }
        if (side == Direction.UP) {
            y = pos.getY() + bounds.maxY + 0.1F;
        }
        if (side == Direction.NORTH) {
            z = pos.getZ() + bounds.minZ - 0.1F;
        }
        if (side == Direction.SOUTH) {
            z = pos.getZ() + bounds.maxZ + 0.1F;
        }
        if (side == Direction.WEST) {
            x = pos.getX() + bounds.minX - 0.1F;
        }
        if (side == Direction.EAST) {
            x = pos.getX() + bounds.maxX + 0.1F;
        }
        manager.add(new TerrainParticle(
                clientLevel, x, y, z, 0.0D, 0.0D, 0.0D, state, pos
        ).setSpriteFromAge(tierSpriteSet(state)).setPower(0.2F).scale(0.6F));
        return true;
    }

    /**
     * Resolves the atlas sprite of the pipe's displayed tier and wraps it in a
     * {@link SpriteSet} that hands that single sprite to every {@link TerrainParticle}.
     */
    private static SpriteSet tierSpriteSet(BlockState state) {
        BaseTier tier = state.getValue(BlockFusedPipe.TIER);
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(
              Mod.MODID, "block/fused_pipe/" + tier.getLowerName() + "_fused_pipe");
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(texture);
        return new SpriteSet() {
            @Override
            public TextureAtlasSprite get(int age, int lifetime) {
                return sprite;
            }

            @Override
            public TextureAtlasSprite get(RandomSource random) {
                return sprite;
            }
        };
    }
}
