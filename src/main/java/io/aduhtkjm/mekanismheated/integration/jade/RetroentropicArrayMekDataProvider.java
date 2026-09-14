package io.aduhtkjm.mekanismheated.integration.jade;

import io.aduhtkjm.mekanismheated.tile.multiblock.RetroentropicArrayData;
import io.aduhtkjm.mekanismheated.tile.multiblock.TileEntityRetroentropicArrayCasing;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * Sends the Retroentropic Array's state to Jade: its (sub-zero) temperature, the current backtrack progress and the
 * input/output items. Only sent once the multiblock is formed. Like the fractionation tower, the whole array shares one
 * multiblock, so looking at any casing resolves the same data.
 */
public enum RetroentropicArrayMekDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("mekanismheated", "retroentropic_array_mek_data");
    static final String KEY = "mh_retroentropic_array_contents";

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof TileEntityRetroentropicArrayCasing casing)) {
            return;
        }
        RetroentropicArrayData multiblock = casing.getMultiblock();
        if (!multiblock.isFormed()) {
            return;
        }
        var registryAccess = accessor.getLevel().registryAccess();
        CompoundTag mhData = new CompoundTag();
        mhData.putDouble("temperature", multiblock.getTemperature());
        mhData.putInt("backtracked", multiblock.getBacktracked());
        mhData.putInt("duration", multiblock.getDuration());
        mhData.putBoolean("processing", multiblock.isProcessing());
        ItemStack input = multiblock.inputSlot.getStack();
        if (!input.isEmpty()) {
            mhData.put("input", input.save(registryAccess));
        }
        ItemStack output = multiblock.outputSlot.getStack();
        if (!output.isEmpty()) {
            mhData.put("output", output.save(registryAccess));
        }
        data.put(KEY, mhData);
    }
}
