package io.aduhtkjm.mekanismheated.integration.jade;

import io.aduhtkjm.mekanismheated.tile.TileEntityPhaseChangeBlock;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * Sends a phase-change block's temperature, melting point and latent heat buffer to Jade, for
 * {@link PhaseChangeMekRenderer} to display.
 */
public enum PhaseChangeMekDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("mekanismheated", "phase_change_mek_data");
    static final String KEY = "mh_phase_change";

    static final String TEMPERATURE = "temperature";
    static final String MELTING_POINT = "meltingPoint";
    static final String BUFFERED_HEAT = "bufferedHeat";
    static final String BUFFER_CAPACITY = "bufferCapacity";

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof TileEntityPhaseChangeBlock phaseChange)) {
            return;
        }
        CompoundTag mhData = new CompoundTag();
        mhData.putDouble(TEMPERATURE, phaseChange.getTotalTemperature());
        mhData.putDouble(MELTING_POINT, phaseChange.getMeltingPoint());
        mhData.putDouble(BUFFERED_HEAT, phaseChange.getBufferedHeat());
        mhData.putDouble(BUFFER_CAPACITY, phaseChange.getBufferCapacity());
        data.put(KEY, mhData);
    }
}
