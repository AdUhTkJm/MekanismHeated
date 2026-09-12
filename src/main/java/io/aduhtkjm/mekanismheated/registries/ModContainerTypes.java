package io.aduhtkjm.mekanismheated.registries;

import io.aduhtkjm.mekanismheated.Mod;
import io.aduhtkjm.mekanismheated.tile.TileEntityAtmosphereHeater;
import io.aduhtkjm.mekanismheated.tile.TileEntityCondenser;
import io.aduhtkjm.mekanismheated.tile.TileEntityCooler;
import io.aduhtkjm.mekanismheated.tile.TileEntityCreativeChunkHeater;
import io.aduhtkjm.mekanismheated.tile.TileEntityCreativeHeatBlock;
import io.aduhtkjm.mekanismheated.tile.TileEntityReactionChamber;
import io.aduhtkjm.mekanismheated.tile.TileEntityTemperatureController;
import io.aduhtkjm.mekanismheated.tile.TileEntityQuenchingEnrichmentChamber;
import io.aduhtkjm.mekanismheated.tile.TileEntityShaker;
import io.aduhtkjm.mekanismheated.tile.TileEntityHeatSmelter;
import io.aduhtkjm.mekanismheated.tile.TileEntityPhaseChangeBlock;
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

    public static final ContainerTypeRegistryObject<EmptyTileContainer<TileEntityCreativeChunkHeater>> CREATIVE_CHUNK_HEATER =
          CONTAINER_TYPES.registerEmpty(ModBlocks.CREATIVE_CHUNK_HEATER, TileEntityCreativeChunkHeater.class);

    //One container type serves all three phase-change blocks, as they share a tile class and have no slots. The GUI
    //title still comes from each block's own name (TileEntityMekanism#getDisplayName uses container.<block>), so the
    //lang file needs a container.mekanismheated.phase_change_block_<tier> entry for each tier.
    public static final ContainerTypeRegistryObject<EmptyTileContainer<TileEntityPhaseChangeBlock>> PHASE_CHANGE_BLOCK =
          CONTAINER_TYPES.registerEmpty("phase_change_block", TileEntityPhaseChangeBlock.class);

    public static final ContainerTypeRegistryObject<MekanismTileContainer<TileEntityCooler>> COOLER =
          CONTAINER_TYPES.register(ModBlocks.COOLER, TileEntityCooler.class);

    public static final ContainerTypeRegistryObject<MekanismTileContainer<TileEntityCondenser>> CONDENSER =
          CONTAINER_TYPES.register(ModBlocks.CONDENSER, TileEntityCondenser.class);

    public static final ContainerTypeRegistryObject<MekanismTileContainer<TileEntityReactionChamber>> REACTION_CHAMBER =
          CONTAINER_TYPES.register(ModBlocks.REACTION_CHAMBER, TileEntityReactionChamber.class);

    //Note: The screens for both of these are 5 pixels taller than the default (166), as all of their machine
    // contents are drawn 5 pixels lower. The player inventory has to be shifted down to match, which must be done
    // here rather than in the screen: GuiMekanism renders a GuiSlot for every menu slot based on the slot's own x/y
    // (see MekanismContainer#addInventorySlots, which positions the player slots using getInventoryYOffset).
    public static final ContainerTypeRegistryObject<MekanismTileContainer<TileEntityQuenchingEnrichmentChamber>> QUENCHING_ENRICHMENT_CHAMBER =
          CONTAINER_TYPES.custom(ModBlocks.QUENCHING_ENRICHMENT_CHAMBER, TileEntityQuenchingEnrichmentChamber.class).offset(0, 5).build();

    public static final ContainerTypeRegistryObject<MekanismTileContainer<TileEntityAtmosphereHeater>> ATMOSPHERE_HEATER =
          CONTAINER_TYPES.custom(ModBlocks.ATMOSPHERE_HEATER, TileEntityAtmosphereHeater.class).offset(0, 5).build();

    //The GUI is 20 pixels wider than the default and the player inventory is shifted 10 pixels right, so that the two
    //extra columns of width appear on either side of the slots rather than on one side only.
    public static final ContainerTypeRegistryObject<MekanismTileContainer<TileEntityTemperatureController>> TEMPERATURE_CONTROLLER =
          CONTAINER_TYPES.custom("temperature_controller", TileEntityTemperatureController.class).offset(10, 0).build();
}