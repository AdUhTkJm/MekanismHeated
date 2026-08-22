package io.aduhtkjm.mekanismheated.registries;

import io.aduhtkjm.mekanismheated.Mod;
import io.aduhtkjm.mekanismheated.tile.TileEntityShaker;
import io.aduhtkjm.mekanismheated.tile.TileEntityHeatSmelter;
import mekanism.common.inventory.container.tile.MekanismTileContainer;
import mekanism.common.registration.impl.ContainerTypeDeferredRegister;
import mekanism.common.registration.impl.ContainerTypeRegistryObject;

public class ModContainerTypes {

    private ModContainerTypes() {
    }

    public static final ContainerTypeDeferredRegister CONTAINER_TYPES = new ContainerTypeDeferredRegister(Mod.MODID);

    public static final ContainerTypeRegistryObject<MekanismTileContainer<TileEntityHeatSmelter>> HEAT_SMELTER =
          CONTAINER_TYPES.register(ModBlocks.HEAT_SMELTER, TileEntityHeatSmelter.class);

    public static final ContainerTypeRegistryObject<MekanismTileContainer<TileEntityShaker>> SHAKER =
          CONTAINER_TYPES.register(ModBlocks.SHAKER, TileEntityShaker.class);
}