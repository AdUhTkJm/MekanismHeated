package io.aduhtkjm.mekanismheated.registries;

import io.aduhtkjm.mekanismheated.MekanismHeated;
import io.aduhtkjm.mekanismheated.MekanismHeatedLang;
import io.aduhtkjm.mekanismheated.block.shaker.ShakerBlock;
import io.aduhtkjm.mekanismheated.tile.TileEntityHeatSmelter;
import mekanism.common.block.attribute.AttributeSideConfig;
import mekanism.common.block.prefab.BlockTile.BlockTileModel;
import mekanism.common.content.blocktype.Machine;
import mekanism.common.content.blocktype.Machine.MachineBuilder;
import mekanism.common.item.block.ItemBlockTooltip;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.registration.impl.BlockDeferredRegister;
import mekanism.common.registration.impl.BlockRegistryObject;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class ModBlocks {

    private ModBlocks() {
    }

    public static final BlockDeferredRegister BLOCKS = new BlockDeferredRegister(MekanismHeated.MODID);

    public static final Machine<TileEntityHeatSmelter> HEAT_SMELTER_TYPE = MachineBuilder
          .createMachine(() -> ModTileEntityTypes.HEAT_SMELTER, MekanismHeatedLang.DESCRIPTION_HEAT_SMELTER)
          .withGui(() -> ModContainerTypes.HEAT_SMELTER)
          .with(AttributeSideConfig.create(TransmissionType.ITEM))
          .build();

    public static final BlockRegistryObject<BlockTileModel<TileEntityHeatSmelter, Machine<TileEntityHeatSmelter>>, ItemBlockTooltip<BlockTileModel<TileEntityHeatSmelter, Machine<TileEntityHeatSmelter>>>> HEAT_SMELTER =
          BLOCKS.register("heat_smelter", () -> new BlockTileModel<>(HEAT_SMELTER_TYPE, properties -> properties.mapColor(MapColor.METAL)),
                (block, properties) -> new ItemBlockTooltip<>(block, true, properties));
}