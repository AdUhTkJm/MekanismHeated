package io.aduhtkjm.mekanismheated.integration.jade;

import io.aduhtkjm.mekanismheated.content.fusedpipe.FusedFunction;
import io.aduhtkjm.mekanismheated.content.fusedpipe.FusedNetwork;
import io.aduhtkjm.mekanismheated.tile.TileEntityFusedPipe;
import mekanism.api.SerializationConstants;
import mekanism.api.chemical.ChemicalStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

public enum FusedPipeChemicalProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    static final Identifier UID = Identifier.fromNamespaceAndPath("mekanismheated", "fused_pipe_chemical");
    static final String KEY = "mek_chem";

    @Override
    public Identifier getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof TileEntityFusedPipe pipe)) {
            return;
        }
        if (!pipe.getConfig().isEnabled(FusedFunction.CHEMICAL)) {
            return;
        }
        FusedNetwork network = pipe.getNetwork();
        if (network == null) {
            return;
        }
        ChemicalStack chemical = network.chemicalTank.getStack();
        long capacity = network.getChemicalCapacity();
        if (capacity <= 0) {
            return;
        }
        CompoundTag chemData = new CompoundTag();
        if (!chemical.isEmpty()) {
            chemData.put(SerializationConstants.CHEMICAL, chemical.save(accessor.getLevel().registryAccess()));
        }
        chemData.putLong("Capacity", capacity);
        data.put(KEY, chemData);
    }
}
