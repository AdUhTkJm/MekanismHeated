package io.aduhtkjm.mekanismheated.integration.jade;

import io.aduhtkjm.mekanismheated.tile.TileEntityHeatSmelter;
import mekanism.api.SerializationConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

public enum HeatSmelterMekDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("mekanismheated", "heat_smelter_mek_data");
    static final String KEY = "mh_heat_smelter_fluids";

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof TileEntityHeatSmelter smelter)) {
            return;
        }
        var fluids = smelter.fluidTank.getFluids();
        if (fluids.isEmpty()) {
            return;
        }
        CompoundTag mhData = new CompoundTag();
        mhData.putInt(SerializationConstants.MAX, smelter.fluidTank.getTotalCapacity());
        ListTag fluidList = new ListTag();
        for (FluidStack fluid : fluids) {
            fluidList.add(fluid.save(accessor.getLevel().registryAccess()));
        }
        mhData.put("fluids", fluidList);
        data.put(KEY, mhData);
    }
}
