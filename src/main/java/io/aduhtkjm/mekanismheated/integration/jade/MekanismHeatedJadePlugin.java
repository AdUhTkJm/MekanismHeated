package io.aduhtkjm.mekanismheated.integration.jade;

import io.aduhtkjm.mekanismheated.block.BlockFusedPipe;
import io.aduhtkjm.mekanismheated.block.heatsmelter.HeatSmelterBlock;
import io.aduhtkjm.mekanismheated.block.reactionchamber.ReactionChamberBlock;
import io.aduhtkjm.mekanismheated.tile.TileEntityFusedPipe;
import io.aduhtkjm.mekanismheated.tile.TileEntityHeatSmelter;
import io.aduhtkjm.mekanismheated.tile.TileEntityReactionChamber;
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
    }
}
