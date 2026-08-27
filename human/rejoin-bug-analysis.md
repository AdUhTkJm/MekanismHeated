# Fused Pipe Network Rejoin Bug — Analysis

## Symptom

After leaving and rejoining a world (or restarting a dedicated server), fused pipe networks
stop functioning. Buffers appear empty; energy/fluid/chemical/heat/items do not flow.

## What was investigated

Three hypotheses were explored and two code fixes were applied. Neither fully resolves the
bug, suggesting the root cause lies elsewhere.

---

## Hypothesis 1: Shares not persisted on auto-save (FIXED)

### Finding

During normal operation, the network's buffer lives in `FusedNetwork`'s internal containers
(`energyContainer`, `fluidTank`, `chemicalTank`, `heatCapacitor`, `itemBuffer`). The
per-node `savedEnergy`, `savedFluid`, etc. fields are **zero** — they are only populated
by `distributeSharesToNodes()`, which is called in two places:

1. `TileEntityFusedPipe.onChunkUnloaded()` — per-node chunk unload
2. `FusedPipeRegistry.onServerStopping()` — server shutdown

Neither of these runs during a normal **auto-save**. Minecraft's auto-save calls
`saveAdditional()` on tiles while the server is still running. At that point the shares
are 0, so the saved NBT contains zeros for all buffer contents.

If the server then crashes (or the player force-quits), `onServerStopping` never fires,
the final world-save never writes the distributed shares, and **all buffer contents are
lost**.

### Fix applied

`TileEntityFusedPipe.saveAdditional()` now calls `network.validateSaveShares(node)`
before writing NBT. `FusedNetwork.validateSaveShares()` flushes the buffer to every
node's saved-share fields (with a per-tick guard to avoid redundant work). This mirrors
Mekanism's `DynamicBufferedNetwork.validateSaveShares()` pattern.

### Why this alone doesn't fix the reported symptom

For a **clean** leave/rejoin (no crash), `onServerStopping` **does** fire, shares **are**
distributed, and the world-save **does** write them. The saved NBT should contain correct
values. So this fix addresses crash-recovery, but the user reports the bug on normal
rejoin.

---

## Hypothesis 2: Capability cache not invalidated after network formation (FIXED)

### Finding

NeoForge caches the result of `BlockCapability` queries. When a machine first queries a
pipe's capability (e.g., energy handler) **before** the network has formed, the provider
returns `null` and that null is cached. When the network later forms (in
`ServerTickEvent.Post`), the pipe's `getNetwork()` is no longer null, but the **cached
null** persists until something explicitly invalidates the capability.

The existing code never called `invalidateCapabilities()` (or the Mekanism equivalent
`invalidateTransmittedCapabilities()`) when a node joined or left a network. Adjacent
machines therefore continued to see `null` capabilities even after the network was fully
formed.

### Fix applied

`FusedPipeNode.setNetwork()` now calls `tile.invalidateTransmittedCapabilities()` when
the network reference changes. This clears NeoForge's capability cache for all
transmitted capability types (energy, fluid, chemical, heat, item) and notifies the
level so adjacent blocks re-query on next access.

### Why this alone doesn't fix the reported symptom

If the network **is** formed correctly and capabilities **are** invalidated, machines
should see valid handlers. The fact that the bug persists suggests either:

- The network is not forming correctly (see Hypothesis 3), or
- The capability invalidation triggers correctly but something else prevents flow

---

## Hypothesis 3: BFS orphan-assignment fails to connect all nodes (NOT FIXED — likely root cause)

### The lifecycle on world load

1. Chunks load. For each tile, `loadAdditional()` reads saved shares from NBT.
2. `clearRemoved()` is called. On server side, `markJoined = true`.
3. On the **first tile tick**, `onUpdateServer()` sees `markJoined`, calls
   `onWorldJoin()` which sets `loaded = true` and calls
   `FusedPipeRegistry.trackOrphan(node)`.
4. `ServerTickEvent.Post` fires: `disperseNetworks()`, `assignOrphans()`,
   `tickNetworks()`.

### The BFS in `assignOrphans()`

`OrphanPathFinder.find()` flood-fills from a start orphan through adjacent fused pipes.
For each neighbor:

```java
TileEntityFusedPipe neighborTile = WorldUtils.getTileEntity(..., pos.relative(side));
if (neighborTile == null || !neighborTile.isLoaded()) {
    continue;  // ← PROBLEM 1
}
FusedPipeNode neighbor = neighborTile.getNode();
if (!neighbor.isValid()) {
    continue;  // ← PROBLEM 2
}
FusedNetwork neighborNetwork = neighbor.getNetwork();
if (neighborNetwork != null) {
    networksFound.add(neighborNetwork);  // found existing network, don't traverse
} else if (trackedNodes.contains(neighbor) && connectedNodes.add(neighbor)) {
    queue.add(neighbor);  // ← PROBLEM 3
}
```

### Problem 1: `neighborTile.isLoaded()` check

`isLoaded()` returns the `loaded` field, which is only set to `true` in
`onWorldJoin()` on the **first tick**. If a neighbor's chunk has loaded but its tile
hasn't ticked yet (e.g., it loaded one tick later due to async chunk loading), the BFS
skips it entirely. This is **not** a problem if all tiles in loaded chunks tick before
`ServerTickEvent.Post`, which they should — but it depends on chunk loading order.

### Problem 2: `neighbor.isValid()` check

```java
public boolean isValid() {
    return !tile.isRemoved() && tile.isLoaded();
}
```

Same issue as Problem 1 — `isLoaded()` returns false for tiles that haven't had their
first tick yet.

### Problem 3 (MOST LIKELY ROOT CAUSE): `trackedNodes.contains(neighbor)` gate

This is the critical check. A neighbor is only traversed during BFS **if it is already
in `trackedNodes`**. A tile is added to `trackedNodes` only when `trackOrphan()` is
called, which happens in `onWorldJoin()` on the **first tile tick**.

Consider a line of 5 pipes at chunk border positions, all loading on world rejoin:

```
Chunk A:  [P1] [P2] [P3]  |  Chunk B:  [P4] [P5]
```

If Chunk A loads and ticks **before** Chunk B:

- Tick N: P1, P2, P3 tick → `trackOrphan()` → all in `trackedNodes` and `orphanNodes`
- Tick N `ServerTickEvent.Post`: `assignOrphans()` runs
  - BFS from P1 finds P2 and P3 (both tracked) → forms network {P1, P2, P3}
  - P4 and P5 are **not in `trackedNodes`** yet → BFS skips them
  - {P1, P2, P3} forms a network. P1 absorbs its saved share. P2 and P3 absorb theirs.
- Tick N+1: P4, P5 tick → `trackOrphan()` → now in `trackedNodes` and `orphanNodes`
- Tick N+1 `ServerTickEvent.Post`: `assignOrphans()` runs
  - BFS from P4: checks P3 (neighbor in Chunk A) → P3 **has a network** → adds to
    `networksFound`. P4 is added to P3's network.
  - BFS from P5: checks P4 (now in P3's network) → network found → P5 added too.

**This should eventually converge.** But there is a subtle failure mode:

### The convergence failure

When P4 joins P3's network via `assignOrphan()`:

```java
if (finder.networksFound.size() == 1) {
    network = finder.networksFound.iterator().next(); // uses existing network
}
for (FusedPipeNode connected : finder.connectedNodes) {
    network.addNode(connected);  // P4 joins P3's network
}
```

P4 calls `addNode()` on P3's network. `addNode()` calls `node.setNetwork(this)`.
This **invalidates capabilities** (after our fix). But does the network now include
P4's saved shares?

**Yes** — `addNode()` absorbs the node's saved shares. So P4's energy/fluid/etc.
should be pulled into the network buffer.

But here is the catch: **the BFS only finds P3 as a neighbor of P4**. It does NOT
traverse through P3 (because P3 already has a network — the BFS stops at networked
nodes and only records the network). So if P4 is adjacent to P3, and P3 is adjacent
to P2, and P2 is adjacent to P1, the BFS correctly merges P4 and P5 into the
existing network.

**This path should work.** So what else could be going wrong?

### Possible remaining causes to investigate

1. **Chunk loading order with async chunks**: If the BFS runs before ALL relevant
   chunks have loaded and tiled, it creates a **partial network**. The remaining nodes
   get absorbed on a subsequent tick. But during those intermediate ticks, the partial
   network is ticking with only some nodes — and some nodes might have already absorbed
   their shares into the partial network. When the remaining nodes join later, they
   bring their shares too, so the buffer should be complete. **This should be fine.**

2. **`onServerStopping` / `setRemoved` ordering**: `onServerStopping` calls
   `distributeSharesToNodes()` on all networks, then `reset()` clears all static sets.
   Then `setRemoved()` fires for tiles, calling `untrack()`, which does:
   ```java
   FusedNetwork network = node.getNetwork();
   if (network != null) {
       network.removeNode(node);
       ...
   }
   ```
   After `reset()`, `node.getNetwork()` still returns the old network object (because
   `reset()` doesn't clear node→network references). So `untrack()` calls
   `network.removeNode(node)` on a network that has already been distributed. This
   modifies the old network object but since no references to it remain in `networks`,
   this is harmless. **Probably not the cause.**

3. **Shares absorbed twice**: After `onServerStopping` distributes shares to nodes, the
   world saves. `saveAdditional()` now calls `validateSaveShares()`, which calls
   `distributeSharesToNodes()` again. But the buffer is already empty (cleared by
   `onServerStopping`'s distribute), so the second distribute is a no-op. On rejoin,
   `loadAdditional()` reads the saved shares. Then `assignOrphans()` forms networks,
   and `addNode()` absorbs the shares. This is correct. **Not the cause.**

4. **The network ticks before all nodes have joined**: On rejoin, `assignOrphans()`
   processes orphans in a snapshot. If P1 is processed first and forms {P1, P2, P3},
   then `tickNetworks()` runs and ticks {P1, P2, P3} while P4 and P5 are still orphans.
   On the next tick, P4 and P5 join. The shares from P4 and P5 are absorbed into the
   network. But during the intermediate tick, the network only had P1-P3's shares and
   was already pulling/emitting. If P1's machine sent energy to the network, that energy
   is now in the buffer. When P4 joins, it brings its shares. The total buffer should
   be correct. **This should be fine.**

5. **The `loaded` flag never becomes `true` for some tiles**: If a tile's `clearRemoved()`
   is never called (e.g., due to a NeoForge/Minecraft lifecycle issue), `markJoined`
   stays false, `onWorldJoin()` is never called, `loaded` stays false, and the tile
   is never tracked. The BFS would skip it (via `isLoaded()` check). The tile would
   never join a network. **Worth checking whether `clearRemoved()` is reliably called
   on all tiles after world load.**

6. **A subtle NeoForge issue**: `ServerTickEvent.Post` might not fire on the first tick
   after world load if the server's tick rate manager returns `runsNormally() == false`
   during initialization. The `onServerTickPost` method returns early in that case:
   ```java
   if (!event.getServer().tickRateManager().runsNormally()) {
       return;
   }
   ```
   If this guard triggers on the first tick, `assignOrphans()` never runs, and no
   networks are formed. **Worth adding logging to confirm.**

---

## Recommended next debugging steps

1. **Add debug logging** in `onServerTickPost` to confirm it fires on the first tick
   after world load, and that `orphanNodes` is non-empty.

2. **Add debug logging** in `assignOrphans` to confirm the BFS finds all expected
   neighbors and that networks are formed with the correct node count.

3. **Check `tickRateManager().runsNormally()`** — if it returns false on the first tick,
   that's the bug.

4. **Check whether `clearRemoved()` is called on all tiles** after world load. If some
   tiles never get `markJoined = true`, they'll never be tracked.

5. **Check chunk loading order** — if the pipe network spans multiple chunks, verify that
   all chunks load and tile before `ServerTickEvent.Post`. Add logging in
   `onWorldJoin()` to see which tiles register and when.

6. **Compare with Mekanism's approach**: Mekanism uses `ChunkTicketLevelUpdatedEvent`
   (which fires when a chunk's ticket level crosses `MAX_VIEW_DISTANCE`) to trigger
   chunkAccessibilityChange, which sets the `loaded` flag and registers orphans. This
   is more deterministic than relying on block entity tick ordering. The fused pipe
   code does NOT use this event — it relies entirely on `clearRemoved()` → `markJoined`
   → first-tick `onWorldJoin()`.
