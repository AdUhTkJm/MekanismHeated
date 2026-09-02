package io.aduhtkjm.mekanismheated.registries;

import io.aduhtkjm.mekanismheated.Mod;
import io.aduhtkjm.mekanismheated.tile.TileEntityCreativeHeatBlock;
import io.aduhtkjm.mekanismheated.tile.TileEntityShaker;
import io.aduhtkjm.mekanismheated.tile.TileEntityHeatSmelter;
import io.aduhtkjm.mekanismheated.tile.multiblock.TileEntityThermalFractionationController;
import mekanism.common.inventory.container.tile.EmptyTileContainer;
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

    public static final ContainerTypeRegistryObject<MekanismTileContainer<TileEntityThermalFractionationController>> THERMAL_FRACTIONATION_CONTROLLER =
          CONTAINER_TYPES.custom("thermal_fractionation_controller", TileEntityThermalFractionationController.class).offset(10, 0).build();

    public static final ContainerTypeRegistryObject<EmptyTileContainer<TileEntityCreativeHeatBlock>> CREATIVE_HEAT_BLOCK =
          CONTAINER_TYPES.registerEmpty(ModBlocks.CREATIVE_HEAT_BLOCK, TileEntityCreativeHeatBlock.class);
}