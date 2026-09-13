package io.aduhtkjm.mekanismheated.integration.jade;

import io.aduhtkjm.mekanismheated.block.BlockFusedPipe;
import io.aduhtkjm.mekanismheated.block.atmosphereheater.AtmosphereHeaterBlock;
import io.aduhtkjm.mekanismheated.block.heatsmelter.HeatSmelterBlock;
import io.aduhtkjm.mekanismheated.block.phasechange.PhaseChangeBlock;
import io.aduhtkjm.mekanismheated.block.reactionchamber.ReactionChamberBlock;
import io.aduhtkjm.mekanismheated.block.temperaturecontroller.TemperatureControllerBlock;
import io.aduhtkjm.mekanismheated.tile.TileEntityAtmosphereHeater;
import io.aduhtkjm.mekanismheated.tile.TileEntityFusedPipe;
import io.aduhtkjm.mekanismheated.tile.TileEntityHeatSmelter;
import io.aduhtkjm.mekanismheated.tile.TileEntityPhaseChangeBlock;
import io.aduhtkjm.mekanismheated.tile.TileEntityReactionChamber;
import io.aduhtkjm.mekanismheated.tile.TileEntityTemperatureController;
import io.aduhtkjm.mekanismheated.tile.multiblock.TileEntityFractionationBlock;
import mekanism.common.block.prefab.BlockBasicMultiblock;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class MekanismHeatedJadePlugin implements IWailaPlugin {

    public static final ResourceLocation CONFIG_KEY = ResourceLocation.fromNamespaceAndPath("mekanismheated", "fused_pipe_network");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(FusedPipeMekDataProvider.INSTANCE, TileEntityFusedPipe.class);
        registration.registerBlockDataProvider(HeatSmelterMekDataProvider.INSTANCE, TileEntityHeatSmelter.class);
        registration.registerBlockDataProvider(ReactionChamberMekDataProvider.INSTANCE, TileEntityReactionChamber.class);
        registration.registerBlockDataProvider(AtmosphereHeaterMekDataProvider.INSTANCE, TileEntityAtmosphereHeater.class);
        registration.registerBlockDataProvider(PhaseChangeMekDataProvider.INSTANCE, TileEntityPhaseChangeBlock.class);
        registration.registerBlockDataProvider(TemperatureControllerMekDataProvider.INSTANCE, TileEntityTemperatureController.class);
        //The whole tower (controller, valve and casing) resolves the same multiblock via getMultiblock(), and all three
        // tiles share the base type TileEntityFractionationBlock, so a single registration covers every part of the tower.
        registration.registerBlockDataProvider(FractionationMekDataProvider.INSTANCE, TileEntityFractionationBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.addConfig(CONFIG_KEY, true);
        registration.registerBlockComponent(FusedPipeMekRenderer.INSTANCE, BlockFusedPipe.class);
        registration.registerBlockComponent(FusedPipeBuiltinRemover.INSTANCE, BlockFusedPipe.class);
        registration.registerBlockComponent(HeatSmelterMekRenderer.INSTANCE, HeatSmelterBlock.class);
        registration.registerBlockComponent(HeatSmelterBuiltinRemover.INSTANCE, HeatSmelterBlock.class);
        registration.registerBlockComponent(ReactionChamberMekRenderer.INSTANCE, ReactionChamberBlock.class);
        registration.registerBlockComponent(ReactionChamberBuiltinRemover.INSTANCE, ReactionChamberBlock.class);
        registration.registerBlockComponent(AtmosphereHeaterMekRenderer.INSTANCE, AtmosphereHeaterBlock.class);
        registration.registerBlockComponent(AtmosphereHeaterBuiltinRemover.INSTANCE, AtmosphereHeaterBlock.class);
        //One component registration covers all three tiers, as they share a block class.
        registration.registerBlockComponent(PhaseChangeMekRenderer.INSTANCE, PhaseChangeBlock.class);
        registration.registerBlockComponent(TemperatureControllerMekRenderer.INSTANCE, TemperatureControllerBlock.class);
        //The tower's controller/valve/casing all use the shared runtime class BlockBasicMultiblock, so register on that
        // and guard by the common fractionation tile base type inside each component; the whole tower gets the tooltip.
        registration.registerBlockComponent(FractionationMekRenderer.INSTANCE, BlockBasicMultiblock.class);
        registration.registerBlockComponent(FractionationBuiltinRemover.INSTANCE, BlockBasicMultiblock.class);
    }
}
