package io.aduhtkjm.mekanismheated.registries;

import io.aduhtkjm.mekanismheated.Mod;
import io.aduhtkjm.mekanismheated.item.CuoDustItem;
import io.aduhtkjm.mekanismheated.item.Fe2O3DustItem;
import io.aduhtkjm.mekanismheated.item.SnO2DustItem;
import mekanism.common.registration.impl.ItemDeferredRegister;
import mekanism.common.registration.impl.ItemRegistryObject;

public class ModItems {

    private ModItems() {
    }

    // 该注册器是 Mek 实现的，与 ModBlocks 中的 BlockDeferredRegister 风格一致
    public static final ItemDeferredRegister ITEMS = new ItemDeferredRegister(Mod.MODID);

    public static final ItemRegistryObject<CuoDustItem> CUO_DUST = ITEMS.registerItem("cuo_dust", CuoDustItem::new);
    public static final ItemRegistryObject<Fe2O3DustItem> FE2O3_DUST = ITEMS.registerItem("fe2o3_dust", Fe2O3DustItem::new);
    public static final ItemRegistryObject<SnO2DustItem> SNO2_DUST = ITEMS.registerItem("sno2_dust", SnO2DustItem::new);
}
