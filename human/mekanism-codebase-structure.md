# Mekanism 10.7.19 - Codebase Structure, Inheritance & Interfaces

*A global map of Mekanism's architecture, focused on class hierarchies and interface
implementation. Written to orient yourself in the codebase (and to understand how the
`mekanismheated` Heat Smelter is built on top of it).*

Version: **10.7.19** - Minecraft **1.21.1** - **NeoForge** 21.1.248

---

## 1. The big picture

Mekanism is a layered mod:

```
           addons: additions / generators / tools   (official modules, depend on the API)
                                    |
                                    | uses
                                    v
          src/main  mekanism.common.* / mekanism.client.*   (the implementation)
                                    |
                                    | implements
                                    v
          src/api   mekanism.api.*   (the public contract: interfaces + recipes)
                                    |
                                    | built on
                                    v
          NeoForge / Minecraft
```

- The **API** (`mekanism.api.*`) holds the *contracts*: the energy/heat/inventory/
  fluid/chemical handler interfaces, the recipes, and the recipe caches.
- The **main** code (`mekanism.common.*`) holds the *implementation*: tile entities,
  blocks, the capability plumbing, GUIs, and the registries.
- Everything an addon like `mekanismheated` does is "implement an API interface" or
  "extend a common class".

There are also `src/datagen` (data generation) and `src/gameTest` (tests built on
NeoForge's TestFramework).

---

## 2. The core API interfaces (the vocabulary)

Every storage type in Mekanism has a **three-layer interface family**:

```
       base                    sided                     tile-facing
  IHeatHandler         ISidedHeatHandler         IMekanismHeatHandler
  IStrictEnergyHandler ISidedStrictEnergyHandler IMekanismStrictEnergyHandler
  (item)               ISidedItemHandler        IMekanismInventory
  IExtendedFluidHandler ISidedFluidHandler      IMekanismFluidHandler
  IChemicalHandler     ISidedChemicalHandler    IMekanismChemicalHandler
        ^                    ^                           ^
        └────────────────────┴───────────────────────────┘
                          each layer extends the previous
                     (the tile-facing layer also implements IContentsListener)
```

- The **base** layer describes storage with no side concept.
- The **sided** layer adds methods taking a `@Nullable Direction` (`null` = internal view).
- The **tile-facing** layer adds `getContainers(@Nullable Direction side)` returning the
  `List<...>` of individual storage objects, plus `canHandleX()` defaults.

The "sided" interfaces are what NeoForge BlockCapabilities see; the "tile-facing"
interfaces are what `TileEntityMekanism` implements.

### Supporting API types

| Type | Role |
|---|---|
| `IContentsListener` (`mekanism.api`) | `onContentsChanged()`; every container/tank/slot/capacitor holds one and fires it on mutation. `TileEntityMekanism.onContentsChanged()` -> `setChanged()`. |
| `INBTSerializable<T>` (NeoForge) | `serializeNBT/deserializeNBT(HolderLookup.Provider, T)`; all tanks/containers/slots/capacitors implement it. |
| `Action` (`mekanism.api`) | `SIMULATE` / `EXECUTE`; everything is queried via `SIMULATE` first. |
| `AutomationType` (`mekanism.api`) | `EXTERNAL` / `INTERNAL` / `MANUAL`; used to gate automation (hoppers etc.). |
| `IIncrementalEnum` / `IDisableableEnum` | enum cycling helpers (`EnumColor`, `SecurityMode`, ...). |
| `IHeatCapacitor`, `IEnergyContainer`, `IInventorySlot`, `IExtendedFluidTank`, `IChemicalTank` | the individual storage objects. All are `INBTSerializable<CompoundTag> & IContentsListener`. |

---

## 3. The tile entity hierarchy (the heart of the mod)

This is the tree every machine lives in. Full chain from Minecraft's root:

```
java.lang.Object
`-- BlockEntity (vanilla; implements Saveable)
    `-- TileEntityUpdateable                       [mekanism.common.tile.base]
        |   implements ITileWrapper
        |   - first ctor arg is a Mekanism TileEntityTypeRegistryObject
        |   - provides isRemote(), markForSave(), sendUpdatePacket(), getUpdateTag...
        `-- CapabilityTileEntity                   [mekanism.common.tile.base]
            |   - adds the NeoForge BlockCapability plumbing:
            |     capabilityCache, ICapabilityResolver map,
            |     static providers CHEMICAL/HEAT/ITEM/FLUID
            `-- TileEntityMekanism                 [mekanism.common.tile.base]
                |   implements 19 interfaces (see below)
                |   - ctor takes Holder<Block>, resolves the tile type via
                |     ((IHasTileEntity) block.value()).getTileType()
                |   - constructor order: presetVariables() -> getInitial*() hooks
                |     -> wrap holders in *HandlerManager -> register resolvers
                |   - static tick entry points tickClient() / tickServer()
                |
                +-- TileEntityConfigurableMachine  [tile.prefab]
                |   |   implements ISideConfiguration
                |   |   - creates TileComponentConfig from
                |   |     Attribute.getOrThrow(block, AttributeSideConfig)
                |   |       .supportedTypes()   -> per-transmission-type side config
                |   |   - ejectorComponent left null for subclasses
                |   |
                |   +-- TileEntityRecipeMachine<RECIPE>   [tile.prefab]
                |   |   |   implements IRecipeLookupHandler<RECIPE>
                |   |   |   - creates the RecipeCacheLookupMonitor in presetVariables()
                |   |   |   - final-wraps the getInitial* hooks to route listeners
                |   |   |     through recipeCacheListener / recipeCacheUnpauseListener
                |   |   |
                |   |   +-- TileEntityProgressMachine<RECIPE>  [tile.prefab]
                |   |   |   |   - adds operatingTicks / ticksRequired /
                |   |   |   |     baseTicksRequired / operationsPerTick
                |   |   |   |
                |   |   |   `-- TileEntityElectricMachine     [tile.prefab]
                |   |   |       |   implements ItemRecipeLookupHandler<ItemStackToItemStackRecipe>
                |   |   |       |   (RECIPE fixed to ItemStackToItemStackRecipe)
                |   |   |       |   - ctor: setupItemIOConfig(input, output, energy slots),
                |   |   |       |     setupInputConfig(ENERGY, energyContainer),
                |   |   |       |     builds the TileComponentEjector
                |   |   |       |   - implements getRecipe(int) / createNewCachedRecipe(...)
                |   |   |       |     via OneInputCachedRecipe.itemToItem(...)
                |   |   |       `-- TileEntityEnergizedSmelter  [tile.machine]
                |   |   |           just supplies getRecipeType() = SMELTING and
                |   |   |           recipeViewerType() = SMELTING
                |   |   |
                |   |   `-- TileEntityAdvancedElectricMachine
                |   |       (chemical + item inputs; e.g. Purification Chamber)
                |   |
                |   `-- TileEntityFactory<RECIPE>   [tile.factory]
                |       implements IRecipeLookupHandler<RECIPE>
                |       (SIBLING branch of RecipeMachine, not an ancestor of the
                |        smelter; factories multiplex one recipe type over many slots)
                |
                +-- TileEntityFuelwoodHeater        [tile.machine]
                |   DIRECT leaf of TileEntityMekanism (no machine prefabs!)
                |   - just a fuel slot + BasicHeatCapacitor
                |   - own onUpdateServer: burn fuel -> handleHeat ->
                |     simulate() heat transfer to adjacent tiles
                |
                +-- TileEntityMultiblock<T extends MultiblockData>  [tile.prefab]
                |   implements IMultiblock<T>, IConfigurable
                |   (base for all multiblocks: reactor, boiler, QIO, ...)
                |
                `-- ... (dozens more leaves: EnergyCube, DigitalMiner, ...)
```

### The interfaces implemented by `TileEntityMekanism`

```java
public abstract class TileEntityMekanism extends CapabilityTileEntity implements
      IFrequencyHandler,            // frequencies (teleporters, QIO, ...)
      ITileDirectional,             // getDirection / setFacing + rotate/mirror
      IConfigCardAccess,            // config cards (mekanism.api)
      ITileActive,                  // the "active" boolean state
      ITileSound,                   // ambient sound (extends ITileWrapper)
      ITileRedstone,                // redstone control mode (extends IRedstoneControl)
      ISecurityTile,                // owner/security (mekanism.api.security)
      IMekanismInventory,           // item handler family (section 2)
      ITileUpgradable,              // upgrade support (SPEED / ENERGY / MUFFLING ...)
      ITierUpgradable,              // tier upgrades (basic -> advanced -> ...)
      IComparatorSupport,           // comparator output
      ITrackableContainer,          // addContainerTrackers(MekanismContainer)
      IMekanismFluidHandler,        // fluid handler family
      IMekanismStrictEnergyHandler, // energy handler family
      ITileHeatHandler,             // heat handler family + simulate() (section 4)
      IMekanismChemicalHandler,     // chemical handler family
      IComputerTile,                // ComputerCraft integration
      ITileRadioactive,             // radiation
      Nameable {                    // vanilla display name
```

**Conventions to internalize:**

- `TileEntityMekanism` has **no abstract methods** - it is a class of *hooks with
  defaults*. Subclasses override `onUpdateServer()`, `getInitialInventory()`,
  `getInitialEnergyContainers()`, `getInitialHeatCapacitors()`, etc.
- Only the *interfaces* force concrete method implementations (e.g.
  `IRecipeLookupHandler.getRecipe(int)`, `createNewCachedRecipe(...)`,
  `getRecipeType()`).
- The `machine` package contains both full machines (on the prefab chain) and simple
  "heat generator" leaves like the Fuelwood Heater that extend `TileEntityMekanism`
  directly - **package placement does not imply hierarchy**.

---

## 4. The heat/energy holder & capability system

### The heat side: API -> tile interface

```
mekanism.api.heat.IHeatHandler                  (base, no side)
        ^
mekanism.api.heat.ISidedHeatHandler             (+ @Nullable Direction methods)
        ^
mekanism.api.heat.IMekanismHeatHandler          (+ getHeatCapacitors(side),
                                                 canHandleHeat(), IContentsListener)
        ^
mekanism.common.capabilities.heat.ITileHeatHandler
    - adds simulate() -> new HeatTransfer(simulateAdjacent(), simulateEnvironment())
    - getAdjacent(Direction) -> finds the neighbor heat handler via BlockCapabilityCache
    - getAmbientTemperature(Direction)
    - updateHeatCapacitors(Direction)
```

Note: there is **no** `ITileEnergyHandler`. The energy analogue is just the API
`IMekanismStrictEnergyHandler`, implemented directly by `TileEntityMekanism`.

### The holder pattern (a per-side view of storage)

```
IHolder                              (canInsert / canExtract)
 +-- BasicHolder<TYPE>               slots registered against RelativeSide,
 |                                   resolved to Direction via facingSupplier
 |                                   (getSlots(null) returns ALL slots = internal view)
 +-- ConfigHolder<TYPE>              side view driven by TileComponentConfig /
 |                                   ConfigInfo / ISlotInfo (the in-GUI side config)
 +-- ProxiedHolder                   predicates (Quantum Entangloporter)

typed interfaces (all extend IHolder):
   IEnergyContainerHolder  -> getEnergyContainers(side)
   IHeatCapacitorHolder    -> getHeatCapacitors(side)
   IInventorySlotHolder    -> getInventorySlots(side)
   IChemicalTankHolder     -> getTanks(side)
   IFluidTankHolder        -> getTanks(side)

concrete holders (each extends BasicHolder<X> implements I...Holder):
   EnergyContainerHolder, HeatCapacitorHolder, InventorySlotHolder,
   ChemicalTankHolder, FluidTankHolder
   + Config* variants, ReadOnly*, Proxied*
```

Builders (the fluent API your tile uses):

| Builder | Pattern |
|---|---|
| `EnergyContainerHelper` | `forSide(facingSupplier)` or `forSideWithConfig(this)` -> `addContainer(...)` -> `build()` |
| `HeatCapacitorHelper` | same shape |
| `InventorySlotHelper` | `readOnly()`, `forSide(...)`, `forSideWithConfig(...)` -> `addSlot(...)` -> `build()` |
| `ChemicalTankHelper` / `FluidTankHelper` | same shape |

### How a holder becomes a NeoForge BlockCapability

```
TileEntityTypeDeferredRegister.mekBuilder(...) attaches the providers:
    .with(Capabilities.CHEMICAL.block(), CapabilityTileEntity.CHEMICAL_HANDLER_PROVIDER)
    .with(Capabilities.HEAT,             CapabilityTileEntity.HEAT_HANDLER_PROVIDER)
    .with(Capabilities.ITEM.block(),     CapabilityTileEntity.ITEM_HANDLER_PROVIDER)
    .with(Capabilities.FLUID.block(),    CapabilityTileEntity.FLUID_HANDLER_PROVIDER)
    + EnergyCompatUtils.addBlockCapabilities(builder)   // strict + NeoForge + compat

The provider looks the capability up in the tile's CapabilityCache:

  CapabilityCache: Map<BlockCapability, ICapabilityResolver>
    ICapabilityResolver<CONTEXT>:
      getSupportedCapabilities() / resolve(cap, side) / invalidate(...)
      +-- BasicSidedCapabilityResolver  (lazily creates a per-side Proxy)
      +-- CapabilityHandlerManager<HOLDER, CONTAINER, HANDLER, SIDED_HANDLER>
          implements ICapabilityHandlerManager<CONTAINER> (extends the resolver)
          +-- EnergyHandlerManager  (special: supports ALL loaded energy caps,
          |                          wraps via EnergyCompatUtils per compat system)
          +-- HeatHandlerManager / ItemHandlerManager / FluidHandlerManager /
              ChemicalHandlerManager   (generic, each with its own Proxy)

  Proxy (in ...capabilities.proxy):
    ProxyHandler (side + holder + readOnly=side==null; consults holder.canInsert/Extract)
      +-- ProxyStrictEnergyHandler / ProxyHeatHandler / ProxyItemHandler / ...
```

End-to-end for heat on a tile:

1. `getInitialHeatCapacitors(...)` builds a `HeatCapacitorHolder` via `HeatCapacitorHelper`.
2. `TileEntityMekanism` wraps it: `new HeatHandlerManager(initialHeatCapacitors, this)`
   and registers it into `capabilityCache`.
3. NeoForge queries the block -> provider -> `HeatHandlerManager.resolve(cap, side)`
   -> lazy `new ProxyHeatHandler(this, side, holder)`.
4. Every call on the proxy delegates to the tile's `ITileHeatHandler` methods
   (`getHeatCapacitors(side)` -> holder -> `BasicHolder.getSlots(side)`).
5. `simulate()` uses `BlockCapabilityCache` to find the adjacent heat handler and
   pushes/pulls temperature through the capacitors; this is what makes the Fuelwood
   Heater heat the Heat Smelter.

---

## 5. The recipe system

### Recipe class hierarchy

```
Recipe<INPUT> (vanilla)
`-- MekanismRecipe<INPUT extends RecipeInput>   [mekanism.api.recipes]
    |   - isSpecial() = true (hidden from the vanilla recipe book)
    |   - isIncomplete() forced abstract
    `-- ItemStackToItemStackRecipe extends MekanismRecipe<SingleRecipeInput>
        |       implements Predicate<ItemStack>
        |   - abstract: test(ItemStack), getInput(), getOutput(ItemStack), ...
        `-- BasicItemStackToItemStackRecipe
            `-- BasicSmeltingRecipe   (group "energized_smelter", serializer SMELTING)
```

Recipe inputs are vanilla `RecipeInput` sub-interfaces in
`mekanism.api.recipes.vanilla_input` (`SingleRecipeInput` for items, plus
`FluidRecipeInput`, `ChemicalRecipeInput`, ...).

### CachedRecipe hierarchy

```
CachedRecipe<RECIPE extends MekanismRecipe<?>>   [mekanism.api.recipes.cache]
 |   - per-tick driver: process()
 |   - fluent setters: setCanHolderFunction, setActive, setEnergyRequirements,
 |     setRequiredTicks, setOperatingTicksChanged, setOnFinish, ...
 |   - abstract: isInputValid(), finishProcessing(int)
 |   - nested OperationTracker + RecipeError enum (NOT_ENOUGH_ENERGY,
 |     NOT_ENOUGH_INPUT, NOT_ENOUGH_OUTPUT_SPACE, INPUT_DOESNT_PRODUCE_OUTPUT, ...)
 +-- OneInputCachedRecipe<INPUT, OUTPUT, RECIPE & Predicate<INPUT>>
 |       static factories: itemToItem, itemToFluid, itemToChemical, ...
 +-- TwoInputCachedRecipe<INPUT_A, INPUT_B, OUTPUT, RECIPE & BiPredicate<A,B>>
 |       static factories: combiner, itemChemicalToItem, ...
 +-- RotaryCachedRecipe / PressurizedReactionCachedRecipe
 +-- ItemStackConstantChemicalToObjectCachedRecipe
 `-- ChemicalChemicalToChemicalCachedRecipe
```

### Input/output handler pattern (the abstraction over storage)

| Interface | Role |
|---|---|
| `IInputHandler<INPUT>` | `getInput()` (peek), `getRecipeInput(ingredient)`, `use(input, ops)`, `calculateOperationsCanSupport(tracker, ...)` |
| `ILongInputHandler<INPUT>` | extends it for `long` amounts (chemicals) |
| `IOutputHandler<OUTPUT>` | `handleOutput(output, ops)`, `calculateOperationsCanSupport(tracker, output)` |

`InputHelper` / `OutputHelper` are static factories: `InputHelper.getInputHandler(slot, error)`
wraps an `IInventorySlot`; `OutputHelper.getOutputHandler(slot, error)` wraps an
`OutputInventorySlot`. Composite outputs (sawmill chance output, PRC, electrolysis) get
multi-destination handlers.

### The lookup handler pattern (how a tile finds recipes)

```
IContentsListener
`-- IRecipeLookupHandler<RECIPE extends MekanismRecipe<?>>        [recipe.lookup]
    |   - getRecipe(int), createNewCachedRecipe(recipe, int), getRecipeType()
    `-- IRecipeTypedLookupHandler<RECIPE, INPUT_CACHE>
        +-- ISingleRecipeLookupHandler<INPUT, RECIPE, SINGLE_CACHE>
        |     nested helpers: ItemRecipeLookupHandler / FluidRecipeLookupHandler /
        |                     ChemicalRecipeLookupHandler  (pure generic aliases)
        +-- IDoubleRecipeLookupHandler<A, B, RECIPE, DOUBLE_CACHE>
        +-- ITripleRecipeLookupHandler<A, B, C, RECIPE, TRIPLE_CACHE>
        `-- IEitherSideRecipeLookupHandler<INPUT, RECIPE, CACHE>

Caches (recipe.lookup.cache):
  IInputRecipeCache -> AbstractInputRecipeCache -> SingleInputRecipeCache
        -> InputRecipeCache.SingleItem  (the cache used by ItemRecipeLookupHandler)
  IInputCache -> BaseInputCache -> ItemInputCache  (Map<Item, List<Recipe>>)

Monitor (recipe.lookup.monitor):
  RecipeCacheLookupMonitor<RECIPE> implements ICachedRecipeHolder<RECIPE>, IContentsListener
    - created in TileEntityRecipeMachine.presetVariables()
    - driven each server tick by updateAndProcess()
```

The per-tick flow (`RecipeCacheLookupMonitor.updateAndProcess()`):

1. `getUpdatedCache(cacheIndex)`:
   - if the world/tag cache was flushed, invalidate the per-tile cache.
   - if no valid cached recipe: `getRecipe(cacheIndex)` -> tile's
     `findFirstRecipe(inputHandler)` -> `getRecipeType().getInputCache()
     .findFirstRecipe(level, input)` (fast `Map<Item, ...>` scan + complex fallback).
   - found -> `createNewCachedRecipe(recipe, index)` (the fluent `OneInputCachedRecipe`
     builder) -> restore saved `operatingTicks`.
2. `cachedRecipe.process()`:
   - if `canHolderFunction` (redstone/control) is true, run
     `calculateOperationsThisTick` -> input + output capacity checks against the
     handlers, capped by available energy.
   - if operations > 0: set active, `useEnergy(ops)`, advance `operatingTicks`;
     on completion `finishProcessing(ops)` consumes input and inserts output.
   - if operations <= 0: set inactive (and errors like NOT_ENOUGH_ENERGY pause the
     cache until contents change).

### How the SMELTING recipe type includes vanilla furnace recipes

`MekanismRecipeType.getRecipesUncached(...)` special-cases `SMELTING`: it takes every
`RecipeType.SMELTING` (vanilla `SmeltingRecipe`), skips `isSpecial()` / `isIncomplete()` /
empty outputs, combines all ingredients into an `ItemStackIngredient` (via
`CompoundIngredient`), and wraps the result in a synthetic `BasicSmeltingRecipe`. So the
Heat Smelter (which uses `MekanismRecipeType.SMELTING`) automatically smelts iron ore
into ingots without any custom recipe definitions.

---

## 6. The block hierarchy

```
net.minecraft.world.level.block.Block
`-- BlockMekanism                              [mekanism.common.block]
    |   - root of all Mekanism blocks
    |   - delegates createBlockStateDefinition / rotate / mirror to attributes
    `-- BlockBase<TYPE extends BlockType>      [block.prefab]
        |   implements IHasDescription, ITypeBlock
        |   - holds the block TYPE (the data carrier)
        |   - runs attribute.adjustProperties() on the BlockBehaviour.Properties
        `-- BlockTile<TILE, TYPE extends BlockTypeTile<TILE>>   [block.prefab]
            |   implements IHasTileEntity<TILE> (which extends vanilla EntityBlock)
            |   - getTileType() forwards to type.getTileType()
            |   - provides newBlockEntity / getTicker defaults via IHasTileEntity
            `-- BlockFactoryMachine<TILE, MACHINE>   [block.prefab]
                |   (machines that also have factory variants:
                |    Energized Smelter, Precision Sawmill, ...)
                `-- BlockFactoryMachineModel (adds IStateFluidLoggable)
```

There is **no** "BlockBasicMachine" - a plain machine block is just `BlockTile`, and a
factory-capable machine is `BlockFactoryMachine`. The Fuelwood Heater is registered
directly as `BlockTile`.

### Block types (the data carrier)

```
BlockType                          [mekanism.common.content.blocktype]
`-- BlockTypeTile<TILE>            adds: tileEntityRegistrar supplier
    `-- Machine<TILE>              adds: default machine attributes (below)
        `-- Machine.FactoryMachine<TILE>
            `-- Factory<TILE>      (factories)
```

Builders mirror the chain (fluent, self-typed):
`BlockType.BlockTypeBuilder` -> `BlockTypeTile.BlockTileBuilder` ->
`Machine.MachineBuilder` (entry point: `MachineBuilder.createMachine(...)` /
`createFactoryMachine(...)`).

### The Attribute system

`Attribute` is a marker interface with one behavioral hook
(`adjustProperties(BlockBehaviour.Properties)`); `AttributeState` adds blockstate hooks
(`fillBlockStateContainer`, `getDefaultState`, `getStateForPlacement`, ...). Attributes
are stored on the block type in a `Map<Class<? extends Attribute>, Attribute>` keyed by
class, and read via `Attribute.get(block/holder/state, Class)` / `getOrThrow`.

Every `Machine` type seeds these by default:

- `AttributeParticleFX` (smoke + redstone particles)
- `AttributeStateActive` (the boolean `active` property + light)
- `AttributeStateFacing` (`HORIZONTAL_FACING` + placement logic)
- `AttributeInventory`, `AttributeSecurity`, `AttributeRedstone`, `AttributeComparator`
- `AttributeUpgradeSupport` (defaults: SPEED, ENERGY, MUFFLING)

The machine builder then layers on the specifics:

| Capability | Attribute | Set via |
|---|---|---|
| GUI container | `AttributeGui` (holds `Supplier<ContainerTypeRegistryObject>`) | `withGui(...)` |
| Sound | `AttributeSound` | `withSound(...)` |
| Energy config | `AttributeEnergy` (usage + storage suppliers) | `withEnergyConfig(...)` |
| Side config | `AttributeSideConfig` (set of TransmissionTypes) | `withSideConfig(...)` |
| Computer integration | `AttributeComputerIntegration` | `withComputerSupport(...)` |

Registration wiring (`MekanismBlocks`):

```java
public static final BlockRegistryObject<BlockFactoryMachine<TileEntityEnergizedSmelter, ...>, ...> ENERGIZED_SMELTER =
      BLOCKS.register("energized_smelter",
            () -> new BlockFactoryMachine<>(MekanismBlockTypes.ENERGIZED_SMELTER, ...),
            (block, properties) -> new ItemBlockTooltip<>(block, true, properties...));

// MekanismBlockTypes:
public static final FactoryMachine<TileEntityEnergizedSmelter> ENERGIZED_SMELTER =
      MachineBuilder.createFactoryMachine(() -> MekanismTileEntityTypes.ENERGIZED_SMELTER,
                                           MekanismLang.DESCRIPTION_ENERGIZED_SMELTER, FactoryType.SMELTING)
            .withGui(() -> MekanismContainerTypes.ENERGIZED_SMELTER)
            .withSound(MekanismSounds.ENERGIZED_SMELTER)
            .withEnergyConfig(...)
            .withSideConfig(TransmissionType.ITEM, TransmissionType.ENERGY)
            .build();
```

So the **block type ties together the tile type and the container type** into the block.

---

## 7. The container system

```
AbstractContainerMenu (vanilla)
`-- MekanismContainer                       [mekanism.common.inventory.container]
    |   implements ISecurityContainer
    |   - ctor takes a ContainerTypeRegistryObject (resolves the vanilla MenuType)
    |   - overridden addSlot() buckets slots into typed lists (tile slots, armor,
    |     main inventory, hotbar, offhand) for quickMoveStack
    |   - track(ISyncableData) + broadcastChanges() -> PacketUpdateContainer
    `-- MekanismTileContainer<TILE>         [container.tile]
        - ctor: super(type, id, inv) -> tile.addContainerTrackers(this)
          -> addSlotsAndOpen() (tile slots via slot.createContainerSlot() + player inv)
```

### The sync system

`ISyncableData` (`isDirty()` -> `DirtyType` CLEAN/SIZE/DIRTY; `getPropertyData(...)`).
Concrete types: `SyncableInt/Long/Double/Float/Boolean/Byte/Short`,
`SyncableItemStack`, `SyncableFluidStack`, `SyncableChemicalStack`, ... created via
`SyncableX.create(getter, setter)`.

`MekanismContainer.track(...)` registers an `ISyncableData`; `broadcastChanges()` sends
dirty entries to the client as `PacketUpdateContainer`, which dispatches back into
`MekanismContainer.handleWindowProperty(short, value)`.

### The container type (Mekanism's `MenuType`)

```
MenuType<CONTAINER> (vanilla)
`-- BaseMekanismContainerType<T, CONTAINER, FACTORY>
    `-- MekanismContainerType<T, CONTAINER>          [container.type]
        - is a vanilla MenuType (client decode via IContainerFactory) PLUS
        - stores a Mekanism-side factory + the expected Class<T> (usually the tile)
        - tile(Class<TILE>, factory) creates one whose buffer variant decodes a
          BlockPos and looks up the tile client-side
        - create(id, inv, Object data): if type.isInstance(data) -> the menu
        - create(Object data): Object -> MenuConstructor bridge
```

### Opening a machine GUI (server side)

Right-click block -> `TileEntityMekanism.onBlockActivated` ->
`Attribute.getOrThrow(block, AttributeGui.class).getProvider(tile, true)` ->
`ContainerTypeRegistryObject.getProvider(name, tile, resetMouse)` ->
`MekanismContainerType.create(tile)` -> `MenuConstructor` wrapped in a `ContainerProvider`
(implements vanilla `MenuProvider`) -> `player.openMenu(...)`.

---

## 8. The GUI (client) system

```
Screen (vanilla)
`-- AbstractContainerScreen<C>
    `-- VirtualSlotContainerScreen<T>
        `-- GuiMekanism<CONTAINER>          [mekanism.client.gui]
            |   implements IGuiWrapper
            |   - holds a window stack (GuiWindow overlays)
            |   - addGuiElements() is the extension point (adds slot widgets,
            |     warning tabs, ...)
            |   - addSlots() maps InventoryContainerSlot.ContainerSlotType to
            |     visual GuiSlot widgets + overlays
            `-- GuiMekanismTile<TILE, CONTAINER>   [client.gui]
                |   - tile = container.getTileEntity()
                |   - addGenericTabs() adds upgrade / redstone / security tabs
                `-- GuiConfigurableTile<TILE, CONTAINER>
                    |   adds side-config + transporter-config tabs
                    `-- GuiElectricMachine<TILE, CONTAINER>
                        adds GuiUpArrow, GuiVerticalPowerBar (energy),
                        GuiEnergyTab, GuiProgress (recipe progress + JEI category)
```

Note: there is no `GuiEnergizedSmelter` class - the smelter shares `GuiElectricMachine`
with the other one-input electric machines.

`GuiElement` (`client.gui.element`) is the widget base:
`extends AbstractWidget implements IFancyFontRenderer, ContainerEventHandler`.

Screen registration (in `mekanism.client.ClientRegistration` on
`RegisterMenuScreensEvent`):

```java
ClientRegistrationUtil.registerElectricScreen(event, MekanismContainerTypes.ENERGIZED_SMELTER);
// -> event.register(type.get(), GuiElectricMachine::new)
```

---

## 9. The registry system

```
DeferredRegister (NeoForge)
`-- MekanismDeferredRegister<T>       forces every holder to be a MekanismDeferredHolder
    |   (register() returns MekanismDeferredHolder<T, I>; custom holderCreator)
    +-- BlockDeferredRegister extends DoubleDeferredRegister<Block, Item>
    |       registers block + item in parallel, folds them into a BlockRegistryObject
    |       (DoubleWrappedRegistryObject: primary block holder + secondary item holder,
    |        .getSecondary() / .asItem())
    +-- TileEntityTypeDeferredRegister   -> TileEntityTypeRegistryObject<BE>
    |       - carries capability providers + clientTicker/serverTicker
    |       - mekBuilder(...) attaches the standard capability providers
    +-- ContainerTypeDeferredRegister    -> ContainerTypeRegistryObject<CONTAINER>
    +-- ItemDeferredRegister             -> ItemRegistryObject<ITEM>

MekanismDeferredHolder<R, T> extends DeferredHolder<R, T> implements INamedEntry
```

The `mekBuilder` pattern (tile types):

```java
public static final TileEntityTypeRegistryObject<TileEntityEnergizedSmelter> ENERGIZED_SMELTER =
      TILE_ENTITY_TYPES.mekBuilder(MekanismBlocks.ENERGIZED_SMELTER, TileEntityEnergizedSmelter::new)
            .clientTicker(TileEntityMekanism::tickClient)
            .serverTicker(TileEntityMekanism::tickServer)
            .withSimple(Capabilities.CONFIG_CARD)
            .build();
```

Container types:

```java
public static final ContainerTypeRegistryObject<MekanismTileContainer<TileEntityEnergizedSmelter>> ENERGIZED_SMELTER =
      CONTAINER_TYPES.register(MekanismBlocks.ENERGIZED_SMELTER, TileEntityEnergizedSmelter.class);
```

The container factory captures the registry object it is registered into, so
`MekanismTileContainer` can hand it to `super(...)`.

---

## 10. End-to-end: a machine from registration to smelting

1. **Registration**: block+item (`MekanismBlocks`), block type (`MekanismBlockTypes`,
   carries tile + container registrars via attributes), tile type (`MekanismTileEntityTypes`
   via `mekBuilder`), container type (`MekanismContainerTypes`). Client: screen registered
   against the container type.
2. **Placement**: `EntityBlock.newBlockEntity` (from `IHasTileEntity`) creates the tile.
   Tile constructor walks the whole chain (`TileEntityUpdateable` -> `CapabilityTileEntity`
   -> `TileEntityMekanism` -> ...). `presetVariables()` creates the
   `RecipeCacheLookupMonitor`; each `getInitial*` hook builds a holder; each holder is
   wrapped in a `*HandlerManager` and registered as a capability resolver.
3. **Right-click**: `AttributeGui.getProvider` -> `MekanismContainerType.create(tile)` ->
   `ContainerProvider` -> `player.openMenu`. Server menu: `MekanismTileContainer` runs
   `tile.addContainerTrackers(this)` then adds slots. Client menu: decoded from `BlockPos`,
   screen created via `MenuScreens` -> `GuiElectricMachine`.
4. **Ticking**: `serverTicker` -> `tileServer.tick()` -> `onUpdateServer()` ->
   `recipeCacheLookupMonitor.updateAndProcess()` -> `getRecipe` (input cache lookup) ->
   `createNewCachedRecipe` -> `cachedRecipe.process()` -> consume energy + input, advance
   progress, output when done.
5. **Sync**: `broadcastChanges()` -> `PacketUpdateContainer` -> tracked data updated on the
   client (progress bar, energy, temperature, ...).

---

## 11. How `mekanismheated`'s Heat Smelter maps onto all of this

Our tile (`TileEntityHeatSmelter`) deliberately diverges from `TileEntityElectricMachine`
while reusing the same recipe machinery:

- It extends **`TileEntityProgressMachine<ItemStackToItemStackRecipe>`** directly (not
  `TileEntityElectricMachine`) and implements
  **`ItemRecipeLookupHandler<ItemStackToItemStackRecipe>`**.
- `getInitialEnergyContainers` creates a **`MachineEnergyContainer.internal(...)`** but
  returns `null` from the holder hook -> **no energy capability is exposed**; the buffer
  is purely internal.
- `getInitialHeatCapacitors` creates a `BasicHeatCapacitor` (via `HeatCapacitorHelper
  .forSide(facingSupplier)`) -> heat IS exposed via `Capabilities.HEAT`, so the Fuelwood
  Heater can feed it.
- `onUpdateServer`: `convertHeatToEnergy()` (heat units -> Joules, capped at the energy
  per tick) then `simulate()` (adjacent conduction + environment losses) then
  `recipeCacheLookupMonitor.updateAndProcess()`.
- `createNewCachedRecipe` uses `OneInputCachedRecipe.itemToItem(...)` with
  `setEnergyRequirements(energyContainer::getEnergyPerTick, energyContainer)` - identical
  to an electric machine, but the energy comes from heat conversion instead of a
  capability.
- Config: `AttributeSideConfig.create(TransmissionType.ITEM)` only; item IO config slots
  added manually because `setupItemIOConfig` assumes an energy slot exists.
- `addContainerTrackers` syncs heat + the internal energy buffer manually.
- Block: `MachineBuilder.createMachine(...)` with `.withGui(...)`,
  `.withSideConfig(TransmissionType.ITEM)`, no `.withEnergyConfig(...)`.

In short: the Heat Smelter is "an electric machine whose energy slot was replaced by a
heat capacitor", implemented by mixing the same interfaces (recipe lookup) and the same
holder/capability plumbing, but choosing which capabilities to expose.

---

## 12. Quick reference

| Question | Where to look |
|---|---|
| What interfaces must a machine tile implement? | `IRecipeLookupHandler` (+ one of the `*RecipeLookupHandler` aliases) |
| How do I add an energy/heat/slot/tank? | override `getInitialEnergyContainers` / `getInitialHeatCapacitors` / `getInitialInventory` / `getInitial*Tanks` and use the `*Helper` builders |
| How do I expose a capability? | build a holder + `*HandlerManager` via `addCapabilityResolvers`; tile types get the standard providers via `mekBuilder` |
| How do I make a new machine block? | `MachineBuilder.createMachine(...).withGui(...).withSideConfig(...).build()` in `MekanismBlockTypes`; register the block in `MekanismBlocks` |
| How do I register a GUI? | `ClientRegistrationUtil.registerScreen(event, containerType, GuiX::new)` |
| How does a recipe get found/processed? | `RecipeCacheLookupMonitor.updateAndProcess()` -> `getRecipe` -> input cache -> `createNewCachedRecipe` -> `process()` |
| Where is vanilla smelting included? | `MekanismRecipeType.getRecipesUncached` special-case for `SMELTING` |
