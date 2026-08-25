# Fused Pipe — design & implementation plan

*Written 2026-08-25 after a deep dive into Mekanism 10.7.19's transmitter subsystem
(`mek/Mekanism/src/main/java/mekanism/common/{content/network,lib/transmitter,tile/transmitter}`).*

**Implementation status:** Phases A + B implemented (2026-08-25). Files:
`content/fusedpipe/{FusedFunction,FusedPipeConfig,FusedPipeNode,FusedNetwork,FusedAcceptorCache,FusedPipeRegistry}.java`,
`tile/TileEntityFusedPipe.java`, `block/BlockFusedPipe.java`, `item/ItemBlockFusedPipe.java`,
registrations in `ModBlocks`/`ModTileEntityTypes`/`ModItems`, listeners wired in `Mod`.
Assets: blockstate/model/item-model (placeholder iron texture), empty loot table
(config-preserving drop handled in `BlockFusedPipe.playerDestroy`), lang en_us+zh_cn.
Not yet done (Phases C-F): fluid, chemical, heat, items; connection-arm visuals;
alloy upgrades; tier-setting GUI/crafting.

A **fused pipe** is a single block that transmits energy, fluid, chemical, heat and items,
each function having an independent tier (basic/advanced/elite/ultimate) or being disabled,
driven by the item's NBT.

Decisions made (2026-08-25):

| Question | Decision |
|---|---|
| Architecture | **From-scratch unified network** (`FusedNetwork` over `FusedPipeNode`s). Rationale: vanilla pipes are overhead-heavy; acceptor-bridging would add more; custom optimizations become possible. |
| Disabled functions | Yes — function absent from NBT = disabled entirely. |
| Side config | One shared per-side ConnectionType for all functions (sneak-right-click cycles). |
| Alloy tier upgrades on placed pipes | Phase 2 (not v1). |
| Rendering | Plain block model first; no connection arms, no item visuals. |
| Tier-setting UX | Deferred — creative/NBT only for now. |
| Initial scope | Phase A (skeleton) + Phase B (graph + energy) only. |

---

## 0. Research conclusions that shape the design

| Finding | Consequence |
|---|---|
| Each vanilla transmitter is generically welded to one network (`UniversalCable<->EnergyNetwork`, ...); each holds exactly one `theNetwork` field | A single object cannot join 5 typed networks -> fusing by reuse is impossible |
| `TransmitterNetworkRegistry` only discovers neighbors that are literally `instanceof TileEntityTransmitter`, one BE per block position | Fused pipes can never *merge* into vanilla pipe networks (without mixins); boundaries will exist regardless of approach |
| Vanilla overhead sources: 5 separate graphs/BFS per area, 5 acceptor-cache sets, per-tick share redistribution (`validateSaveShares`), registry sweeps | A unified graph does ONE BFS/refresh/acceptor pass per position and skips disabled functions entirely -> genuine perf win |
| Reusable public internals: `EmitUtils` + `Target`/`SplitInfo` (fair split), `VariableCapacityEnergyContainer`, `VariableCapacityFluidTank`, `VariableCapacityChemicalTank`, `VariableHeatCapacitor`, `EnergyHandlerManager` + `DynamicStrictEnergyHandler`, `EnergyCompatUtils` (FE etc.), tier enums for capacity values, `TransitRequest`, `CapabilityTileEntity.capabilityProvider(...)` | We rebuild the network layer but reuse storage/distribution/compat plumbing |

**Interop reality:** machines see normal capabilities (native behavior). Vanilla pipes treat a
fused pipe as an acceptor via capabilities and vice versa — flow crosses boundaries with <=1 tick
latency and buffers at the boundary. No network merging with vanilla pipes.

## 1. Architecture

```
TileEntityFusedPipe (1 BE per position)
 +- FusedPipeNode            (per-position content: config ref, heat capacitor, item transit queue, saved shares)
     +- member of FusedNetwork  (unified graph over many nodes)
          |- energy:    VariableCapacityEnergyContainer   (network buffer = sum of enabled node capacities)
          |- fluid:     VariableCapacityFluidTank         (single-fluid like vanilla)
          |- chemical:  VariableCapacityChemicalTank      (single-chemical like vanilla)
          |- heat:      NO buffer - per-node VariableHeatCapacitor; network drives 2-phase simulate pass
          |- items:     transit entries live on nodes; network supplies routing (BFS)
          +- AcceptorCache: Map<pos, EnumMap<Direction, per-type BlockCapabilityCache>>  (built ONCE, shared by all types)

FusedPipeRegistry: static; ServerTickEvent.Post -> process joins/splits -> tick networks
```

Per-tick pipeline per network (server post): pull sides -> buffers -> fair-split emission
(`EmitUtils`) per enabled function -> heat simulate pass -> advance item transit & deliver.
Disabled functions are skipped everywhere (no caches, no NBT, no capability).

## 2. Files

All under `src/main/java/io/aduhtkjm/mekanismheated/`.

**content/fusedpipe/**
1. `FusedFunction.java` — enum `ENERGY, FLUID, CHEMICAL, HEAT, ITEM`; maps to vanilla tier enums
   (`CableTier`...`TransporterTier`) for capacity/pull-rate lookups.
2. `FusedPipeConfig.java` — `EnumMap<FusedFunction, ITier>` (absent = disabled); NBT (de)serialization;
   validation/clamping.
3. `FusedPipeNode.java` — per-position state: back-ref to tile, shared `ConnectionType[]`,
   heat capacitor, item queue, per-node saved shares (energy long / FluidStack / ChemicalStack).
4. `FusedNetwork.java` — UUID identity; node set + Long2ObjectMap index; merge (`adoptFrom`) /
   split (re-BFS components on removal); acceptor cache with invalidation listeners; `tick()` per
   function; save-share split/collect across nodes.
5. `FusedPipeRegistry.java` — static lifecycle: track/untrack nodes, pending joins, orphan re-BFS,
   chunk ticket handling (simplified), client mirror map; NeoForge bus subscriber.
6. `routing/ItemRouter.java` — BFS to reachable acceptors with prediction; delivery delay =
   distance x ticks-per-hop. No colors/round-robin v1.
7. `capability/FusedItemInsertHandler.java` — insert-only IItemHandler facade creating transit
   entries (analogous to `CursedTransporterItemHandler`).

**tile/, block/, item/**
8. `tile/TileEntityFusedPipe.java extends CapabilityTileEntity implements ISidedConfigurable,
   IAlloyInteraction` — node created in ctor; lifecycle fan-out; sneak-right-click cycles shared
   side config; NBT save/load (config + node data incl. shares); capability resolvers registered in
   ctor gated by insert/extract predicates.
9. `block/BlockFusedPipe.java` — centered-cube shape v1; neighborChanged routing; playerDestroy
   drops item with preserved NBT; pushReaction(BLOCK).
10. `item/ItemBlockFusedPipe.java extends ItemBlockTooltip` — tooltip lists enabled functions +
    tiers; writes default config NBT if absent.

**registries/ + client/**
11. Register in existing `ModBlocks` / `ModTileEntityTypes` / `ModItems` / creative tab. BE type
    wiring: server ticker, CONFIGURABLE provider (`CapabilityTileEntity.capabilityProvider`),
    ALLOY_INTERACTION, `EnergyCompatUtils.addBlockCapabilities(builder)`, FLUID/CHEMICAL/HEAT
    providers, custom ITEM provider.
12. Client: own ModelProperty connection bits -> plain block model first; placeholder texture; lang.

## 3. Behavior details

- **Side config**: one `ConnectionType[6]` per tile shared by all functions. Cycle order matches vanilla.
- **Redstone reactive**: right-click toggles whole pipe; powered = no connections.
- **Heat**: copy `ITileHeatHandler.simulate()` math onto the node; conduction targets = adjacent
  handlers via cache (other fused nodes' capacitors + vanilla conductors + machines), ambient loss included.
- **Items**: enter at any node (machine push or PULL side); router finds best destination among cached
  acceptors (predicted capacity); stack hops with distance-proportional delay; delivered via
  `addToInventory`. In-transit stacks drop on break. No movement rendering.
- **Persistence**: network buffers redistributed equally into node shares at save/unload, re-summed on load.
- **Chemical parity**: dump tank as radiation on last-node destruction like vanilla.
- **NBT tiers**: commands/anvil for now; GUI/crafting later.

## 4. Phases

1. **A — Skeleton**: files 1-3, 8-12 minus transmission; placeable block, NBT round-trip, tooltips.
   Verify: place/break/reload preserves config.
2. **B — Graph + energy**: registry, network merge/split, acceptor cache, energy pull/emit via
   `EmitUtils`. Verify: generator -> fused -> machine; merge/split correctness; FE interop both ways.
3. **C — Fluid + chemical**: buffers, single-type lock, boundary interop with pipes/tubes.
4. **D — Heat**: capacitor + simulate pass; Fuelwood Heater <-> fused <-> machine chain.
5. **E — Items**: router + insertion handler + transit lifecycle; routing, inventory-full deferral,
   drop-on-break.
6. **F — Polish**: share persistence across unload/reload, redstone edge cases, perf pass, game tests.

## 5. Risks / notes

- **Boundary latency** with vanilla pipes is inherent (registry won't merge non-TET tiles).
- `DynamicStrictEnergyHandler`/manager ctor signatures assumed public — verify first thing in
  Phase B (fallback: thin hand-rolled resolver ~60 lines).
- Item routing without visual stacks may confuse players — mitigate later with particles/debug overlay.
- Estimated ~2.5-3k LOC total; Phase B is the architectural risk hotspot.

## 6. Key reference points in Mekanism source

- Transmitter base: `mekanism/common/content/network/transmitter/Transmitter.java`
- Buffered base: `.../BufferedTransmitter.java`; concrete: `UniversalCable`, `MechanicalPipe`,
  `PressurizedTube`, `ThermodynamicConductor`, `LogisticalTransporter(Base)`
- Networks: `mekanism/common/content/network/{EnergyNetwork,FluidNetwork,ChemicalNetwork,HeatNetwork,InventoryNetwork}.java`
- Graph machinery: `mekanism/common/lib/transmitter/{DynamicNetwork,DynamicBufferedNetwork,TransmitterNetworkRegistry,ConnectionType,TransmissionType}.java`
- Acceptors: `mekanism/common/lib/transmitter/acceptor/*` (`AcceptorCache`, `EnergyAcceptorCache`)
- Tile base: `mekanism/common/tile/transmitter/TileEntityTransmitter.java`; capability plumbing:
  `mekanism/common/tile/base/CapabilityTileEntity.java`
- Distribution: `mekanism/common/lib/distribution/{Target,SplitInfo,EmitUtils}` (`EmitUtils` in util)
- Storage: `mekanism/common/capabilities/{energy/VariableCapacityEnergyContainer,fluid/VariableCapacityFluidTank,chemical/VariableCapacityChemicalTank,heat/VariableHeatCapacitor}`
- Energy compat: `mekanism/common/integration/energy/EnergyCompatUtils.java`
- Tiers: `mekanism/common/tier/{CableTier,PipeTier,TubeTier,ConductorTier,TransporterTier}.java`
- Item insertion helper: `mekanism/common/lib/inventory/TransitRequest.java`
