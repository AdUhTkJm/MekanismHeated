package io.aduhtkjm.mekanismheated.integration.jade;

import java.util.List;
import io.aduhtkjm.mekanismheated.content.fusedpipe.FusedFunction;
import io.aduhtkjm.mekanismheated.content.fusedpipe.FusedNetwork;
import io.aduhtkjm.mekanismheated.tile.TileEntityFusedPipe;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.fluids.FluidStack;
import snownee.jade.api.Accessor;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;

public enum FusedPipeFluidProvider implements IServerExtensionProvider<FluidView.Data> {
    INSTANCE;

    @Override
    public Identifier getUid() {
        return Identifier.fromNamespaceAndPath("mekanismheated", "fused_pipe_fluid");
    }

    @Override
    public List<ViewGroup<FluidView.Data>> getGroups(Accessor<?> accessor) {
        if (!(accessor.getTarget() instanceof TileEntityFusedPipe pipe)) {
            return null;
        }
        if (!pipe.getConfig().isEnabled(FusedFunction.FLUID)) {
            return null;
        }
        FusedNetwork network = pipe.getNetwork();
        if (network == null) {
            return null;
        }
        FluidStack fluid = network.fluidTank.getFluid();
        long capacity = network.getFluidCapacity();
        if (capacity <= 0) {
            return null;
        }
        JadeFluidObject jadeFluid = fluid.isEmpty() ? JadeFluidObject.empty()
              : JadeFluidObject.of(fluid.getFluid(), fluid.getAmount(), fluid.getComponentsPatch());
        return List.of(new ViewGroup<>(List.of(new FluidView.Data(jadeFluid, capacity))));
    }

    @Override
    public boolean shouldRequestData(Accessor<?> accessor) {
        return accessor.getTarget() instanceof TileEntityFusedPipe pipe
              && pipe.getConfig().isEnabled(FusedFunction.FLUID)
              && pipe.getNetwork() != null;
    }
}
