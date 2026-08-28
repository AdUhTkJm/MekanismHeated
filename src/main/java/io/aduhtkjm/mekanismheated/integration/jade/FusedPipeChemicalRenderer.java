package io.aduhtkjm.mekanismheated.integration.jade;

import mekanism.api.MekanismAPI;
import mekanism.api.SerializationConstants;
import mekanism.api.chemical.ChemicalStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public enum FusedPipeChemicalRenderer implements IComponentProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public Identifier getUid() {
        return FusedPipeChemicalProvider.UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag serverData = accessor.getServerData();
        if (!serverData.contains(FusedPipeChemicalProvider.KEY, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag chemData = serverData.getCompound(FusedPipeChemicalProvider.KEY);
        long capacity = chemData.getLong("Capacity");
        if (capacity <= 0) {
            return;
        }
        if (chemData.contains(SerializationConstants.CHEMICAL, Tag.TAG_COMPOUND)) {
            ChemicalStack chemical = ChemicalStack.parseOptional(
                  accessor.getLevel().registryAccess(),
                  chemData.getCompound(SerializationConstants.CHEMICAL));
            if (!chemical.isEmpty()) {
                Identifier chemId = MekanismAPI.CHEMICAL_REGISTRY.getKey(chemical.getType());
                String name = chemId != null ? chemId.toString() : "unknown";
                tooltip.add(Component.literal("Chemical: " + name + " " + chemical.getAmount() + " / " + capacity + " mB"));
            } else {
                tooltip.add(Component.literal("Chemical: empty / " + capacity + " mB"));
            }
        } else {
            tooltip.add(Component.literal("Chemical: empty / " + capacity + " mB"));
        }
    }
}
