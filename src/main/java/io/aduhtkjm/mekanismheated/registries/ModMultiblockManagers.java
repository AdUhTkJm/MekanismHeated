package io.aduhtkjm.mekanismheated.registries;

import io.aduhtkjm.mekanismheated.tile.multiblock.*;
import mekanism.common.lib.multiblock.MultiblockCache;
import mekanism.common.lib.multiblock.MultiblockManager;

public class ModMultiblockManagers {

    private ModMultiblockManagers() {
    }

    /**
     * Forces this class to initialize, which constructs (and thereby self-registers) every manager below.
     *
     * <p>Mekanism calls {@link MultiblockManager#createOrLoadAll()} exactly once per world load, from
     * {@code CommonWorldTickHandler#worldLoadEvent}. That is the only time a manager's saved data is created/loaded and
     * the only time its data handler starts accepting dirty marks. A manager whose holding class is not initialized
     * until the first tile calls {@code getManager()} (i.e. well after the world loaded) therefore misses that pass for
     * the whole session: its cache map stays memory-only and is never written to disk, so everything it backs — heat,
     * items, fluids — silently resets on the next reload.</p>
     *
     * <p>Call this from the mod constructor so the registration happens while the mod is still loading, long before any
     * level can load.</p>
     */
    public static void init() {
    }

    public static final MultiblockManager<FractionationMultiblockData> FRACTIONATION_MANAGER =
          new MultiblockManager<>("fractionation", FractionationCache::new, FractionationValidator::new);

    public static final MultiblockManager<RetroentropicArrayData> RETROENTROPIC_MANAGER =
        new MultiblockManager<>("retroentropic_array", MultiblockCache::new, RetroentropicArrayValidator::new);

    public static final MultiblockManager<LargeHeatSmelterData> LARGE_HEAT_SMELTER_MANAGER =
        new MultiblockManager<>("large_heat_smelter", MultiblockCache::new, LargeHeatSmelterValidator::new);
}
