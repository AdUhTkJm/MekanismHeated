package io.aduhtkjm.mekanismheated.client;

import io.aduhtkjm.mekanismheated.Mod;
import io.aduhtkjm.mekanismheated.client.gui.machine.GuiHeatSmelter;
import io.aduhtkjm.mekanismheated.registries.ModContainerTypes;
import mekanism.client.ClientRegistrationUtil;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = Mod.MODID, value = Dist.CLIENT)
public class ModClient {

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        ClientRegistrationUtil.registerScreen(event, ModContainerTypes.HEAT_SMELTER, GuiHeatSmelter::new);
    }
}