# Large Heat Smelter — dual-mode machine & multiblock plan

*Written 2026-09-07 after a deep dive into the Heat Smelter tile, Mekanism's
multiblock framework (`mek/.../lib/multiblock`), and the in-repo Thermal Fractionation
Tower (`content/fractionation/`, `tile/multiblock/`).*

**Implementation status:** Phase 0 done (2026-09-07). `tile/HeatSmelterLogic.java` created; `TileEntityHeatSmelter` now delegates to it (behavior unchanged). Phases 1–6 not started.

A **Large Heat Smelter** is a solid cuboid of ordinary `heat_smelter` blocks that fuses
into one machine with a larger fluid buffer and a larger heat reservoir. The same block
also keeps working as a standalone single-block machine.

## Decisions (locked)

| Question | Decision |
|---|---|
| Block kind | **Dual-mode** — the existing `heat_smelter` block is both a standalone machine and a multiblock part. No new part/controller blocks. |
| Cuboid shape | **Solid cuboid** — every block in the `W×H×D` region is a `heat_smelter` (interior filled, not hollow). |
| Fluid/heat/item I/O | **Direct face access, no valve blocks** — shell blocks expose the shared tanks on their outward faces; interior blocks expose nothing. |
| Scaling | **Fluid + heat capacity scale by volume (`W·H·D`); item slots stay fixed** (1 input / 1 output / 1 fuel). Container count is constant → no custom `MultiblockCache` subclass. |
| Engine | **Reuse Mekanism's** `Structure`/`MultiblockManager`/`CuboidStructureValidator`/`MultiblockData`/`MultiblockCache`. Implement `IMultiblock<T>` (do NOT extend `TileEntityMultiblock`). |
| Vanilla fallback | **None.** Single block works standalone; a full cuboid is the only way to make a large one. |
| Contents on form | **Absorb** — each block's standalone inventory/fluid/heat is moved into the shared `MultiblockData`; the per-block containers become dormant. |
| Contents on break | **Do NOT distribute back** — the master drops its `MultiblockData` (as an item), each other block drops as an empty block. Rationale: distribution is sometimes impossible (e.g. contents no longer fit). |
| Max size cap | **6×6×6** — enforced in the validator + config; anything larger is rejected. |

## Why the engine is reusable (and the one catch)

The structure engine is **interface-driven**, not class-driven.
`Structure`/`StructureNode`/`CuboidStructureValidator`/`MultiblockManager`
only require a `BlockEntity & IMultiblockBase`;
`MultiblockManager.tick` only requires `Structure<T>`. So any
`TileEntityProgressMachine` subclass can drive it just by implementing
`IMultiblock<T>` (a `FractionationValidator`-style `CuboidStructureValidator`).

**The one real catch — recipe coupling.** Mekanism's recipe-cache subsystem is
tile-bound, not recipe-bound:

- `HeatSensitiveOneInputCachedRecipe` is bound to the concrete `TileEntityHeatSmelter`.
- `HeatSmelterRecipeCacheLookupMonitor` extends Mekanism's
  `RecipeCacheLookupMonitor`, which uses the tile-bound `IRecipeLookupHandler`.

So the shared brain **cannot reuse** `HeatSmelterRecipeCacheLookupMonitor`. Instead it uses
a **self-contained manual recipe loop** (same pattern as
`FractionationMultiblockData.processRecipes`), reading temperature/heat/fuel directly from
its own fields. To avoid two divergent copies of the smelting math, the pure logic
(`findRecipeFor`, `speedFactor`, `burnFuel`, `tryAlloy`) is factored out of
`TileEntityHeatSmelter` into shared statics (`HeatSmelterLogic`) used by **both** the
standalone path and the multiblock path.

## Base-class conflict and the solution

- `TileEntityHeatSmelter` extends `TileEntityProgressMachine<HeatSmelterRecipe>` (extends
  `TileEntityRecipeMachine`).
- Mekanism's `TileEntityMultiblock` extends `TileEntityMekanism` and implements
  `IMultiblock<T>` + `IConfigurable`.

A single class can't extend both. Solution: a new shared base

```
TileEntityProgressMultiblockMachine<T> extends TileEntityProgressMachine<T>
    implements IMultiblock<T>
```

carrying **only** the multiblock glue, ported verbatim from `TileEntityMultiblock`
(~100–150 lines): the `@CapabilityInject` capability caches, `getStructure`/`setStructure`
/`onNeighborTileChange`/`onBreak`/`getStructureUpdateInfo`/`sendStructureUpdate`/
`onStructureForm`/`onStructureChanged`/`canBeMaster`/`createMultiblock`/`getManager`/
`getStructureData`, and `onLoad`/`onUnload` multiblock bookkeeping. `IConfigurable`
(annihilator config) is **omitted** — a smelter has no annihilator.

The ported glue has exactly **two** references to the old base:
1. `MultiblockData.getTile() -> ((TileEntityMultiblock)getTile()).getWorld()` →
   change the return type to `World`.
2. `onStructureForm()` calls `getMultiblock().getContainer().form(this)` →
   keep, but `MultiblockCache.form(MultiblockData, T)` now accepts our shared base type `T`.

Everything else is type-agnostic.

## Planned file layout

```
tile/
  TileEntityHeatSmelter.java            (existing; delegate to HeatSmelterLogic)
  HeatSmelterLogic.java                 (NEW shared statics: findRecipeFor/speedFactor/burnFuel/tryAlloy)
  prefab/
    TileEntityProgressMultiblockMachine.java   (NEW shared base; see above)
  multiblock/
    LargeHeatSmelterTile.java           (NEW) extends TileEntityProgressMultiblockMachine<LargeHeatSmelterData>
    LargeHeatSmelterData.java           (NEW) extends MultiblockData<LargeHeatSmelterTile>
    LargeHeatSmelterValidator.java      (NEW) extends CuboidStructureValidator
    ModLargeHeatSmelter.java            (NEW) static MultiblockManager<LargeHeatSmelterData>
```

### `LargeHeatSmelterTile` (the part tile)
- `extends TileEntityProgressMultiblockMachine<LargeHeatSmelterData>`.
- Reuses the standalone tile's container layout: `input`/`output`/`fuel` slots +
  `buffer`/`reservoir` fluid tanks + heat capacitor (same `getInitialInventory`,
  `getInitialFluidTanks`, `getInitialHeatCapacitors`).
- Implements `IMekanismRecipe` (delegating recipe state to the shared brain).
- `canBeMaster() = true` (any member can become master).
- `createMultiblock()` → `new LargeHeatSmelterData`; `getManager()` → `ModLargeHeatSmelter.INSTANCE`.
- On `structureForm()`: absorb own container contents into the shared data
  (inventory → shared `input`/`output`/`fuel`; fluid → shared `buffer`/`reservoir`;
  heat → shared heat capacitor), then **dorm** own containers (clear + mark dormant).
- Capability exposure: shell blocks (outward faces that are part of the structure's
  external surface) expose the shared `input`/`output`/`fuel`/`buffer`/`reservoir`/heat
  on those faces only; interior blocks expose nothing. Implemented via the
  `MultiblockCache`-backed dynamic handlers (as fractionation does), keyed off
  `MultiblockStructureInfo` so only outward-facing members advertise.
- Progress: driven by the shared brain tick (see below), replicated to members for
  the existing `HeatSmelterRecipeProgressWidget`.

### `LargeHeatSmelterData` (the shared brain)
- `extends MultiblockData<LargeHeatSmelterTile>`, implements `IMekanismRecipe`,
  `IRecipeLookupHandler`, `IRecipeHandler<HeatSmelterRecipe>`, and
  `IHeatHandler`/`IHeatingInteraction`-compatible heat behavior.
- Holds the **single** real `input`/`output`/`fuel` slots, `buffer`/`reservoir`
  fluid tanks, heat capacitor, `progress`, `currentRecipe`, `heat` — scaled at form:
  - `buffer`/`reservoir` capacity × `W·H·D`.
  - heat capacitor capacity × `W·H·D`.
  - item slots: 1/1/1 (not scaled).
- Owns its own `MultiblockRecipeHandler` + a manual `HeatSmelterRecipe` recipe set
  (rebuilt on `onResourceUpdate`, same as fractionation). Reads temperature/heat
  directly from its fields.
- `tick()` (called by `MultiblockManager`): drives the manual recipe loop using
  `HeatSmelterLogic` statics; updates `progress`, consumes fuel, produces output,
  runs `tryAlloy` on fuel depletion.
- `structureForm()`: absorbs contents from each member (see "Contents on form").
- **On break: does not distribute.** The master block drops this data serialized as
  an item (existing `MultiblockData` drop mechanism); other blocks drop as empty.
- `getTile()` returns `World` (the one type change above).

### `LargeHeatSmelterValidator`
- `extends CuboidStructureValidator`, filters to `ModBlocks.HEAT_SMELTER`.
- Enforces max 6×6×6 (config-driven); rejects larger regions.

### `ModLargeHeatSmelter`
- Static `MultiblockManager<LargeHeatSmelterData>`, self-registers (ticked by Mekanism).

## Shared logic factoring (Phase 0) — DONE

Extracted into `tile/HeatSmelterLogic.java` (final class, all static, no tile state).
Signatures that landed (adapted to the actual current code, which differs from the
earlier sketch):

- `@Nullable HeatSmelterRecipe findRecipeFor(Level, ItemStack input, double temperature, boolean enforceTemperature)`
  — from `findRecipe`. The temperature gate uses `temperature >= recipe.getTemperatureThreshold()`
  instead of `canProcess(this)`, because that is exactly what the recipes' `canProcess(ISidedHeatHandler)`
  does for a single-capacitor machine (`getTotalTemperature() == heatCapacitor.getTemperature()`).
  This keeps the logic tile-agnostic (no `ISidedHeatHandler` needed) and shareable with the multiblock brain.
- `double speedFactor(double temperature)` — from `getSpeedFactor` (BASE/FULL_SPEED_TEMPERATURE, clamped 0..1).
- `int burnFuel(Level, double currentTemperature, IHeatCapacitor heat, ItemStack fuel)` — from `burnFuel()`.
  Discrete per-fuel-item burn (not dt-based, as the earlier sketch assumed): checks temp < MAX_FUEL_TEMPERATURE,
  finds the fuel-conversion recipe, adds `recipe.getOutput(itemInput)` heat to `heat`, returns the item count to
  consume. The caller applies the shrink to its slot and logs the mismatch.
- `@Nullable AlloyConfig tryAlloyOnce(Level, MultiFluidTank tank, @Nullable AlloyConfig lastApplied)` — the
  `tryAlloying` orchestration (empty/client gate, last-applied fast path, full alloy-recipe scan). Returns the
  applied/updated config.
- `boolean applyAlloy(MultiFluidTank, FluidStackIngredient in1, in2, FluidStackIngredient output, List<FluidStack>)`
  and `@Nullable FluidStack findMatchingFluid(FluidStackIngredient, List<FluidStack>)` — the alloy math.
- `record AlloyConfig(FluidStackIngredient input1, input2, output)` — the old `AlloyCache`.

`TileEntityHeatSmelter` now: `findRecipe`/`getSpeedFactor`/`burnFuel`/`tryAlloying` delegate to these; the
`lastAlloy` field is `HeatSmelterLogic.AlloyConfig`; `applyAlloy`/`findMatchingFluid`/`AlloyCache` removed;
unused imports pruned. `canBurnFuel()` is left as the tile's public display predicate (unchanged). The multiblock
`LargeHeatSmelterData.tick()` (Phase 3) will call the same statics. Standalone behavior is unchanged.

Note: the earlier `canProcess(this)` coupling is sidestepped rather than resolved — the tile is treated as an
`ISidedHeatHandler` at the recipe call sites but its declaration/parents do not show it; using a plain
`double temperature` avoids depending on that. Worth a look if Phase 3 needs the shared reservoir to expose a
sided heat handler to other systems.

## Capability-exposure detail (shell vs interior)

- `MultiblockStructureInfo` already provides `getExteriorFaceCount()` (0..6) and
  `getFacesOnExterior(Direction)`. A member with `exteriorFaceCount > 0` is a shell
  block; for each exterior face it exposes the shared handler; interior faces (and
  fully-interior blocks) expose nothing.
- Providers are registered on the `LargeHeatSmelterTile` type and return
  dynamic handlers backed by the `MultiblockCache`, gated by the member's exterior
  face info — mirroring fractionation's `TileEntityMultiblock` providers.
- On structure form/tear-down, `invalidateCaches()` + `markDirty` fire so neighbor
  capability caches refresh.

## Phases

- **Phase 0 — shared logic.** Extract `HeatSmelterLogic` statics; standalone
  `TileEntityHeatSmelter` delegates. No behavior change. *Verify: standalone smelter
  works identically (smelts, alloys, heat scaling).*
- **Phase 1 — shared base.** `TileEntityProgressMultiblockMachine<T>` with ported
  `IMultiblock` glue; the `World` type fix. *Verify: compiles; a dummy subclass
  registers.*
- **Phase 2 — validator + manager.** `LargeHeatSmelterValidator` (6×6×6 cap) +
  `ModLargeHeatSmelter`. *Verify: placing a cuboid reports a valid structure;
  oversized regions rejected.*
- **Phase 3 — tile + data (no I/O).** `LargeHeatSmelterTile` + `LargeHeatSmelterData`
  with scaled tanks, absorption on form, manual recipe tick via `HeatSmelterLogic`.
  *Verify: form a 2×2×2, confirm tanks scale by 8, a recipe processes, fuel burns,
  output appears, alloy triggers.*
- **Phase 4 — capability exposure + GUI.** Shell/interior face-gated capability
  providers; large-smelter GUI (reusing widgets, showing scaled tanks/progress).
  *Verify: pipes/transporters/heat can connect to outward faces; interior blocks
  expose nothing; GUI shows shared state.*
- **Phase 5 — persistence + break/drop.** NBT for shared data + dormant member
  containers; master drops data item on break, others drop empty; contents preserved
  on save/reload. *Verify: form → save → reload → still works; break → data item
  dropped, empty blocks, contents not lost.*
- **Phase 6 — polish.** Config for max size; lang (en_us/zh_cn); edge cases (partial
  break of a formed structure, world border, player break mid-form, redstone
  auto-off); perf check at 6×6×6 (216 tiles).

## Risks & mitigations

| Risk | Mitigation |
|---|---|
| Solid 6×6×6 = 216 full smelter tiles (each with tanks/caches) → perf. | Cap at 6×6×6 (decided); per-tick work is only the shared brain tick; members are inert once formed. Profile at max size in Phase 6. |
| Two copies of smelting math diverge. | Single `HeatSmelterLogic` source of truth (Phase 0); both paths call it. |
| Recipe/temperature behavior differs between standalone and multiblock. | Same `HeatSmelterLogic` + same `HeatSensitiveOneInputCachedRecipe` semantics; manual loop replicates `getProgressFor`/speed-factor math exactly. |
| Capability caches not invalidated on form/tear-down → pipes miss tanks. | Port `invalidateCaches()` + `markDirty`; test pipe attach/detach in Phase 4. |
| Ported glue references old base. | Only 2 spots (`getTile()`, `form` param); type-fix both. |
| Break drops: contents lost if naively discarded. | Master serializes full data (incl. item/fluid/heat) to the dropped item — contents preserved, just not redistributed. |
| Absorb-on-form double-count (standalone + shared). | Absorb moves contents out of member containers into shared, then members go dormant (cleared). One copy at a time. |

## Relevant files

- `src/main/java/io/aduhtkjm/mekanismheated/tile/TileEntityHeatSmelter.java` — main tile;
  `onUpdateServer`, `findRecipe`(:313), `burnFuel`(:277), `tryAlloying`(:366),
  `getSpeedFactor`(:535), `getInitialInventory`/`getInitialFluidTanks`/`getInitialHeatCapacitors`.
- `src/main/java/io/aduhtkjm/mekanismheated/recipe/cache/HeatSensitiveOneInputCachedRecipe.java` —
  temperature-aware fractional-progress recipe; bound to `TileEntityHeatSmelter`.
- `src/main/java/io/aduhtkjm/mekanismheated/recipe/lookup/monitor/HeatSmelterRecipeCacheLookupMonitor.java` —
  cache monitor; bound to `TileEntityHeatSmelter` + Mekanism `RecipeCacheLookupMonitor` (NOT reusable in data).
- `src/main/java/io/aduhtkjm/mekanismheated/content/fractionation/FractionationMultiblockData.java` —
  reference for shared brain + manual recipe loop + container registration.
- `src/main/java/io/aduhtkjm/mekanismheated/content/fractionation/FractionationValidator.java` —
  reference `CuboidStructureValidator` subclass.
- `src/main/java/io/aduhtkjm/mekanismheated/content/fractionation/ModFractionation.java` —
  reference `MultiblockManager` static.
- `src/main/java/io/aduhtkjm/mekanismheated/content/fractionation/FractionationCache.java` —
  reference custom `MultiblockCache` (variable tanks; NOT needed here — tanks fixed).
- `src/main/java/io/aduhtkjm/mekanismheated/tile/multiblock/TileEntityFractionationBlock.java` —
  reference `TileEntityMultiblock` part tile.
- `src/main/java/io/aduhtkjm/mekanismheated/tile/multiblock/TileEntityThermalFractionationController.java` —
  reference master (`canBeMaster()=true`).
- `src/main/java/io/aduhtkjm/mekanismheated/registries/ModBlocks.java` — `HEAT_SMELTER` (no new block).
- `src/main/java/io/aduhtkjm/mekanismheated/registries/ModTileEntityTypes.java` — `HEAT_SMELTER` (no new type).
- `mek/Mekanism/src/main/java/mekanism/common/tile/prefab/TileEntityMultiblock.java` —
  the ~100–150 lines of glue to port into the shared base.
- `mek/Mekanism/src/main/java/mekanism/common/tile/prefab/TileEntityProgressMachine.java` —
  standalone base; `onUpdateServer` drives the recipe cache monitor.
- `mek/Mekanism/src/main/java/mekanism/common/lib/multiblock/Structure.java` — flood-fill/validation;
  `tick`/`runUpdate` need only `BlockEntity & IMultiblockBase`(:116,126).
- `mek/Mekanism/src/main/java/mekanism/common/lib/multiblock/IMultiblockBase.java` —
  structure-node interface (`getStructure`/`setStructure`/`getDefaultData`).
- `mek/Mekanism/src/main/java/mekanism/common/lib/multiblock/IMultiblock.java` —
  multiblock-tile interface (`createMultiblock`/`getManager`/`canBeMaster`).
- `mek/Mekanism/src/main/java/mekanism/common/lib/multiblock/MultiblockManager.java` —
  per-structure-type registry; self-registers, ticks all managers.
- `mek/Mekanism/src/main/java/mekanism/common/lib/multiblock/CuboidStructureValidator.java` —
  cuboid validator base.
- `mek/Mekanism/src/main/java/mekanism/common/lib/multiblock/MultiblockData.java` —
  shared brain base; implements `IMekanismInventory`/`IMekanismFluidHandler`/`ITileHeatHandler`.
- `human/registering-a-multiblock.md` — in-repo runbook.
