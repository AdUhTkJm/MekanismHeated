package io.aduhtkjm.mekanismheated.integration.jade;

import java.util.List;
import io.aduhtkjm.mekanismheated.content.fusedpipe.FusedFunction;
import io.aduhtkjm.mekanismheated.content.fusedpipe.FusedNetwork;
import io.aduhtkjm.mekanismheated.tile.TileEntityFusedPipe;
import net.minecraft.resources.Identifier;
import snownee.jade.api.Accessor;
import snownee.jade.api.view.EnergyView;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;

public enum FusedPipeEnergyProvider implements IServerExtensionProvider<EnergyView.Data> {
    INSTANCE;

    @Override
    public Identifier getUid() {
        return Identifier.fromNamespaceAndPath("mekanismheated", "fused_pipe_energy");
    }

    @Override
    public List<ViewGroup<EnergyView.Data>> getGroups(Accessor<?> accessor) {
        if (!(accessor.getTarget() instanceof TileEntityFusedPipe pipe)) {
            return null;
        }
        if (!pipe.getConfig().isEnabled(FusedFunction.ENERGY)) {
            return null;
        }
        FusedNetwork network = pipe.getNetwork();
        if (network == null) {
            return null;
        }
        long energy = network.getEnergy();
        long capacity = network.getEnergyCapacity();
        if (capacity <= 0) {
            return null;
        }
        return List.of(new ViewGroup<>(List.of(new EnergyView.Data(energy, capacity))));
    }

    @Override
    public boolean shouldRequestData(Accessor<?> accessor) {
        return accessor.getTarget() instanceof TileEntityFusedPipe pipe
              && pipe.getConfig().isEnabled(FusedFunction.ENERGY)
              && pipe.getNetwork() != null;
    }
}
