package io.aduhtkjm.mekanismheated.registries;

import io.aduhtkjm.mekanismheated.MekanismHeated;
import io.aduhtkjm.mekanismheated.tile.TileEntityHeatSmelter;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.registration.impl.TileEntityTypeDeferredRegister;
import mekanism.common.registration.impl.TileEntityTypeRegistryObject;
import mekanism.common.tile.base.TileEntityMekanism;

public class MekHeatedTileEntityTypes {

    private MekHeatedTileEntityTypes() {
    }

    public static final TileEntityTypeDeferredRegister TILE_ENTITY_TYPES = new TileEntityTypeDeferredRegister(MekanismHeated.MODID);

    public static final TileEntityTypeRegistryObject<TileEntityHeatSmelter> HEAT_SMELTER = TILE_ENTITY_TYPES.mekBuilder(MekHeatedBlocks.HEAT_SMELTER, TileEntityHeatSmelter::new)
          .clientTicker(TileEntityMekanism::tickClient)
          .serverTicker(TileEntityMekanism::tickServer)
          .withSimple(Capabilities.CONFIG_CARD)
          .build();
}