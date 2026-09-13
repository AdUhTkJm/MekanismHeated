package io.aduhtkjm.mekanismheated.integration.jade;

import io.aduhtkjm.mekanismheated.tile.multiblock.FractionationMultiblockData;
import io.aduhtkjm.mekanismheated.tile.multiblock.TileEntityFractionationBlock;
import mekanism.api.SerializationConstants;
import mekanism.api.fluid.IExtendedFluidTank;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * Sends the thermal fractionation tower's state to Jade: the feed sump and each output bank as individual fluids, plus
 * the tower's temperature, height and bank count. Only sent once the multiblock is formed. The renderer only shows a
 * gauge for the non-empty tanks, so an idle or partially filled tower does not show a gauge per empty bank.
 */
public enum FractionationMekDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("mekanismheated", "fractionation_mek_data");
    static final String KEY = "mh_fractionation_contents";

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof TileEntityFractionationBlock fractionation)) {
            return;
        }
        FractionationMultiblockData multiblock = fractionation.getMultiblock();
        if (!multiblock.isFormed()) {
            return;
        }
        var registryAccess = accessor.getLevel().registryAccess();
        CompoundTag mhData = new CompoundTag();
        mhData.putDouble("temperature", multiblock.getTemperature());
        mhData.putInt("height", multiblock.height());
        mhData.putInt("bank_count", multiblock.getBankCount());
        CompoundTag sumpTag = new CompoundTag();
        sumpTag.put(SerializationConstants.FLUID, multiblock.inputTank.getFluid().saveOptional(registryAccess));
        sumpTag.putInt(SerializationConstants.MAX, multiblock.getSumpCapacity());
        mhData.put("sump", sumpTag);
        ListTag bankList = new ListTag();
        for (IExtendedFluidTank bank : multiblock.getOutputBanks()) {
            CompoundTag bankTag = new CompoundTag();
            bankTag.put(SerializationConstants.FLUID, bank.getFluid().saveOptional(registryAccess));
            bankTag.putInt(SerializationConstants.MAX, bank.getCapacity());
            bankList.add(bankTag);
        }
        mhData.put("banks", bankList);
        data.put(KEY, mhData);
    }
}
