package io.aduhtkjm.mekanismheated.registries;

import io.aduhtkjm.mekanismheated.Mod;
import io.aduhtkjm.mekanismheated.content.obsidiandust.ObsidianDustVariant;
import io.aduhtkjm.mekanismheated.content.unstablelava.UnstableLavaVariant;
import io.aduhtkjm.mekanismheated.item.*;
import java.util.List;
import mekanism.common.registration.impl.ItemDeferredRegister;
import mekanism.common.registration.impl.ItemRegistryObject;
import net.minecraft.world.item.CreativeModeTab;

public class ModItems {
    private ModItems() {
    }

    public static final ItemDeferredRegister ITEMS = new ItemDeferredRegister(Mod.MODID);

    public static final ItemRegistryObject<ItemSpongeIronIngot> SPONGE_IRON_INGOT = ITEMS.registerItem("sponge_iron_ingot", ItemSpongeIronIngot::new);
    public static final ItemRegistryObject<ItemImpureSnIngot> IMPURE_SN_INGOT = ITEMS.registerItem("impure_sn_ingot", ItemImpureSnIngot::new);
    public static final ItemRegistryObject<ItemThermoenergeticAlloyIngot> THERMOENERGETIC_ALLOY_INGOT =
          ITEMS.registerItem("thermoenergetic_alloy_ingot", ItemThermoenergeticAlloyIngot::new);

    public static final ItemRegistryObject<ItemCuODust> CUO_DUST = ITEMS.registerItem("cuo_dust", ItemCuODust::new);
    public static final ItemRegistryObject<ItemFe2O3Dust> FE2O3_DUST = ITEMS.registerItem("fe2o3_dust", ItemFe2O3Dust::new);
    public static final ItemRegistryObject<ItemSnO2Dust> SNO2_DUST = ITEMS.registerItem("sno2_dust", ItemSnO2Dust::new);
    public static final ItemRegistryObject<ItemCaCO3Dust> CACO3_DUST = ITEMS.registerItem("caco3_dust", ItemCaCO3Dust::new);
    public static final ItemRegistryObject<ItemPureFe2O3Dust> PURE_FE2O3_DUST = ITEMS.registerItem("pure_fe2o3_dust", ItemPureFe2O3Dust::new);
    public static final ItemRegistryObject<ItemFeS2Dust> FES2_DUST = ITEMS.registerItem("fes2_dust", ItemFeS2Dust::new);
    public static final ItemRegistryObject<ItemCu2SDust> CU2S_DUST = ITEMS.registerItem("cu2s_dust", ItemCu2SDust::new);
    public static final ItemRegistryObject<ItemPureCuODust> PURE_CUO_DUST = ITEMS.registerItem("pure_cuo_dust", ItemPureCuODust::new);

    //Obsidian dusts: obsidian dust with the metal it was condensed from in its top-left corner. One register call per
    //ore; the matching overlay textures come from scripts/obsidian_dust_overlay.py.
    public static final ObsidianDustVariant IRON_OBSIDIAN_DUST = ObsidianDustVariant.register(ITEMS, "iron", "Fe");
    public static final ObsidianDustVariant COPPER_OBSIDIAN_DUST = ObsidianDustVariant.register(ITEMS, "copper", "Cu");
    public static final ObsidianDustVariant TIN_OBSIDIAN_DUST = ObsidianDustVariant.register(ITEMS, "tin", "Sn");
    public static final ObsidianDustVariant OSMIUM_OBSIDIAN_DUST = ObsidianDustVariant.register(ITEMS, "osmium", "Os");
    public static final ObsidianDustVariant GOLD_OBSIDIAN_DUST = ObsidianDustVariant.register(ITEMS, "gold", "Au");
    public static final ObsidianDustVariant LEAD_OBSIDIAN_DUST = ObsidianDustVariant.register(ITEMS, "lead", "Pb");

    /** Every obsidian dust variant, in the order they are shown in the creative tab. */
    public static final List<ObsidianDustVariant> OBSIDIAN_DUSTS =
          List.of(IRON_OBSIDIAN_DUST, COPPER_OBSIDIAN_DUST, TIN_OBSIDIAN_DUST, OSMIUM_OBSIDIAN_DUST,
              GOLD_OBSIDIAN_DUST, LEAD_OBSIDIAN_DUST);

    public static void registerDisplayedItems(CreativeModeTab.ItemDisplayParameters itemDisplayParameters, CreativeModeTab.Output output) {
        output.accept(ModBlocks.HEAT_SMELTER);
        output.accept(ModBlocks.SHAKER);
        output.accept(ModBlocks.FUSED_PIPE);

        output.accept(ModBlocks.THERMAL_FRACTIONATION_CONTROLLER);
        output.accept(ModBlocks.THERMAL_FRACTIONATION_VALVE);
        output.accept(ModBlocks.THERMAL_FRACTIONATION_CASING);
        output.accept(ModBlocks.THERMAL_CASING);
        output.accept(ModBlocks.DISTILLATION_TRAY);
        output.accept(ModBlocks.CREATIVE_HEAT_BLOCK);
        output.accept(ModBlocks.CREATIVE_CHUNK_HEATER);
        output.accept(ModBlocks.COOLER);
        output.accept(ModBlocks.CONDENSER);
        output.accept(ModBlocks.QUENCHING_ENRICHMENT_CHAMBER);
        output.accept(ModBlocks.REACTION_CHAMBER);
        output.accept(ModBlocks.ATMOSPHERE_HEATER);
        output.accept(ModBlocks.PHASE_CHANGE_LOW);
        output.accept(ModBlocks.PHASE_CHANGE_MEDIUM);
        output.accept(ModBlocks.PHASE_CHANGE_HIGH);

        output.accept(ModItems.SPONGE_IRON_INGOT.get());
        output.accept(ModItems.IMPURE_SN_INGOT.get());
        output.accept(ModItems.THERMOENERGETIC_ALLOY_INGOT.get());
        output.accept(ModItems.CUO_DUST.get());
        output.accept(ModItems.PURE_CUO_DUST.get());
        output.accept(ModItems.FE2O3_DUST.get());
        output.accept(ModItems.PURE_FE2O3_DUST.get());
        output.accept(ModItems.SNO2_DUST.get());
        output.accept(ModItems.CACO3_DUST.get());
        output.accept(ModItems.FES2_DUST.get());
        output.accept(ModItems.CU2S_DUST.get());
        for (ObsidianDustVariant variant : OBSIDIAN_DUSTS) {
            output.accept(variant.item().get());
        }

        output.accept(ModFluids.WOOD_TAR.getBucket());
        output.accept(ModFluids.ASPHALT.getBucket());
        output.accept(ModFluids.METHANOL.getBucket());
        output.accept(ModFluids.ACETIC_ACID.getBucket());
        output.accept(ModFluids.LIQUID_NITROGEN.getBucket());
        output.accept(ModFluids.LIQUID_AIR_REMNANT.getBucket());
        output.accept(ModFluids.MOLTEN_IRON.getBucket());
        output.accept(ModFluids.MOLTEN_COPPER.getBucket());
        output.accept(ModFluids.MOLTEN_TIN.getBucket());
        output.accept(ModFluids.MOLTEN_BRONZE.getBucket());
        output.accept(ModFluids.MOLTEN_OSMIUM.getBucket());
        output.accept(ModFluids.MOLTEN_THERMOENERGETIC_ALLOY.getBucket());
        output.accept(ModFluids.MOLTEN_CASING_ALLOY.getBucket());
        output.accept(ModFluids.MOLTEN_INFUSED_ALLOY.getBucket());
        output.accept(ModFluids.MOLTEN_REINFORCED_ALLOY.getBucket());
        for (UnstableLavaVariant variant : ModFluids.UNSTABLE_LAVA_VARIANTS) {
            output.accept(variant.bucket().get());
        }
    }
}
