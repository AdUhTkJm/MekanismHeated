package io.aduhtkjm.mekanismheated.registries;

import io.aduhtkjm.mekanismheated.Mod;
import io.aduhtkjm.mekanismheated.tile.TileEntityShaker;
import io.aduhtkjm.mekanismheated.tile.TileEntityHeatSmelter;
import io.aduhtkjm.mekanismheated.tile.multiblock.TileEntityFractionationBlock;
import io.aduhtkjm.mekanismheated.tile.multiblock.TileEntityThermalFractionationController;
import io.aduhtkjm.mekanismheated.tile.multiblock.TileEntityThermalFractionationValve;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.registration.impl.TileEntityTypeDeferredRegister;
import mekanism.common.registration.impl.TileEntityTypeRegistryObject;
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
}
