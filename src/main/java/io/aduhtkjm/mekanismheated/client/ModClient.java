package io.aduhtkjm.mekanismheated.client;

import io.aduhtkjm.mekanismheated.Mod;
import io.aduhtkjm.mekanismheated.client.gui.machine.GuiHeatSmelter;
import io.aduhtkjm.mekanismheated.client.gui.machine.GuiShaker;
import io.aduhtkjm.mekanismheated.client.renderer.TileEntityShakerRenderer;
import io.aduhtkjm.mekanismheated.item.CuoDustItem;
import io.aduhtkjm.mekanismheated.item.Fe2O3DustItem;
import io.aduhtkjm.mekanismheated.item.SnO2DustItem;
import io.aduhtkjm.mekanismheated.registries.ModContainerTypes;
import io.aduhtkjm.mekanismheated.registries.ModItems;
import io.aduhtkjm.mekanismheated.registries.ModTileEntityTypes;
import mekanism.client.ClientRegistrationUtil;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;

@EventBusSubscriber(modid = Mod.MODID, value = Dist.CLIENT)
public class ModClient {

    @SubscribeEvent
    public static void registerItemColorHandlers(RegisterColorHandlersEvent.Item event) {
        ClientRegistrationUtil.registerItemColorHandler(event, (stack, tintIndex) -> tintIndex == 1 ? CuoDustItem.TINT : -1, ModItems.CUO_DUST);
        ClientRegistrationUtil.registerItemColorHandler(event, (stack, tintIndex) -> tintIndex == 1 ? Fe2O3DustItem.TINT : -1, ModItems.FE2O3_DUST);
        ClientRegistrationUtil.registerItemColorHandler(event, (stack, tintIndex) -> tintIndex == 1 ? SnO2DustItem.TINT : -1, ModItems.SNO2_DUST);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        ClientRegistrationUtil.registerScreen(event, ModContainerTypes.HEAT_SMELTER, GuiHeatSmelter::new);
        ClientRegistrationUtil.registerScreen(event, ModContainerTypes.SHAKER, GuiShaker::new);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModTileEntityTypes.SHAKER.get(), TileEntityShakerRenderer::new);
    }

    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(ModelResourceLocation.standalone(TileEntityShakerRenderer.MODEL_LOCATION));
        event.register(ModelResourceLocation.standalone(TileEntityShakerRenderer.GLASS_MODEL_LOCATION));
    }
}