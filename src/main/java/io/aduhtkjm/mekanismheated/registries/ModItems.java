package io.aduhtkjm.mekanismheated.registries;

import io.aduhtkjm.mekanismheated.Mod;
import io.aduhtkjm.mekanismheated.item.*;
import mekanism.common.registration.impl.ItemDeferredRegister;
import mekanism.common.registration.impl.ItemRegistryObject;
import net.minecraft.world.item.CreativeModeTab;

public class ModItems {
    private ModItems() {
    }

    public static final ItemDeferredRegister ITEMS = new ItemDeferredRegister(Mod.MODID);

    public static final ItemRegistryObject<SpongeIronIngotItem> SPONGE_IRON_INGOT = ITEMS.registerItem("sponge_iron_ingot", SpongeIronIngotItem::new);
    public static final ItemRegistryObject<ImpureSnIngotItem> IMPURE_SN_INGOT = ITEMS.registerItem("impure_sn_ingot", ImpureSnIngotItem::new);

    public static final ItemRegistryObject<CuoDustItem> CUO_DUST = ITEMS.registerItem("cuo_dust", CuoDustItem::new);
    public static final ItemRegistryObject<Fe2O3DustItem> FE2O3_DUST = ITEMS.registerItem("fe2o3_dust", Fe2O3DustItem::new);
    public static final ItemRegistryObject<SnO2DustItem> SNO2_DUST = ITEMS.registerItem("sno2_dust", SnO2DustItem::new);
    public static final ItemRegistryObject<CaCO3DustItem> CACO3_DUST = ITEMS.registerItem("caco3_dust", CaCO3DustItem::new);
    public static final ItemRegistryObject<PureFe2O3DustItem> PURE_FE2O3_DUST = ITEMS.registerItem("pure_fe2o3_dust", PureFe2O3DustItem::new);
    public static final ItemRegistryObject<FeS2DustItem> FES2_DUST = ITEMS.registerItem("fes2_dust", FeS2DustItem::new);

    public static void registerDisplayedItems(CreativeModeTab.ItemDisplayParameters itemDisplayParameters, CreativeModeTab.Output output) {
        output.accept(ModBlocks.HEAT_SMELTER);
        output.accept(ModBlocks.SHAKER);
        output.accept(ModBlocks.FUSED_PIPE);

        output.accept(ModBlocks.THERMAL_FRACTIONATION_CONTROLLER);
        output.accept(ModBlocks.THERMAL_FRACTIONATION_VALVE);
        output.accept(ModBlocks.THERMAL_FRACTIONATION_CASING);
        output.accept(ModBlocks.DISTILLATION_TRAY);

        output.accept(ModItems.SPONGE_IRON_INGOT.get());
        output.accept(ModItems.IMPURE_SN_INGOT.get());
        output.accept(ModItems.CUO_DUST.get());
        output.accept(ModItems.FE2O3_DUST.get());
        output.accept(ModItems.PURE_FE2O3_DUST.get());
        output.accept(ModItems.SNO2_DUST.get());
        output.accept(ModItems.CACO3_DUST.get());
        output.accept(ModItems.FES2_DUST.get());

        output.accept(ModFluids.WOOD_TAR.getBucket());
        output.accept(ModFluids.ASPHALT.getBucket());
        output.accept(ModFluids.METHANOL.getBucket());
        output.accept(ModFluids.ACETIC_ACID.getBucket());
    }
}
