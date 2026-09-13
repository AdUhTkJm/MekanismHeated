package io.aduhtkjm.mekanismheated.registries;

import io.aduhtkjm.mekanismheated.Mod;
import io.aduhtkjm.mekanismheated.tile.*;
import io.aduhtkjm.mekanismheated.tile.multiblock.TileEntityFractionationBlock;
import io.aduhtkjm.mekanismheated.tile.multiblock.TileEntityRetroentropicArrayCasing;
import io.aduhtkjm.mekanismheated.tile.multiblock.TileEntityThermalFractionationController;
import io.aduhtkjm.mekanismheated.tile.multiblock.TileEntityThermalFractionationValve;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.registration.impl.TileEntityTypeDeferredRegister;
import mekanism.common.registration.impl.TileEntityTypeRegistryObject;
import mekanism.common.tile.base.CapabilityTileEntity;
import mekanism.common.tile.base.TileEntityMekanism;

public class ModTileEntityTypes {

    private ModTileEntityTypes() {
    }

    public static final TileEntityTypeDeferredRegister TILE_ENTITY_TYPES = new TileEntityTypeDeferredRegister(Mod.MODID);

    public static final TileEntityTypeRegistryObject<TileEntityHeatSmelter> HEAT_SMELTER = TILE_ENTITY_TYPES.mekBuilder(ModBlocks.HEAT_SMELTER, TileEntityHeatSmelter::new)
          .clientTicker(TileEntityMekanism::tickClient)
          .serverTicker(TileEntityMekanism::tickServer)
          .withSimple(Capabilities.CONFIG_CARD)
          .build();

    public static final TileEntityTypeRegistryObject<TileEntityShaker> SHAKER = TILE_ENTITY_TYPES.mekBuilder(ModBlocks.SHAKER, TileEntityShaker::new)
          .clientTicker(TileEntityMekanism::tickClient)
          .serverTicker(TileEntityMekanism::tickServer)
          .withSimple(Capabilities.CONFIG_CARD)
          .build();

    public static final TileEntityTypeRegistryObject<TileEntityThermalFractionationController> THERMAL_FRACTIONATION_CONTROLLER = TILE_ENTITY_TYPES
          .mekBuilder(ModBlocks.THERMAL_FRACTIONATION_CONTROLLER, TileEntityThermalFractionationController::new)
          .clientTicker(TileEntityMekanism::tickClient)
          .serverTicker(TileEntityMekanism::tickServer)
          .withSimple(Capabilities.CONFIGURABLE)
          .build();

    public static final TileEntityTypeRegistryObject<TileEntityThermalFractionationValve> THERMAL_FRACTIONATION_VALVE = TILE_ENTITY_TYPES
          .mekBuilder(ModBlocks.THERMAL_FRACTIONATION_VALVE, TileEntityThermalFractionationValve::new)
          .clientTicker(TileEntityMekanism::tickClient)
          .serverTicker(TileEntityMekanism::tickServer)
          .withSimple(Capabilities.CONFIGURABLE)
          .build();

    public static final TileEntityTypeRegistryObject<TileEntityFractionationBlock> THERMAL_FRACTIONATION_CASING = TILE_ENTITY_TYPES
          .mekBuilder(ModBlocks.THERMAL_FRACTIONATION_CASING, TileEntityFractionationBlock::new)
          .clientTicker(TileEntityMekanism::tickClient)
          .serverTicker(TileEntityMekanism::tickServer)
          .withSimple(Capabilities.CONFIGURABLE)
          .build();

    public static final TileEntityTypeRegistryObject<TileEntityFusedPipe> FUSED_PIPE = TILE_ENTITY_TYPES
          .builder(ModBlocks.FUSED_PIPE, TileEntityFusedPipe::new)
          .serverTicker(TileEntityFusedPipe::tickServer)
          .with(Capabilities.CONFIGURABLE, TileEntityFusedPipe.CONFIGURABLE_PROVIDER)
          .with(Capabilities.FLUID.block(), CapabilityTileEntity.FLUID_HANDLER_PROVIDER)
          .with(Capabilities.CHEMICAL.block(), CapabilityTileEntity.CHEMICAL_HANDLER_PROVIDER)
          .with(Capabilities.HEAT, CapabilityTileEntity.HEAT_HANDLER_PROVIDER)
          .with(Capabilities.ITEM.block(), TileEntityFusedPipe.ITEM_HANDLER_PROVIDER)
          .withSimple(Capabilities.ALLOY_INTERACTION)
          .build();

    public static final TileEntityTypeRegistryObject<TileEntityCreativeHeatBlock> CREATIVE_HEAT_BLOCK = TILE_ENTITY_TYPES
          .mekBuilder(ModBlocks.CREATIVE_HEAT_BLOCK, TileEntityCreativeHeatBlock::new)
          .serverTicker(TileEntityMekanism::tickServer)
          .build();

    public static final TileEntityTypeRegistryObject<TileEntityCreativeChunkHeater> CREATIVE_CHUNK_HEATER = TILE_ENTITY_TYPES
          .mekBuilder(ModBlocks.CREATIVE_CHUNK_HEATER, TileEntityCreativeChunkHeater::new)
          .serverTicker(TileEntityMekanism::tickServer)
          .build();

    public static final TileEntityTypeRegistryObject<TileEntityCooler> COOLER = TILE_ENTITY_TYPES.mekBuilder(ModBlocks.COOLER, TileEntityCooler::new)
          .clientTicker(TileEntityMekanism::tickClient)
          .serverTicker(TileEntityMekanism::tickServer)
          .withSimple(Capabilities.CONFIG_CARD)
          .build();

    public static final TileEntityTypeRegistryObject<TileEntityCondenser> CONDENSER = TILE_ENTITY_TYPES.mekBuilder(ModBlocks.CONDENSER, TileEntityCondenser::new)
          .clientTicker(TileEntityMekanism::tickClient)
          .serverTicker(TileEntityMekanism::tickServer)
          .withSimple(Capabilities.CONFIG_CARD)
          .build();

    public static final TileEntityTypeRegistryObject<TileEntityReactionChamber> REACTION_CHAMBER = TILE_ENTITY_TYPES
          .mekBuilder(ModBlocks.REACTION_CHAMBER, TileEntityReactionChamber::new)
          .clientTicker(TileEntityMekanism::tickClient)
          .serverTicker(TileEntityMekanism::tickServer)
          .withSimple(Capabilities.CONFIG_CARD)
          .build();

    public static final TileEntityTypeRegistryObject<TileEntityQuenchingEnrichmentChamber> QUENCHING_ENRICHMENT_CHAMBER =
          TILE_ENTITY_TYPES.mekBuilder(ModBlocks.QUENCHING_ENRICHMENT_CHAMBER, TileEntityQuenchingEnrichmentChamber::new)
                .clientTicker(TileEntityMekanism::tickClient)
                .serverTicker(TileEntityMekanism::tickServer)
                .withSimple(Capabilities.CONFIG_CARD)
                .build();

    public static final TileEntityTypeRegistryObject<TileEntityAtmosphereHeater> ATMOSPHERE_HEATER = TILE_ENTITY_TYPES
          .mekBuilder(ModBlocks.ATMOSPHERE_HEATER, TileEntityAtmosphereHeater::new)
          .clientTicker(TileEntityMekanism::tickClient)
          .serverTicker(TileEntityMekanism::tickServer)
          .withSimple(Capabilities.CONFIG_CARD)
          .build();

    //No heat capability is registered: the controller reads its neighbours' heat capacitors, it does not own any.
    public static final TileEntityTypeRegistryObject<TileEntityTemperatureController> TEMPERATURE_CONTROLLER = TILE_ENTITY_TYPES
          .mekBuilder(ModBlocks.TEMPERATURE_CONTROLLER, TileEntityTemperatureController::new)
          .clientTicker(TileEntityMekanism::tickClient)
          .serverTicker(TileEntityMekanism::tickServer)
          .withSimple(Capabilities.CONFIG_CARD)
          .build();

    //The three phase-change blocks share one tile class; the block they belong to supplies the tier.
    public static final TileEntityTypeRegistryObject<TileEntityPhaseChangeBlock> PHASE_CHANGE_LOW = TILE_ENTITY_TYPES
          .mekBuilder(ModBlocks.PHASE_CHANGE_LOW, TileEntityPhaseChangeBlock::new)
          .serverTicker(TileEntityMekanism::tickServer)
          .build();

    public static final TileEntityTypeRegistryObject<TileEntityPhaseChangeBlock> PHASE_CHANGE_MEDIUM = TILE_ENTITY_TYPES
          .mekBuilder(ModBlocks.PHASE_CHANGE_MEDIUM, TileEntityPhaseChangeBlock::new)
          .serverTicker(TileEntityMekanism::tickServer)
          .build();

    public static final TileEntityTypeRegistryObject<TileEntityPhaseChangeBlock> PHASE_CHANGE_HIGH = TILE_ENTITY_TYPES
          .mekBuilder(ModBlocks.PHASE_CHANGE_HIGH, TileEntityPhaseChangeBlock::new)
          .serverTicker(TileEntityMekanism::tickServer)
          .build();

    public static final TileEntityTypeRegistryObject<TileEntityRetroentropicArrayCasing> RETROENTROPIC_ARRAY_CASING = TILE_ENTITY_TYPES
        .mekBuilder(ModBlocks.RETROENTROPIC_ARRAY_CASING, TileEntityRetroentropicArrayCasing::new)
        .clientTicker(TileEntityMekanism::tickClient)
        .serverTicker(TileEntityMekanism::tickServer)
        .withSimple(Capabilities.CONFIGURABLE)
        .build();
}
