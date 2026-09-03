package io.aduhtkjm.mekanismheated.item;

import io.aduhtkjm.mekanismheated.block.cooler.CoolerBlock;
import io.aduhtkjm.mekanismheated.tile.TileEntityCooler;
import mekanism.common.attachments.containers.energy.EnergyContainersBuilder;
import mekanism.common.item.block.ItemBlockTooltip;
import mekanism.common.registries.MekanismDataComponents;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

public class ItemBlockCooler extends ItemBlockTooltip<CoolerBlock> {

    public ItemBlockCooler(CoolerBlock block, Item.Properties properties) {
        super(block, true, properties
              .component(MekanismDataComponents.ENERGY_USAGE, TileEntityCooler.BASE_USAGE)
        );
    }

    @Nullable
    @Override
    protected EnergyContainersBuilder addDefaultEnergyContainers(EnergyContainersBuilder builder) {
        return builder.addContainer(ComponentBackedCoolerEnergyContainer::create);
    }
}
