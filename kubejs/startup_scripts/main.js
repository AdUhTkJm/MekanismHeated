StartupEvents.registry("item", event => {
  // -----------------------------
  // Dusts
  // -----------------------------
  event.create("fe2o3_dust")
    .tooltip("§6Fe₂O₃§r")
    .texture("kubejs:item/dust")
    .color(0, 0x972020);

  event.create("cuo_dust")
    .tooltip("§6CuO§r")
    .texture("kubejs:item/dust")
    .color(0, 0x010101);

  event.create("caco3_dust")
    .tooltip("§6CaCO₃§r")
    .texture("kubejs:item/dust")
    .color(0, 0xF7F7F);

  event.create("sno2_dust")
    .tooltip("§6SnO₂§r")
    .texture("kubejs:item/dust")
    .color(0, 0xF7EDCA);

  event.create("pure_fe2o3_dust")
    .tooltip("§6Fe₂O₃ (纯)§r")
    .texture("kubejs:item/dust")
    .color(0, 0x650E0E);

  event.create("fes2_dust")
    .tooltip("§6FeS₂§r")
    .texture("kubejs:item/dust")
    .color(0, 0xE9EE67);

  // -----------------------------
  // Ingots
  // -----------------------------
  event.create("sponge_iron_ingot")
    .tooltip("§6Fe (不纯)§r")
    .texture("kubejs:item/ingot")
    .color(0, 0xEBCABC);

  event.create("impure_sn_ingot")
    .tooltip("§6Sn (不纯)§r")
    .texture("kubejs:item/ingot")
    .color(0, 0xCBDDFC);
});

StartupEvents.registry('mekanism:gas', event => {
  event.create("co").tint(0xE1E1E1);
  event.create("co2").tint(0x333333);
});

StartupEvents.registry("block", event => {
  // These dummy blocks are used for Custom Machinery to mimic.
  // I really can't find a way to make Custom Machinery work in itself.
  event.create("dummy_shaker")
    .textures({
      "west": "kubejs:block/shaker_side",
      "east": "kubejs:block/shaker_side",
      "up": "kubejs:block/shaker_top",
      "down": "kubejs:block/shaker_top",
      "north": "kubejs:block/shaker_front",
      "south": "kubejs:block/shaker_front",
    });

  // These are real machines.
  event.create("shaker", "custommachinery:custom_machine").machine("custommachinery:shaker");
  event.create("energized_smelter", "custommachinery:custom_machine").machine("custommachinery:energized_smelter");
});

// Make sure these items can react inside lava.
ItemEvents.modification(event => {
  const fireproofItems = [
    'mekanism:dust_iron',
    'mekanism:dust_copper',
    'minecraft:redstone',
    'minecraft:copper_ingot',
    "kubejs:impure_sn_ingot",
    'mekanism:ingot_bronze',
  ]
  
  fireproofItems.forEach(id => {
    event.modify(id, item => {
      item.setFireResistant();
    })
  })
});
