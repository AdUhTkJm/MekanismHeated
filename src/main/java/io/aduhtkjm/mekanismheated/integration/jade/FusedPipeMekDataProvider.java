package io.aduhtkjm.mekanismheated.integration.jade;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import io.aduhtkjm.mekanismheated.content.fusedpipe.FusedFunction;
import io.aduhtkjm.mekanismheated.content.fusedpipe.FusedNetwork;
import io.aduhtkjm.mekanismheated.tile.TileEntityFusedPipe;
import mekanism.api.SerializationConstants;
import mekanism.api.chemical.ChemicalStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.IItemHandler;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

public enum FusedPipeMekDataProvider implements IServerDataProvider<BlockAccessor> {
    INSTANCE;

    static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath("mekanismheated", "fused_pipe_mek_data");
    static final String KEY = "mh_mek_data";
    private static final int MAX_DISPLAYED_ITEMS = 4;

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

        // Heat
        if (pipe.getConfig().isEnabled(FusedFunction.HEAT)) {
            if (network.getTotalHeatCapacity() > 0) {
                CompoundTag heatTag = new CompoundTag();
                heatTag.putDouble("temperature", network.getTemperature());
                mhData.put("heat", heatTag);
            }
        }

        // Items
        if (pipe.getConfig().isEnabled(FusedFunction.ITEM)) {
            List<ItemStack> items = aggregateItems(network);
            if (!items.isEmpty()) {
                ListTag itemList = new ListTag();
                for (ItemStack stack : items) {
                    itemList.add(stack.save(accessor.getLevel().registryAccess()));
                }
                mhData.put("items", itemList);
            }
        }

        if (!mhData.isEmpty()) {
            data.put(KEY, mhData);
        }
    }

    /**
     * Aggregates the network item buffer by item kind and returns at most {@value #MAX_DISPLAYED_ITEMS}
     * kinds (largest counts first), so the Jade tooltip stays compact no matter how much is buffered.
     */
    private static List<ItemStack> aggregateItems(FusedNetwork network) {
        IItemHandler handler = network.getItemHandler();
        List<ItemStack> aggregated = new ArrayList<>();
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            boolean merged = false;
            for (ItemStack representative : aggregated) {
                if (ItemStack.isSameItemSameComponents(representative, stack)) {
                    representative.grow(stack.getCount());
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                aggregated.add(stack.copy());
            }
        }
        aggregated.sort(Comparator.comparingInt(ItemStack::getCount).reversed());
        if (aggregated.size() > MAX_DISPLAYED_ITEMS) {
            aggregated = aggregated.subList(0, MAX_DISPLAYED_ITEMS);
        }
        return aggregated;
    }
}
