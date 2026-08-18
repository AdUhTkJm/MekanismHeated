ServerEvents.recipes(event => {
  event.remove({ output: 'mekanism:crusher' });
  event.remove({ output: 'mekanism:basic_crushing_factory' });
  event.remove({ output: 'mekanism:advanced_crushing_factory' });
  event.remove({ output: 'mekanism:elite_crushing_factory' });
  event.remove({ output: 'mekanism:ultimate_crushing_factory' });
  event.remove({ output: 'mekanism:basic_universal_cable' });
  event.remove({ output: 'mekanism:basic_thermodynamic_conductor' });
  event.remove({ output: 'mekanismgenerators:heat_generator' });

  event.shaped(Item.of("mekanism:crusher"), [
    "ACA",
    "CBC",
    "ACA"
  ], {
    "A": "minecraft:cobblestone",
    "B": "minecraft:iron_ingot",
    "C": "minecraft:copper_ingot",
  });

  event.shaped(Item.of("mekanism:basic_universal_cable"), [
    "ABA"
  ], {
    "A": "minecraft:iron_ingot",
    "B": "minecraft:redstone",
  });

  event.shaped(Item.of("mekanism:basic_thermodynamic_conductor"), [
    "ABA"
  ], {
    "A": "minecraft:iron_ingot",
    "B": "minecraft:copper_ingot",
  });

  event.shaped(Item.of("mekanismgenerators:heat_generator"), [
    "AAA",
    "B B",
    "CDC"
  ], {
    "A": "minecraft:iron_ingot",
    "B": "#minecraft:planks",
    "C": "minecraft:copper_ingot",
    "D": "minecraft:furnace",
  });
});
