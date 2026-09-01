package io.aduhtkjm.mekanismheated.integration.jade;

import io.aduhtkjm.mekanismheated.tile.TileEntityHeatSmelter;
import mekanism.common.integration.lookingat.LookingAtUtils;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.JadeIds;
import snownee.jade.api.TooltipPosition;
import snownee.jade.api.config.IPluginConfig;

public enum HeatSmelterBuiltinRemover implements IComponentProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("mekanismheated", "heat_smelter_remover");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (accessor.getBlockEntity() instanceof TileEntityHeatSmelter) {
            // Remove Jade universal fluid storage widget (vanilla)
            tooltip.remove(JadeIds.UNIVERSAL_FLUID_STORAGE);
            // Remove Mekanism mek_data fluid elements (rendered by JadeTooltipRenderer as MekElement)
            // These are tagged with the element ID, not the renderer UID; all 16 slots are removed here
            // and replaced with the filtered elements from HeatSmelterMekRenderer
            tooltip.remove(LookingAtUtils.FLUID);
        }
    }

    @Override
    public int getDefaultPriority() {
        return TooltipPosition.TAIL;
    }
}
