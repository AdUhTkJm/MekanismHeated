ServerEvents.recipes(event => {
  event.custom({
    type: "ae2:transform",
    circumstance: {
      type: "fluid",
      tag: "minecraft:water",
    },
    ingredients: [
      { item: "minecraft:sand" },
    ],
    result: {
      id: "minecraft:clay",
      count: 1,
    }
  });

  event.custom({
    type: "ae2:transform",
    circumstance: {
      type: "fluid",
      tag: "minecraft:lava",
    },
    ingredients: [
      { item: "mekanism:dust_iron" },
      { item: "mekanism:dust_copper" },
    ],
    result: {
      id: "minecraft:redstone",
      count: 1,
    }
  });

  event.custom({
    type: "ae2:transform",
    circumstance: {
      type: "fluid",
      tag: "minecraft:lava",
    },
    ingredients: [
      { item: "minecraft:copper_ingot" },
      { item: "kubejs:impure_sn_ingot" },
    ],
    result: {
      id: "mekanism:ingot_bronze",
      count: 1,
    }
  });
});
