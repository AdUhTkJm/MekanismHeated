package io.aduhtkjm.mekanismheated.registries;

import io.aduhtkjm.mekanismheated.Config;
import io.aduhtkjm.mekanismheated.Mod;
import io.aduhtkjm.mekanismheated.ModLang;
import io.aduhtkjm.mekanismheated.block.BlockFusedPipe;
import io.aduhtkjm.mekanismheated.block.condenser.CondenserBlock;
import io.aduhtkjm.mekanismheated.block.cooler.CoolerBlock;
import io.aduhtkjm.mekanismheated.block.creative.CreativeHeatBlock;
import io.aduhtkjm.mekanismheated.block.fractionation.DistillationTrayBlock;
import io.aduhtkjm.mekanismheated.block.heatsmelter.HeatSmelterBlock;
import io.aduhtkjm.mekanismheated.block.shaker.ShakerBlock;
import io.aduhtkjm.mekanismheated.item.ItemBlockCooler;
import io.aduhtkjm.mekanismheated.item.ItemBlockFusedPipe;
import io.aduhtkjm.mekanismheated.content.fusedpipe.FusedPipeConfig;
import io.aduhtkjm.mekanismheated.tile.TileEntityCondenser;
import io.aduhtkjm.mekanismheated.tile.TileEntityCooler;
import io.aduhtkjm.mekanismheated.tile.TileEntityCreativeHeatBlock;
import io.aduhtkjm.mekanismheated.tile.TileEntityShaker;
import io.aduhtkjm.mekanismheated.tile.TileEntityHeatSmelter;
import io.aduhtkjm.mekanismheated.tile.multiblock.TileEntityFractionationBlock;
import io.aduhtkjm.mekanismheated.tile.multiblock.TileEntityThermalFractionationController;
import io.aduhtkjm.mekanismheated.tile.multiblock.TileEntityThermalFractionationValve;
import mekanism.common.block.attribute.AttributeSideConfig;
import mekanism.common.block.attribute.AttributeStateFacing;
import mekanism.common.block.attribute.AttributeUpgradeSupport;
import mekanism.common.block.attribute.Attributes;
import mekanism.common.block.attribute.Attributes.AttributeCustomResistance;
import mekanism.common.block.prefab.BlockBasicMultiblock;
import mekanism.common.content.blocktype.Machine;
import mekanism.common.content.blocktype.Machine.MachineBuilder;
import mekanism.common.content.blocktype.BlockTypeTile;
import mekanism.common.content.blocktype.BlockTypeTile.BlockTileBuilder;
import mekanism.common.item.block.ItemBlockTooltip;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.registration.impl.BlockDeferredRegister;
import mekanism.common.registration.impl.BlockRegistryObject;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.component.CustomData;
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

    public static final BlockRegistryObject<HeatSmelterBlock, ItemBlockTooltip<HeatSmelterBlock>> HEAT_SMELTER =
          BLOCKS.register("heat_smelter", () -> new HeatSmelterBlock(HEAT_SMELTER_TYPE, BlockBehaviour.Properties.of().mapColor(MapColor.METAL)),
                (block, properties) -> new ItemBlockTooltip<>(block, true, properties));

    public static final Machine<TileEntityShaker> SHAKER_TYPE = MachineBuilder
          .createMachine(() -> ModTileEntityTypes.SHAKER, ModLang.DESCRIPTION_SHAKER)
          .withGui(() -> ModContainerTypes.SHAKER)
          .withEnergyConfig(() -> Config.Shaker.ENERGY_PER_TICK.get(), () -> Config.Shaker.MAX_ENERGY.get())
          .with(AttributeSideConfig.create(TransmissionType.ITEM, TransmissionType.ENERGY, TransmissionType.FLUID))
          .build();

    public static final BlockRegistryObject<ShakerBlock, BlockItem> SHAKER =
          BLOCKS.register("shaker", () -> new ShakerBlock(SHAKER_TYPE, BlockBehaviour.Properties.of().mapColor(MapColor.METAL)));

    // Thermal Fractionation Tower
    public static final BlockTypeTile<TileEntityThermalFractionationController> THERMAL_FRACTIONATION_CONTROLLER_TYPE = BlockTileBuilder
          .createBlock(() -> ModTileEntityTypes.THERMAL_FRACTIONATION_CONTROLLER, ModLang.DESCRIPTION_THERMAL_FRACTIONATION_CONTROLLER)
          .withGui(() -> ModContainerTypes.THERMAL_FRACTIONATION_CONTROLLER)
          .with(Attributes.ACTIVE, new AttributeStateFacing(), new AttributeCustomResistance(9))
          .externalMultiblock()
          .build();

    public static final BlockTypeTile<TileEntityThermalFractionationValve> THERMAL_FRACTIONATION_VALVE_TYPE = BlockTileBuilder
          .createBlock(() -> ModTileEntityTypes.THERMAL_FRACTIONATION_VALVE, ModLang.DESCRIPTION_THERMAL_FRACTIONATION_VALVE)
          .with(new AttributeCustomResistance(9))
          .externalMultiblock()
          .build();

    public static final BlockTypeTile<TileEntityFractionationBlock> THERMAL_FRACTIONATION_CASING_TYPE = BlockTileBuilder
          .createBlock(() -> ModTileEntityTypes.THERMAL_FRACTIONATION_CASING, ModLang.DESCRIPTION_THERMAL_FRACTIONATION_CASING)
          .with(new AttributeCustomResistance(9))
          .externalMultiblock()
          .build();

    public static final BlockRegistryObject<BlockBasicMultiblock<TileEntityThermalFractionationController>, ItemBlockTooltip<BlockBasicMultiblock<TileEntityThermalFractionationController>>> THERMAL_FRACTIONATION_CONTROLLER =
          BLOCKS.register("thermal_fractionation_controller",
                () -> new BlockBasicMultiblock<>(THERMAL_FRACTIONATION_CONTROLLER_TYPE, properties -> properties.mapColor(MapColor.METAL)),
                (block, properties) -> new ItemBlockTooltip<>(block, true, properties));

    public static final BlockRegistryObject<BlockBasicMultiblock<TileEntityThermalFractionationValve>, ItemBlockTooltip<BlockBasicMultiblock<TileEntityThermalFractionationValve>>> THERMAL_FRACTIONATION_VALVE =
          BLOCKS.register("thermal_fractionation_valve",
                () -> new BlockBasicMultiblock<>(THERMAL_FRACTIONATION_VALVE_TYPE, properties -> properties.mapColor(MapColor.METAL)),
                (block, properties) -> new ItemBlockTooltip<>(block, true, properties));

    public static final BlockRegistryObject<BlockBasicMultiblock<TileEntityFractionationBlock>, ItemBlockTooltip<BlockBasicMultiblock<TileEntityFractionationBlock>>> THERMAL_FRACTIONATION_CASING =
          BLOCKS.register("thermal_fractionation_casing",
                () -> new BlockBasicMultiblock<>(THERMAL_FRACTIONATION_CASING_TYPE, properties -> properties.mapColor(MapColor.METAL)),
                (block, properties) -> new ItemBlockTooltip<>(block, true, properties));

    public static final BlockRegistryObject<DistillationTrayBlock, BlockItem> DISTILLATION_TRAY =
          BLOCKS.register("distillation_tray", DistillationTrayBlock::new);

    public static final BlockRegistryObject<BlockFusedPipe, ItemBlockFusedPipe> FUSED_PIPE =
          BLOCKS.register("fused_pipe",
                () -> new BlockFusedPipe(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(1.0F, 6.0F).noOcclusion().forceSolidOn()),
                (block, properties) -> new ItemBlockFusedPipe(block,
                      properties.component(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(FusedPipeConfig.createDefaultBlockEntityData()))));

    public static final Machine<TileEntityCreativeHeatBlock> CREATIVE_HEAT_BLOCK_TYPE = MachineBuilder
          .createMachine(() -> ModTileEntityTypes.CREATIVE_HEAT_BLOCK, ModLang.DESCRIPTION_CREATIVE_HEAT_BLOCK)
          .withGui(() -> ModContainerTypes.CREATIVE_HEAT_BLOCK)
          .without(AttributeUpgradeSupport.class)
          .build();

    public static final BlockRegistryObject<CreativeHeatBlock, BlockItem> CREATIVE_HEAT_BLOCK =
          BLOCKS.register("creative_heat_block", () -> new CreativeHeatBlock(CREATIVE_HEAT_BLOCK_TYPE, BlockBehaviour.Properties.of().mapColor(MapColor.METAL)));

    public static final Machine<TileEntityCooler> COOLER_TYPE = MachineBuilder
          .createMachine(() -> ModTileEntityTypes.COOLER, ModLang.DESCRIPTION_COOLER)
          .withGui(() -> ModContainerTypes.COOLER)
          .withEnergyConfig(() -> TileEntityCooler.BASE_USAGE, null)
          .build();

    public static final BlockRegistryObject<CoolerBlock, ItemBlockCooler> COOLER =
          BLOCKS.register("cooler", () -> new CoolerBlock(COOLER_TYPE, BlockBehaviour.Properties.of().mapColor(MapColor.METAL)),
                (block, properties) -> new ItemBlockCooler(block, properties));

    public static final Machine<TileEntityCondenser> CONDENSER_TYPE = MachineBuilder
          .createMachine(() -> ModTileEntityTypes.CONDENSER, ModLang.DESCRIPTION_CONDENSER)
          .withGui(() -> ModContainerTypes.CONDENSER)
          .with(AttributeSideConfig.create(TransmissionType.ITEM, TransmissionType.FLUID, TransmissionType.HEAT))
          .build();

    public static final BlockRegistryObject<CondenserBlock, ItemBlockTooltip<CondenserBlock>> CONDENSER =
          BLOCKS.register("condenser", () -> new CondenserBlock(CONDENSER_TYPE, BlockBehaviour.Properties.of().mapColor(MapColor.METAL)),
                (block, properties) -> new ItemBlockTooltip<>(block, true, properties));
}
