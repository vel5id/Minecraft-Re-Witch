# Hexerei — Rituals Expansion (3 new rites + SpawnItemRite)

**Date:** 2026-06-25
**Extends:** the Ritual Circles slice (`ritual/` package). Reuses `RitualActivation`, `RitualRecipes.ALL`,
the chalk cycling (`CycleRiteC2SPacket` / `RitualChalkItem`), `ChunkTaintData`, and `AltarPowerManager`.
Adds **no new networking, no new BlockEntity, no new SavedData**. All four rites plug into the existing
`Rite.perform(ServerLevel, BlockPos)` contract and the ordered `RitualRecipes.ALL` list the chalk scrolls through.

## Premise

Today the mod has exactly **one** rite (TEMPEST). The system is built to be extended: a `RitualRecipe` binds
`(id, CircleSize, sacrificeId, powerCost, Rite, nameKey)`, `RitualActivation.tryPerform` does circle-check →
nearest-sacrifice → power-debit-first → consume → `rite.perform`, and the chalk cycles `RitualRecipes.ALL`
in order. This slice adds **four** rites that follow that exact pattern:

1. **Rite of Verdant Growth** (SMALL) — bonemeals/grows crops in a radius; the "white-magic" counterpart to Tempest.
2. **Rite of the Bound Beast** (SMALL) — summons a tamed wolf familiar at the circle.
3. **Rite of the Waning Moon** (MEDIUM — *new ring geometry*) — a night/sleep-curse area effect; the first
   rite that needs a larger circle, proving the `CircleSize` extension point.
4. **SpawnItemRite (Rite of Manifestation)** (SMALL) — drops a configured `ItemStack` as an `ItemEntity` at
   the circle center. This is the **generic bridge** the Charm-Pouch spec's *Slice 2* explicitly calls for
   (sacrifice item + power → a crafted charm item). It is parameterised, so future "ritual-crafted" items add
   only a `RitualRecipe` line, not a new `Rite` class.

The core decidable logic of each rite — ring geometry, the MEDIUM offset table, the affected-block sweep
bounds, the spawn `ItemStack` resolution — is factored into **pure static methods** unit-tested without a
Minecraft runtime, mirroring the existing `RitualCircle` / `cycleIndex` pattern. The in-world side effects are
covered by `@GameTest`.

## Registry Objects

No `DeferredRegister` entries change for the three world-effect rites — a rite is plain Java wired only into
`RitualRecipes.ALL`. The table below lists the **`RitualRecipe` constants** added to `RitualRecipes` (the
unit of registration for this subsystem) and the one **new `CircleSize` enum constant**.

| Kind | id / constant | Notes |
|---|---|---|
| `RitualRecipe` | `hexerei:verdant` (`RitualRecipes.VERDANT`) | SMALL, sacrifice `hexerei:mandrake_root`, 60 power, `VerdantRite`, nameKey `ritual.hexerei.verdant` |
| `RitualRecipe` | `hexerei:bound_beast` (`RitualRecipes.BOUND_BEAST`) | SMALL, sacrifice `hexerei:wolfsbane`, 120 power, `BoundBeastRite`, nameKey `ritual.hexerei.bound_beast` |
| `RitualRecipe` | `hexerei:waning_moon` (`RitualRecipes.WANING_MOON`) | **MEDIUM**, sacrifice `hexerei:belladonna_flower`, 150 power, `WaningMoonRite`, nameKey `ritual.hexerei.waning_moon` |
| `RitualRecipe` | `hexerei:manifest_chalk` (`RitualRecipes.MANIFEST_CHALK`) | SMALL, sacrifice `hexerei:wormwood`, 40 power, `SpawnItemRite(() -> new ItemStack(HexereiItems.RITUAL_CHALK.get(), 8))`, nameKey `ritual.hexerei.manifest_chalk` |
| `CircleSize` | `CircleSize.MEDIUM` | new enum constant; radius-3 ring (20 glyphs). Delegates to `RitualCircle.mediumRing` / `isMediumComplete` (new pure methods) |
| `Rite` class | `VerdantRite`, `BoundBeastRite`, `WaningMoonRite`, `SpawnItemRite` | 4 new final classes in `ritual/` implementing `Rite` |

**`RitualRecipes.ALL` ordering (the chalk scroll order):**
```java
public static final List<RitualRecipe> ALL =
    List.of(TEMPEST, VERDANT, MANIFEST_CHALK, BOUND_BEAST, WANING_MOON);
```
Rationale for order: cheapest/most-benign first so a new player scrolling the chalk meets the gentle rites
(Tempest is the existing default at index 0 and stays there to preserve the saved-NBT default and existing
GameTest expectations; Verdant + Manifest are low-cost utility; Bound Beast + Waning Moon are the
expensive/aggressive end). `cycleIndex` already wraps over any list length via `Math.floorMod`, so no chalk
code changes.

### [DEPENDENCY] note on sacrifice items
All four sacrifices are **existing** real item ids (`hexerei:mandrake_root`, `hexerei:wolfsbane`,
`hexerei:belladonna_flower`, `hexerei:wormwood` — all registered in `HexereiItems` / `HexereiCrops`). **No new
sacrifice item is required by this slice.** If a future rite wants a bespoke reagent (e.g. a "moon dust"), that
item would be flagged `[DEPENDENCY]` and registered first; none are introduced here to keep the slice closed.

The Charm-Pouch *Slice 2* will reuse `SpawnItemRite` with a charm supplier
(`() -> new ItemStack(HexereiItems.WARD_CHARM.get())`); that charm item is a **[DEPENDENCY] on the Charm-Pouch
slice** and is out of scope here. This slice's `MANIFEST_CHALK` recipe spawns ritual chalk (an item that
already exists) purely to exercise and ship the `SpawnItemRite` machinery end-to-end.

## Mechanics

### New CircleSize: MEDIUM (pure geometry, unit-tested)

`CircleSize.MEDIUM` is added as a second enum constant, mirroring `SMALL`. It delegates to two new pure methods
on `RitualCircle`. The ring is a radius-3 square outline (Chebyshev shell) on the center's Y-layer — **20
positions** (the 7×7 perimeter minus corners would be 24; we use the 8-symmetry "rounded" outline below to read
as a circle, matching how `SMALL`'s 12 read as a ring rather than a full square shell).

**MEDIUM (dx,dz) offset table — 20 entries, radius 3** (clockwise from north, 8-fold symmetric):
```
{ 0,-3},{ 1,-3},{ 2,-2},{ 3,-1},{ 3, 0},{ 3, 1},{ 2, 2},{ 1, 3},
{ 0, 3},{-1, 3},{-2, 2},{-3, 1},{-3, 0},{-3,-1},{-2,-2},{-1,-3},
{ 2,-3},{ 3,-2},{-2,-3},{-3,-2}
```
The first 16 are the 8-symmetric rounded outline (the same shape as `SMALL` scaled to r=3); the last 4 fill the
"shoulder" gaps so the outline never has a 2-block hole an item could roll through. This is a **deliberate
table** (not generated) so the unit test pins it exactly, exactly like `RitualCircle.SMALL`. Count = 20 is the
documented magic number.

New pure methods (no MC runtime beyond `BlockPos`, which is already used in pure tests):
```java
public static int mediumSize();                                  // returns 20
public static List<BlockPos> mediumRing(BlockPos center);        // center.offset(dx,0,dz) for each row
public static boolean isMediumComplete(Predicate<BlockPos> isGlyph, BlockPos center);
```
`CircleSize.MEDIUM.ringPositions` → `mediumRing`; `.isComplete` → `isMediumComplete`. `RitualActivation`,
`RitualChalkItem`, and `CycleRiteC2SPacket` need **no change** — they already call through the `CircleSize`
abstraction (chalk draws `rite.circleSize().ringPositions(...)`, activation checks `circleSize().isComplete(...)`).

### Rite 1 — VerdantRite (SMALL, "white magic" growth)
**perform() effect** (concrete 1.20.1 server APIs):
- Sweep a 7×7×3 box centered on the circle (`dx,dz ∈ [-3,3]`, `dy ∈ [-1,1]`), bounded by a pure
  `VerdantRite.cells()` helper returning the offset list (unit-testable).
- For each pos: `BlockState bs = level.getBlockState(p)`; if `bs.getBlock() instanceof BonemealableBlock bm`
  and `bm.isValidBonemealTarget(level, p, bs, false)` and `bm.isBonemealSuccess(level, level.getRandom(), p, bs)`,
  call `bm.performBonemeal(level, level.getRandom(), p, bs)`. This grows vanilla crops **and** Hexerei
  `WitchCropBlock` (which implements `BonemealableBlock`) — reusing existing bonemeal logic, no per-crop code.
- Cap at `MAX_GROWTHS = 12` blocks grown per cast (prevents a full-field instant harvest in one cheap rite).
- Feedback: `level.sendParticles(ParticleTypes.HAPPY_VILLAGER, cx, cy+0.5, cz, 40, 2.0,0.5,2.0, 0.0)`;
  `level.playSound(null, center, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 0.8f, 1.0f)`.
- **Taint:** `ChunkTaintData.get(level).addTaint(new ChunkPos(center), powerCost/4f)` → `60/4 = 15f`
  (the established `powerCost/4` taint convention; 15f lands exactly at the LOW threshold — a benign rite that
  *just barely* taints, thematically "nature magic costs the land a little").
- `HexereiNetwork.sendTaintSync(level, cp)` after adding taint (same as Tempest, so the client taint cache updates).

### Rite 2 — BoundBeastRite (SMALL, familiar summon)
**perform() effect:**
- Spawn a tamed wolf at center+1Y: `Wolf wolf = EntityType.WOLF.create(level)`; set position with
  `wolf.moveTo(center.getX()+0.5, center.getY()+1, center.getZ()+0.5, level.getRandom().nextFloat()*360f, 0f)`;
  `wolf.setHealth(wolf.getMaxHealth())`; `level.addFreshEntity(wolf)`.
- Owner binding is **deferred** (no player handle in `Rite.perform`): the wolf is spawned **un-owned but
  spawn-persistent** (`wolf.setPersistenceRequired()`), and a thematic "wild-but-friendly" `MobEffectInstance`
  is skipped — see *Out of Scope* for the owner-aware variant. Spawning a hostile-neutral wolf at the circle is
  the shippable core; the unit test pins the spawn offset, the GameTest pins that a `Wolf` exists post-cast.
- Feedback: `SoundEvents.WOLF_HOWL` at center, `ParticleTypes.SOUL` burst (16 particles).
- **Taint:** `addTaint(cp, 120/4f) = 30f` → MEDIUM band start (binding a living creature is a heavier act).

### Rite 3 — WaningMoonRite (MEDIUM, night/curse aura)
**perform() effect:**
- `level.setDayTime(level.getDayTime() - (level.getDayTime() % 24000L) + 18000L)` to set time to midnight
  (18000 = midnight in the 24000-tick day). Pure helper `WaningMoonRite.midnightOf(long dayTime)` returns the
  target tick (unit-testable: `midnightOf(1000) == 18000`, `midnightOf(20000) == 42000` i.e. next-day midnight
  if already past 18000 — pick forward so the day always advances *to* night, never backward).
- Apply WEAKNESS + SLOWNESS to every hostile `Monster` within `AURA_RADIUS = 10` of center:
  `level.getEntitiesOfClass(Monster.class, new AABB(center).inflate(10))` →
  `m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 0))` and `MobEffects.MOVEMENT_SLOWDOWN, 600, 0`.
  (600 ticks = 30 s.) Affected-mob predicate is pure-testable via the radius math
  (`WaningMoonRite.inRange(centerVec, mobVec)`), the effect application is GameTest-covered.
- Feedback: `SoundEvents.EVOKER_PREPARE_SUMMON`, `ParticleTypes.WITCH` ring over the 20 glyph positions.
- **Taint:** `addTaint(cp, 150/4f) = 37.5f` → upper MEDIUM (a strong curse rite; near-but-not HIGH).

### Rite 4 — SpawnItemRite (the generic charm bridge)
**Shape:** `public final class SpawnItemRite implements Rite` holding a
`Supplier<ItemStack> stackSupplier` (a supplier, **not** a captured `ItemStack`, so registry objects are
resolved lazily after registration is frozen — mirrors how `RitualRecipes` references `HexereiItems` only via
supplier-style access inside the rite, and avoids holding a stale stack instance).
**perform() effect:**
- `ItemStack out = stackSupplier.get().copy()` (copy so repeated casts never share/mutate one instance).
- Drop at center+1Y, zero motion so it lands on the circle (where the next ritual could consume it, enabling
  ritual chains): 
  ```java
  ItemEntity ie = new ItemEntity(level, center.getX()+0.5, center.getY()+1.0, center.getZ()+0.5, out);
  ie.setDeltaMovement(Vec3.ZERO);
  ie.setDefaultPickUpDelay();
  level.addFreshEntity(ie);
  ```
- Feedback: `SoundEvents.AMETHYST_BLOCK_CHIME`, `ParticleTypes.END_ROD` upward fountain (24 particles).
- **Taint:** `addTaint(cp, powerCost/4f)` = `40/4 = 10f` → below LOW (manifestation is the cleanest rite;
  10f < 15f so a single cast leaves the chunk at `NONE`, but repeated crafting *accumulates* into LOW — taint
  as a soft cost on mass-producing items, consistent with `ChunkTaintData.addTaint`'s permanent-floor accrual
  of `delta*0.1`).
- Pure seam: `SpawnItemRite.resolve(Supplier<ItemStack>)` → the copied stack; unit test asserts the supplier is
  invoked each call and the returned stack is a distinct copy (id + count preserved, mutating the result does
  not affect the next call).

## Persistence
- **No new persistence.** Rites are stateless effects. Taint already persists via `ChunkTaintData` (SavedData);
  each rite's `addTaint` writes through it exactly as Tempest does.
- The chalk's selected-rite NBT (`hexerei:rite` string key, read null-safely in existing code) automatically
  covers the new recipes — they are just new entries in `ALL` / `BY_ID`. `RitualRecipes.fromTag` already falls
  back to `ALL.get(0)` (TEMPEST) for unknown/absent tags, so old chalk stacks keep working.

## Client/Server
- **Server-authoritative:** all four `perform()` bodies run only from `RitualActivation.tryPerform`
  (server-only) and `WaningMoonRite`/`BoundBeastRite` entity + time mutations are server-side.
- **Client visuals:** `level.sendParticles` / `level.playSound(null, ...)` from the server reach clients via
  vanilla packets — no new `@OnlyIn(Dist.CLIENT)` code, no `HexereiNetwork` additions beyond the already-used
  `sendTaintSync`.
- **Chalk cycling** already round-trips over `CycleRiteC2SPacket`; the MEDIUM glyph count (20) flows through the
  existing `item.hexerei.ritual_chalk.circle` action-bar message (its `%d` is `ringPositions(...).size()`), so
  scrolling onto Waning Moon shows "20 glyphs" with **no packet or client change** — but see *Decisions* on the
  lang string, which currently hardcodes the word "Small".

## Assets
This slice ships **no new blocks, items, models, or textures** — every rite reuses existing registry objects,
vanilla entities (Wolf), vanilla particles/sounds, and the existing ritual chalk/circle/glyph art. The only
asset deltas are **lang keys** (en_us + ru_ru) for the four new rite names plus the MEDIUM-circle hover/action
string fix.

**Lang keys (en_us + ru_ru):**

| key | en_us | ru_ru |
|---|---|---|
| `ritual.hexerei.verdant` | Rite of Verdant Growth | Обряд буйного роста |
| `ritual.hexerei.bound_beast` | Rite of the Bound Beast | Обряд связанного зверя |
| `ritual.hexerei.waning_moon` | Rite of the Waning Moon | Обряд убывающей луны |
| `ritual.hexerei.manifest_chalk` | Rite of Manifestation | Обряд воплощения |
| `item.hexerei.ritual_chalk.circle.small` | Small circle · %d glyphs | Малый круг · %d глифов |
| `item.hexerei.ritual_chalk.circle.medium` | Large circle · %d glyphs | Большой круг · %d глифов |

(The existing single `item.hexerei.ritual_chalk.circle` key hardcodes "Small"; to honor MEDIUM correctly the
chalk tooltip/action-bar should pick the key by `circleSize()`. This is a tiny, mechanical change in
`RitualChalkItem.appendHoverText` and `CycleRiteC2SPacket.handle` — see *Decisions* if you'd rather defer it
and accept the cosmetic "Small" label on the large circle.)

## Balance (justified numbers)

| Rite | Circle | Sacrifice | Power | Taint (`power/4`) | Justification |
|---|---|---|---|---|---|
| Verdant | SMALL (12) | mandrake_root | **60** | 15 (LOW edge) | Cheaper than Tempest (100): a utility farm-boost should be repeatable. 60 is mid-range vs cauldron brews (30–60) so it competes for altar power. `MAX_GROWTHS=12` caps yield so cheap≠free harvest. |
| Manifest | SMALL (12) | wormwood | **40** | 10 (NONE single / LOW accrued) | Lowest cost — it's a *crafting* primitive that may fire repeatedly; cheap enough to be a viable production loop yet still gated on an altar. 8 chalk/cast is generous but chalk has 64 durability, so this is convenience, not exploit. |
| Bound Beast | SMALL (12) | wolfsbane | **120** | 30 (MEDIUM) | Above Tempest: a permanent persistent mob is the strongest SMALL-circle payoff. Wolfsbane (the lycanthrope herb) is the thematically correct reagent. |
| Waning Moon | MEDIUM (20) | belladonna_flower | **150** | 37.5 (upper MEDIUM) | Most expensive + needs the larger circle (20 glyphs ≈ harder to build/defend): a battlefield-control curse. Belladonna (deadly nightshade) suits a moon/death rite. 150 is the top of the band so a player must bank power. |

Cross-rite reasoning:
- **Taint scaling stays on the documented `powerCost/4` rule** (DESIGN-NOTES Ritual slice; Tempest uses an
  explicit `25f` ≈ 100/4). Keeping every new rite on `powerCost/4f` makes "more powerful = dirtier land" an
  invariant, and the four taints (10/15/30/37.5) climb monotonically with power, which is the intended signal.
- **MAX_GROWTHS = 12** matches the SMALL glyph count (a tidy, defensible cap; one growth per glyph drawn).
- **AURA_RADIUS = 10** for Waning Moon vs the 5-block Hexbane charm aura: a 150-power *ritual* should out-range
  a passive charm, and 10 ≈ the MEDIUM circle's radius-3 footprint plus standoff.
- **600-tick (30 s) debuffs** on Waning Moon: long enough to matter in a fight, short enough not to be permanent.

## Test Plan

### Unit (pure logic, no game runtime — `src/test/java`)
- **`RitualCircleTest` (extend):** `mediumSize() == 20`; `mediumRing(center)` has 20 distinct positions all at
  Chebyshev distance 3 from center on the same Y; `isMediumComplete` true for the full set, false when one cell
  is removed, false for `p -> false`. Assert the MEDIUM offset table exactly (hard-coded mirror like the SMALL
  test) so the table can't silently drift.
- **`CircleSizeTest` (extend):** `CircleSize.MEDIUM.ringPositions(...).size() == 20`; complete/incomplete cases.
- **`RitualRecipesTest` (extend):** `ALL.size() == 5`; `ALL.get(0) == TEMPEST` (default preserved);
  `BY_ID` contains all five ids and round-trips; `fromTag` returns TEMPEST for null/unknown tag; `match`
  returns VERDANT for a complete SMALL circle + `hexerei:mandrake_root` **only when** TEMPEST didn't already
  claim it — *note:* both TEMPEST and VERDANT use mandrake_root on a SMALL circle, so `match` returns the
  **first** in list order (TEMPEST). Test asserts this collision resolves to TEMPEST and document it as
  intended (chalk selection differentiates them in practice via the drawn circle, but `match` is sacrifice+geometry
  only). **[DECISION] flag below.**
- **`SpawnItemRiteTest`:** `resolve(supplier)` returns a copy with the supplier's id + count; mutating the
  returned stack's count does not affect a second `resolve` call; supplier is invoked once per call.
- **`WaningMoonRiteTest`:** `midnightOf(1000) == 18000`; `midnightOf(18000) == 18000`; `midnightOf(20000) ==
  42000` (rolls to next day's midnight, never backward); `inRange(center, p)` true at dist ≤ 10, false beyond.
- **`VerdantRiteTest`:** `cells()` yields the expected 7×7×3 = 147 offsets, dedup, centered; `MAX_GROWTHS == 12`.

### GameTest (`src/main/.../test`, arena — extend `RitualGameTests` or a new `RitualExpansionGameTests`)
Reuse the existing `buildCircle` + `FakeAltar` + `dropSacrifice` helpers (generalize `buildCircle` to take a
`CircleSize` so it can lay the 20-glyph MEDIUM ring).
- **Verdant:** place a `Blocks.WHEAT` at age 0 in range, full circle + mandrake_root + powered FakeAltar,
  `tryPerform`; assert SUCCESS, sacrifice consumed, 100 power → 40, and the wheat's `AGE` increased (or a
  `WitchCropBlock` advanced). Assert chunk taint increased (`ChunkTaintData.getTaint > 0`).
- **Manifest:** full SMALL circle + wormwood + powered altar (cost 40), `tryPerform`; assert SUCCESS and an
  `ItemEntity` holding `RITUAL_CHALK` now exists within 2 blocks of center (scan via `getEntitiesOfClass`),
  and exactly 40 power consumed.
- **Bound Beast:** full circle + wolfsbane + powered altar (120), `tryPerform`; assert SUCCESS and a `Wolf`
  entity exists near center, `isPersistenceRequired()`.
- **Waning Moon:** build the **MEDIUM** ring (20 glyphs) + belladonna_flower + powered altar (150); spawn a
  `Zombie` within 10 blocks; set time to noon (`setDayTime(6000)`); `tryPerform`; assert SUCCESS, time is now
  midnight band (`getDayTime() % 24000 == 18000`), and the zombie has `MobEffects.WEAKNESS`. Mark
  `required = false` if the large-arena scan proves flaky (per the project's flaky-GameTest convention),
  with the pure radius/time tests as the authoritative check.
- **Incomplete MEDIUM circle** (19 glyphs) + belladonna → `NO_RECIPE`, sacrifice intact, power untouched
  (mirrors the existing `incompleteCircleFails` test for SMALL).

## Out of Scope (explicit cuts)
- **Owner-aware Bound Beast:** binding the wolf to the activating player needs a player handle threaded through
  `Rite.perform` (currently `(ServerLevel, BlockPos)` only) — a signature change deferred to a follow-up. This
  slice ships an un-owned persistent wolf.
- **Charm crafting recipes:** the `SpawnItemRite` entries that drop *charms* depend on the Charm-Pouch slice's
  `CharmItem`s `[DEPENDENCY]`; only the `RITUAL_CHALK`-spawning recipe ships here to prove the mechanism.
- **New reagent items / new circle textures / LARGE (radius-4) circle:** not needed by these four rites.
- **Rite cooldowns, multi-tick rituals, or ritual BlockEntities:** all four remain instant, matching the slice's
  "the circle block has no BlockEntity" decision.
- **Particle/biome overhauls** beyond the per-rite vanilla particle bursts.

## Decisions needed (human owner)
1. **TEMPEST vs VERDANT share (SMALL + mandrake_root).** `RitualRecipes.match` keys only on circle+sacrifice, so
   it returns the **first** (TEMPEST). In-world both are reachable because the chalk *draws* the same SMALL ring
   for either, and activation just matches the sacrifice. To make them distinct, either (a) give Verdant a
   different sacrifice (e.g. `artichoke` or `belladonna_flower`), or (b) accept that SMALL+mandrake always fires
   Tempest and re-theme Verdant's reagent. **Recommend (a): switch Verdant's sacrifice to `hexerei:artichoke`**
   to remove the collision. (Spec currently lists mandrake_root to surface this for you.)
2. **MEDIUM circle lang label.** Update the chalk to pick `circle.small`/`circle.medium` by `CircleSize`
   (cleaner) vs. leave the single hardcoded "Small" key and accept the cosmetic mislabel on the large circle.
3. **MEDIUM ring shape** — confirm the 20-position rounded outline reads as a circle in-game vs. a fuller
   24-position square shell (`[UNVERIFIED]` until visually checked).
