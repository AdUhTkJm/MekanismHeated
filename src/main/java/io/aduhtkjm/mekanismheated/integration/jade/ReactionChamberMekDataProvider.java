package io.aduhtkjm.mekanismheated.integration.jade;

import io.aduhtkjm.mekanismheated.tank.MultiFluidChemicalTank;
import io.aduhtkjm.mekanismheated.tile.TileEntityReactionChamber;
import mekanism.api.SerializationConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * Sends the reaction chamber's shared fluid/chemical pool to Jade, but only the non-empty stacks: the machine's
 * {@link MultiFluidChemicalTank} exposes 16 fluid views and 16 chemical views, so Mekanism's own provider would render
 * sixteen empty gauges for each type. Instead this sends one list of fluids and one list of chemicals plus the pool's
 * total capacity, and {@link ReactionChamberMekRenderer} reconstructs one gauge per stored substance.
 */
public enum ReactionChamberMekDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("mekanismheated", "reaction_chamber_mek_data");
    static final String KEY = "mh_reaction_chamber_contents";

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof TileEntityReactionChamber chamber)) {
            return;
        }
        if (chamber.contentsTank.isEmpty()) {
            return;
        }
        CompoundTag mhData = new CompoundTag();
        mhData.putInt(SerializationConstants.MAX, chamber.contentsTank.getTotalCapacity());
        ListTag fluidList = new ListTag();
        for (FluidStack fluid : chamber.contentsTank.getFluids()) {
            fluidList.add(fluid.save(accessor.getLevel().registryAccess()));
        }
        mhData.put("fluids", fluidList);
        ListTag chemicalList = new ListTag();
        for (var chemical : chamber.contentsTank.getChemicals()) {
            chemicalList.add(chemical.save(accessor.getLevel().registryAccess()));
        }
        mhData.put("chemicals", chemicalList);
        data.put(KEY, mhData);
    }
}
