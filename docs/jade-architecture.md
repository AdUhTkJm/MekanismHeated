# Jade Architecture Overview

Source: `mek/Jade/src/main/java/snownee/jade`

Jade is a fork of Waila: a client-side tooltip overlay that shows information about the block/entity you are looking at. Core loop: every tick, ray-trace the target → build an accessor → gather components from registered plugins → build an element tree → render it on the HUD.

## 1. Plugin system

### Plugin = annotation + interface

A plugin is a class annotated `@WailaPlugin` (`api/WailaPlugin.java`) implementing `IWailaPlugin` (`api/IWailaPlugin.java`), which has two entry points:

- `register(IWailaCommonRegistration)` — common side: server data providers + storage/energy/fluid/progress extension providers
- `registerClient(IWailaClientRegistration)` — client side: tooltip component providers, icon providers, config options, callbacks

The annotation's `value` is an optional required modid; the plugin is skipped if that mod isn't loaded.

### Discovery

Happens at load time in `CommonProxy.loadComplete(FMLLoadCompleteEvent)` (`util/CommonProxy.java:488`):

1. Scans every mod's jar-scan data (`ModList.get().getAllScanData()`) for `@WailaPlugin` annotations.
2. Filters out plugins whose required modid is not loaded.
3. For each class: instantiate it, open a registration session (`startSession()`), call `plugin.register(common)` and (client only) `plugin.registerClient(client)`, then `endSession()`.

Sessions (`impl/ClientRegistrationSession.java`, `impl/CommonRegistrationSession.java`) just buffer registrations in lists and flush them into the singleton registries `WailaClientRegistration` / `WailaCommonRegistration` in `end()`.

### What a plugin registers

- **`IComponentProvider<BlockAccessor/EntityAccessor>`** via `registerBlockComponent` / `registerEntityComponent` (and `...Icon` variants that override the tooltip's icon). This is the main plugin unit: it has a `uid` (ResourceLocation) and implements `appendTooltip(ITooltip, accessor, config)`.
- **`IServerDataProvider`** (common side, keyed by block *or* block-entity class) — runs on the server to fill a `CompoundTag` that gets synced back to the client.
- **`addConfig(uid, ...)`** options (bool/enum/string/int/float), `hideTarget`, `usePickedResult`.
- **Prioritized callbacks** (`JadeBeforeRenderCallback`, `JadeAfterRenderCallback`, `JadeRayTraceCallback`, `JadeTooltipCollectedCallback`, `JadeBeforeTooltipCollectCallback`, `JadeItemModNameCallback`), stored in `CallbackContainer`s (`impl/CallbackContainer.java`).

### Storage & ordering

- Providers live in `HierarchyLookup`s (`impl/lookup/HierarchyLookup.java:31`) — a multimap of class → provider. Lookup for a target walks the **class hierarchy upward** (e.g. `MekanismFurnaceBlockEntity` → `TileComponentEnergized` → `BlockEntity` → `Block`), so a provider registered to a base class applies to all subclasses. Results are cached per class and sorted by priority.
- Priority comes from each provider's uid via `PriorityStore` (`impl/PriorityStore.java`), which is user-configurable through `jade/sort-order.json` — players can reorder providers. Sub-provider uids are sorted right after their primary key.
- Every non-required provider automatically gets a config toggle (`WailaClientRegistration.tryAddConfig`, `impl/WailaClientRegistration.java:310`), shown in Jade's config screen. `isRequired()` providers can't be disabled.
- Server data providers are id-mapped so packets can reference them by id (`HierarchyLookup.idMapper`).

## 2. Per-tick pipeline (how registered plugins get used)

`ClientTickEvent.Post` → `WailaTickHandler.tickClient()` (`overlay/WailaTickHandler.java:83`):

1. **Ray trace** the target (block or entity) via `RayTracing.INSTANCE`, then build an immutable `BlockAccessor`/`EntityAccessor` snapshot (level, player, block state, block entity, hit result, server-data tag).
2. Run `JadeRayTraceCallback`s — plugins can swap the accessor (e.g. Jade's own camouflage logic in `JadeClient.builtInOverrides`).
3. `ObjectDataCenter.set(accessor)` (`impl/ObjectDataCenter.java:27`): if the target changed, cached server data is invalidated. If server-connected, it asks the accessor handler which `IServerDataProvider`s apply and, rate-limited to 250 ms, sends a `RequestBlockPacket` (`network/RequestBlockPacket.java` — providers serialized by id-mapper id). The server runs each provider's `appendServerData` against the real block entity and replies with `ReceiveDataPacket` (a `CompoundTag`), merged into the accessor. This is how plugins read server-authoritative data (ME storage, energy, etc.).
4. **Gather components**: `BlockAccessorClientHandler.gatherComponents` (`impl/BlockAccessorClientHandler.java:88`) iterates the hierarchy-lookup result for the block's class (priority-sorted, filtered by enabled-config), calling `provider.appendTooltip(tooltip, accessor, config)`. Each element added is tagged with the current provider's uid, so later providers can find/modify/remove other providers' lines via `tooltip.get(tag)` / `tooltip.remove(tag)` (`impl/Tooltip.java`). In LITE display mode, providers with |priority| > 5000 are suppressed unless the details key is held.
5. Wrap everything in a `BoxElement` (`impl/ui/BoxElement.java`) — the **root UI element** — tagged `jade:root`, set its icon (from icon providers), then run `JadeTooltipCollectedCallback`s so plugins can edit the finished element tree. The result is stored as `WailaTickHandler.rootElement`.

## 3. UI rendering

`RenderGuiEvent.Post` (no GUI open) or `ScreenEvent.Render.Pre/Post` (for screens where Jade chooses to draw before/after the GUI) → `OverlayRenderer.renderOverlay478757` (`overlay/OverlayRenderer.java:110`):

1. **Gating**: display mode (TOGGLE / HOLD_KEY), boss-bar overlap, GUI visibility, and a "linger" tooltip with fade-out delay when the target is lost. Alpha animation optional.
2. `renderOverlay`: `root.updateRect(rect)` computes the screen position (clamped to screen edges, with an animated "chase" toward the expected rect); `JadeBeforeRenderCallback`s can cancel the whole draw.
3. Translate the pose to the rect (z = 1, or z = 100 when drawn over a GUI), apply scale, then `root.render(guiGraphics, ...)`.
4. `BoxElement.render` (`impl/ui/BoxElement.java:133`) draws, in order: the box background via a `BoxStyle` (solid/gradient border, round corners, padding — all theme-driven), the box progress bar, the icon, then each `Tooltip.Line`. A `Line` is a row of `IElement`s rendered left→right (or right-aligned). Element types live in `impl/ui/` (TextElement, ItemStackElement, ProgressElement, FluidStackElement, CompoundElement for nested sections, spacers, health/armor bars, ...). `IDisplayHelper`/`DisplayHelper` do the actual themed text/box drawing, and `IThemeHelper` supplies the `Theme` (colors, fonts, padding) which is user-configurable and reloadable.
5. `JadeAfterRenderCallback`s run last (this is how e.g. the breaking-progress bar is added).

## 4. Supporting pieces

- **Config**: `PluginConfig` holds the per-uid entries (bool/enum/string/int/float) with listeners for live updates; main settings in `WailaConfig`; screens in `gui/` (`HomeConfigScreen`, `PluginsConfigScreen`).
- **Views**: `api/view/*` (ItemView, EnergyView, FluidView, ProgressView) — standardized sub-panels; a server-side `IServerExtensionProvider` serializes data for one, a client-side `IClientExtensionProvider` renders it, so e.g. tank contents or energy can be displayed uniformly.
- **Networking**: only the packets in `network/`; the whole client/server split is client-raytraces → requests → server-computes-tag → client-renders.
- **Built-in plugins** (in the Jade jar): `CorePlugin` (name, mod name, distance, block face), `VanillaPlugin`, `UniversalPlugin`, `HarvestPlugin`, `AccessibilityPlugin`, `DebugPlugin`.

## Relevance to this project

Our own addon uses this exact pattern in `src/main/java/io/aduhtkjm/mekanismheated/integration/jade/MekanismHeatedJadePlugin.java` (`@WailaPlugin`, server data provider + component provider + removal of Mekanism's built-in lines). The flow we hit: our `appendTooltip` is called during step 4 above with our uid-tagged elements, then rendered as lines of the box in step 3.
