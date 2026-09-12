# Overview
Mekanism: Heated is an addon of Mekanism that uses heat and temperature to control machines, rather than energy. It also adds more chemical instances and their reactions, since they usually happen under a certain temperature.

JEI and Jade are supported.

The mod is still in its early development. Expect more updates later.

# New Machines

## Heat Smelter

A smelter, but not only a smelter. It supports 4 recipe types:

- Smelting. Starts working above 300K and linearly scales to full speed at 1000K, where full speed is 100 ticks per item (double speed of vanilla furnace).
- Melting. Every supported item has a different temperature when they start melting, e.g. copper ingot at 1084℃ and iron ingot at 1535℃, to match physical reality. Note that cobblestone can be melted into lava at 1500K.
- Temperature-controlled smelting. Similar to normal smelting, but happens only at a certain temperature.
- Alloying. Two melted metals that can alloy will automatically alloy inside the machine.

![A heat smelter containing liquids.](https://cdn.modrinth.com/data/cached_images/81a34fefa1d42baaebfe38aa275d053d5b202c84.png)

This is a working heat smelter with liquids. The block beside it is a creative heat source, obtainable only in creative mod and can set temperature freely.

They can form into a larger smelter, from 2x2x2 to 6x6x6, similar to jumbo furnaces:

![A large smelter.](https://cdn.modrinth.com/data/cached_images/43170b17c966089edba54d9b7732184999a1589f.png)

## Condenser

Condenses molten metal to ingots. Also allows inputting another item to combine them, e.g. pouring molten diamond onto infused alloy will give you reinforced alloy.

## Fractionation Tower

Similar to evaporation tower, but separated by distillation trays. Every compartment formed by the trays will be an output tank, with the exception that the lowest compartment will be the input tank. Works only under a certain temperature range.

Passively generates liquid nitrogen, liquid oxygen and liquid air remnant under 85K (i.e. fractionating air).

![A fractionation tower with a creative heat source attached to it.](https://cdn.modrinth.com/data/cached_images/c5ff1eb0c4f81d7fd609ac3b1e0491c2a5c56f63.png)

This is a fractionation tower with a creative heat source attached to it.

**NOTE:** I didn't change the texture for now, it looks the same as evaporation tower. This is on TODO list.

## Cooler

Like resistive heater, but uses energy to cool things down.

## Phase-Change Blocks

Three heat capacitors that melt at different temperatures (1000 K, 1750 K and 3000 K by default). Below its melting
point a block behaves like any other heat capacitor, but at the melting point it starts absorbing heat as latent heat
instead of warming up, until its internal buffer (1,000,000 J by default) is full. After that its temperature rises
again as normal. When it cools down or gives heat to its neighbours, the buffer is drained first, so the block stays at
its melting point until the latent heat is gone.

The three tiers differ only in their melting point; the melting points are configurable, and the buffer size and
thermal properties are shared by all three (see the `phaseChange` config section).

## Shaker

A normal Mekanism-like machine that produces chemical instances (Fe2O3, SnO2, CuO etc) from cobblestone, gravel, sand or clay. These are then fed into other machines to extract ingots. In Create you can get iron and gold out of cobblestone, so why in Mekanism you can't?

## Reaction Chamber

Not pressurized reaction chamber; this one is much more flexible. Just input any chemicals and fluids, or even plus an item, then it will react to its full, while recipes are controlled by temperature. It holds at most 16 types of chemicals and 16 types of fluids. It can become chaotic!

![A full reaction chamber](https://cdn.modrinth.com/data/cached_images/be3f3a9da14746de5aa29f42876e0d36bdc252ae.png)

This is a full reaction chamber. The reaction going on is C + H2O = CO + H2.

## Fused pipes

5-in-1 pipe. Transmits energy, chemical, fluid, heat and items. Note that these pipes have buffer, like Mekanism pipes do. When you have Jade and look at it, you'll see the pipe network's contents.

In the previous image, the pipe between the water tank and the reaction chamber is a fused pipe.


# Credits

Some assets are derived from Mekanism (Licensed under MIT). We sincerely thank everyone who contributed to Mekanism.

