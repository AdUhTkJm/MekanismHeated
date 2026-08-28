package io.aduhtkjm.mekanismheated.integration.jade;

import io.aduhtkjm.mekanismheated.block.BlockFusedPipe;
import io.aduhtkjm.mekanismheated.tile.TileEntityFusedPipe;
import net.minecraft.resources.Identifier;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

@WailaPlugin
public class MekanismHeatedJadePlugin implements IWailaPlugin {

    public static final Identifier CONFIG_KEY = Identifier.fromNamespaceAndPath("mekanismheated", "fused_pipe_network");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerEnergyStorage(FusedPipeEnergyProvider.INSTANCE, TileEntityFusedPipe.class);
        registration.registerFluidStorage(FusedPipeFluidProvider.INSTANCE, TileEntityFusedPipe.class);
        registration.registerItemStorage(FusedPipeItemProvider.INSTANCE, TileEntityFusedPipe.class);
        registration.registerBlockDataProvider(FusedPipeChemicalProvider.INSTANCE, TileEntityFusedPipe.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.addConfig(CONFIG_KEY, true);
        registration.registerBlockComponent(FusedPipeChemicalRenderer.INSTANCE, BlockFusedPipe.class);
    }
}
