# Temperature Controller — spec & implementation plan

*Written 2026-09-12 after a deep dive into the Cooler (`tile/TileEntityCooler`,
`capabilities/energy/CoolerEnergyContainer`, `client/gui/machine/GuiCooler`), Mekanism's
`TileEntityResistiveHeater` / `Attributes.AttributeRedstoneEmitter` / `Capabilities.HEAT`,
and the per-chunk ambient feature (`content/ambient/`).*

**Implementation status:** Phase 1 done (2026-09-12) — the expression engine in
`content/expression/` (nine files). Phases 2–6 not started. Verified standalone under WSL JDK 21 with a 167-check
harness; no build or test was run under `/mnt`.

The **Temperature Controller** is a single-block, enrichment-chamber-shaped machine that
reads the ambient temperature and the temperature of the heat capacitors of the six blocks
next to it, evaluates a user-written expression over those readings every tick, and pushes
the result out in one of two ways: as the energy/tick setting of adjacent Coolers and
Resistive Heaters, or as a redstone signal strength 0–15. Its front face has a 6×16 px
"window" whose pixels light up with the temperature.

---

## 1. Locked decisions

These were confirmed with the author before writing this spec.

| Question | Decision |
|---|---|
| Front strip geometry | **Use the art as drawn: 6 px wide, 16 rows tall.** The white rectangle in `textures/block/temperature_controller/front.png` is exactly `x = 5..10` (inclusive) for all `y = 0..15` — i.e. 6×16, not 8×16. Constants live in `TemperatureControllerRenderer`. |
| Energy-mode targets | **All six adjacent blocks.** Any neighbour that is a `TileEntityCooler` or `TileEntityResistiveHeater` is driven to the expression value; the other four/five neighbours are unaffected. The same six are the ones `north.T` … `down.T` read from. |
| Power / gating | **No energy buffer.** The controller is gated by redstone through Mekanism's normal redstone-control tab (`ITileRedstone` + `AttributeRedstone`), i.e. `canFunction()` / `isRedstoneActivated()`. It evaluates and writes only while the configured control mode is satisfied. |
| Strip colour ramp | **Fixed colour per row.** A row's colour depends only on its own height (row 0 = bottom = green → row 15 = top = red). Unlit rows are grey. The colour ramp is therefore fully visible only at the top of the temperature range. |

---

## 2. What already exists and is reused

| Need | Existing thing | Where |
|---|---|---|
| Brand-new Cooler/Resistive-Heater energy/tick write | `setEnergyUsageFromPacket(long joules)` on both | `tile/TileEntityCooler`, `mek .../TileEntityResistiveHeater` |
| Redstone output without a custom `Block` subclass | `Attributes.AttributeRedstoneEmitter<TILE>`; `BlockTile.getSignal` consults it; `isSignalSource` becomes true | `mek .../block/attribute/Attributes.java`, `block/prefab/BlockTile.java` |
| Ambient temperature (with chunk delta) | `HeatAPI.getAmbientTemp(LevelReader, BlockPos)` — already patched by `MixinHeatAPI`; use `tile.getAmbientTemperature(null)` or the static call | `mek .../api/heat/HeatAPI.java`, `mixin/MixinHeatAPI.java` |
| Neighbour block temperature | `level.getCapability(Capabilities.HEAT, neighbourPos, side.getOpposite())` → `IHeatHandler.getTotalTemperature()` | `mek .../common/capabilities/Capabilities.java:61` |
| Redstone gating | `TileEntityMekanism.canFunction()` → `isRedstoneActivated()` + the `AttributeRedstone` tab | `mek .../tile/base/TileEntityMekanism.java:1118` |
| Text input widget | `mekanism.client.gui.element.text.GuiTextField` (validator, enter handler, checkmark, right-click clear) | `mek .../client/gui/element/text/GuiTextField.java` |
| Expression charset validator | `mekanism.api.functions.CharPredicate` (write our own `EXPRESSION_CHARS`) | `mek .../common/util/text/InputValidator.java` |
| Energy unit conversion (matches the Cooler GUI) | `MekanismUtils.convertToJoules(long)` | `mek .../common/util/MekanismUtils.java:515` |
| Temperature display | `MekanismUtils.getTemperatureDisplay(value, TemperatureUnit.KELVIN, true)` | `GuiCooler` |
| BER precedent (rotation + baked model drawing) | `client/renderer/TileEntityShakerRenderer` — copy its `applyFacingTransform` / `getFacingRotation` helpers verbatim | `src/.../client/renderer/TileEntityShakerRenderer.java` |
| Wider GUI precedent | `CONTAINER_TYPES.custom(name, tileClass).offset(10, 0).build()` + `imageWidth += 20` | `ModContainerTypes`, `GuiThermalFractionationController` |
| Container sync of non-primitive data | `SyncableEnum`, `SyncableDouble`, `SyncableByteArray`, `SyncableBoolean` | `mek .../inventory/container/sync/` |

No mixins are required. No Mekanism classes need patching.

---

## 3. Block, item and assets

### 3.1 Block model

Copy Mekanism's enrichment-chamber model and swap only the front:

`assets/mekanismheated/models/block/temperature_controller.json`

```json
{
  "parent": "mekanism:block/machine",
  "textures": {
    "sides": "mekanism:block/enrichment_chamber/right",
    "front": "mekanismheated:block/temperature_controller/front",
    "west": "mekanism:block/enrichment_chamber/right",
    "east": "mekanism:block/enrichment_chamber/left",
    "south": "mekanism:block/enrichment_chamber/back",
    "up": "mekanism:block/enrichment_chamber/top",
    "down": "mekanism:block/enrichment_chamber/bottom"
  }
}
```

This is exactly `mekanism:block/enrichment_chamber` with `front` replaced. The other five
faces reuse Mekanism's textures directly — legal because Mekanism is a hard dependency and
already a runtime asset source for this mod (`mekanism:block/models/ports` is used by the
Cooler model).

`models/item/temperature_controller.json`:

```json
{ "parent": "mekanismheated:block/temperature_controller" }
```

### 3.2 Blockstate

Same shape as `cooler.json`/`enrichment_chamber.json`: `facing` × `active`, all eight
variants pointing at the one model (the `active` property exists because
`Attributes.ACTIVE_LIGHT` is a `Machine` default; as with `shaker.json` we simply do not
swap models for it). `active` is a plain indicator state and drives the light level 8.

### 3.3 Other assets / data

| File | Content |
|---|---|
| `loot_table/blocks/temperature_controller.json` | standard self-drop (copy `loot_table/blocks/thermal_casing.json` shape) |
| `recipe/crafting/temperature_controller.json` | **proposal** (needs sign-off, see §12): shapeless/shaped with `mekanism:enrichment_chamber`, `mekanismheated:thermal_casing`, `mekanism:advanced_control_circuit`, `mekanism:steel_casing` |
| `lang/en_us.json`, `lang/zh_cn.json` | see §9 |
| `ModItems.registerDisplayedItems` | add `output.accept(ModBlocks.TEMPERATURE_CONTROLLER);` |

### 3.4 Registration (`ModBlocks`)

```java
public static final Machine<TileEntityTemperatureController> TEMPERATURE_CONTROLLER_TYPE = MachineBuilder
      .createMachine(() -> ModTileEntityTypes.TEMPERATURE_CONTROLLER, ModLang.DESCRIPTION_TEMPERATURE_CONTROLLER)
      .withGui(() -> ModContainerTypes.TEMPERATURE_CONTROLLER)
      //Only the five attributes we actually use:
      //  ACTIVE_LIGHT (active state + light), STATE_FACING, SECURITY, REDSTONE, REDSTONE_EMITTER
      .without(AttributeInventory.class, AttributeUpgradeSupport.class, AttributeParticleFX.class, AttributeComparator.class)
      .with(new AttributeRedstoneEmitter<>((tile, side) -> tile.getRedstoneOutput()))
      .build();

public static final BlockRegistryObject<TemperatureControllerBlock, ItemBlockTooltip<TemperatureControllerBlock>> TEMPERATURE_CONTROLLER =
      BLOCKS.register("temperature_controller",
            () -> new TemperatureControllerBlock(TEMPERATURE_CONTROLLER_TYPE, BlockBehaviour.Properties.of().mapColor(MapColor.METAL)),
            (block, properties) -> new ItemBlockTooltip<>(block, true, properties));
```

- No `AttributeSideConfig`: the block has no item/fluid/energy/heat capability, so there
  is nothing to configure per side.
- No `withEnergyConfig`: there is no energy buffer (§1).
- `AttributeRedstoneEmitter.getRedstoneLevel(tile, side)` returns `tile.getRedstoneOutput()`,
  which is non-zero only in `REDSTONE` output mode and only while
  `canFunction()` + the expression evaluates successfully. `AttributeRedstoneEmitter` also
  makes `BlockTile.isSignalSource` true and `canConnectRedstone` true.
- `AttributeComparator` is dropped on purpose: `TileEntityMekanism.getRedstoneLevel()` is
  inventory-based and would always read 0 here, which is worse than not offering it.
  (See §12 open question 4 if you would rather have the comparator mirror the output.)

`TemperatureControllerBlock extends BlockTile<TileEntityTemperatureController, Machine<TileEntityTemperatureController>>`,
copied from `CoolerBlock` (same `FACING` constant, same `getStateForPlacement` override).
This matches `AttributeStateFacing`'s `PLAYER_LOCATION` placement, so `FACING` is the
direction the player is looking (the front face points away from the player).

---

## 4. The expression language

Implemented in a new package `content/expression/`. **It is deliberately free of both Minecraft and Mekanism types**
— its own `Side` enum instead of `net.minecraft.core.Direction`, its own `byIndex` instead of `IIncrementalEnum`, no
JetBrains annotations — so the whole package compiles and runs under a plain `javac` with no classpath. That is what
let Phase 1 be verified standalone (see §13). The tile does the mapping between `Side` and `Direction`.

### 4.1 Lexical structure

```
NUMBER   := [0-9]+ ( "." [0-9]+ )?
IDENT    := [A-Za-z_][A-Za-z0-9_]* ( "." [A-Za-z_][A-Za-z0-9_]* )*
OPERATOR := "+" | "-" | "*" | "/" | ">" | "<" | "!=" | "==" | "=" | "?" | ":" | "(" | ")"
```

- Whitespace (space, tab) is skipped between tokens.
- `.` is consumed as part of an identifier when it is followed by an identifier character
  — required for `north.T`. A `.` that is not part of a number or a qualified name
  (`foo.`, `foo..bar`, `.5`) is a lex error.
- `!` is only valid as part of `!=`. `=` and `==` are two spellings of the same operator.
- **No** scientific notation, hex literals, string literals, `&&`/`||`/`%`, or comments.
  All numbers are `double`.
- Because names may contain `.` and there are no user-defined variables, the lexer never
  needs to disambiguate a field access from a name: `north.T` is a single token.

### 4.2 Grammar (recursive descent, lowest precedence first)

```
expression     := ternary EOF
ternary        := comparison ( "?" expression ":" ternary )?      // right-associative
comparison     := additive ( (">" | "<" | "=" | "==" | "!=") additive )?
additive       := multiplicative ( ("+" | "-") multiplicative )*
multiplicative := unary ( ("*" | "/") unary )*
unary          := "-" unary | primary
primary        := NUMBER | IDENT | "(" expression ")"
```

Notes and deliberate choices:

- **Parentheses are supported** even though they were not in the original operator list.
  Without them `a ? b : c` cannot be grouped, and `1 + 2 * 3` is the only grouping the
  player gets. This is a strict superset of the requested language.
- Ternary binds loosest and nests to the right, so `a ? b : c ? d : e` is `a ? b : (c ? d : e)`.
- Comparisons are **non-associative**: `comparison` accepts at most one comparison
  operator, so `1 < 2 < 3` is a parse error rather than silently becoming `(1<2)<3 = 1`.
- Unary minus binds tighter than `*` and `/`, so `-2 * 3` is `(-2) * 3`. Repeated unary
  minus (`--T`) is accepted.
- IDENT resolution is delegated to a caller-supplied predicate, so the same parser is
  reusable and unknown names become parse errors with a column, not runtime surprises.
- The parser is recursive, so it carries two safety bounds in order to stay total for *any*
  input rather than merely for inputs under the length cap — see §4.7.

### 4.3 Values and operator semantics

| Operator | Semantics |
|---|---|
| `+ - * /` | IEEE-754 `double` arithmetic |
| `>` `<` | `1.0` if true, `0.0` if false |
| `=`, `==`, `!=` | **epsilon equality**: `abs(a-b) <= 1e-9 * max(1, abs(a), abs(b))`. Exact `==` on accumulated doubles is a trap (`T = 300` should work on a jungle biome); a relative epsilon makes it behave the way a player expects. `!=` is its negation. |
| `a ? b : c` | `a` is *true* when it is non-zero and not `NaN`. The untaken branch is **not** evaluated, which makes the ternary the way to guard a side read: `T > 500 ? north.T : 0` never touches the north neighbour unless the ambient temperature is above 500 K. (Guarding *inside* the condition does not work — `north.T > 0 ? … : …` still reads `north.T` to compute the condition, and errors if it is unreadable.) |

Result validity: the expression's value **must be finite**. `NaN` and `±Infinity`
(including every division by zero, since `x/0` is `±Infinity` in IEEE-754) are runtime
errors. This is the single, easy-to-explain rule that covers division by zero, `0/0`, and
overflow, so there are no special cases in the spec or the code.

### 4.4 Built-in variables

There are no user-defined variables and no assignment.

| Name | Value |
|---|---|
| `T` | ambient temperature at the controller's own position, in Kelvin, **including the per-chunk delta** (`HeatAPI.getAmbientTemp` is already patched by `MixinHeatAPI`, so this comes for free) |
| `north.T`, `south.T`, `east.T`, `west.T`, `up.T`, `down.T` | `IHeatHandler.getTotalTemperature()` of the block on that side of the controller |

Direction names are matched **case-sensitively** and spelled exactly as above (`north`,
`south`, `east`, `west`, `up`, `down`) because they double as `Direction.getName()` values
(used for error text). `T` is upper-case. Any other identifier is a parse error
(`unknown_variable`), reported with its column.

A side variable is a **runtime** error, not a parse error, when the neighbour at evaluation
time has no heat capability at all (no block entity, no `Capabilities.HEAT`, or
`getHeatCapacitorCount() == 0`) — this is exactly the case the request calls out ("the block
to read from doesn't contain any heat capacitor"). It is deliberately *not* a parse error
because the neighbour can change at any time and the expression text is unchanged.

### 4.5 Errors

```java
public enum ParseErrorKind {
    UNEXPECTED_CHARACTER,   // lexer: stray character
    UNEXPECTED_TOKEN,       // parser: token that cannot start/continue the current rule
    UNEXPECTED_END,         // parser: input ended too early (e.g. `T > ` or `T ? 2`)
    UNKNOWN_VARIABLE,       // identifier that is not a built-in
    TOO_DEEP,               // nesting past ExpressionParser.MAX_DEPTH
    TOO_LARGE               // more than ExpressionParser.MAX_NODES syntax nodes
}

public class ExpressionParseException extends Exception {
    ParseErrorKind kind();
    int column();        // 0-based offset into the source, for the caret
    String detail();     // the offending text, substituted into the lang message
}
```

Runtime failures surface as an enum instead of an exception so they can be synced cheaply:

```java
public enum ExpressionRuntimeError {
    NONE, NO_HEAT_CAPACITOR, RESULT_NOT_FINITE
}
```

The evaluator needs `Direction` only to build the error payload, so `content/expression/`
stays free of gameplay code through this injection point:

```java
@FunctionalInterface
public interface VariableResolver {
    /** @throws ExpressionRuntimeException when a name is legal but not currently readable. */
    double resolve(String name) throws ExpressionRuntimeException;
}
```

`ExpressionRuntimeException` carries `(ExpressionRuntimeError kind, @Nullable Direction side)`.

### 4.6 Worked examples

| Expression | Behaviour |
|---|---|
| `T` | outputs the ambient temperature (300 at plains default) |
| `T > 500 ? 200 : 0` | 200 FE/t once the chunk is above 500 K |
| `(north.T + south.T) / 2` | average of two neighbours' temperatures |
| `T > 100000 ? north.T : 5` | with an unreadable north neighbour still outputs **5** — the untaken branch is never evaluated |
| `north.T > 0 ? 1 : 0` | with an unreadable north neighbour this is a `NO_HEAT_CAPACITOR` runtime error, because the condition itself reads `north.T` |
| `west.T * 2` | runtime error while the west neighbour has no heat capacitor |
| `1 < 2 < 3` | parse error (`UNEXPECTED_TOKEN` at the second `<`) |
| `T +` | parse error (`UNEXPECTED_END` at column 3, i.e. one past the end) |
| `foo` | parse error (`UNKNOWN_VARIABLE` at column 0) |
| `1 + 1 + 1 + …` (thousands of terms) | parse error (`TOO_LARGE`), not a stack overflow — see §4.7 |

### 4.7 Safety bounds (the parser is total)

Because the controller evaluates player-supplied text on a server tick, `ExpressionParser` must be *total*: for any
`String` it either returns a tree or throws `ExpressionParseException`, and never an `Error`. Two constants enforce
that, and both were added after measurement rather than guessed:

| Constant | Value | Bounds | Failure |
|---|---|---|---|
| `MAX_DEPTH` | 512 | the parser's own recursion, which grows with nesting | `TOO_DEEP` |
| `MAX_NODES` | 2048 | the size of the tree it builds, and hence the recursion depth of `Expr.evaluate` over that tree | `TOO_LARGE` |

`MAX_NODES` is not redundant: a *flat* chain such as `1 + 1 + 1 + …` needs no parser recursion beyond a constant
amount, but builds a deeply left-nested tree, and evaluation recurses over the tree. Measured on JDK 21, 100 000 such
terms overflow the stack during evaluation with `MAX_NODES` removed, while `MAX_DEPTH` alone does not catch it.

Reference points, measured: 127 nested parentheses (255 characters, the worst shape the default 256-character
cap allows) and 255 unary minuses (256 characters) both parse; 128 nested parentheses consume 256 depth units
because a parenthesised level costs two (one `ternary` plus one `unary`). Both constants sit far above anything the
configured length cap can produce, so they should never fire in normal play.

---

## 5. Tile entity behaviour

`TileEntityTemperatureController extends TileEntityMekanism` — the same base as
`TileEntityCooler`, and like the Fuelwood Heater it is a *leaf* of the hierarchy that
overrides only the hooks it needs. It owns no heat capacitors, no energy, no inventory.

### 5.1 State

```java
public enum OutputMode implements IIncrementalEnum<OutputMode> { REDSTONE, ENERGY }
```

| Field | Sides | Purpose |
|---|---|---|
| `String expression` | server writes, client mirrors | last submitted expression; `""` means "never configured" |
| `OutputMode outputMode` | server writes, client mirrors | default `REDSTONE` |
| `Expr compiled` + `String compiledFrom` | server | parse tree, recompiled only when the text changes |
| `ExpressionRuntimeError runtimeError` + `Direction errorSide` | server, synced | last runtime failure, for the GUI |
| `double lastOutput` | server, synced | last evaluated value (GUI "Output" line) |
| `int redstoneOutput` | server, read by the block | 0–15, and `0` in `ENERGY` mode |
| `byte displayLevel` | **both** (reduced update tag) | 0–16 rows lit on the front face |

Derived, not stored: `working = runtimeError == NONE && compiled != null && canFunction()`.

### 5.2 Server tick (`onUpdateServer`)

```
boolean needsPacket = super.onUpdateServer();

// 1. (Re)compile if the source text changed. Parse errors are NOT sent as text: the
//    client re-parses the same string locally with the shared parser (§6.3).
if (!expression.equals(compiledFrom)) recompile();   // sets compiled == null on failure

// 2. Evaluate. Anything that makes the controller "not working" collapses to 0 here;
//    whether that 0 is then acted on depends on the mode (see emit()).
double value = 0;
runtimeError = NONE;
errorSide = null;
if (canFunction()) {                        // isRedstoneActivated(): DISABLED/HIGH/LOW/PULSE
    if (compiled != null) {                 // empty or unparsable -> value stays 0
        try {
            value = compiled.eval(resolver);
        } catch (ExpressionRuntimeException e) {
            runtimeError = e.kind();
            errorSide = e.side();
        }
    }
}
lastOutput = value;

// 3. Emit.
emit(value);

// 4. Front-strip level (independent of working/error state; see §12 open question 7).
byte level = computeDisplayLevel(tile.getAmbientTemperature(null));
if (level != displayLevel) {
    displayLevel = level;
    needsPacket = true;
}
return needsPacket;
```

`emit(double value)`:

```java
private void emit(double value) {
    if (outputMode == OutputMode.REDSTONE) {
        // Runs unconditionally so that closing the redstone gate (or hitting an error)
        // actively drops the signal with a neighbour update rather than leaving it latched.
        int level = clampToInt(value, 0, 15);
        if (level != redstoneOutput) {
            redstoneOutput = level;
            level().updateNeighborsAt(getBlockPos(), getBlockState().getBlock());
            markForSave();
        }
    } else if (canFunction() && runtimeError == NONE && compiled != null) {
        // ENERGY mode only writes while the gate is open and the expression is clean;
        // on gated/empty/parse/runtime failure the neighbours are left untouched
        // (§12 open question 1).
        long joules = MekanismUtils.convertToJoules(
              clampToLong(value, 0, Config.TemperatureController.MAX_ENERGY_OUTPUT.get()));
        for (Direction side : EnumUtils.DIRECTIONS) {
            BlockPos pos = worldPosition.relative(side);
            if (level().getBlockEntity(pos) instanceof TileEntityCooler cooler) {
                if (cooler.getEnergyContainer().getEnergyPerTick() != joules) {
                    cooler.setEnergyUsageFromPacket(joules);
                }
            } else if (level().getBlockEntity(pos) instanceof TileEntityResistiveHeater heater) {
                // Mekanism's own machine, can't implement our interface:
                if (heater.getEnergyContainer().getEnergyPerTick() != joules) {
                    heater.setEnergyUsageFromPacket(joules);
                }
            }
        }
    }
}
```

Details that matter:

- **`!=` guards before writing.** `setEnergyUsageFromPacket` calls `markForSave()`
  unconditionally, so an unguarded per-tick write would mark the chunk dirty (and the
  neighbour's tile) 20 times a second forever. Reading the neighbour's current
  `getEnergyPerTick()` first is free and avoids all of that churn.
- **`redstoneOutput` is forced to 0 in `ENERGY` mode**, because the `redstoneOutput`
  field is only ever written inside the `REDSTONE` branch; the emitter therefore reports
  nothing while the controller is driving coolers/heaters.
- `clampToInt`/`clampToLong` use `Math.round` (half-up) then clamp. A non-finite value
  can never reach here because evaluation rejects it first.
- Writing into a neighbour is intentionally *not* gated on the neighbour's own redstone
  control; that is the neighbour's business, exactly as if a player had typed the number
  into its GUI.

### 5.3 Ambient temperature on the client

`getAmbientTemperature` / `HeatAPI.getAmbientTemp` does **not** include the per-chunk delta
on the client (`ChunkAmbientTemperature.getDelta` returns 0 for anything that is not a
`ServerLevel`). Therefore the strip must not be computed on the client: the level is
computed on the server and synced (§5.1, §6.4).

### 5.4 Persistence and the config card

- `saveAdditional` / `loadAdditional`: `expression` (`"Expression"`, string),
  `outputMode` (`"OutputMode"`, `OutputMode.name()`).
- `getReducedUpdateTag` / `handleUpdateTag`: `displayLevel` (`"DisplayLevel"`, byte).
  `getUpdateTag` delegates to the reduced tag in `TileEntityUpdateable`, so a chunk-send
  also carries the current level.
- `getConfigurationData` / `setConfigurationData`: copy `expression` + `outputMode` so a
  config card clones a controller's setup (same idea as the Cooler copying `ENERGY_USAGE`).
- `addContainerTrackers(MekanismContainer)`: see §6.4.

---

## 6. GUI

`GuiTemperatureController extends GuiMekanismTile<TileEntityTemperatureController, MekanismTileContainer<TileEntityTemperatureController>>`,
mirroring `GuiCooler`'s structure with the extra mode button and a wider window.

Container type (in `ModContainerTypes`), following the fractionation controller precedent
for a widened GUI:

```java
public static final ContainerTypeRegistryObject<MekanismTileContainer<TileEntityTemperatureController>> TEMPERATURE_CONTROLLER =
      CONTAINER_TYPES.custom("temperature_controller", TileEntityTemperatureController.class).offset(10, 0).build();
```

There are no tile slots, so `MekanismTileContainer` adds only the player inventory.
`GuiTemperatureController` sets `imageWidth += 20; inventoryLabelX += 10; dynamicSlots = true;`.

### 6.1 Layout (positions indicative, final values tuned while implementing)

```
        Title text                                        [security tab]
 ┌──────────────────────────────────────────────────────┐
 │  ┌────────────────────────────────────────────────┐  │  y = 19
 │  │ Ambient: 812.4 K                               │  │
 │  │ Output:  420 FE/t                              │  │  GuiInnerScreen
 │  │ Status:  Working                               │  │  (48, 19, 130, 40)
 │  └────────────────────────────────────────────────┘  │
 │  [ Energy ]  ┌────────────────────────────────────┐  │  y = 61
 │   mode btn   │ T > 500 ? 200 : 0                  │  │  GuiTextField
 │   (8,61)     └────────────────────────────────────┘  │  (48, 61, 130, 12)
 │  Inventory                                           │
 │  [ ][ ][ ] …                                         │
 └──────────────────────────────────────────────────────┘
   [upgrade]                                              [redstone tab]
```

The generic tabs added by `GuiMekanismTile.addGenericTabs()` and the redstone tab give
the player the standard redstone-control modes for free (§1), so there is deliberately no
custom "requires redstone" toggle in this GUI.

Widgets:

| Widget | Details |
|---|---|
| `GuiInnerScreen(this, 48, 19, 130, 40, lines)` | three lines: `AMBIENT`, `OUTPUT`, `STATUS` (see §6.3). `clearFormat()` + a per-line colour chosen manually, because the status line colour is dynamic. |
| `MekanismButton(this, 8, 61, 36, 14, modeLabel, click)` | mode toggle. `setMessage(...)` is refreshed each frame from `tile.getOutputMode()`; `setTooltip` explains what the mode does. Click → `PacketSetTemperatureControllerMode`. |
| `GuiTextField(this, 48, 61, 130, 12)` | `setMaxLength(Config.TemperatureController.MAX_EXPRESSION_LENGTH.get())`, `setInputValidator(EXPRESSION_CHARS)`, `setEnterHandler(this::submit)`, `addCheckmarkButton(NORMAL, this::submit)`, `setBackground(BackgroundType.DEFAULT)`. **No `setInitialFocus`** — grabbing focus would swallow the player's keyboard shortcuts for a machine that has nothing else to type into; the player clicks the field to edit. Right-click clears (free, from `ClearingEditBox`). |

`EXPRESSION_CHARS` = letters, digits, and `+ - * / > < = ! ? : ( ) . _` and space:

```java
private static final CharPredicate EXPRESSION_CHARS = InputValidator.LETTER_OR_DIGIT.or(
      InputValidator.from('+', '-', '*', '/', '>', '<', '=', '!', '?', ':', '(', ')', '.', '_', ' '));
```

This is a *keyboard* filter only; the real validation is the parser, and a paste already
goes through the same predicate in `GuiTextField.keyPressed`.

### 6.2 Submitting

`submit()` sends the current text verbatim (even if empty or syntactically invalid) and
leaves focus in the field so the player can keep editing; the server stores it, tries to
compile it, and reports the outcome. The text is *not* cleared, unlike the numeric
`GuiCooler` field, because re-typing a long expression after a typo would be hostile.

The GUI mirrors `tile.getExpression()` back into the field only when (a) the field is not
focused and (b) the synced value differs from what is displayed. Condition (a) is what
stops a slow server round-trip from clobbering characters the player has typed since
submitting, and (b) is what keeps two players' GUIs in sync.

### 6.3 Status line

`Status` is assembled on the client from three inputs: the synced expression string
(parsed locally with the shared parser), the synced `ExpressionRuntimeError` (+ side), and
`tile.isRedstoneActivated()`. Because the client already has the expression string, a
**syntax error is reported locally with the client's own language** — no error text is sent
over the wire. Only runtime errors need a code, because only the server can see the world.

The redstone gate is client-accurate for free: `TileEntityMekanism.addContainerTrackers`
already tracks `isPowered()` and `wasPowered()` (`SyncableBoolean` → `redstone` /
`redstoneLastTick`), which is everything `isRedstoneActivated()` reads. Our override must
call `super.addContainerTrackers(container)` before adding its own trackers.

| State | Line | Colour | Field text colour |
|---|---|---|---|
| `EMPTY` | `No expression set` | yellow | normal |
| parse error | `Syntax error at column N: <reason>` | red | red |
| runtime error `NO_HEAT_CAPACITOR` | `No heat capacitor on the north side` | red | red |
| runtime error `RESULT_NOT_FINITE` | `Result is not a finite number` | red | red |
| not `canFunction()` | `Inactive: redstone gate` | yellow | normal |
| `OK` | `Working` | green | normal |

"Output" shows `EnergyDisplay.of(joules)` in `ENERGY` mode and `<n> redstone` in
`REDSTONE` mode, both derived from the synced `lastOutput` + mode. "Ambient" shows
`MekanismUtils.getTemperatureDisplay(...)`. Note the ambient *value* is synced for display
(a double), while the strip *level* is synced as a byte — the strip needs the server's
chunk-delta-aware value, and the GUI wants the number.

### 6.4 Container trackers

```java
container.track(SyncableByteArray.create(
      () -> expression.getBytes(StandardCharsets.UTF_8),
      bytes -> expression = new String(bytes, StandardCharsets.UTF_8)));
container.track(SyncableEnum.create(OutputMode::byIndex, OutputMode.REDSTONE, this::getOutputMode, v -> outputMode = v));
container.track(SyncableEnum.create(ExpressionRuntimeError::byIndex, ExpressionRuntimeError.NONE, this::getRuntimeError, v -> runtimeError = v));
container.track(SyncableByte.create(this::getErrorSideOrdinal, v -> errorSide = v < 0 ? null : Side.byIndex(v)));
container.track(SyncableDouble.create(this::getLastOutput, v -> lastOutput = v));
container.track(SyncableDouble.create(this::getAmbientTemperature, v -> clientAmbientTemperature = v));
```

`SyncableString` does not exist in Mekanism 10.7.19; `SyncableByteArray` over UTF-8 is the
idiomatic substitute (compare `SyncableFrequency`, which does the same thing for a richer
type). `OutputMode` and `ExpressionRuntimeError` both implement `IIncrementalEnum` so they
get a `byIndex(int)` for free; `SyncableByte.create` takes a
`mekanism.api.functions.ByteSupplier` + fastutil `ByteConsumer`, hence the byte-typed
getters (`getErrorSideOrdinal()` returns `byte`). `container.broadcastChanges()` already
runs from `MekanismContainer`, so no manual sending is needed.

---

## 7. Front-strip renderer

`client/renderer/TileEntityTemperatureControllerRenderer implements BlockEntityRenderer<TileEntityTemperatureController>`,
registered in `ModClient.registerRenderers`.

### 7.1 Geometry

The white window in `front.png` occupies texture pixels `x = 5..10` (6 px), `y = 0..15`
(16 px). In block coordinates the front face is the north plane (`z = 0`, outward normal
`−Z`), so with the model's UV convention:

| Axis | Range |
|---|---|
| x | `5/16` … `11/16` (6 px wide, one quad per row) |
| y for row `r` (0 = bottom) | `r/16` … `(r+1)/16` |
| z | `−0.002` (just outside the face; tunable — the model's face is at `z = 0`) |

One quad per row (16 quads), not per pixel (96), since a whole row shares one colour.

Rendering:

1. `poseStack.pushPose()`; apply the facing transform exactly as
   `TileEntityShakerRenderer.applyFacingTransform`/`getFacingRotation` do
   (`NORTH → 0`, `EAST → -90`, `SOUTH → 180`, `WEST → 90`; the comment there explains that
   block-model JSON Y rotations use the opposite sign from `Axis.YP`).
2. `VertexConsumer` from `bufferSource.getBuffer(RenderType.cutout())`; add one quad per
   row with the row's ARGB colour, full-bright vs. packed light — use the `packedLight`
   passed to `render` so the strip respects the room's lighting.
3. `poseStack.popPose()`.

Only `NORTH/EAST/SOUTH/WEST` occur because the block uses `HORIZONTAL_FACING`; the
`default` arm of the switch covers north and any unforeseen value.

### 7.2 Level and colours

`displayLevel ∈ [0, 16]` is computed **on the server** (§5.3) and synced. Row `r`
(`r = 0` at the bottom) is lit when `r < displayLevel`.

```
lit colour = hue ramp at 120° - 8°*r, full saturation and value
unlit      = 0xFF565656  (neighbouring casing shade, so the window reads as "off")
```

Hue interpolation gives a green → yellow → orange → red sweep (a plain RGB lerp
green→red muddies through olive). Implement it as a `private static final int[16]`
precomputed from the table below (or a tiny private HSL→RGB helper) rather than calling a
vanilla colour utility, so the renderer has no dependency on a helper whose existence in
1.21.1 would need checking. Resulting palette:

| Row (from bottom) | Hue | Colour | | Row | Hue | Colour |
|---|---|---|---|---|---|---|
| 0 | 120° | `#00FF00` | | 8 | 56° | `#FFEE00` |
| 1 | 112° | `#22FF00` | | 9 | 48° | `#FFCC00` |
| 2 | 104° | `#44FF00` | | 10 | 40° | `#FFAA00` |
| 3 | 96° | `#66FF00` | | 11 | 32° | `#FF8800` |
| 4 | 88° | `#88FF00` | | 12 | 24° | `#FF6600` |
| 5 | 80° | `#AAFF00` | | 13 | 16° | `#FF4400` |
| 6 | 72° | `#CCFF00` | | 14 | 8° | `#FF2200` |
| 7 | 64° | `#EEFF00` | | 15 | 0° | `#FF0000` |

`computeDisplayLevel(temperature)`:

```
f      = (temperature - MIN) / (MAX - MIN)     // MIN = 300 K, MAX = 1800 K, both configurable
level  = f <= 0 ? 0 : f >= 1 ? 16 : (int) Math.floor(f * 16)
```

`floor` (not `round`/`ceil`) means the strip starts lighting up as soon as the temperature
is above `MIN` (the first row appears at `MIN + (MAX-MIN)/16 = 393.75 K`) and reaches all
16 rows exactly at `MAX`. Because level is an integer, the update packet is sent at most 16
times as the temperature sweeps the whole range — this is what keeps the reduced update
tag traffic negligible.

### 7.3 Edge cases

- **Item form / inventory icon.** The item model shows the raw texture with its white
  window (no BER in an item context). Accepted; the alternative is a second texture or a
  client-side item renderer, which is not worth it.
- **Chunk not loaded / BE null.** The renderer only runs with a live BE.
- **GUI open.** The BER keeps working; the container's ambient double and the update tag's
  level are both kept current.
- **`displayLevel` before the first sync** is 0 (all grey), which is the correct default.

---

## 8. Networking

Two client → server payloads, registered in `Mod.registerPayloadHandlers` alongside
`PacketCoolerSetEnergy`. Both use `PacketUtils.blockEntity(context, pos)` to resolve the
tile, mirroring the existing packet.

| Payload | Fields | Effect |
|---|---|---|
| `PacketSetTemperatureExpression` | `BlockPos pos`, `String expression` | `tile.setExpressionFromPacket(expression)` → store, invalidate the compiled tree, `markForSave()` |
| `PacketSetTemperatureControllerMode` | `BlockPos pos`, `byte modeOrdinal` | `tile.setOutputModeFromPacket(mode)` → store, `markForSave()`, `sendUpdatePacket()`, and **zero the now-inactive output**: switching to `ENERGY` must clear `redstoneOutput` and call `updateNeighborsAt` (otherwise a latched signal would stay on forever, since the `REDSTONE` branch is the only writer); switching to `REDSTONE` needs no cleanup, the next tick re-emits |

No server → client packets are needed: the container trackers cover the GUI, and the
reduced update tag covers the in-world strip.

---

## 9. Lang

`ModLang` additions (all under the `gui`/`description` groups used by the mod):

```java
DESCRIPTION_TEMPERATURE_CONTROLLER("description", "temperature_controller"),

GUI_TEMPERATURE_CONTROLLER_AMBIENT("gui", "temperature_controller.ambient"),
GUI_TEMPERATURE_CONTROLLER_OUTPUT("gui", "temperature_controller.output"),
GUI_TEMPERATURE_CONTROLLER_STATUS("gui", "temperature_controller.status"),
GUI_TEMPERATURE_CONTROLLER_STATUS_OK("gui", "temperature_controller.status.ok"),
GUI_TEMPERATURE_CONTROLLER_STATUS_EMPTY("gui", "temperature_controller.status.empty"),
GUI_TEMPERATURE_CONTROLLER_STATUS_GATED("gui", "temperature_controller.status.gated"),
GUI_TEMPERATURE_CONTROLLER_MODE_ENERGY("gui", "temperature_controller.mode.energy"),
GUI_TEMPERATURE_CONTROLLER_MODE_REDSTONE("gui", "temperature_controller.mode.redstone"),
GUI_TEMPERATURE_CONTROLLER_INPUT_HINT("gui", "temperature_controller.input_hint"),
GUI_TEMPERATURE_CONTROLLER_ERROR_NO_HEAT("gui", "temperature_controller.error.no_heat"),
GUI_TEMPERATURE_CONTROLLER_ERROR_NOT_FINITE("gui", "temperature_controller.error.not_finite"),
GUI_TEMPERATURE_CONTROLLER_ERROR_SYNTAX("gui", "temperature_controller.error.syntax"),
GUI_TEMPERATURE_CONTROLLER_ERROR_UNEXPECTED_CHARACTER("gui", "temperature_controller.error.unexpected_character"),
GUI_TEMPERATURE_CONTROLLER_ERROR_UNEXPECTED_TOKEN("gui", "temperature_controller.error.unexpected_token"),
GUI_TEMPERATURE_CONTROLLER_ERROR_UNEXPECTED_END("gui", "temperature_controller.error.unexpected_end"),
GUI_TEMPERATURE_CONTROLLER_ERROR_UNKNOWN_VARIABLE("gui", "temperature_controller.error.unknown_variable"),
GUI_TEMPERATURE_CONTROLLER_ERROR_TOO_DEEP("gui", "temperature_controller.error.too_deep"),
GUI_TEMPERATURE_CONTROLLER_ERROR_TOO_LARGE("gui", "temperature_controller.error.too_large"),
```

`en_us.json` (and matching `zh_cn.json`):

```json
"block.mekanismheated.temperature_controller": "Temperature Controller",
"container.mekanismheated.temperature_controller": "Temperature Controller",
"description.mekanismheated.temperature_controller": "Reads the ambient temperature and the heat of adjacent blocks, evaluates an expression, and drives adjacent coolers and resistive heaters or emits a redstone signal.",
"gui.mekanismheated.temperature_controller.ambient": "Ambient: %1$s",
"gui.mekanismheated.temperature_controller.output": "Output: %1$s",
"gui.mekanismheated.temperature_controller.status": "Status: %1$s",
"gui.mekanismheated.temperature_controller.status.ok": "Working",
"gui.mekanismheated.temperature_controller.status.empty": "No expression set",
"gui.mekanismheated.temperature_controller.status.gated": "Inactive: redstone gate",
"gui.mekanismheated.temperature_controller.mode.energy": "Energy",
"gui.mekanismheated.temperature_controller.mode.redstone": "Redstone",
"gui.mekanismheated.temperature_controller.input_hint": "Use T, north.T, south.T, east.T, west.T, up.T, down.T with + - * / > < = != and a ? b : c",
"gui.mekanismheated.temperature_controller.error.syntax": "Syntax error at column %1$s: %2$s",
"gui.mekanismheated.temperature_controller.error.no_heat": "No heat capacitor on the %1$s side",
"gui.mekanismheated.temperature_controller.error.not_finite": "Result is not a finite number",
"gui.mekanismheated.temperature_controller.error.unexpected_character": "unexpected character '%1$s'",
"gui.mekanismheated.temperature_controller.error.unexpected_token": "unexpected '%1$s'",
"gui.mekanismheated.temperature_controller.error.unexpected_end": "unexpected end of expression",
"gui.mekanismheated.temperature_controller.error.unknown_variable": "unknown variable '%1$s'",
"gui.mekanismheated.temperature_controller.error.too_deep": "Expression is nested too deeply",
"gui.mekanismheated.temperature_controller.error.too_large": "Expression is too complex"
```

`container.mekanismheated.temperature_controller` is required for the GUI title
(`TileEntityMekanism#getDisplayName` looks up `container.<block>`).

---

## 10. Config

New `Config.TemperatureController` section, all in the existing `Config` class:

| Key | Type | Default | Meaning |
|---|---|---|---|
| `maxEnergyOutput` | long | `1_000_000` | Upper clamp, in the player's configured energy unit per tick, on the value written to adjacent coolers/heaters |
| `displayMinTemperature` | double | `300` | Kelvin at/below which the front strip is entirely grey |
| `displayMaxTemperature` | double | `1800` | Kelvin at which all 16 rows are lit |
| `maxExpressionLength` | int | `256` | Server-side cap on the submitted expression (guards the packet and the text field) |

`Config` is registered as `ModConfig.Type.COMMON`, i.e. each side reads its own file. That
is safe here because the display level is computed server-side and the client renderer
needs no config value at all.

---

## 11. File checklist

New files:

```
src/main/java/io/aduhtkjm/mekanismheated/
  content/expression/Expr.java                     // sealed AST + eval  [DONE]
  content/expression/ExpressionParser.java         // lexer + recursive-descent parser  [DONE]
  content/expression/ExpressionParseException.java [DONE]
  content/expression/ParseErrorKind.java           [DONE]
  content/expression/ExpressionRuntimeError.java   [DONE]
  content/expression/ExpressionRuntimeException.java [DONE]
  content/expression/VariableResolver.java         [DONE]
  content/expression/Side.java                     [DONE]
  content/expression/OutputMode.java               [DONE]
  block/temperaturecontroller/TemperatureControllerBlock.java
  tile/TileEntityTemperatureController.java
  client/gui/machine/GuiTemperatureController.java
  client/renderer/TileEntityTemperatureControllerRenderer.java
  network/PacketSetTemperatureExpression.java
  network/PacketSetTemperatureControllerMode.java

src/main/resources/assets/mekanismheated/
  blockstates/temperature_controller.json
  models/block/temperature_controller.json
  models/item/temperature_controller.json

src/main/resources/data/mekanismheated/loot_table/blocks/temperature_controller.json
src/main/resources/data/mekanismheated/recipe/crafting/temperature_controller.json
```

Modified files:

| File | Change |
|---|---|
| `registries/ModBlocks.java` | block type + block registration (§3.4) |
| `registries/ModTileEntityTypes.java` | `mekBuilder(...).clientTicker/serverTicker.withSimple(Capabilities.CONFIG_CARD)` |
| `registries/ModContainerTypes.java` | `custom("temperature_controller", ...).offset(10, 0).build()` |
| `registries/ModItems.java` | add to the creative tab |
| `client/ModClient.java` | register screen + block entity renderer |
| `Mod.java` | register the two payloads |
| `ModLang.java` | new entries |
| `Config.java` | new section |
| `lang/en_us.json`, `lang/zh_cn.json` | new keys |

No new mixin, no new recipe type, no new capability.

---

## 12. Open questions / decisions taken by default

These were not settled by the Q&A; each has a default in this spec, all are cheap to flip.

1. **Energy-mode behaviour while gated or errored.** Spec: the controller *stops writing*
   and leaves the neighbouring machine at its last value (writing `0` would silently kill
   a heater the player set up on purpose). Redstone mode naturally drops to 0. Flip if you
   would rather force neighbours to 0 whenever the controller is not working.
2. **Default output mode** is `REDSTONE` (harmless without a target and immediately
   visible). `ENERGY` is the other candidate if you expect the block to be used mainly as
   a power scheduler.
3. **Default redstone control** is Mekanism's `HIGH` for this block (the author chose
   "gated by redstone input"), i.e. it does nothing until powered; the redstone tab can
   set `DISABLED` to make it always run. This requires calling
   `setControlType(RedstoneControl.HIGH)` after `super(...)` in the tile constructor —
   **verify at implementation time** that this survives `presetVariables()` / the data
   component round-trip in `TileEntityMekanism.applyImplicitComponents`.
4. **Comparator output** is disabled (`AttributeComparator` removed). Alternative: have
   the comparator mirror `redstoneOutput`/`lastOutput`, which would let a comparator read
   the controller in `ENERGY` mode.
5. **Parentheses** are supported (§4.2). If you want the language to be exactly the seven
   operators, the parser change is a two-line deletion.
6. **No scientific notation / hex / `%` / `&&` / `||`.** Easy to add later.
7. **The front strip ignores error state**: it always shows the ambient temperature, so a
   broken expression does not blank the display. Alternative: flash red or go dark on
   error, which would need one more bit in the update tag.
8. **Crafting recipe** in §3.3 is a proposal and needs your input.
9. **Units.** In `ENERGY` mode the expression result is interpreted in the player's
   configured Mekanism energy unit per tick and converted with
   `MekanismUtils.convertToJoules`, exactly like the number typed into the Cooler or
   Resistive Heater GUI. So `T` (Kelvin) and the energy result share one expression but
   different units — documented in the tooltip/`input_hint`.
10. **No Jade integration** in v1. A small Jade provider showing `T`, the output and the
    status would fit the mod's existing Jade work and is listed as future work.

---

## 13. Implementation plan

### Phase 1 — expression engine (no gameplay) — **DONE**
`content/expression/*` in full, plus `OutputMode`. Nine files: `Expr` (sealed AST + the epsilon/finiteness rules),
`ExpressionParser` (lexer + parser + the two safety bounds), `ExpressionParseException`, `ParseErrorKind`,
`ExpressionRuntimeError`, `ExpressionRuntimeException`, `VariableResolver`, `Side`, `OutputMode`.
- Verified standalone in WSL with JDK 21: compiled the nine files with `javac -Xlint:all` (only the
  `serialVersionUID` notes that Mekanism's own exceptions also produce) and ran a 167-check harness covering the §4.6
  table and §14 cases 1, 5–11: literals, precedence, associativity, all eight operators, `==`/`=`, epsilon
  equality (including `NaN` and `Infinity`), ternary nesting and short-circuiting, every built-in variable, custom
  variable predicates, 40 parse-error cases with exact kind/column/detail, 11 runtime-error cases, and both safety
  bounds. Plus a robustness probe with 20 001 characters of nested parentheses, 10 001 unary minuses and 400 001
  characters of flat terms — all clean parse errors.
- Deviation from the plan, adopted deliberately: `Side`/`byIndex`/no annotations, so the package has no Minecraft or
  Mekanism dependency (§4). Syntax errors therefore need no wire format at all, since the client can parse.
- **Verify (author, in-game):** nothing yet — Phase 1 has no gameplay surface. The engine is exercised for real in
  Phase 2.

### Phase 2 — tile + block + registration, no GUI
`TileEntityTemperatureController` (state, tick, `emit`, persistence, config card,
trackers), `TemperatureControllerBlock`, `TemperatureControllerType`, tile type,
`Mod.registerPayloadHandlers`.
- **Verify:** place the block, confirm it renders as an enrichment chamber with the white
  window; `/data get block` shows the expression/mode; a hard-coded `T` drives an adjacent
  cooler (watch its GUI usage) and an adjacent Mekanism resistive heater; the redstone tab
  gates it; a neighbour with no heat capacitor produces the runtime error instead of a
  crash; repeated ticks do not spam chunk saves (check with a debugger or by watching
  `markForSave` indirectly via the neighbour's GUI not flickering).

### Phase 3 — assets
Blockstate, block/item models, loot table, recipe, creative tab, lang (both files).
- **Verify:** item appears in the tab with the right name and tooltip description; block
  faces are enrichment-chamber on five sides and ours on the front; rotating with a wrench
  keeps the front correct; breaking drops the block.

### Phase 4 — GUI + packets
`GuiTemperatureController`, both payloads, container type, screen registration.
- **Verify:** the widened window lines up with the player inventory (slots are where they
  look like they are — this is the classic `offset`/`imageWidth` trap); typing an invalid
  expression turns the status line and the field red without a round-trip; a valid
  expression shows the output; the mode button flips and survives reopening the GUI and a
  world reload; the expression survives reopening; right-click clears the field.

### Phase 5 — front strip renderer
`TileEntityTemperatureControllerRenderer`, reduced update tag, `computeDisplayLevel`.
- **Verify:** at 300 K the strip is fully grey; artificially heating the chunk (creative
  chunk heater / `/mekanismheated` chunk-temperature command from `ChunkTemperatureCommand`)
  lights rows bottom-up and the colours sweep green → yellow → orange → red; the display
  survives a `F3+A` chunk reload (i.e. it comes from `getUpdateTag`) and stays correct for
  a second player joining (i.e. it comes from `sendUpdatePacket` while the chunk is
  tracked).

### Phase 6 — polish
Config card round-trip, `en_us`/`zh_cn` completeness, and optional Jade provider.
- **Verify:** config card copy/paste moves the expression and mode; no missing lang keys
  (`F3+T` + `en_us` sanity check).

---

## 14. Test checklist (consolidated)

| # | Case | Expected |
|---|---|---|
| 1 | Empty expression | status "No expression set", no output, strip still shows ambient |
| 2 | `T` | output equals the ambient temperature at the block, chunk delta included |
| 3 | `north.T` with an air neighbour | runtime error naming the north side; no crash; nothing written to neighbours |
| 4 | `north.T` with a heat smelter / heat pipe to the north | reads that block's total temperature, **not** the controller's |
| 5 | `1 / 0` | runtime error "Result is not a finite number" |
| 6 | `T > 100000 ? 1/0 : 5` | outputs 5 — the taken branch is clean, the `1/0` branch is never evaluated |
| 7 | `T > 100000 ? north.T : 5`, north neighbour unreadable | outputs **5**, no error (untaken branch not evaluated) |
| 8 | `north.T > 0 ? 1 : 0`, north neighbour unreadable | runtime error naming the north side (the condition reads `north.T`) |
| 9 | `T = 300` on a chunk at exactly plains ambient | true (epsilon equality) |
| 10 | `1 < 2 < 3` | parse error at the second `<` |
| 11 | `(T + 0) * 2` | parses; parentheses work |
| 12 | `T > 500 ? 200 : 0` in `ENERGY` mode next to a cooler | cooler usage becomes 200 in the configured energy unit (J internally), and only when the expression's branch flips |
| 13 | Same, next to a Mekanism resistive heater | its usage follows, and its sound scale updates |
| 14 | `ENERGY` mode, adjacent to *both* a cooler and an unrelated machine | only the cooler is touched |
| 15 | `REDSTONE` mode, value 7 | a lamp on the side lights with strength 7; a wire reads 0 before the value changes and 7 after |
| 16 | Redstone control set to `HIGH`, no signal | no output of either kind; status "Inactive: redstone gate" |
| 17 | `DISABLED` | always runs |
| 18 | Value 99 in `REDSTONE` mode | clamped to 15 |
| 19 | Negative value in `ENERGY` mode | clamped to 0 |
| 20 | Chunk temperature changed at runtime (atmosphere heater / command) | strip and GUI update without a chunk reload |
| 21 | World reload | expression + mode + strip level all restored |
| 22 | Two players, one opens the GUI while the other edits | the field eventually shows the server value |
| 23 | `F3+A` | strip still correct (comes from `getUpdateTag`) |
