package io.aduhtkjm.mekanismheated.registries;

import io.aduhtkjm.mekanismheated.Config;
import io.aduhtkjm.mekanismheated.Mod;
import io.aduhtkjm.mekanismheated.ModLang;
import io.aduhtkjm.mekanismheated.block.BlockFusedPipe;
import io.aduhtkjm.mekanismheated.block.atmosphereheater.AtmosphereHeaterBlock;
import io.aduhtkjm.mekanismheated.block.condenser.CondenserBlock;
import io.aduhtkjm.mekanismheated.block.cooler.CoolerBlock;
import io.aduhtkjm.mekanismheated.block.creative.CreativeChunkHeaterBlock;
import io.aduhtkjm.mekanismheated.block.creative.CreativeHeatBlock;
import io.aduhtkjm.mekanismheated.block.fractionation.DistillationTrayBlock;
import io.aduhtkjm.mekanismheated.block.heatsmelter.HeatSmelterBlock;
import io.aduhtkjm.mekanismheated.block.phasechange.PhaseChangeBlock;
import io.aduhtkjm.mekanismheated.block.quenchingenrichmentchamber.QuenchingEnrichmentChamberBlock;
import io.aduhtkjm.mekanismheated.block.reactionchamber.ReactionChamberBlock;
import io.aduhtkjm.mekanismheated.block.shaker.ShakerBlock;
import io.aduhtkjm.mekanismheated.block.temperaturecontroller.TemperatureControllerBlock;
import io.aduhtkjm.mekanismheated.content.phasechange.PhaseChangeTier;
import io.aduhtkjm.mekanismheated.item.ItemBlockFusedPipe;
import io.aduhtkjm.mekanismheated.content.fusedpipe.FusedPipeConfig;
import io.aduhtkjm.mekanismheated.tile.TileEntityAtmosphereHeater;
import io.aduhtkjm.mekanismheated.tile.TileEntityCondenser;
import io.aduhtkjm.mekanismheated.tile.TileEntityCooler;
import io.aduhtkjm.mekanismheated.tile.TileEntityCreativeChunkHeater;
import io.aduhtkjm.mekanismheated.tile.TileEntityCreativeHeatBlock;
import io.aduhtkjm.mekanismheated.tile.TileEntityShaker;
import io.aduhtkjm.mekanismheated.tile.TileEntityHeatSmelter;
import io.aduhtkjm.mekanismheated.tile.TileEntityPhaseChangeBlock;
import io.aduhtkjm.mekanismheated.tile.TileEntityQuenchingEnrichmentChamber;
import io.aduhtkjm.mekanismheated.tile.TileEntityReactionChamber;
import io.aduhtkjm.mekanismheated.tile.TileEntityTemperatureController;
import io.aduhtkjm.mekanismheated.tile.multiblock.TileEntityFractionationBlock;
import io.aduhtkjm.mekanismheated.tile.multiblock.TileEntityRetroentropicArrayCasing;
import io.aduhtkjm.mekanismheated.tile.multiblock.TileEntityThermalFractionationController;
import io.aduhtkjm.mekanismheated.tile.multiblock.TileEntityThermalFractionationValve;
import mekanism.common.block.attribute.AttributeParticleFX;
import mekanism.common.block.attribute.AttributeSideConfig;
import mekanism.common.block.attribute.AttributeStateFacing;
import mekanism.common.block.attribute.AttributeUpgradeSupport;
import mekanism.common.block.attribute.Attributes;
import mekanism.common.block.attribute.Attributes.AttributeComparator;
import mekanism.common.block.attribute.Attributes.AttributeCustomResistance;
import mekanism.common.block.attribute.Attributes.AttributeInventory;
import mekanism.common.block.attribute.Attributes.AttributeRedstoneEmitter;
import mekanism.common.block.prefab.BlockBasicMultiblock;
import mekanism.common.config.MekanismConfig;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class ModBlocks {

    private ModBlocks() {
    }

    public static final BlockDeferredRegister BLOCKS = new BlockDeferredRegister(Mod.MODID);

    public static final BlockRegistryObject<Block, BlockItem> THERMAL_CASING =
          BLOCKS.register("thermal_casing", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(3.5F, 9F).sound(SoundType.METAL));

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

    public static final Machine<TileEntityCreativeChunkHeater> CREATIVE_CHUNK_HEATER_TYPE = MachineBuilder
          .createMachine(() -> ModTileEntityTypes.CREATIVE_CHUNK_HEATER, ModLang.DESCRIPTION_CREATIVE_CHUNK_HEATER)
          .withGui(() -> ModContainerTypes.CREATIVE_CHUNK_HEATER)
          .without(AttributeUpgradeSupport.class)
          .build();

    public static final BlockRegistryObject<CreativeChunkHeaterBlock, BlockItem> CREATIVE_CHUNK_HEATER =
          BLOCKS.register("creative_chunk_heater", () -> new CreativeChunkHeaterBlock(CREATIVE_CHUNK_HEATER_TYPE, BlockBehaviour.Properties.of().mapColor(MapColor.METAL)));

    public static final Machine<TileEntityCooler> COOLER_TYPE = MachineBuilder
          .createMachine(() -> ModTileEntityTypes.COOLER, ModLang.DESCRIPTION_COOLER)
          .withGui(() -> ModContainerTypes.COOLER)
          .withEnergyConfig(() -> TileEntityCooler.BASE_USAGE, null)
          .build();

    public static final BlockRegistryObject<CoolerBlock, BlockItem> COOLER =
          BLOCKS.register("cooler", () -> new CoolerBlock(COOLER_TYPE, BlockBehaviour.Properties.of().mapColor(MapColor.METAL)));

    public static final Machine<TileEntityCondenser> CONDENSER_TYPE = MachineBuilder
          .createMachine(() -> ModTileEntityTypes.CONDENSER, ModLang.DESCRIPTION_CONDENSER)
          .withGui(() -> ModContainerTypes.CONDENSER)
          .with(AttributeSideConfig.create(TransmissionType.ITEM, TransmissionType.FLUID, TransmissionType.HEAT))
          .build();

    public static final BlockRegistryObject<CondenserBlock, ItemBlockTooltip<CondenserBlock>> CONDENSER =
          BLOCKS.register("condenser", () -> new CondenserBlock(CONDENSER_TYPE, BlockBehaviour.Properties.of().mapColor(MapColor.METAL)),
                (block, properties) -> new ItemBlockTooltip<>(block, true, properties));

    public static final Machine<TileEntityQuenchingEnrichmentChamber> QUENCHING_ENRICHMENT_CHAMBER_TYPE = MachineBuilder
          .createMachine(() -> ModTileEntityTypes.QUENCHING_ENRICHMENT_CHAMBER, ModLang.DESCRIPTION_QUENCHING_ENRICHMENT_CHAMBER)
          .withGui(() -> ModContainerTypes.QUENCHING_ENRICHMENT_CHAMBER)
          .withEnergyConfig(() -> Config.QuenchingEnrichmentChamber.ENERGY_PER_TICK.get(), () -> Config.QuenchingEnrichmentChamber.MAX_ENERGY.get())
          .with(AttributeSideConfig.create(TransmissionType.ITEM, TransmissionType.FLUID, TransmissionType.ENERGY))
          .build();

    public static final BlockRegistryObject<QuenchingEnrichmentChamberBlock, ItemBlockTooltip<QuenchingEnrichmentChamberBlock>> QUENCHING_ENRICHMENT_CHAMBER =
          BLOCKS.register("quenching_enrichment_chamber",
                () -> new QuenchingEnrichmentChamberBlock(QUENCHING_ENRICHMENT_CHAMBER_TYPE, BlockBehaviour.Properties.of().mapColor(MapColor.METAL)),
                (block, properties) -> new ItemBlockTooltip<>(block, true, properties));

    public static final Machine<TileEntityReactionChamber> REACTION_CHAMBER_TYPE = MachineBuilder
          .createMachine(() -> ModTileEntityTypes.REACTION_CHAMBER, ModLang.DESCRIPTION_REACTION_CHAMBER)
          .withGui(() -> ModContainerTypes.REACTION_CHAMBER)
          .with(AttributeSideConfig.create(TransmissionType.ITEM, TransmissionType.FLUID, TransmissionType.CHEMICAL, TransmissionType.HEAT))
          .without(AttributeUpgradeSupport.class)
          .build();

    public static final BlockRegistryObject<ReactionChamberBlock, ItemBlockTooltip<ReactionChamberBlock>> REACTION_CHAMBER =
          BLOCKS.register("reaction_chamber", () -> new ReactionChamberBlock(REACTION_CHAMBER_TYPE, BlockBehaviour.Properties.of().mapColor(MapColor.METAL)),
                (block, properties) -> new ItemBlockTooltip<>(block, true, properties));

    public static final Machine<TileEntityAtmosphereHeater> ATMOSPHERE_HEATER_TYPE = MachineBuilder
          .createMachine(() -> ModTileEntityTypes.ATMOSPHERE_HEATER, ModLang.DESCRIPTION_ATMOSPHERE_HEATER)
          .withGui(() -> ModContainerTypes.ATMOSPHERE_HEATER)
          .withEnergyConfig(
                //Energy config values are in FE/RF; the energy container works in Mekanism Joules, so convert with Mekanism's FE conversion rate.
                () -> Math.round(Config.AtmosphereHeater.ENERGY_PER_TICK.get() * MekanismConfig.general.forgeConversionRate.get()),
                () -> Math.round(Config.AtmosphereHeater.MAX_ENERGY.get() * MekanismConfig.general.forgeConversionRate.get()))
          .with(AttributeSideConfig.create(TransmissionType.ITEM, TransmissionType.CHEMICAL, TransmissionType.ENERGY))
          .without(AttributeUpgradeSupport.class)
          .build();

    public static final BlockRegistryObject<AtmosphereHeaterBlock, BlockItem> ATMOSPHERE_HEATER =
          BLOCKS.register("atmosphere_heater", () -> new AtmosphereHeaterBlock(ATMOSPHERE_HEATER_TYPE, BlockBehaviour.Properties.of().mapColor(MapColor.METAL)));

    public static final Machine<TileEntityTemperatureController> TEMPERATURE_CONTROLLER_TYPE = MachineBuilder
          .createMachine(() -> ModTileEntityTypes.TEMPERATURE_CONTROLLER, ModLang.DESCRIPTION_TEMPERATURE_CONTROLLER)
          .withGui(() -> ModContainerTypes.TEMPERATURE_CONTROLLER)
          .without(AttributeInventory.class, AttributeUpgradeSupport.class, AttributeParticleFX.class, AttributeComparator.class)
          .with(new AttributeRedstoneEmitter<>((tile, side) -> tile.getRedstoneOutput()))
          .build();

    public static final BlockRegistryObject<TemperatureControllerBlock, ItemBlockTooltip<TemperatureControllerBlock>> TEMPERATURE_CONTROLLER =
          BLOCKS.register("temperature_controller",
                () -> new TemperatureControllerBlock(TEMPERATURE_CONTROLLER_TYPE, BlockBehaviour.Properties.of().mapColor(MapColor.METAL)),
                (block, properties) -> new ItemBlockTooltip<>(block, true, properties));

    // Phase-change blocks. The three tiers only differ by melting point (see Config.PhaseChange); the block itself
    // stores the tier, which the shared TileEntityPhaseChangeBlock reads back out of its block state.
    public static final Machine<TileEntityPhaseChangeBlock> PHASE_CHANGE_LOW_TYPE = MachineBuilder
          .createMachine(() -> ModTileEntityTypes.PHASE_CHANGE_LOW, ModLang.DESCRIPTION_PHASE_CHANGE_LOW)
          .withGui(() -> ModContainerTypes.PHASE_CHANGE_BLOCK)
          .without(AttributeUpgradeSupport.class)
          .build();

    public static final Machine<TileEntityPhaseChangeBlock> PHASE_CHANGE_MEDIUM_TYPE = MachineBuilder
          .createMachine(() -> ModTileEntityTypes.PHASE_CHANGE_MEDIUM, ModLang.DESCRIPTION_PHASE_CHANGE_MEDIUM)
          .withGui(() -> ModContainerTypes.PHASE_CHANGE_BLOCK)
          .without(AttributeUpgradeSupport.class)
          .build();

    public static final Machine<TileEntityPhaseChangeBlock> PHASE_CHANGE_HIGH_TYPE = MachineBuilder
          .createMachine(() -> ModTileEntityTypes.PHASE_CHANGE_HIGH, ModLang.DESCRIPTION_PHASE_CHANGE_HIGH)
          .withGui(() -> ModContainerTypes.PHASE_CHANGE_BLOCK)
          .without(AttributeUpgradeSupport.class)
          .build();

    public static final BlockRegistryObject<PhaseChangeBlock, ItemBlockTooltip<PhaseChangeBlock>> PHASE_CHANGE_LOW =
          BLOCKS.register("phase_change_block_low",
                () -> new PhaseChangeBlock(PhaseChangeTier.LOW, PHASE_CHANGE_LOW_TYPE, phaseChangeProperties(MapColor.COLOR_LIGHT_BLUE)),
                (block, properties) -> new ItemBlockTooltip<>(block, true, properties));

    public static final BlockRegistryObject<PhaseChangeBlock, ItemBlockTooltip<PhaseChangeBlock>> PHASE_CHANGE_MEDIUM =
          BLOCKS.register("phase_change_block_medium",
                () -> new PhaseChangeBlock(PhaseChangeTier.MEDIUM, PHASE_CHANGE_MEDIUM_TYPE, phaseChangeProperties(MapColor.QUARTZ)),
                (block, properties) -> new ItemBlockTooltip<>(block, true, properties));

    public static final BlockRegistryObject<PhaseChangeBlock, ItemBlockTooltip<PhaseChangeBlock>> PHASE_CHANGE_HIGH =
          BLOCKS.register("phase_change_block_high",
                () -> new PhaseChangeBlock(PhaseChangeTier.HIGH, PHASE_CHANGE_HIGH_TYPE, phaseChangeProperties(MapColor.COLOR_ORANGE)),
                (block, properties) -> new ItemBlockTooltip<>(block, true, properties));

    private static BlockBehaviour.Properties phaseChangeProperties(MapColor mapColor) {
        return BlockBehaviour.Properties.of().mapColor(mapColor).strength(3.5F, 9F).sound(SoundType.METAL);
    }

    public static final BlockTypeTile<TileEntityRetroentropicArrayCasing> RETROENTROPIC_ARRAY_TYPE = BlockTileBuilder
        .createBlock(() -> ModTileEntityTypes.RETROENTROPIC_ARRAY_CASING, ModLang.DESCRIPTION_RETROENTROPIC_ARRAY_CASING)
        .with(new AttributeCustomResistance(9))
        .externalMultiblock()
        .build();

    public static final BlockRegistryObject<BlockBasicMultiblock<TileEntityRetroentropicArrayCasing>, ItemBlockTooltip<BlockBasicMultiblock<TileEntityRetroentropicArrayCasing>>> RETROENTROPIC_ARRAY_CASING =
        BLOCKS.register("retroentropic_array_casing",
            () -> new BlockBasicMultiblock<>(RETROENTROPIC_ARRAY_TYPE, BlockBehaviour.Properties.of().mapColor(MapColor.METAL)),
            (block, properties) -> new ItemBlockTooltip<>(block, true, properties));;
}
