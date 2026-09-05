package io.aduhtkjm.mekanismheated;

import com.mojang.logging.LogUtils;
import io.aduhtkjm.mekanismheated.content.fusedpipe.FusedPipeRegistry;
import io.aduhtkjm.mekanismheated.content.moltenfluid.MoltenFluidHandler;
import io.aduhtkjm.mekanismheated.network.PacketCoolerSetEnergy;
import io.aduhtkjm.mekanismheated.network.PacketSetHeatTarget;
import io.aduhtkjm.mekanismheated.recipe.ModRecipeSerializers;
import io.aduhtkjm.mekanismheated.recipe.ModRecipeTypes;
import io.aduhtkjm.mekanismheated.registries.ModBlocks;
import io.aduhtkjm.mekanismheated.registries.ModChemicals;
import io.aduhtkjm.mekanismheated.registries.ModContainerTypes;
import io.aduhtkjm.mekanismheated.registries.ModFluids;
import io.aduhtkjm.mekanismheated.registries.ModItems;
import io.aduhtkjm.mekanismheated.registries.ModTileEntityTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.fluids.capability.wrappers.FluidBucketWrapper;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@net.neoforged.fml.common.Mod(Mod.MODID)
public class Mod {
    public static final String MODID = "mekanismheated";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> HEATED_TAB = CREATIVE_MODE_TABS.register("heated_tab", () -> CreativeModeTab.builder()
          .title(Component.translatable("itemGroup.mekanismheated"))
          .withTabsBefore(CreativeModeTabs.COMBAT)
          .icon(() -> ModBlocks.HEAT_SMELTER.asItem().getDefaultInstance())
          .displayItems(ModItems::registerDisplayedItems).build());

    public Mod(IEventBus modEventBus, ModContainer modContainer) {
        // Register the Deferred Registers to the mod event bus so blocks/tiles/containers get registered
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModFluids.FLUIDS.register(modEventBus);
        ModTileEntityTypes.TILE_ENTITY_TYPES.register(modEventBus);
        ModContainerTypes.CONTAINER_TYPES.register(modEventBus);
        ModChemicals.CHEMICALS.register(modEventBus);
        ModRecipeTypes.RECIPE_TYPES.register(modEventBus);
        ModRecipeSerializers.RECIPE_SERIALIZERS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerBucketCapabilities);
        modEventBus.addListener(this::registerPayloadHandlers);

        // Register ourselves for server and other game events we are interested in.
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.addListener(FusedPipeRegistry::onServerTickPost);
        NeoForge.EVENT_BUS.addListener(FusedPipeRegistry::onServerStopping);
        NeoForge.EVENT_BUS.addListener(MoltenFluidHandler::onEntityTickPost);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(ModFluids.FLUIDS::registerBucketDispenserBehavior);
        event.enqueueWork(MoltenFluidHandler::init);
    }

    private void registerBucketCapabilities(final RegisterCapabilitiesEvent event) {
        for (var bucket : ModFluids.FLUIDS.getBucketEntries()) {
            Item item = bucket.value();
            if (item instanceof BucketItem && item.getClass() != BucketItem.class) {
                event.registerItem(Capabilities.FluidHandler.ITEM, (stack, ctx) -> new FluidBucketWrapper(stack), item);
            }
        }
    }

    private void registerPayloadHandlers(final RegisterPayloadHandlersEvent event) {
        event.registrar(MODID)
              .playToServer(PacketSetHeatTarget.TYPE, PacketSetHeatTarget.STREAM_CODEC, PacketSetHeatTarget::handle)
              .playToServer(PacketCoolerSetEnergy.TYPE, PacketCoolerSetEnergy.STREAM_CODEC, PacketCoolerSetEnergy::handle);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("MekanismHeated server starting");
    }
}