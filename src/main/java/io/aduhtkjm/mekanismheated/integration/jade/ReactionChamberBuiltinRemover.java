package io.aduhtkjm.mekanismheated.integration.jade;

import io.aduhtkjm.mekanismheated.tile.TileEntityReactionChamber;
import mekanism.common.integration.lookingat.LookingAtUtils;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.JadeIds;
import snownee.jade.api.TooltipPosition;
import snownee.jade.api.config.IPluginConfig;

/**
 * Removes every default rendering for the reaction chamber so only the non-empty contents added by
 * {@link ReactionChamberMekRenderer} remain: Jade's universal fluid/energy widgets and Mekanism's mek_data gauges for all
 * sixteen fluid views and sixteen chemical views of the shared pool (empty ones included).
 */
public enum ReactionChamberBuiltinRemover implements IComponentProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("mekanismheated", "reaction_chamber_remover");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (accessor.getBlockEntity() instanceof TileEntityReactionChamber) {
            tooltip.remove(JadeIds.UNIVERSAL_FLUID_STORAGE);
            tooltip.remove(JadeIds.UNIVERSAL_ENERGY_STORAGE);
            tooltip.remove(LookingAtUtils.FLUID);
            tooltip.remove(LookingAtUtils.CHEMICAL);
        }
    }

    @Override
    public int getDefaultPriority() {
        //Run in the tail to ensure we are after Mekanism's JadeTooltipRenderer and JadeBuiltinRemover,
        // which add the mek_data fluid/chemical elements and strip the universal widgets respectively.
        return TooltipPosition.TAIL;
    }
}
