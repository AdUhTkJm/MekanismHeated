const log = function(event, msg) {
  const player = event.level.getPlayers().getFirst();
  player.tell(msg);
}

// Make cobblestone a gravity block. Only triggered on placement!
BlockEvents.placed(event => {
  const block = event.getBlock();
  if (block.id != 'minecraft:cobblestone')
    return;

  const level = event.getLevel();
  const pos = block.getPos();
  let below = level.getBlock(pos.below());

  if (below.block.id == 'minecraft:air') {
    block.set('minecraft:air');
    let falling = level.createEntity('minecraft:falling_block');
    falling.setPosition(pos.x + 0.5, pos.y, pos.z + 0.5);
    falling.mergeNbt({
      BlockState: { Name: "minecraft:cobblestone" },
      Time: 1
    });
    falling.persistentData.putDouble('start', pos.y);
    
    falling.spawn();
  }
});

function convert(name) {
  if (name == 'minecraft:cobblestone')
    return 'minecraft:gravel';

  if (name == 'minecraft:gravel')
    return 'minecraft:sand';
}

function sideProduct(name) {
  switch (name) {
  case 'minecraft:cobblestone':
    return [{
      name: 'kubejs:fe2o3_dust',
      chance: 0.15
    }];
  case 'minecraft:gravel':
    return [{
      name: 'kubejs:cuo_dust',
      chance: 0.15
    }, {
      name: 'kubejs:caco3_dust',
      chance: 0.15
    }];
  }
}

EntityEvents.spawned(event => {
  const entity = event.getEntity();
  if (entity.type != 'minecraft:falling_block' || entity.getNbt()?.BlockState?.Name != 'minecraft:gravel')
    return;

  entity.persistentData.putDouble('start', entity.position().y);
});

// Check the height from which the cobblestone entity falls,
// and replace with gravel when the height is sufficient.
LevelEvents.tick(event => {
  const level = event.getLevel();
  if (level.isClientSide())
      return;

  let allFalling = level.getEntities().filter(e => e.type === 'minecraft:falling_block');

  allFalling.forEach(entity => {
    if (!entity.persistentData.contains('start'))
      return;

    const below = level.getBlock(entity.blockPosition().below());
    if (below.id !== 'minecraft:air') {
      const start = entity.persistentData.getDouble('start');
      const dist = start - entity.y;

      // This means we have to place cobblestone at a height of 4.
      if (dist >= 3) {
        const name = entity.getNbt().BlockState.Name;
        entity.mergeNbt({
          BlockState: { Name: convert(name) }
        });
      
        // Generate possible side products.
        for (const product of sideProduct(name)) {
          if (Math.random() > product.chance)
            continue;

          const item = level.createEntity('item');
          const pos = entity.position();
          item.setPosition(pos.x(), pos.y(), pos.z());
          item.mergeNbt({
            Item: { id: product.name }
          });
          item.spawn();
        }
      }
      
      entity.persistentData.remove('start');
    }
  });
});

ServerEvents.recipes(event => {
  let yamlRecipe = (yaml) => event.custom(Lychee.toJSON(yaml));
  yamlRecipe(`
    type: lychee:block_crushing
    falling_block: minecraft:cobblestone
    item_in: kubejs:sponge_iron_ingot
    post: drop iron_ingot
  `).id("kubejs:forge_sponge_iron_by_cobblestone");
  yamlRecipe(`
    type: lychee:block_crushing
    falling_block: minecraft:anvil
    item_in: kubejs:sponge_iron_ingot
    post: drop 6x iron_ingot
  `).id("kubejs:forge_sponge_iron_by_anvil");
});
