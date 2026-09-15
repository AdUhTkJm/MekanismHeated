package io.aduhtkjm.mekanismheated.integration.jade;

import io.aduhtkjm.mekanismheated.tank.MultiFluidTank;
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
        // While formed, the fluids live in the shared brain.
        // So show the same tank for the GUI and the in-world renderer.
        MultiFluidTank tank = smelter.getDisplayFluidTank();
        var fluids = tank.getFluids();
        if (fluids.isEmpty()) {
            return;
        }
        CompoundTag mhData = new CompoundTag();
        mhData.putInt(SerializationConstants.MAX, tank.getTotalCapacity());
        ListTag fluidList = new ListTag();
        for (FluidStack fluid : fluids) {
            fluidList.add(fluid.save(accessor.getLevel().registryAccess()));
        }
        mhData.put("fluids", fluidList);
        data.put(KEY, mhData);
    }
}
