package io.aduhtkjm.mekanismheated.client;

import io.aduhtkjm.mekanismheated.Mod;
import io.aduhtkjm.mekanismheated.client.gui.machine.GuiCooler;
import io.aduhtkjm.mekanismheated.client.gui.machine.GuiCreativeHeatBlock;
import io.aduhtkjm.mekanismheated.client.gui.machine.GuiHeatSmelter;
import io.aduhtkjm.mekanismheated.client.gui.machine.GuiShaker;
import io.aduhtkjm.mekanismheated.client.gui.machine.GuiThermalFractionationController;
import io.aduhtkjm.mekanismheated.client.renderer.TileEntityHeatSmelterRenderer;
import io.aduhtkjm.mekanismheated.client.renderer.TileEntityShakerRenderer;
import io.aduhtkjm.mekanismheated.content.fusedpipe.FusedPipeConfig;
import io.aduhtkjm.mekanismheated.item.*;
import io.aduhtkjm.mekanismheated.registries.ModBlocks;
import io.aduhtkjm.mekanismheated.registries.ModContainerTypes;
import io.aduhtkjm.mekanismheated.registries.ModFluids;
import io.aduhtkjm.mekanismheated.registries.ModItems;
import io.aduhtkjm.mekanismheated.registries.ModTileEntityTypes;
import mekanism.client.ClientRegistrationUtil;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = Mod.MODID, value = Dist.CLIENT)
public class ModClient {

    @SubscribeEvent
    public static void registerItemColorHandlers(RegisterColorHandlersEvent.Item event) {
        ClientRegistrationUtil.registerBucketColorHandler(event, ModFluids.FLUIDS);

        ClientRegistrationUtil.registerItemColorHandler(event, (stack, tintIndex) -> tintIndex == 1 ? CuoDustItem.TINT : -1, ModItems.CUO_DUST);
        ClientRegistrationUtil.registerItemColorHandler(event, (stack, tintIndex) -> tintIndex == 1 ? Fe2O3DustItem.TINT : -1, ModItems.FE2O3_DUST);
        ClientRegistrationUtil.registerItemColorHandler(event, (stack, tintIndex) -> tintIndex == 1 ? SnO2DustItem.TINT : -1, ModItems.SNO2_DUST);
        ClientRegistrationUtil.registerItemColorHandler(event, (stack, tintIndex) -> tintIndex == 1 ? CaCO3DustItem.TINT : -1, ModItems.CACO3_DUST);
        ClientRegistrationUtil.registerItemColorHandler(event, (stack, tintIndex) -> tintIndex == 1 ? PureFe2O3DustItem.TINT : -1, ModItems.PURE_FE2O3_DUST);
        ClientRegistrationUtil.registerItemColorHandler(event, (stack, tintIndex) -> tintIndex == 1 ? FeS2DustItem.TINT : -1, ModItems.FES2_DUST);

        ClientRegistrationUtil.registerItemColorHandler(event, (stack, tintIndex) -> tintIndex == 1 ? ImpureSnIngotItem.TINT : -1, ModItems.IMPURE_SN_INGOT);
        ClientRegistrationUtil.registerItemColorHandler(event, (stack, tintIndex) -> tintIndex == 1 ? SpongeIronIngotItem.TINT : -1, ModItems.SPONGE_IRON_INGOT);
    }

    @SubscribeEvent
    public static void registerItemDecorations(RegisterItemDecorationsEvent event) {
        event.register(ModBlocks.FUSED_PIPE, new FusedPipeItemDecorator());
    }

    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        ClientRegistrationUtil.registerFluidExtensions(event, ModFluids.FLUIDS);
        event.registerBlock(new FusedPipeBlockExtensions(), ModBlocks.FUSED_PIPE.get());
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        ClientRegistrationUtil.registerScreen(event, ModContainerTypes.HEAT_SMELTER, GuiHeatSmelter::new);
        ClientRegistrationUtil.registerScreen(event, ModContainerTypes.SHAKER, GuiShaker::new);
        ClientRegistrationUtil.registerScreen(event, ModContainerTypes.THERMAL_FRACTIONATION_CONTROLLER, GuiThermalFractionationController::new);
        ClientRegistrationUtil.registerScreen(event, ModContainerTypes.CREATIVE_HEAT_BLOCK, GuiCreativeHeatBlock::new);
        ClientRegistrationUtil.registerScreen(event, ModContainerTypes.COOLER, GuiCooler::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModTileEntityTypes.HEAT_SMELTER.get(), TileEntityHeatSmelterRenderer::new);
        event.registerBlockEntityRenderer(ModTileEntityTypes.SHAKER.get(), TileEntityShakerRenderer::new);
    }

    @SubscribeEvent
    public static void registerRenderTypes(FMLClientSetupEvent event) {
        ItemBlockRenderTypes.setRenderLayer(ModBlocks.HEAT_SMELTER.get(), RenderType.cutout());
        ItemBlockRenderTypes.setRenderLayer(ModBlocks.COOLER.get(), RenderType.cutout());
    }

    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(ModelResourceLocation.standalone(TileEntityShakerRenderer.MODEL_LOCATION));
        event.register(ModelResourceLocation.standalone(TileEntityShakerRenderer.GLASS_MODEL_LOCATION));
    }

    @SubscribeEvent
    public static void registerItemProperties(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(ModBlocks.FUSED_PIPE.asItem(),
              ResourceLocation.fromNamespaceAndPath(Mod.MODID, "tier"),
              (stack, level, entity, seed) -> {
                  CustomData data = stack.get(DataComponents.BLOCK_ENTITY_DATA);
                  if (data == null) {
                      return 0;
                  }
                  CompoundTag tag = data.copyTag();
                  if (!tag.contains(FusedPipeConfig.TAG_CONFIG, Tag.TAG_COMPOUND)) {
                      return 0;
                  }
                  return FusedPipeConfig.displayTier(tag.getCompound(FusedPipeConfig.TAG_CONFIG)).ordinal();
              }));
    }
}