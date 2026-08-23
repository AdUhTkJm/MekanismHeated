package io.aduhtkjm.mekanismheated.content.fractionation;

import java.util.List;
import mekanism.api.fluid.IExtendedFluidTank;
import mekanism.common.capabilities.fluid.BasicFluidTank;
import mekanism.common.lib.multiblock.MultiblockCache;

/**
 * Multiblock cache for fractionation towers. The number of fluid tanks depends on how many distillation tray layers the
 * tower had when it formed, so unlike most multiblocks the tank count can grow or shrink between formations.
 * {@link MultiblockCache#sync} only prefabricates cache containers when its list is entirely empty, which crashes once a
 * reformed tower has more tanks than the cached layout; this subclass tops the cache up to match first.
 */
public class FractionationCache extends MultiblockCache<FractionationMultiblockData> {

    @Override
    public void sync(FractionationMultiblockData data) {
        List<IExtendedFluidTank> cacheTanks = getFluidTanks(null);
        int required = data.getFluidTanks(null).size();
        while (cacheTanks.size() < required) {
            //Same shape of tank CacheSubstance.FLUID prefabs with; contents are copied by the super call below.
            // Extra cache tanks left over after shrinking a tower are kept so removing and re-adding a tray layer
            // preserves its contents.
            cacheTanks.add(BasicFluidTank.create(Integer.MAX_VALUE, this));
        }
        super.sync(data);
    }
}
