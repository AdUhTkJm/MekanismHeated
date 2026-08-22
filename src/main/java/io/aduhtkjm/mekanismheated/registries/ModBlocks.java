package io.aduhtkjm.mekanismheated.registries;

import io.aduhtkjm.mekanismheated.Config;
import io.aduhtkjm.mekanismheated.Mod;
import io.aduhtkjm.mekanismheated.ModLang;
import io.aduhtkjm.mekanismheated.block.shaker.ShakerBlock;
import io.aduhtkjm.mekanismheated.tile.TileEntityShaker;
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

    // 该注册器是 Mek 实现的，不是 NeoForge 自带的
    // 它可以同时注册 Block 和其对应的 Item，比原版注册器方便
    public static final BlockDeferredRegister BLOCKS = new BlockDeferredRegister(Mod.MODID);

    public static final Machine<TileEntityHeatSmelter> HEAT_SMELTER_TYPE = MachineBuilder
          .createMachine(() -> ModTileEntityTypes.HEAT_SMELTER, ModLang.DESCRIPTION_HEAT_SMELTER)
          .withGui(() -> ModContainerTypes.HEAT_SMELTER)
          .with(AttributeSideConfig.create(TransmissionType.ITEM, TransmissionType.FLUID, TransmissionType.HEAT))
          .build();

    public static final BlockRegistryObject<BlockTileModel<TileEntityHeatSmelter, Machine<TileEntityHeatSmelter>>, ItemBlockTooltip<BlockTileModel<TileEntityHeatSmelter, Machine<TileEntityHeatSmelter>>>> HEAT_SMELTER =
          BLOCKS.register("heat_smelter", () -> new BlockTileModel<>(HEAT_SMELTER_TYPE, properties -> properties.mapColor(MapColor.METAL)),
                (block, properties) -> new ItemBlockTooltip<>(block, true, properties));

    public static final Machine<TileEntityShaker> SHAKER_TYPE = MachineBuilder
          .createMachine(() -> ModTileEntityTypes.SHAKER, ModLang.DESCRIPTION_SHAKER)
          .withGui(() -> ModContainerTypes.SHAKER)
          .withEnergyConfig(() -> Config.Shaker.ENERGY_PER_TICK.get(), () -> Config.Shaker.MAX_ENERGY.get())
          .with(AttributeSideConfig.create(TransmissionType.ITEM, TransmissionType.ENERGY, TransmissionType.FLUID))
          .build();

    public static final BlockRegistryObject<ShakerBlock, BlockItem> SHAKER =
          BLOCKS.register("shaker", () -> new ShakerBlock(SHAKER_TYPE, BlockBehaviour.Properties.of().mapColor(MapColor.STONE)));
}
