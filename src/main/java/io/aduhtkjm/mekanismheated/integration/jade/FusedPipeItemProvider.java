package io.aduhtkjm.mekanismheated.integration.jade;

import java.util.ArrayList;
import java.util.List;
import io.aduhtkjm.mekanismheated.content.fusedpipe.FusedFunction;
import io.aduhtkjm.mekanismheated.content.fusedpipe.FusedNetwork;
import io.aduhtkjm.mekanismheated.tile.TileEntityFusedPipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import snownee.jade.api.Accessor;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ViewGroup;

public enum FusedPipeItemProvider implements IServerExtensionProvider<ItemStack> {
    INSTANCE;

    @Override
    public ResourceLocation getUid() {
        return ResourceLocation.fromNamespaceAndPath("mekanismheated", "fused_pipe_items");
    }

    @Override
    public List<ViewGroup<ItemStack>> getGroups(Accessor<?> accessor) {
        if (!(accessor.getTarget() instanceof TileEntityFusedPipe pipe)) {
            return null;
        }
        if (!pipe.getConfig().isEnabled(FusedFunction.ITEM)) {
            return null;
        }
        FusedNetwork network = pipe.getNetwork();
        if (network == null) {
            return null;
        }
        IItemHandler handler = network.getItemHandler();
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty()) {
                items.add(stack);
            }
        }
        if (items.isEmpty()) {
            return null;
        }
        return List.of(new ViewGroup<>(items));
    }

    @Override
    public boolean shouldRequestData(Accessor<?> accessor) {
        return accessor.getTarget() instanceof TileEntityFusedPipe pipe
              && pipe.getConfig().isEnabled(FusedFunction.ITEM)
              && pipe.getNetwork() != null;
    }
}
