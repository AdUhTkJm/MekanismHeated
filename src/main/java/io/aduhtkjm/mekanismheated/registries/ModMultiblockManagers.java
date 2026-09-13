package io.aduhtkjm.mekanismheated.registries;

import io.aduhtkjm.mekanismheated.tile.multiblock.*;
import mekanism.common.lib.multiblock.MultiblockCache;
import mekanism.common.lib.multiblock.MultiblockManager;

public class ModMultiblockManagers {

    private ModMultiblockManagers() {
    }

    public static final MultiblockManager<FractionationMultiblockData> FRACTIONATION_MANAGER =
          new MultiblockManager<>("fractionation", FractionationCache::new, FractionationValidator::new);

    public static final MultiblockManager<RetroentropicArrayData> RETROENTROPIC_MANAGER =
        new MultiblockManager<>("fractionation", MultiblockCache::new, RetroentropicArrayValidator::new);

    public static final MultiblockManager<LargeHeatSmelterData> LARGE_HEAT_SMELTER_MANAGER =
        new MultiblockManager<>("large_heat_smelter", MultiblockCache::new, LargeHeatSmelterValidator::new);
}
