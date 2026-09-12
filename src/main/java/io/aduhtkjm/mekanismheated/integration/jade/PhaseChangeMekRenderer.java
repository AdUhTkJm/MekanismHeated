package io.aduhtkjm.mekanismheated.integration.jade;

import io.aduhtkjm.mekanismheated.ModLang;
import mekanism.common.MekanismLang;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.UnitDisplayUtils.TemperatureUnit;
import mekanism.common.util.text.TextUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Displays the phase-change block data sent by {@link PhaseChangeMekDataProvider}: temperature, melting point and how
 * full the latent heat buffer is. Mekanism's own Jade integration does not show heat, so there is nothing to remove.
 */
public enum PhaseChangeMekRenderer implements IComponentProvider<BlockAccessor> {
    INSTANCE;

    @Override
    public ResourceLocation getUid() {
        return PhaseChangeMekDataProvider.UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag serverData = accessor.getServerData();
        if (!serverData.contains(PhaseChangeMekDataProvider.KEY, Tag.TAG_COMPOUND)) {
            return;
        }
        CompoundTag mhData = serverData.getCompound(PhaseChangeMekDataProvider.KEY);
        tooltip.add(MekanismLang.TEMPERATURE.translate(MekanismUtils.getTemperatureDisplay(
              mhData.getDouble(PhaseChangeMekDataProvider.TEMPERATURE), TemperatureUnit.KELVIN, true)));
        tooltip.add(ModLang.GUI_PHASE_CHANGE_MELTING_POINT.translate(MekanismUtils.getTemperatureDisplay(
              mhData.getDouble(PhaseChangeMekDataProvider.MELTING_POINT), TemperatureUnit.KELVIN, true)));
        tooltip.add(ModLang.GUI_PHASE_CHANGE_BUFFER.translate(
              TextUtils.format(mhData.getDouble(PhaseChangeMekDataProvider.BUFFERED_HEAT))
                    + " / " + TextUtils.format(mhData.getDouble(PhaseChangeMekDataProvider.BUFFER_CAPACITY))));
    }
}
