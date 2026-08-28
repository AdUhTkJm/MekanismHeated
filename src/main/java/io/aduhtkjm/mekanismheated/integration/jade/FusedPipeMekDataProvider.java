package io.aduhtkjm.mekanismheated.integration.jade;

import io.aduhtkjm.mekanismheated.content.fusedpipe.FusedFunction;
import io.aduhtkjm.mekanismheated.content.fusedpipe.FusedNetwork;
import io.aduhtkjm.mekanismheated.tile.TileEntityFusedPipe;
import mekanism.api.SerializationConstants;
import mekanism.api.chemical.ChemicalStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

public enum FusedPipeMekDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("mekanismheated", "fused_pipe_mek_data");
    static final String KEY = "mh_mek_data";

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof TileEntityFusedPipe pipe)) {
            return;
        }
        FusedNetwork network = pipe.getNetwork();
        if (network == null) {
            return;
        }

        CompoundTag mhData = new CompoundTag();

        // Energy
        if (pipe.getConfig().isEnabled(FusedFunction.ENERGY)) {
            long energyCapacity = network.getEnergyCapacity();
            if (energyCapacity > 0) {
                CompoundTag energyTag = new CompoundTag();
                energyTag.putLong(SerializationConstants.ENERGY, network.getEnergy());
                energyTag.putLong(SerializationConstants.MAX, energyCapacity);
                mhData.put("energy", energyTag);
            }
        }

        // Fluid
        if (pipe.getConfig().isEnabled(FusedFunction.FLUID)) {
            long fluidCapacity = network.getFluidCapacity();
            if (fluidCapacity > 0) {
                CompoundTag fluidTag = new CompoundTag();
                fluidTag.putInt(SerializationConstants.MAX, (int) fluidCapacity);
                FluidStack fluid = network.fluidTank.getFluid();
                if (!fluid.isEmpty()) {
                    fluidTag.put(SerializationConstants.FLUID,
                          fluid.save(accessor.getLevel().registryAccess()));
                }
                mhData.put("fluid", fluidTag);
            }
        }

        // Chemical
        if (pipe.getConfig().isEnabled(FusedFunction.CHEMICAL)) {
            long chemicalCapacity = network.getChemicalCapacity();
            if (chemicalCapacity > 0) {
                CompoundTag chemTag = new CompoundTag();
                chemTag.putLong(SerializationConstants.MAX, chemicalCapacity);
                ChemicalStack chemical = network.chemicalTank.getStack();
                if (!chemical.isEmpty()) {
                    chemTag.put(SerializationConstants.CHEMICAL,
                          chemical.save(accessor.getLevel().registryAccess()));
                }
                mhData.put("chemical", chemTag);
            }
        }

        if (!mhData.isEmpty()) {
            data.put(KEY, mhData);
        }
    }
}
