package io.aduhtkjm.mekanismheated.content.fractionation;

import mekanism.common.lib.multiblock.MultiblockManager;

public class ModFractionation {

    private ModFractionation() {
    }

    public static final MultiblockManager<FractionationMultiblockData> FRACTIONATION_MANAGER =
          new MultiblockManager<>("fractionation", FractionationCache::new, FractionationValidator::new);
}
