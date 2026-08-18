ServerEvents.recipes(event => {
  event.recipes.custommachinery
    .custom_machine("custommachinery:shaker", 200)
    .requireItem("5x minecraft:clay")
    .produceItem("3x kubejs:sno2_dust");

  // Smelter recipes.
  const SMELTER = "custommachinery:energized_smelter";
  event.recipes.custommachinery
    .custom_machine(SMELTER, 150)
    .requireItem("kubejs:caco3_dust")
    .requireItem("kubejs:sno2_dust")
    .requireItem("minecraft:sand")
    .produceItem("kubejs:impure_sn_ingot")
    .requireTempCelsius("[600,)")
    .requireHeatPerTick(60);

  event.recipes.custommachinery
    .custom_machine(SMELTER, 400)
    .requireItem("minecraft:cobblestone")
    .produceFluid("minecraft:lava")
    .requireTempCelsius("[1000,)")
    .requireHeatPerTick(60);

  event.recipes.custommachinery
    .custom_machine(SMELTER, 1)
    .requireItem("minecraft:charcoal", "input_redstone")
    .produceHeat(2000);

  event.recipes.custommachinery
    .custom_machine(SMELTER, 1)
    .requireItem("minecraft:coal", "input_redstone")
    .produceHeat(2000);

  event.recipes.custommachinery
    .custom_machine(SMELTER, 1)
    .requireItem("mekanism:block_charcoal", "input_redstone")
    .produceHeat(18000);

  event.recipes.custommachinery
    .custom_machine(SMELTER, 1)
    .requireItem("minecraft:coal_block", "input_redstone")
    .produceHeat(18000);

  event.recipes.custommachinery
    .custom_machine(SMELTER, 1)
    .requireItem("minecraft:lava_bucket", "input_redstone")
    .produceHeat(25000);
});

// Mekanism recipes.
ServerEvents.recipes(event => {
  event.recipes.mekanism.crushing("9x kubejs:cuo_dust", "minecraft:oxidized_copper");
});
