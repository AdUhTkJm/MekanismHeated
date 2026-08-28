package io.aduhtkjm.mekanismheated.integration.jade;

import io.aduhtkjm.mekanismheated.tile.TileEntityFusedPipe;
import mekanism.common.integration.lookingat.LookingAtUtils;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.JadeIds;
import snownee.jade.api.TooltipPosition;
import snownee.jade.api.config.IPluginConfig;

public enum FusedPipeBuiltinRemover implements IComponentProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("mekanismheated", "fused_pipe_remover");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (accessor.getTarget() instanceof TileEntityFusedPipe) {
            // Remove Jade universal storage widgets (energy, fluid)
            tooltip.remove(JadeIds.UNIVERSAL_ENERGY_STORAGE);
            tooltip.remove(JadeIds.UNIVERSAL_FLUID_STORAGE);
            // Remove Mekanism mek_data elements (rendered by JadeTooltipRenderer as MekElement)
            // These are tagged with element IDs, not the renderer UID
            tooltip.remove(LookingAtUtils.ENERGY);
            tooltip.remove(LookingAtUtils.FLUID);
            tooltip.remove(LookingAtUtils.CHEMICAL);
        }
    }

    @Override
    public int getDefaultPriority() {
        return TooltipPosition.TAIL;
    }
}
