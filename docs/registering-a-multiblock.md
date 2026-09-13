# Registering a new Mekanism multiblock — procedure & class map

*How a multiblock structure (like our Thermal Fractionation Tower) gets registered, validated,
formed and ticked in Mekanism 10.7.19, written after building one from scratch. Paths are relative
to `mek/Mekanism/src/main/java` unless prefixed with our addon package `io.aduhtkjm.mekanismheated`.*

---

## 1. The cast of classes

```
MultiblockManager<T>            lib/multiblock/MultiblockManager.java
  │  one instance per structure TYPE ("evaporation", "inductionMatrix", our "fractionation").
  │  Created once with:  name + cache supplier + validator supplier.
  │  Self-registers into a static set; Mekanism's world-tick handler calls
  │  createOrLoadAll()/endOfTick for ALL managers, so addon managers need no extra hooks.
  │  Owns the persistent MultiblockCache map (saved data under data/<namespace>/<nameLower>).
  │
  ├─ MultiblockCache<T>         lib/multiblock/MultiblockCache.java
  │    persists container CONTENTS across formations/save-load via CacheSubstance
  │    (ITEMS / FLUID / CHEMICAL / ENERGY / HEAT). Contents are copied by INDEX.
  │
  ├─ IStructureValidator<T>     lib/multiblock/IStructureValidator.java
  │    stateless-per-attempt checker; created fresh for every validation run.
  │
  ├─ CuboidStructureValidator   lib/multiblock/CuboidStructureValidator.java
  │    the standard cuboid implementation (what 99% of structures extend).
  │
  └─ FormationProtocol<T>       lib/multiblock/FormationProtocol.java
       orchestrates ONE validation attempt; collects locations/internalLocations/
       valves/idsFound, then forms or fails.

TileEntityMultiblock<T>         tile/prefab/TileEntityMultiblock.java
  base tile for every part block. Holds its Structure reference, runs the half-second
  revalidation ticker, elects the "master" (renderer/ticker), syncs update tags.
  Our tiles extend this (directly or via a small hierarchy).

MultiblockData                  lib/multiblock/MultiblockData.java
  the FORMED structure's shared brain: bounds, volume, locations, internalLocations,
  valves, tanks/capacitors/slots, tick() logic, redstone level. One instance lives on
  every node tile (via its Structure), but only the master calls tick().

Structure                       lib/multiblock/Structure.java
  per-node graph object: which tiles are currently connected (flood-fill merged),
  which of them could be master, plus the current MultiblockData. NOT the shape check.
```

Key interfaces:

| Interface | Role |
|---|---|
| `IMultiblockBase` | anything that can join a node graph (multiblock tiles + structural glass). |
| `IMultiblock<T>` | a *typed* part: `getManager()` binds it to exactly ONE `MultiblockManager`. |
| `IStructuralMultiblock` | generic frame blocks (structural glass) usable by many managers. |
| `IInternalMultiblock` | tiles placed INSIDE a structure (e.g. boiler superheating elements); notified on form/unform. |

---

## 2. Hard constraint: one manager per tile, identity-checked blocks

A tile's `getManager()` is fixed by its class. The framework compares managers **by
reference** everywhere (`Structure.isCompatible`, `MultiblockManager.isCompatible`,
`CuboidStructureValidator.validateFrame → isFrameCompatible`). Consequences:

- You cannot reuse another mod's (or even vanilla Mekanism's) casing blocks inside your
  own structure — their tiles report a different manager and the flood fill refuses to
  merge. That is why the fractionation tower got its own lookalike casing/valve blocks.
- Your validator identifies casing blocks via `BlockType.is(block, YOUR_blockType)` —
  again reference identity against the `BlockType` instances you built.

---

## 3. Registration checklist (what we implemented for the fractionation tower)

### Step 1 — `MultiblockData` subclass
`content/fractionation/FractionationMultiblockData.java`

- Constructor takes the `BlockEntity`, builds tanks/capacitors, and **must add every
  container to the inherited lists** (`fluidTanks.add(...)`, `heatCapacitors.add(...)`) —
  those lists are what capabilities, caching and GUIs see. A field alone exposes nothing
  (this exact mistake cost us heat input on valves).
- `tick(Level)` = server-side logic (heat loss, recipe processing, packet-throttling).
- Override `writeUpdateTag`/`readUpdateTag` to sync anything not covered by
  `@ContainerSync` (which only handles single fields — no lists!).
- Anything layout-dependent (our tank count) must be rebuilt on formation; expose a
  method for the validator to call.

### Step 2 — validator
`content/fractionation/FractionationValidator.java extends CuboidStructureValidator<T>`

| Override | Purpose |
|---|---|
| `precheck()` | find candidate cuboid via `StructureHelper.fetchCuboid(structure, MIN, MAX, sides, tolerance)`; reset per-attempt state. Return false = fail fast. |
| `getCasingType(state)` | map block states to `CasingType.FRAME / VALVE / OTHER / INVALID` (identity compare your `BlockType`s). |
| `getStructureRequirement(pos)` | per-position rule: `FRAME` (edges), `OTHER` (wall faces), `INNER` (interior), `IGNORED` (corners of evaporation-style tops). |
| `validateFrame(ctx, pos, state, type, needsFrame)` | hook for controller counting (fail on two controllers with `FormationResult.fail(..., noIgnore=true)` so IGNORED corners can't hide duplicates). Call `super` at the end. |
| `validateInner(state, ...)` | what interior positions may contain (air? trays?). Collect per-position info here (we count trays per y-level). |
| `postcheck(structure, chunkMap)` | cross-position rules + configure the fresh `MultiblockData` (capacities, layout geometry). Runs BEFORE contents are applied. |

Geometry vocabulary (`VoxelCuboid.WallRelative`): a position matching N faces of the
cuboid is CORNER (N≥3), EDGE (N=2), SIDE (N=1), INVALID/inner (N=0).

### Step 3 — manager holder
`content/fractionation/ModFractionation.java`

```java
public static final MultiblockManager<FractionationMultiblockData> FRACTIONATION_MANAGER =
      new MultiblockManager<>("fractionation", FractionationCache::new, FractionationValidator::new);
```
Static field in a class touched during startup. The static `managers` set picks it up;
saved-data loading and the end-of-tick dirty-sync come for free. Use a CUSTOM cache
subclass if any container count varies between formations (see §6).

### Step 4 — tiles
`tile/multiblock/`

- Base casing tile (`TileEntityFractionationBlock`): `createMultiblock()` returns a fresh
  data instance, `getManager()` returns the manager, `canBeMaster() = false`.
- Valve subclass: overrides `getInitialFluidTanks` / `getInitialHeatCapacitors` returning
  `side -> getMultiblock().get...Tanks(side)` and declares `persists(FLUID/HEAT) = false`.
- Controller subclass: `delaySupplier = NO_DELAY`, `onUpdateServer` mirrors
  `setActive(multiblock.isFormed())`, `canBeMaster() = true`.

Holder lambdas implement bare `IFluidTankHolder` etc. whose `canInsert`/`canExtract`
default to **true on all sides** — no side-config exists unless you build a
`TileComponentConfig` (machines only; valves have none by design).

### Step 5 — block types & blocks
`registries/ModBlocks.java`

```java
BlockTypeTile<...> TYPE = BlockTileBuilder
      .createBlock(() -> ModTileEntityTypes.X, DESCRIPTION_LANG_ENTRY)
      .withGui(() -> ModContainerTypes.X)          // controllers only
      .with(Attributes.ACTIVE, new AttributeStateFacing(), new AttributeCustomResistance(9))
      .externalMultiblock()                        // AttributeMultiblock.EXTERNAL etc.
      .build();

BLOCKS.register("name", () -> new BlockBasicMultiblock<>(TYPE, props -> props.mapColor(...)),
                (block, properties) -> new ItemBlockTooltip<>(block, true, properties));
```
`BlockBasicMultiblock` provides wrench/right-click plumbing; `externalMultiblock()`
marks it as an external multiblock part (mob spawn/pathing rules).

### Step 6 — tile entity types
`registries/ModTileEntityTypes.java`

```java
TILE_ENTITY_TYPES.mekBuilder(ModBlocks.X, TileX::new)
      .clientTicker(TileEntityMekanism::tickClient)
      .serverTicker(TileEntityMekanism::tickServer)
      .withSimple(Capabilities.CONFIGURABLE)   // configurator right-click support
      .build();
```
`mekBuilder` attaches the standard FLUID/ITEM/CHEMICAL/HEAT capability providers; they
only actually expose something when the corresponding `getInitial*Tanks`-style hook
returns a non-null holder.

### Step 7 — GUI (optional, controller only)
- `ModContainerTypes`: `CONTAINER_TYPES.custom("name", TileClass.class).offset(x, y).build()`.
- Add `.withGui(...)` to the block type (this is what makes right-click open it).
- Screen class extends `GuiMekanismTile<TILE, CONTAINER>`; register in
  `ModClient.registerScreens` via `ClientRegistrationUtil.registerScreen`.
- Dynamic content (variable tank counts): pass suppliers into custom widgets; rebuild
  nothing — widgets re-query each frame.

### Step 8 — assets & text
blockstates (+ item models), loot tables, `ModLang` entries, en_us/zh_cn JSON. Note the
runtime description key for tooltips is `block.<ns>.<path>.description`.

---

## 4. Formation flow (end to end)

```
place/break a part block
  └─ BlockTile.neighborChanged ──► TileEntityMultiblock.onNeighborChange
       ├─ adjacent same-manager IMultiblockBase? ──► Structure flood-fill merge
       └─ formed && pos inside bounds changed ──► Structure.markForUpdate(world, invalidate=true)

next tick: TileEntityMultiblock.onUpdateServer (ticker ≥ 3)
  └─ Structure.tick(tile, tryValidateEveryHalfSecond)
       └─ Structure.runUpdate(controller)          [controller = any node, prefers canBeMaster]
            └─ controller.createFormationProtocol().doUpdate()
                 ├─ validator = manager.createValidator(); validator.init(...)
                 ├─ precheck()                     → fetchCuboid from merged plane maps
                 ├─ validate(): for every position in cuboid:
                 │     FRAME/OTHER/IGNORED → getCasingType + validateFrame (collect locations,
                 │                            valves, found caches by UUID)
                 │     INNER              → validateInner; non-air recorded into internalLocations
                 ├─ postcheck(structure, chunkMap) → cross-checks; CONFIGURE the new data here
                 ├─ form(): pointer.setMultiblockData(manager, structureFound)
                 │     reused/merged caches → manager.replaceCaches(...)
                 │     cache.apply(registry, data)   ← persisted contents copied back IN BY INDEX
                 │     data.onCreated(world)        ← capacities, ambient temp, IInternalMultiblock notify
                 └─ FormationResult (success text shown via configurator right-click on failure)

back in TileEntityMultiblock.structureChanged(data):
  invalidateCapabilitiesFull(); first formed-and-canBeMaster node becomes master
  (hasMaster/isMaster), comparator levels pushed to valves.
```

Revalidation triggers worth knowing:
- `Structure.tick` retries **every half second while the structure is invalid**, and
  `recheckStructure` forces immediate updates.
- `onNeighborChange` only watches interiors of **formed** multiblocks
  (`MultiblockData.isPositionInsideBounds` requires `isFormed()`). Interior changes to an
  unformed structure are invisible to the framework — our tray block works around this
  by nudging neighbouring nodes itself (§6).
- Right-clicking any part with a Configurator runs `onRightClick` → immediate attempt +
  chat feedback of the localized failure reason.

## 5. Tick flow (formed)

```
serverTicker → TileEntityMultiblock.onUpdateServer
  ├─ structure.tick(...)                    keep revalidation scheduling alive
  ├─ if master && data.tick(level) → needsPacket (update tag to tracking clients)
  └─ manager.markTicked(data)
ServerTickEvent.Post → MultiblockManager.endOfTick → handleDirtyMultiblock
  └─ cache.sync(data)                        copy contents INTO the cache (then saved)
```

Client side: master writes `getReducedUpdateTag` (formed flag + `data.writeUpdateTag`),
every node reads it; `@ContainerSync` fields additionally stream to open GUIs via
`SyncMapper` (supports single fields of tank/capacitor/primitive/array types — NOT lists).

---

## 6. Pitfalls we actually hit (checklist for next time)

1. **Lookalike blocks are mandatory** when not reusing vanilla-Mekanism casings (§2).
2. **Register containers in the data's lists**, not just fields — else capabilities
   silently expose nothing (valves had no heat until `heatCapacitors.add(...)`).
3. **Variable container counts break `MultiblockCache.sync`**: it only prefabricates
   cache containers when its list is *empty*, then indexes pairwise →
   `IndexOutOfBoundsException` after reforming with MORE tanks. Fix: cache subclass that
   tops up lists before `super.sync` (`FractionationCache`); extra cache entries are kept
   so shrinking+regrowing preserves contents.
4. **Unformed structures ignore interior neighbour changes** — a valid shell + later
   inner blocks deadlocks. Fix: have the inner block (`DistillationTrayBlock`)
   `markForUpdate(level, true)` nearby nodes on place/break.
5. **Sided fluid access per position**: filter the holder list per side using stored
   layout geometry (`FractionationMultiblockData.getTankForLevel`); empty list ⇒ no
   capability exposed at all (`CapabilityHandlerManager.resolve` returns null).
6. **Machine side-config defaults to NONE on all sides** (`ConfigInfo` constructor);
   loop `EnumUtils.SIDES` setting `DataType.INPUT` after `setupInputConfig` for
   "accept from all sides" defaults (applies to newly placed machines only).
7. **Index stability matters**: sump first, banks bottom-up — the cache copies by index;
   append-only ordering keeps contents aligned across layout changes.
8. **Per-attempt state**: validators are created fresh per attempt; still reset counters
   in `precheck` defensively. Never store positions from the mutable `BlockPos` handed
   to `validateNode`/`validateInner` without copying.

---

## 7. Where things live in this mod

| Concern | File(s) |
|---|---|
| Data + layout + processing | `content/fractionation/FractionationMultiblockData.java` |
| Shape rules | `content/fractionation/FractionationValidator.java` |
| Cache persistence | `content/fractionation/FractionationCache.java` |
| Manager | `content/fractionation/ModFractionation.java` |
| Tiles | `tile/multiblock/TileEntityFractionationBlock.java` (+ Valve/Controller) |
| Blocks & types | `registries/ModBlocks.java` (`THERMAL_FRACTIONATION_*`) |
| Tile types | `registries/ModTileEntityTypes.java` |
| Container/GUI | `registries/ModContainerTypes.java`, `client/gui/machine/GuiThermalFractionationController.java`, `client/gui/element/GuiMixedFluidGauge.java` |
| Recipe | `recipe/FractionationRecipe.java`, `recipe/BasicFractionationRecipe.java`, registries in `ModRecipeTypes`/`ModRecipeSerializers`, examples in `data/mekanismheated/recipe/fractionating/` |
