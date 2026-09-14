package io.aduhtkjm.mekanismheated.integration.jade;

import io.aduhtkjm.mekanismheated.tile.multiblock.TileEntityRetroentropicArrayCasing;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.JadeIds;
import snownee.jade.api.TooltipPosition;
import snownee.jade.api.config.IPluginConfig;

/**
 * Removes Jade's universal item storage widget for the Retroentropic Array so only the labelled input/output items added
 * by {@link RetroentropicArrayMekRenderer} remain. The array exposes its two shared slots as an item handler (via
 * {@code TileEntityMultiblock#getInitialInventory}), which Jade's universal plugin would otherwise render a second time.
 * Registered on the shared {@code BlockBasicMultiblock} class, so it only acts for the array's casing.
 */
public enum RetroentropicArrayBuiltinRemover implements IComponentProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("mekanismheated", "retroentropic_array_remover");

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (accessor.getBlockEntity() instanceof TileEntityRetroentropicArrayCasing) {
            tooltip.remove(JadeIds.UNIVERSAL_ITEM_STORAGE);
        }
    }

    @Override
    public int getDefaultPriority() {
        //Run in the tail to ensure we are after Jade's universal item storage provider.
        return TooltipPosition.TAIL;
    }
}
