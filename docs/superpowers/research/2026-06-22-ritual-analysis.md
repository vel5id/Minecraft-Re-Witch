I have enough detail in the two findings to synthesize the design reference directly. No further file reads are needed.

# Hexerei Ritual-Circle System — Design Reference

> Status: design spec for an **original** Hexerei subsystem (Forge / Mojmaps, Minecraft 1.20.1). Inspired by the classic witch-ritual archetype (chalk glyphs drawn on the ground around a focus, consuming a sacrifice to produce a magical effect). All code, names, IDs, and assets are Hexerei-original; no upstream code or strings are copied.

---

## 1. Ritual overview

A **ritual** in Hexerei is the act of inscribing a circle of chalk glyphs on the ground around a central **focus block** (a powered cauldron), placing/throwing a **sacrifice** into that circle, and **activating** it. When the inscribed glyph layout matches a registered **ritual recipe** and the environmental gates pass, the recipe's **rite** runs — a timed server-side state machine that produces an observable effect (weather, lightning, terrain, item transforms, …) and consumes altar power scaled by circle size.

Flow at a glance:

```
draw glyphs (chalk)  ─┐
drop sacrifice item  ─┼─►  activate focus (right-click / redstone)
powered cauldron     ─┘         │
                                ▼
                  scan glyph ring → tally CircleSpec(s)
                  gather entities + sacrifice + weather/time
                                │
                  match against RitualRegistry recipes
                                │  (first match)
                                ▼
                  consume sacrifice → consume power → run Rite steps
                                │
                                ▼
                  observable effect (e.g. thunderstorm)
```

Two key design principles carried over from the classic archetype, **both preserved**:

1. **Matching is by count + color, not by exact coordinate.** A ring is "correct" when it contains the right *number* of glyphs of each color, regardless of rotation or which specific cells are filled. This makes the system forgiving and rotation-agnostic.
2. **Glyph cosmetic variety never affects matching.** Each placed glyph picks a random decorative texture; detection only ever reads the glyph's **color**.

> Note: in the source material there were two *separate* circle systems — a ritual glyph-pattern system and a simpler cauldron-brew ring checker (`CircleUtil`). **Hexerei uses only the glyph-pattern ritual system.** We do not port the brew/NBT ritual engine. The cauldron is reused purely as the *focus / activation point*, not as a brew-driven second ritual engine.

---

## 2. Glyph block design

`GlyphBlock` — a flat chalk mark drawn on the top face of a solid block.

| Property | Value |
|---|---|
| Shape | Flat slab AABB `(0, 0, 0)` → `(1, 1/64, 1)` (0.015625 tall) |
| Collision | None (empty collision shape) |
| Opacity / solidity | Non-solid, non-opaque, no occlusion |
| Render | Top face only; translucent/cutout chalk texture |
| Drops | Nothing |
| Support | `canSurvive()` requires the block **below** to have a solid, full top face |
| Neighbor update | Re-checks support; self-destructs if support removed |
| Removal | Right-click with the **broom** item deletes the glyph |

**Three color variants** (the matching dimension), modeled as an enum the ritual matcher reads:

```java
enum GlyphColor { RITUAL, OTHERWHERE, INFERNAL }   // ordinals 0,1,2
```

Implement as **three distinct blocks** (`hexerei:glyph_ritual`, `hexerei:glyph_otherwhere`, `hexerei:glyph_infernal`), each carrying its `GlyphColor`. (Alternatively one block + an enum property; three blocks is simpler for loot/model wiring and matches the "color is the identity" semantics.)

**Cosmetic variant property:** `IntegerProperty VARIANT = 0..11`, rolled randomly at placement, selecting 1 of 12 chalk textures. **Detection must never read `VARIANT`.**

**Placement — `ChalkItem`:** one chalk item per color (`chalk_ritual`, `chalk_otherwhere`, `chalk_infernal`), each bound to its glyph block. `useOn()` behavior:

- Target = top face of a solid block, space above air/replaceable, glyph can survive → place glyph at `pos.above()` with random `VARIANT`.
- Target = existing glyph of **same** color → re-roll `VARIANT` (re-randomize texture).
- Target = existing glyph of **different** color → replace with this color.
- Target = the **focus block** → place the glyph directly on top of it.
- Chalk is a damageable tool (64 uses, 1 dmg/use; creative ignores durability); plays a chalk-scratch sound.

---

## 3. Circle detection geometry

Detection is a **single static scan mask** centered on the focus block, evaluated at the focus's own Y level (glyphs sit on the ground at the same Y as the focus). We define rings as **named cell sets** (offsets `(dx, dz)` from center). Each cell, when it contains a glyph, contributes that glyph's color to the ring's tally.

We define **three concentric rings** as rounded squares at radius 4 / 6 / 8. Cell counts: **inner = 16, middle = 28, outer = 40** glyphs. The required-glyph→radius relation we standardize on:

```
radius(required) = (required + 2) / 6 + 1     // integer math
  16 → 4     28 → 6     40 → 8
```

### Authoritative representation: a 17×17 char mask

Rather than hand-listing 84 offsets, the **source of truth is a 17×17 character mask** (`A` = inner ring, `B` = middle ring, `C` = outer ring, `.` = empty, center cell at index `[8][8]` is the focus — never a glyph). Iterate the mask once, and for every world cell that holds a glyph, bucket it into the ring (`A`/`B`/`C`) and increment that ring's per-color counter.

```
row\col 0         1
        0123456789012345 6
   0    .......CCC.......   ← dz = +8
   1    .....CC...CC.....
   2    ....C.......C....
   3    ...C.BBBBB...C...     wait — see note
```

> **Implementation note on the mask:** The exact rounded-square cell layout must total **16 / 28 / 40** cells for A / B / C respectively, with A reaching ±4, B reaching ±6, C reaching ±8 from center. Because matching is **count-based**, the *precise* per-cell placement is not load-bearing for correctness — only (a) the per-ring total count and (b) the max extent matter. Author one canonical mask in a resource/constant, verify its `A`/`B`/`C` counts are exactly 16/28/40 at build time (a unit test asserting the counts), and freeze it. Do **not** make detection depend on which specific cells are filled.

### Concrete coordinate sets for our small / medium rings

For the **first slice and the medium tier** we only need two rings. Below are concrete, explicit offset sets (rounded-square rings). These are authoritative for Hexerei (we choose clean symmetric rings; we are not bound to reproduce any external layout):

**SMALL ring `A` — radius 4, 16 cells** (a rounded square at Chebyshev radius 4: the 4 edge-midpoint triples + corners trimmed). Offsets `(dx, dz)`:

```
( 0,-4) ( 1,-4) (-1,-4)      top edge (3)
( 0, 4) ( 1, 4) (-1, 4)      bottom edge (3)
(-4, 0) (-4, 1) (-4,-1)      left edge (3)
( 4, 0) ( 4, 1) ( 4,-1)      right edge (3)
( 3,-3) (-3,-3) ( 3, 3) (-3, 3)   corners (4)
                              = 16 cells
```

**MEDIUM ring `B` — radius 6, 28 cells** (rounded square at radius 6, 5 cells per edge + 2-step diagonal corners):

```
edges (5 each, dx ∈ {-2..2} on top/bottom, dz ∈ {-2..2} on sides):
 top   (dx, -6) for dx ∈ {-2,-1,0,1,2}      (5)
 bottom(dx,  6) for dx ∈ {-2,-1,0,1,2}      (5)
 left  (-6, dz) for dz ∈ {-2,-1,0,1,2}      (5)
 right ( 6, dz) for dz ∈ {-2,-1,0,1,2}      (5)
corners (rounded, 2 cells each):
 ( 5,-4) ( 4,-5)   ( -5,-4) (-4,-5)
 ( 5, 4) ( 4, 5)   ( -5, 4) (-4, 5)         (8)
                                = 28 cells
```

(The **outer ring `C` — radius 8, 40 cells** follows the same construction: 7 cells per edge + 3-cell rounded corners = 28 + 12 = 40. Defined for completeness; not used in the first slice.)

**Entity / sacrifice scan bounds:** an AABB of `center ± maxRingRadius` (for the outer mask, `± 8`; `getMaxDistance()` returns the max ring radius of the matched recipe, or a default of 4 if a recipe has no circle).

---

## 4. Ritual model (circle + sacrifice → rite)

### `CircleSpec` — a glyph *tally requirement*, not a geometry object

```java
// immutable per-color requirement
record CircleSpec(int ritual, int otherwhere, int infernal) {
    int required() { return ritual + otherwhere + infernal; }
    int radius()   { return (required() + 2) / 6 + 1; }   // 4/6/8
}
```

A scanned ring produces an **accumulator** of the same shape `{ritual, otherwhere, infernal}` counts. A ring **matches** a `CircleSpec` when all three counts are equal AND the total equals `required()`. Convenience constructors:

- `new CircleSpec(16, 0, 0)` → pure-ritual inner ring.
- `new CircleSpec(0, 40, 0)` → pure-otherwhere outer ring.
- A recipe may require **multiple** concentric rings (all must be satisfied).

`purity()` helper (optional): returns the single color if a ring is monochrome, else "mixed" — useful for risk/flavor effects later (e.g. infernal rings add misfortune).

### `RitualRecipe`

```java
record RitualRecipe(
    ResourceLocation id,          // stable key (NOT a dense byte index)
    List<CircleSpec> circles,     // required ring(s)
    Sacrifice sacrifice,          // item/living/power consumed to trigger
    Rite rite,                    // the effect (Step factory)
    Set<RitualCondition> gates    // environmental gates
)
```

**`RitualCondition`** (boolean gates checked at activation):
`DESTROYS_CIRCLE`, `ONLY_IN_RAIN`, `ONLY_IN_STORM`, `ONLY_AT_NIGHT`, `ONLY_AT_DAY`, `ONLY_OVERWORLD`.

**`Sacrifice`** (sealed hierarchy; consumed when the rite triggers):
- `SacrificeItem(Ingredient, count)` — one or more item stacks present as `ItemEntity` (or in a holder) within `maxDistance`, consumed one at a time.
- `SacrificeLiving`, `SacrificeMultiple`, `SacrificeOptionalItem`, `SacrificePower` — defined for later; **only `SacrificeItem` (and `SacrificePower`=0) needed for the first slice.**

### `Rite` + `RiteStep`

Collapse the classic Rite/RitualStep split into one interface:

```java
interface Rite {
    default void onStart(ServerLevel level, BlockPos pos, ActiveRitual ctx) {}
    RiteResult tick(ServerLevel level, BlockPos pos, ActiveRitual ctx);
}
enum RiteResult { CONTINUE, UPKEEP, DONE, ABORT, ABORT_REFUND }
```

- `CONTINUE` — not finished this tick; keep ticking (was "STARTING").
- `UPKEEP` — persistent step; must survive world save/load and re-run.
- `DONE` — advance / finish.
- `ABORT` / `ABORT_REFUND` — cancel (refund variant gives the sacrifice back).

`ActiveRitual` (runtime context) holds: the matched recipe, initiator name, stage counter, `maxDistance`, and a helper `getItemsInRadius(level, pos, r)` for item-transform rites.

> **First slice keeps every rite a one-shot `DONE` step** → no UPKEEP, no persistence, fully stateless and GameTest-friendly.

---

## 5. Activation flow end-to-end

The single Hexerei entry point is the **focus block = powered cauldron BE**, triggered by **right-click with an activator** (or a **redstone pulse**). (We deliberately do **not** implement the brew-NBT ritual path.)

```
1. Player right-clicks cauldron BE (or redstone rising edge).
2. CauldronBE.tryActivateRitual():
   a. Read time/weather: isDay, isRaining, isThundering, dimension.
   b. Scan the 17×17 glyph mask at (pos, sameY):
        for each mask cell with a glyph → bucket into ring accumulator
        by GlyphColor.  Produces accumA / accumB / accumC.
   c. Gather entities (incl. ItemEntities) within pos ± maxMaskRadius.
3. For each RitualRecipe in RitualRegistry (stable iteration order):
        recipe.matches(accumRings, gates, entities, day/rain/thunder, dim)
        → first match wins.
4. On match:
   a. Check power: cost = baseCost * powerScale(circles).
      If power source absent or consumePower(cost) == false → abort+drain.
   b. Consume sacrifice (one stack at a time; remove ItemEntity).
   c. If DESTROYS_CIRCLE gate → delete the matched glyphs.
   d. Build ActiveRitual(recipe, initiator, stage=0); call rite.onStart(...).
   e. Push onto CauldronBE.activeRituals.
5. Each BE tick: for active ritual, call rite.tick(...):
        CONTINUE → keep; DONE → pop/finish; UPKEEP → move to persistent list;
        ABORT(_REFUND) → cancel (refund sacrifice on _REFUND).
6. When activeRituals empties, ritual lifecycle ends.
```

No-match or failed-power gives clear feedback (particles/sound) and leaves the circle intact.

---

## 6. Altar-power integration

Reuse the existing Hexerei altar subsystem (`AltarPowerManager` / `IPowerSource`) unchanged in contract:

```java
interface IPowerSource {
    int  getCurrentPower();
    boolean consumePower(int amount);   // deduct; false if insufficient
}
```

At activation the cauldron BE locates the nearest altar via the altar manager's "find closest power source" lookup (cache the result on the BE; re-resolve on failure).

**Cost model:**

```
neededPower = baseCost * powerScale
powerScale  = 1.0
              - 0.2 per ritual circle present beyond a baseline   // bigger circle, cheaper
              - 0.37 per infernal-colored circle (also adds "risk"/misfortune later)
neededPower == 0           → free (no power source required)
consumePower(neededPower) == false → abort ritual + drain/refund
```

- **Power is scaled by circle size/composition**, matching the design intent that larger or infernal circles change the cost (and infernal adds risk).
- `getCurrentPower()` is a soft pre-gate (fail fast with feedback); `consumePower()` is the hard gate that actually deducts.
- **First slice sets `baseCost = 0`** so the slice needs zero altar infrastructure to pass, while the integration point is wired and ready.

---

## 7. Recommended FIRST SLICE

**Goal:** exercise every core subsystem end-to-end — circle detection → sacrifice match/consume → rite execution → observable, queryable level state — with **zero rendering, zero AI, zero persistence, zero power infra.**

**Scope (build exactly this, nothing more):**

1. **One glyph block + one chalk item** — `glyph_ritual` (`GlyphColor.RITUAL`) and `chalk_ritual`. Flat AABB, no collision, top-only render, `VARIANT 0..11` cosmetic, `canSurvive()` on solid top face, broom removal. (Otherwhere/infernal stubbed but not registered yet, or registered but unused.)
2. **One circle size** — the **SMALL ring `A`, radius 4, 16 cells** (Section 3). Single ring, single color → `CircleSpec(16, 0, 0)`.
3. **One simple verifiable rite** — **"Rite of the Tempest"**: one-shot storm call.
   - Recipe: `circles=[CircleSpec(16,0,0)]`, `sacrifice=SacrificeItem(<hexerei storm reagent>, 1)`, `gates={ONLY_OVERWORLD}` (or none), `baseCost=0`.
   - Rite: single `DONE` step that runs server-side:
     ```java
     serverLevel.setWeatherParameters(0, durationTicks, /*raining*/true, /*thundering*/true);
     return RiteResult.DONE;
     ```
4. **Activation hook** — right-click the cauldron BE (skip redstone for the slice).

**GameTest (headless, deterministic):**

```
1. Build a flat solid floor; place the cauldron focus.
2. Place 16 glyph_ritual blocks on the small ring A cells (via setBlock).
3. Spawn the storm-reagent ItemEntity inside the ring.
4. Trigger activation (call CauldronBE.tryActivateRitual()).
5. Assert: serverLevel.isThundering() == true
        && the reagent ItemEntity is removed (consumed).
6. (Negative test) Remove one glyph → assert no match, no thunder, item intact.
```

This is fully verifiable across ticks because weather state persists and is queryable. **Explicitly out of scope for the slice:** brew/NBT path, UPKEEP/persistence, coven scaling, multiple ring tiers, multiple glyph colors in matching, non-zero power, all non-`SacrificeItem` sacrifice types, broom UX polish.

> Even-simpler smoke alternative if needed first: **"Rite of Kindling"** — strike one `LightningBolt` at center via `serverLevel.addFreshEntity(...)`. But the storm rite is preferred because its result is a *persistent, queryable* level state.

---

## 8. 1.20.1 implementation mapping (Mojmaps / Forge)

| Concern | Hexerei implementation |
|---|---|
| **Glyph block** | `GlyphBlock extends Block` (3 instances or 1+enum). `getShape/getCollisionShape` → `Block.box(0,0,0,16,0.25,16)` flat; `Shapes.empty()` collision. `RenderType` cutout, top-only model. `IntegerProperty VARIANT = IntegerProperty.create("variant",0,11)`. `canSurvive()` → `level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)`. `neighborChanged` → drop if `!canSurvive`. `useWithItem` for broom removal. No loot table (empty). |
| **Chalk item** | `ChalkItem extends Item`, `useOn(UseOnContext)` per Section 2; `setDamage`/`hurtAndBreak` for durability; `Holder`-bound `GlyphBlock`. |
| **Circle scan** | Static `int[][]`/char mask constant (or generated offset list per ring). `RingScanner.scan(Level, BlockPos center)` → iterates mask, reads `getBlockState`, classifies `GlyphColor`, returns `EnumMap<RingId, GlyphAccumulator>`. Build-time unit test asserts ring counts = 16/28/40. |
| **CircleSpec / accumulator** | `record CircleSpec`, `record GlyphAccumulator`; `matches()` compares the three counts + total. |
| **Rite** | `interface Rite { onStart; RiteResult tick(...) }`, `enum RiteResult`. `RiteOfTempest implements Rite`. |
| **Ritual registry** | `DeferredRegister`-style or a simple `Map<ResourceLocation, RitualRecipe>` populated at mod-init. **No dense byte ids** — `ResourceLocation` keys; iteration order stable (registration order). Persistence of in-progress rituals (later) keyed by `ResourceLocation`, not list index. |
| **Activation hook (cauldron BE)** | In `CauldronBlockEntity`: `use()`/`useWithItem` (right-click) → `tryActivateRitual()`. Optional `neighborChanged`/`setRemoved` for redstone later. BE `tick()` (via `BlockEntityTicker`, server side only) iterates `activeRituals`, calls `rite.tick(...)`, handles `RiteResult`. Server guard: `if (level.isClientSide) return;`. |
| **Entity/sacrifice gather** | `level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(maxRadius))`; `Ingredient.test(stack)` for match; `itemEntity.discard()` on consume; refund = spawn a fresh `ItemEntity` on `ABORT_REFUND`. |
| **Weather (rite effect)** | `((ServerLevel)level).setWeatherParameters(0, durationTicks, true, true)`; verify with `level.isThundering()`. Lightning: `EntityType.LIGHTNING_BOLT.create(level)` + `level.addFreshEntity`. |
| **Power consume** | `AltarPowerManager.findClosestPowerSource(level, pos)` → `IPowerSource`; `getCurrentPower()` soft gate; `consumePower(cost)` hard gate; `powerScale` per Section 6. |
| **Conditions** | `level.isDay()`, `level.isRaining()`, `level.isThundering()`, `level.dimension() == Level.OVERWORLD`. |

---

## 9. Risks / open questions

1. **Mask fidelity vs. count-only matching.** Because matching is count-based, the exact per-cell layout is cosmetic to *correctness*. Risk: someone later makes detection position-dependent. **Mitigation:** keep `RingScanner` count-only; unit-test ring totals (16/28/40); document that filled-cell arrangement is free.
2. **Two-system confusion (historical).** The classic source had a separate brew-ring checker that did *not* drive rituals. **Hexerei intentionally omits the brew/NBT ritual engine.** Do not reintroduce a second ritual path; the cauldron is only the focus/activation point.
3. **Glyph `VARIANT` must never leak into matching.** Enforced by `RingScanner` reading only `GlyphColor`. Add a test that placing different variants does not change match outcome.
4. **Registry keys.** Use `ResourceLocation` keys, **not** dense 1-based byte indices (the classic `getRitual(id-1)` pattern is fragile and breaks on insertion/reordering). Persistence (when added) must key on the stable id.
5. **Radius formula constants.** `(required+2)/6+1` gives 4/6/8 for 16/28/40 by integer truncation — **verify with a unit test** if ring sizes ever change.
6. **First-slice boundaries.** Coven scaling, Grassper-style item holders, misfortune/risk from infernal circles, attuned/necro catalyst stones, multi-ring recipes, UPKEEP persistence, and all non-`SacrificeItem` sacrifices are **deferred**. Don't let them creep into the slice.
7. **Server-side guards.** Weather, lightning, item discard, and BE ticking must be server-only; double-check `level.isClientSide` guards to avoid desync.
8. **Persistence (post-slice).** UPKEEP rites will need to serialize `{recipeId, stage, pos, initiator}` to BE NBT and rebuild the `Rite` on load. The first one-shot `DONE` rite avoids this entirely — keep it stateless to defer the persistence design.
9. **Open — `matches()` layering order.** Confirm the intended precedence of gates vs. circle vs. sacrifice when several recipes could match (recommend: filter by gates → by circle(s) → by sacrifice, first match wins, with registration order deterministic). Needs a decision before adding a second recipe.
10. **Open — activator item vs. bare right-click.** Decide whether activation requires a specific activator item (e.g. a wand) or any bare-hand right-click on the cauldron. Slice can use bare right-click; production likely wants a gated activator to avoid accidental triggers.

Relevant existing source for the port (decompiled reference only — reimplement fresh, do not copy): `blocks/BlockCircle.java` (the 17×17 PATTERN scan), `blocks/BlockCircleGlyph.java`, `ritual/Circle.java`, `ritual/Rite.java`, `ritual/RiteRegistry.java`, `item/ItemChalk.java`, and the storm rite `ritual/RiteWeatherCallStorm.java`, all under `/home/h621l/minecraft/hexerei-work/decompiled/com/emoniph/witchery/`.