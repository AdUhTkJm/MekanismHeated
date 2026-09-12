package io.aduhtkjm.mekanismheated.content.ambient;

import io.aduhtkjm.mekanismheated.Config;
import io.aduhtkjm.mekanismheated.content.unstablelava.UnstableLavaFluid;
import io.aduhtkjm.mekanismheated.content.unstablelava.UnstableLavaVariant;
import io.aduhtkjm.mekanismheated.registries.ModFluids;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import mekanism.api.heat.HeatAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Turns blocks into unstable lava once a chunk's ambient temperature exceeds the configured threshold, and keeps
 * the resulting lava from draining away so that the process cascades.
 *
 * <p>The feature runs two independent passes over every ticking chunk (see {@link Config.AmbientMelting}):
 * <ul>
 *     <li><b>Source pass</b> (default every 20 ticks, 15 random positions per chunk): any flowing unstable lava
 *         found is promoted back to a source block. This is what makes the melting cascade: melted blocks refill
 *         the flowing lava around them instead of letting it run off.</li>
 *     <li><b>Melt pass</b> (default every 40 ticks, 1 random position per chunk): for chunks whose effective
 *         ambient temperature is above the melt threshold, a random block is replaced with unstable lava if
 *         {@link BlockMeltFilter} accepts it.</li>
 * </ul>
 *
 * <p>Loaded chunks are tracked through {@link ChunkEvent} so the passes only ever touch chunks that exist, and
 * non-ticking chunks (outside simulation distance and not force-loaded) are skipped. The filter is rebuilt
 * whenever the config reloads.
 */
public final class AmbientMeltingHandler {

    private static final Map<ResourceKey<Level>, LongSet> LOADED_CHUNKS = new Object2ObjectOpenHashMap<>();

    @Nullable
    private static volatile BlockMeltFilter cachedFilter;
    private static int ticks;

    private AmbientMeltingHandler() {
    }

    /**
     * Invalidates the cached {@link BlockMeltFilter} so it is rebuilt from the new config on the next pass.
     */
    public static void onConfigReload() {
        cachedFilter = null;
    }

    private static BlockMeltFilter filter() {
        BlockMeltFilter filter = cachedFilter;
        if (filter == null) {
            filter = BlockMeltFilter.fromConfig();
            cachedFilter = filter;
        }
        return filter;
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LOADED_CHUNKS.computeIfAbsent(level.dimension(), key -> new LongOpenHashSet()).add(event.getChunk().getPos().toLong());
        }
    }

    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LongSet chunks = LOADED_CHUNKS.get(level.dimension());
            if (chunks != null) {
                chunks.remove(event.getChunk().getPos().toLong());
            }
        }
    }

    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LOADED_CHUNKS.remove(level.dimension());
        }
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (!server.tickRateManager().runsNormally()) {
            return;
        }
        int tick = ++ticks;
        boolean sourcePass = tick % Config.AmbientMelting.SOURCE_INTERVAL.get() == 0;
        boolean meltPass = tick % Config.AmbientMelting.MELT_INTERVAL.get() == 0;
        if (!sourcePass && !meltPass) {
            return;
        }
        BlockMeltFilter filter = filter();
        for (ServerLevel level : server.getAllLevels()) {
            LongSet chunks = LOADED_CHUNKS.get(level.dimension());
            if (chunks != null && !chunks.isEmpty()) {
                process(level, chunks, sourcePass, meltPass, filter);
            }
        }
    }

    private static void process(ServerLevel level, LongSet chunks, boolean sourcePass, boolean meltPass, BlockMeltFilter filter) {
        ServerChunkCache chunkSource = level.getChunkSource();
        RandomSource random = level.random;
        int sourceSamples = Config.AmbientMelting.SOURCE_SAMPLES.get();
        int meltSamples = Config.AmbientMelting.MELT_SAMPLES.get();
        for (LongIterator iterator = chunks.iterator(); iterator.hasNext(); ) {
            long packed = iterator.nextLong();
            if (!chunkSource.isPositionTicking(packed)) {
                //Loaded but not ticking (outside simulation distance and not force-loaded): nothing to do.
                continue;
            }
            ChunkPos chunkPos = new ChunkPos(packed);
            // We don't do cascade melting outside the melting chunk.
            if (!isAboveThreshold(level, chunkPos)) {
                continue;
            }

            if (sourcePass && sourceSamples > 0) {
                cascadeMelting(level, chunkPos, sourceSamples, filter, random);
            }
            if (meltPass && meltSamples > 0) {
                meltBlocks(level, chunkPos, meltSamples, filter, random);
            }
        }
    }

    /**
     * Attempts to melt blocks below unstable lava.
     */
    private static void cascadeMelting(ServerLevel level, ChunkPos chunkPos, int samples, BlockMeltFilter filter, RandomSource random) {
        for (int i = 0; i < samples; i++) {
            BlockPos pos = randomPosition(level, chunkPos, random);
            FluidState fluid = level.getFluidState(pos);
            if (fluid.getType() instanceof UnstableLavaFluid) {
                BlockPos adjacent = pos.relative(Direction.getRandom(random));
                if (!filter.test(level.getBlockState(adjacent))) {
                    continue;
                }
                level.setBlock(adjacent, ModFluids.UNSTABLE_LAVA.block().get().defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    private static void meltBlocks(ServerLevel level, ChunkPos chunkPos, int samples, BlockMeltFilter filter, RandomSource random) {
        BlockState lava = ModFluids.UNSTABLE_LAVA.block().get().defaultBlockState();
        for (int i = 0; i < samples; i++) {
            BlockPos pos = randomPosition(level, chunkPos, random);
            if (!filter.test(level.getBlockState(pos))) {
                continue;
            }
            level.setBlock(pos, lava, Block.UPDATE_ALL);
        }
    }

    private static boolean isAboveThreshold(ServerLevel level, ChunkPos chunkPos) {
        //HeatAPI.getAmbientTemp already includes this mod's per-chunk ambient delta through MixinHeatAPI.
        return HeatAPI.getAmbientTemp(level, chunkPos.getWorldPosition()) > Config.AmbientMelting.MELT_THRESHOLD.get();
    }

    private static BlockPos randomPosition(ServerLevel level, ChunkPos chunkPos, RandomSource random) {
        int x = chunkPos.getMinBlockX() + random.nextInt(16);
        int z = chunkPos.getMinBlockZ() + random.nextInt(16);
        int y = level.getMinBuildHeight() + random.nextInt(level.getHeight());
        return new BlockPos(x, y, z);
    }
}
