package io.aduhtkjm.mekanismheated.registries;

import io.aduhtkjm.mekanismheated.Mod;
import io.aduhtkjm.mekanismheated.content.upgrade.HeatedUpgradeAware;
import mekanism.common.registration.MekanismDeferredHolder;
import mekanism.common.registration.impl.DataComponentDeferredRegister;
import net.minecraft.core.component.DataComponentType;

public class ModDataComponents {
    private ModDataComponents() {
    }

    public static final DataComponentDeferredRegister DATA_COMPONENTS = new DataComponentDeferredRegister(Mod.MODID);

    /**
     * Heat upgrades installed on a machine. Set when a machine is picked up (see the mod's loot tables, which copy every
     * block entity component) and read back when it is placed.
     */
    public static final MekanismDeferredHolder<DataComponentType<?>, DataComponentType<HeatedUpgradeAware>> HEATED_UPGRADES =
          DATA_COMPONENTS.simple("heated_upgrades", builder -> builder.persistent(HeatedUpgradeAware.CODEC)
                .networkSynchronized(HeatedUpgradeAware.STREAM_CODEC));
}
