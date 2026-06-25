# Design Notes

Hexerei is built slice-by-slice. Every numeric value in the code is a deliberate design choice,
documented here per slice — never invented on the fly. Values flagged `[UNVERIFIED]` still need
in-game confirmation.

> **This document is subordinate to the Constitution.** `WARRANTLY/` (the three design-law
> documents — `Конституция_мира_ведьм.md`, `Грамматика_векторов.md`, `Модель_данных_привязки.md`)
> decides *whether* a mechanic may exist and *what shape* it must take. DESIGN-NOTES only records the
> *numbers* once the Constitution has admitted the mechanic. Before adding a slice or changing balance,
> pass the litmus checklist in `Конституция_мира_ведьм.md`; a number that serves a Law-breaking mechanic
> is not "balanced," it is out of scope. See the "Design law" gate in the root `CLAUDE.md`.

# Ritual Circles slice

A **Ritual Circle** center block, ringed by **Ritual Glyph** chalk blocks, performs a **rite** when
right-clicked: the activation scans the circle, finds a sacrifice item, debits altar power, consumes the
sacrifice, and runs the rite.

- **Circle geometry** (pure, `RitualCircle`): a small circle is 12 glyphs in a radius-2 ring on the
  center's Y-layer (offsets `(0,±2),(±1,±2),(±2,±1),(±2,0)`) — unit-tested over a position predicate.
- **Ritual model** (`RitualRecipe`): required circle + sacrifice item id + altar-power cost + a `Rite`.
  Matching (`RitualRecipes.match`) is pure. Starter: **Rite of the Tempest** — small circle + 1
  mandrake root + 100 power → thunderstorm (`setWeatherParameters`).
- **Activation** (`RitualActivation.tryPerform`, server-only, testable without a player): circle check →
  nearest sacrifice `ItemEntity` → recipe match → **power consumed first** (so a failed power check leaves
  the sacrifice intact) → sacrifice consumed → `Rite.perform`. Returns `SUCCESS / NO_RECIPE / NO_POWER`.
- **Glyph block**: flat 1/16 slab, no collision, requires a sturdy face below, drops itself. **Circle
  block**: no BlockEntity (the slice's rite is instant). Visuals reuse vanilla textures only.
- **Verification**: a `GameTest` asserts the raw weather-data flag (`getLevelData().isThundering()`) — NOT
  `Level.isThundering()`, which reads the *interpolated* thunder level that lags ~90 ticks.

# Witch's Cauldron (brewing core) slice

The cauldron is a `BlockEntity`: fill water (right-click water bucket) → heat from a block in the
`hexerei:cauldron_heat_sources` tag below (fire/soul_fire/lava/magma + lit campfires, checked in code)
→ boils after **100 ticks** → absorbs herb `ItemEntity`s while boiling (multiset-filtered so only
ingredients that progress toward a recipe are taken) → gated on a nearby altar via
`AltarPowerManager` → right-click a glass bottle to collect a drinkable `Brew`.

- **Brews are plain data** (`Brew` = id, name key, color, power cost, list of `BrewEffect`s); recipes
  match on item-id multisets (`BrewRecipes`, pure & unit-tested). Starter brews: Sleeping Draught
  (mandrake root + belladonna flower, 50 power) and Brew of Frailty (wolfsbane + wormwood, 30 power).
- **Power**: `requiredPower` = the matched brew's cost; collection tries each in-range altar in
  distance order and debits the first that can pay the full cost (atomic per-altar). The cached
  `powered` flag is for display only — `collectBrew` re-checks `consumePower` authoritatively.
- **Visuals reuse vanilla art** (cauldron model parent `minecraft:block/cauldron`; brew item = vanilla
  potion overlay + bottle textures, tinted by brew color) — no third-party assets.

### Review-driven decisions (post-implementation adversarial review)
- **Loot tables**: both cauldron and altar now drop themselves (`loot_tables/blocks/*`) — blocks
  without a loot table silently drop nothing.
- **Drain interaction**: right-click an empty bucket to rinse out a wrong/incomplete mix (recovers a
  water bucket) — avoids a dead-end where a bad mix could only be cleared by breaking the block.
- **Creative drink**: drinking a brew in creative applies effects but consumes nothing and yields no
  bottle (was a bottle dupe).
- **Stack intake**: leftover from a thrown stack is popped back out of the cauldron, not trapped.
- **No phantom swing** on a full cauldron with a water bucket.

# Herbs (Crops) slice

## Deliberate design decisions
- **`WitchCropBlock`** is a self-contained `BushBlock` + `BonemealableBlock`, not vanilla
  `CropBlock`: per-crop AGE maxima (belladonna/mandrake/artichoke/snowbell/mindrake=4, garlic=5,
  wolfsbane=7) need their own `IntegerProperty`; vanilla `CropBlock` hardcodes `AGE_7`. The AGE
  property is created/cached per max via a static stash passed before `super()` (block init is single-threaded).
- **Growth**: light ≥ 9; `nextInt((int)(25/f)+1)==0` (integer truncation is intentional). `getGrowthRate`
  uses `FarmBlock.MOISTURE` as the fertility input.
- **Bonemeal**: `canFertilize` true → `+random(2..maxAge)`; false → `+1` (a deliberate counter-intuitive inversion that gives slow crops a partial boost on poor soil).
- **Drops** via overridden `getDrops` (not loot JSON): immature → 1 seed; mature → `3+fortune` seed rolls
  each `nextInt(15)≤7`, +1 produce, Snowbell +20% Icy Needle; Mindrake → 1 seed +25% produce.
- **Seeds = `ItemNameBlockItem`** (vanilla seed placement). Mindrake-bulb & Garlic are one item used as both seed and produce. Snowbell produce = vanilla `minecraft:snowball`.
- **Soil**: crops plant **only on farmland** (tilled soil), like vanilla, and do **not** stack.
  Wormwood keeps a self-stand exception so its mature upward-growth segment survives (player still
  plants the first on farmland). Water Artichoke plants on water.
- **Altar synergy**: any `WitchCropBlock` contributes 4/20 to altar power (wired in `AltarBlockEntity.resolveDynamic`).

## Deferred (future slices)
- **Mandrake / Mindrake live entities** — the screaming mob that spawns on harvest. This slice always
  drops the root/bulb (no entity). The mandrake entity + spawn gate (day 90% / night 10%, never peaceful) = future.
- **Treefyd** seed (entity spawner, not a staged crop). **Mutandis** seed acquisition. Mindrake dropped-item 3s lifespan.

## Known `[UNVERIFIED]`
- **Artichoke food** `nutrition 20, saturation 0.0` fills the whole hunger bar — very high; may retune.
- **Artichoke water placement** — registered as a water plant; `ItemNameBlockItem` placement on water surface needs in-game verification.

# Herbs & Mushrooms overhaul slice

Adds 6 new herbs + 3 mushrooms, renames `snowbell` → `hellebore`, and regenerates all crop textures
(the 98-PNG batch lands into the manifest paths separately; this slice is code + data + JSON only).

## Deliberate design decisions
- **`snowbell` → `hellebore` rename at the id level** (field `SNOWBELL`→`HELLEBORE`, id `snowbell`→
  `hellebore`, blockstate/stage-model/seed-item assets, and both lang files — "Hellebore" / "Морозник").
  The **internal `snowbell` boolean** on `WitchCropBlock`/`CropDrops` is **kept** (not renamed) — it is
  the non-user-visible "+20% bonus drop" flag, now documented as *the hellebore bonus-drop flag*.
  Renaming it would churn `CropDrops`/`WitchCropBlock` for zero player-facing gain.
- **Hellebore drops unchanged** ([DECISION-HELLEBORE-DROP] Option A): mature produce stays vanilla
  `minecraft:snowball` + 20% `icy_needle`. `icy_needle` is already a brew ingredient, so re-theming
  would break two recipes for no mechanical gain. Only the two icons are regenerated (frost-rose look).
- **4 new farmland crops via the existing `crop(...)` helper** — no new classes:
  `crowseye` (poison/blinding berry), `celandine` (cleansing flower), `hops` (sedative, `wormwood=true`
  so it reuses the two-tall self-stacking behaviour), `sandwort` (`big=false` ⇒ bonemeal +1, resistance
  reagent). All are `maxAge=4` (5 stages), drop via `WitchCropBlock.getDrops` (no loot JSON), and
  auto-contribute 4/20 altar power via the `instanceof WitchCropBlock` branch.
- **`MistletoeBlock extends WitchCropBlock`** ([DECISION-MISTLETOE]) overrides only `mayPlaceOn` to
  `BlockTags.LOGS || BlockTags.LEAVES` (parasitic on wood, not farmland). Registered via a parallel
  `cropSubclass(...)` helper (the stock `crop(...)` hard-codes `new WitchCropBlock`), and added to
  `CROP_BLOCKS`/`SEED_ITEMS`/`SEED_BY_CROP` so the creative tab + 4/20 altar synergy still fire.
  **v1 = on-top-of-wood placement** (inherited supported-below survival); true side-hanging is a follow-up.
- **`BloodMossBlock extends CarpetBlock`** ([DECISION-BLOODMOSS]) — a flat 1px ground-cover decoration,
  **not** a staged crop (a cross-billboard would read wrong for "moss"). **Non-spreading v1**
  (deterministic, no GameTest burden); taint-gated spread is a follow-up. Drops itself via a loot JSON
  (it does not override `getDrops`). Registered as a plain `BlockItem` (a decoration, not a seed).
- **`WitchMushroomBlock extends BushBlock`** ([DECISION-MUSHROOMS]) — one shared class for all three
  mushrooms. Vanilla-mushroom behaviour: small-mushroom shape `box(5,0,5,11,6,11)`, place on solid top,
  survive only where `getRawBrightness < 13` OR full sky access. **No growth stages** (single cross
  sprite, no `_stage_N`), **no huge variant** (bonemeal does nothing) v1. A per-instance `glow` flag;
  `ZEVANTY` sets `.lightLevel(s->8)`. Each drops itself via a loot JSON; `zevanty` has a 2nd loot pool
  for **25% `glowing_spore`** (data, not code).
- **6 new produce items + `glowing_spore`** are plain non-edible `Item`s (brew reagents — recipes are
  designed in the brews overhaul, not here). Produce + mushroom/moss `BlockItem`s are added explicitly
  to the creative tab (the `SEED_ITEMS` loop only covers seeds).
- **Assets reference the manifest texture paths verbatim** so the incoming batch PNGs line up:
  crop stage models parent `minecraft:block/crop` (`crop` = `hexerei:block/<name>_stage_N`); mushroom
  models parent `minecraft:block/cross`; blood moss parents `minecraft:block/carpet` (`wool`); seeds +
  produce parent `minecraft:item/generated`; mushroom item icons use the block sprite as `layer0`.

## Known `[UNVERIFIED]`
- **Blood moss altar power 4/20** — wired into `resolveDynamic` reusing the reserved `EMBER_MOSS`
  moss-tier idea; the value is a placeholder pending in-game balance.
- **Mushrooms get no altar power** (they are reagents, not nature-power blocks) — by decision, revisit
  if cave-farm power matters.
- **Sandwort plants on farmland** ([DECISION-SANDWORT-SOIL]) for v1 (no per-crop sand soil enum); a
  `sand`-placement variant is a follow-up.

# Altar slice

## Deliberate design decisions
- **Joined state**: a single boolean blockstate property `joined` marks an altar block that has
  merged into a formed multiblock.
- **Power registry**: a **per-`ServerLevel`, server-only, transient `AltarPowerManager`** rebuilt
  from block-entity load/unload. Keeping it server-side and per-world avoids any cross-world or
  client-server state leakage.
- **Core position** serialized as `CoreX/CoreY/CoreZ` NBT ints (positions are `BlockPos`).
- **Power store**: kept as `float`, with recharge floored to `int` every 20 ticks (preserves balance).
- **Power scan cadence**: `maxPower` is recomputed only at formation and on right-click; the tick
  only recharges, it does not rescan. Re-right-click to refresh after changing nearby nature blocks.
- **Formation trigger**: handled in `onPlace`, so the altar also forms from `/setblock`, worldgen
  and GameTest placement (not just hand-placement). Guarded against the `joined`-flag self-change to avoid recursion.
- **GUI open**: the altar GUI is a Container-less, read-only `Screen`, opened directly **client-side**
  in `use()` via `DistExecutor` (the block-entity is already synced) — no custom packet needed.
- **Recipe**: temporary vanilla-only placeholder (`altar_placeholder.json`) so the altar is
  craftable for testing. The full recipe is preserved disabled in
  `altar_authentic.json.disabled` and re-enabled when its ingredients exist.

## Deferred to future slices (guarded in code with `// FUTURE SLICE`)
- **Additional power-table blocks** — the power table supports these entries, but their blocks
  don't exist yet:
  LEAVES 4/50, LOG 3/100, DEMON_HEART 40/2, INFINITY_EGG 1000/1, CROP_* 4/20, EMBER_MOSS 4/20,
  SPANISH_MOSS 3/20, GLINT_WEED 2/20, CRITTER_SNARE 2/10, BLOOD_ROSE 2/10, GRASSPER 2/10,
  WISPY_COTTON 3/20. Add each entry to `AltarPowerTable`/`AltarBlockEntity.resolveDynamic`
  when the block lands.
- **Artefact bonuses** (skulls/torch/candelabra/chalice/arthana/mystic-branch/pentacle/infinity-egg)
  → `powerScale`/`rechargeScale`/`rangeScale`/`enhancementLevel` stay at 1/0 this slice.
  (PowerSource fields + NBT are already in place for forward-compat.)
- **`NaturePowerFX`** green nature particles (cosmetic, omitted).
- **`INullSource` / void bramble** power suppression (not part of the altar core).

## Known `[UNVERIFIED]` items to revisit
- Core-coord NBT key form (`CoreX/Y/Z` chosen; no live saves to migrate yet).
- Full-recipe item ids (`breath_of_the_goddess`, `exhale_of_the_horned_one`,
  `witchlog`) and whether `minecraft:potion` matches as an ingredient under 1.20.1 components.
- Flower power: a flower block contributes 4/30. Flowers are matched via the
  `#minecraft:small_flowers` tag, so a mixed flower field yields slightly more than a strict
  two-type cap. Acceptable design nuance.

# Altar Artefacts + Taint Punishment slice

One slice ships both halves: artefacts that scale a ritual's taint/effect, and the player
punishment that finally makes taint bite.

## Artefacts (Part A)
- **One artefact slot per altar** (purifier OR amplifier, never both — multiplier-stacking balance
  is unverified). Stored as a single `ItemStack` in the **core** `AltarBlockEntity` NBT (`Artefact`),
  placed/swapped/retrieved by a world right-click on a formed altar (no new packet). Drops on
  break/de-form.
- Three concrete items, each an immutable `(taintMul, effectMul, enhancement)` `ArtefactDef`:
  | id | archetype | taintMul | effectMul | enhancement |
  |----|-----------|---------:|----------:|------------:|
  | `bone_charm` | purifier | 0.5 | 1.0 | 0 |
  | `wax_poppet` | purifier (deep) | 0.25 | 0.9 | 0 |
  | `obsidian_skull` | amplifier | 2.0 | 1.5 | 1 |
- `taintMul` is **universal**: all 5 rites route their taint write through `Rites.addRitualTaint`,
  which multiplies by `RitualContext.currentTaintMul()`. `effectMul` is **advisory** — only
  `VerdantRite` reads it this slice (growth budget = `clamp(round(MAX_GROWTHS * effectMul), 1, 2*MAX)`;
  skull→18, poppet→11, default→12).
- The multiplier reaches `Rite.perform` (whose signature is unchanged) via a server-thread
  `RitualContext` ThreadLocal that `RitualActivation` sets around `perform` in try/finally. The
  multiplier comes from **exactly the funding altar** — `payPower` returns the `@Nullable IPowerSource`
  that paid.
- `obsidian_skull` activates the dormant `enhancementLevel` field (→1) for future gated rites.
  `powerScale`/`rechargeScale`/`rangeScale` deliberately stay at 1.0 this slice (power-economy change
  is out of scope; the fields stay wired for #19 Coven Power).
- Acquisition is **temporary** shapeless recipes (like `altar_placeholder`): bone_charm = bone +
  string; wax_poppet = honeycomb + string; obsidian_skull = obsidian + wither_skeleton_skull. The
  "earned via ritual" path is a follow-up.
- No world render of the placed artefact this slice (a floating-item `BlockEntityRenderer` is a
  separable cosmetic follow-up).

## Taint Punishment (Part B)
- `WorldTaintAura.punishPlayers(ServerLevel)` runs on the **same 200-tick pulse** as `pulse`, called
  from `HexereiLevelEvents.onLevelTick` under `gt % 200`. It iterates **players, not altars** — taint
  outlives the altar that caused it, so each player is judged by the `TaintLevel` of their **own
  chunk**. Creative/spectator are immune.
- The ladder (`TaintPunishment.effectsFor`, a pure unit-tested mapping resolved to vanilla effects):
  | chunk TaintLevel | effects |
  |------------------|---------|
  | NONE (<15) | none |
  | LOW (≥15) | Hunger I |
  | MEDIUM (≥40) | Hunger I + Weakness I |
  | HIGH (≥70) | Hunger I + Weakness I + Wither I |
- **`TAINT_PUNISH_REFRESH = 220`** ticks (`TaintPunishment.REFRESH_TICKS`): one 200-tick pulse period
  + ~1 s slack. Invariant **duration > cadence** so the debuff never gaps while the player stays, and
  lapses ~1 s after leaving. All amplifiers are 0 (level I); re-applying each pulse just refreshes the
  timer (vanilla replaces equal/stronger — no runaway). Effects are `ambient, hidden particles,
  visible HUD icon` (mirrors `CharmTickHandler`) so the player sees *why*.
- Wither I (vanilla `MobEffects.WITHER`, not the project's Withering-Bile brew) for HIGH: ~2.5 hearts
  lost per pulse window, fully regenerable by leaving — a strong "get out" signal, not instant death
  (the Cleansing Loop is the intended counter, out of scope here).

# Vector core (`soul/`) slice — Phase 1 (WARRANTLY migration)

The constitutional vector model (Грамматика/Модель данных), built pure-logic-first. This is the
single bloodstream every later slice converts onto; it does **not** yet change item behaviour.

- **`Correspondence`** — 6 domains (forest, stone, water, death, sky, threshold). Deliberately small
  (Art. VI — each axis is player-facing complexity). Serialized by lowercase `key()`, not enum name.
- **`Act`** — transient delta {domain weights, reciprocity∈[-1,1], binding∈[-1,1], defilement∈[0,1],
  magnitude≥0}. `sum()`: domain weights **add**, magnitude **adds**, polarity axes are
  **magnitude-weighted means** (a big reagent dominates the mix). `interactionStrength = clamp01(|recip|
  + |binding| + defile)` — the "|interaction|" term of the disturbance write.
- **`Integration`** — the ONLY Act→State path (Грамматика §2). Per bond-domain weight `w`:
  `debt += max(0,-recip)·mag·w`, `loyalty += max(0,+recip)·mag·w`, `resentment += defile·mag·w`,
  `disturbance[D] += interactionStrength·mag·w`, `sealIntegrity = clamp01(integrity + binding·mag)`.
- **`Decay`** — asymmetric (the Article III loop guard). Rates per 1200-tick decay step:
  `RESENTMENT_RATE=0.5`, `LOYALTY_RATE=0.25`, `FEAR_RATE=0.5`. **debt never decays here** (only
  reciprocity>0 acts pay it). `resentment` floors at `debt + Σ mark.severity`; loyalty/fear → 0.
- **`Resolution`** — success = `align·standing·placePenalty`, never a dice roll (Грамматика §6-7). Knobs:
  `DEBT_WEIGHT=0.5`, `LOYALTY_WEIGHT=0.5` (standing = clamp01(1 − 0.5·debt + 0.5·loyalty));
  `SUCCESS_THRESHOLD=0.5`, `RESENT_RESIST=0.5`, `DEFILE_THRESHOLD=0.5`, `STANDING_CAPACITY=3` (magnitude a
  unit of standing carries before OVERREACH). Failure precedence: DEFILEMENT → RESISTANCE → OVERREACH →
  MISALIGNMENT → SUCCESS. `[UNVERIFIED]` — all thresholds are first-pass; tune once rites read them.
- **`ChunkSoulData.disturbance`** — per-domain successor to scalar `ChunkTaintData`. Same asymmetric
  floor: `SCAR_FRACTION=0.1` of every applied add scars permanently; `MAX_DISTURBANCE=100`,
  `DECAY_PER_TICK=0.5` (mirrors the old taint numbers, now keyed by `Correspondence`).
- **`PlayerSoulData`** — essence + totalDebt + marks(List<Bond>) + amulets(List<UUID>); copied on
  `PlayerEvent.Clone` so debt survives death (Модель §3). `spendEssence` debits only if affordable
  (consume-before-input discipline carried from the old power model).
- Persistence: Forge **Capabilities** (`HexereiCapabilities`) — chunk attachment persists with the chunk,
  player attachment copied on respawn. `[UNVERIFIED]` runtime attach/persist — covered by compile + NBT
  round-trip unit tests; in-world GameTest/dedicated-server smoke still pending.

# Phase 2-3 migration — Slice A: Reagent → Act (`soul/`)

Additive pure-logic foundation for rituals/brews reading the vector model. No live code touched.

- **`ReagentDescriptor`** {domain, reciprocity, binding, defilement, magnitude} → `toAct()` is a
  single-domain `Act` (weight 1.0). **`ActAssembler.assemble`** sums reagents via `Act.sum`.
- **`ReagentRegistry`** seed values (all `[UNVERIFIED]`, first pass; datapack-driven source is a later slice).
  `reciprocity<0` = a take (debt); `defilement` = profaning the deadly/poisonous. Magnitude ≈ ritual weight.

  | item | domain | recip | bind | defile | mag | rationale |
  |---|---|---|---|---|---|---|
  | mandrake_root / mindrake_bulb | THRESHOLD | −0.30 | 0 | 0.10–0.15 | 1.0 | screaming root, between worlds |
  | belladonna_flower / wolfsbane / crowseye_berry | DEATH | 0 | 0 | 0.20 | 0.5–0.6 | deadly poisons |
  | wormwood | THRESHOLD | 0 | 0 | 0.05 | 0.5 | bitter, dream |
  | celandine | FOREST | +0.20 | 0 | 0 | 0.5 | cleansing (an offering) |
  | hops | FOREST | 0 | 0 | 0 | 0.5 | sedative |
  | mistletoe_sprig | FOREST | −0.20 | 0 | 0.05 | 0.6 | parasite (a small take) |
  | sandwort | STONE | 0 | +0.10 | 0 | 0.5 | resistance |
  | garlic | THRESHOLD | 0 | +0.20 | 0 | 0.5 | ward (binding) |
  | artichoke / hellebore / icy_needle | WATER | 0 | 0 | 0–0.05 | 0.4–0.5 | water & frost |
  | glowing_spore | THRESHOLD | 0 | 0 | 0 | 0.4 | luminous fungal |
  | blood_moss | DEATH | 0 | 0 | 0.10 | 0.6 | crimson |

# Phase 2-3 migration — Slice B: Essence economy (replace free AltarPower)

The Article III fix: the altar no longer fills for free. **No passive recharge** (the
`serverTick` `BASE_POWER_PER_UPDATE` drift is removed; only the >cap clamp remains).
`maxPower` (the 29³ scan) is kept as the **capacity** cap only; the reservoir is **earned**.

- **`EssenceSource`** — `essenceFrom(act) = ESSENCE_PER_MAGNITUDE(=10) · magnitude · max(0,−reciprocity)`
  (only takes free essence). `breakRelease(domain, mag, defile)` = a full take (reciprocity −1).
- **`ReleaseBlocks`** — block-id → {domain, magnitude, defilement} for harvestable witch crops,
  mushrooms, blood moss (all `[UNVERIFIED]`; magnitudes 0.4–1.0, deadly herbs carry defilement 0.05–0.20).
- **`EssenceSourcing`** (Forge `BlockEvent.BreakEvent`, server) — breaking a release block:
  credits `AltarBlockEntity.gainEssence(essenceFrom(act))` to the nearest altar within `getRange()`,
  AND writes `ChunkSoulData.disturbance[domain] += …` (first live use of the Phase-1 capability).
  Power and danger are one act.
- **`AltarBlockEntity.gainEssence`** — routes to the multiblock core, clamps to capacity.

Balance `[UNVERIFIED]`: a crop yields 4–10 essence; rituals cost ~50–100 → ~10–20 harvests per rite.
**In-world unverified** — compile + unit-tested (EssenceSource, ReleaseBlocks); the BreakEvent→altar
credit + disturbance write needs a dedicated-server smoke test (GameTest altar scans are flaky).

# Phase 2-3 — Slice B′: The Hungering Altar (rite-gated drain + regional curse)

Replaces the player-harvest source (removed — farming your own crop for power was illogical). An
altar gives NO essence by default; a rite awakens it into a *hungering* altar that EATS the land.

- **Activation:** `HungeringRite` (recipe `HUNGERING` — MEDIUM ring + an **obsidian_skull** sacrifice,
  altar-power cost **0** so it bootstraps the loop). Sets `AltarBlockEntity.hungering` (NBT-persisted,
  synced, routed to the multiblock core) and burns an initial `DEATH` disturbance scar (`AWAKENING_SCAR=30`).
- **Drain (`AltarDrain`, gated on hungering, every `DRAIN_INTERVAL=40` ticks, only while below capacity):**
  consumes the NEAREST living block within `DRAIN_RADIUS=5` — `grass_block→dirt`, leaves/flowers/vanilla
  bushes/witch-crops→air — deterministically (nearest first, so the blight grows outward, no dice). Each
  bite = a take `Act` → `gainEssence(essenceFrom)` + `ChunkSoulData.disturbance[domain]+=…`.
- **Regional curse (`HungeringAltar`, every `PENALTY_INTERVAL=100` ticks):** every player within
  `PENALTY_CHUNK_RADIUS=6` chunks (96 blocks) gets Weakness (+Darkness at amp≥2). Amplifier =
  `min(3, disturbance / DISTURB_PER_AMP(=30))` — the danger grows with the place's accumulated unrest.
- Power and danger are the same act: essence only ever comes with a spreading scar + a cursed region.
  All knobs `[UNVERIFIED]`. **In-world unverified** (drain/curse loop) — `penaltyAmplifier` unit-tested;
  needs a playtest. Offerings + altar-held-artefact radius scaling are Slice B″.

# Phase 2-3 — Slice B″: Offerings (deliberate sacrifice, diminishing returns)

The active counterpart to the Hungering Altar's passive drain. Right-click an altar with a
spirit-bearing reagent (anything in `ReagentRegistry`) → consume one → essence (a take of its spirit).

- **`Offering`** (pure): `essence = ESSENCE_PER_MAGNITUDE · magnitude · yieldMultiplier(satiation)`,
  `yieldMultiplier = 1/(1+satiation)`. Bulk-feeding one domain saturates it (5×mag-1 offerings ≈ 23
  essence, not 50) — anti-grind, so no domain becomes a parallel essence farm.
- **`AltarBlockEntity`**: per-domain `satiation` (core-only, NBT, routed); `offer(reagent)` credits
  essence + raises that domain's satiation; serverTick decays satiation (`SATIATION_DECAY=1`/20-tick step).
- **`AltarBlock.use`**: holding a reagent → offer (consume 1) with SOUL particles + sound; artefact
  placement and the GUI are unchanged. All knobs `[UNVERIFIED]`; `Offering` math unit-tested.

# Rune redesign — symbols instead of connecting lines

The old `rune` block was a redstone-like 8-way connecting decal (`RuneShapes`, eight boolean
properties) whose neighbour detection mis-resolved which segment to show. Replaced by distinct
**symbols**: each rune shows one of 18 `RuneSymbol` glyphs (3 per `Correspondence` domain), stored as
the `symbol` IntegerProperty (0–17, declaration order). `RuneShapes` + its test + the six connection
models/textures are removed.

- **Block:** `RuneBlock` keeps the 12×12×1 decal, sturdy-face-below survival, and the bound-circle
  break penalty (`onRemove` → `RitualDestruction`) — a symbol swap (same block) does NOT penalize.
- **Assets:** `block/rune_glyph` parent (thin slab, up-face `#rune`) + 18 child models +
  `blockstates/rune.json` (18 variants) + 18 line-glyph textures (generated, Elder-Futhark style,
  white strokes on transparent). Item icon = `block/rune_branch`.
- **Chalk:** right-click an existing rune to cycle its symbol; the ring is still drawn from a sigil.
- **Ritual matcher unchanged** — it tests a rune's PRESENCE, not its symbol; the symbol→domain meaning
  feeds the rite's `Act` once rituals read the vector model (Slice D/E). In-world render unverified.

# Phase 2-3 — Slice C: taint scalar → per-domain disturbance (one store)

The scalar `ChunkTaintData` SavedData is retired; `ChunkSoulData.disturbance[domain]` (Phase 1) is now
the single store of place-unrest. The dual system is gone — the Hungering Altar's disturbance now drives
the same visible taint ladder / aura / punishment / altar blockstate as the rites.

- **`Disturbance`** (server gateway over the chunk capability): `add(level,cp,domain,amt)` (+sync),
  `total(cp)`, `level(cp)` = `TaintLevel.fromValue(total)`. Decay is **lazy** (`ChunkSoulData.lazyDecay`,
  `DECAY_INTERVAL=1200`, applied on access) — no all-loaded-chunks sweep; the per-domain scar floor holds.
- **Writers carry a domain:** `Rites.addRitualTaint(level,cp,domain,base)` → `Disturbance.add`. Tempest→SKY,
  Verdant/BoundBeast→FOREST, Eclipse/WaningMoon/BloodMoon→DEATH, SpawnItem→THRESHOLD; RitualDestruction→DEATH.
- **Readers:** `WorldTaintAura`, altar `TAINT_LEVEL` blockstate, `sendTaintSync`, chunk-watch → `Disturbance`.
  `TaintLevel` thresholds (15/40/70) now read TOTAL disturbance; `TaintLevel`, `Rites.scaledTaint`,
  `TaintSyncS2CPacket`/`ClientTaintCache` (scalar visual) are preserved.
- Removed `ChunkTaintData` + `TaintDataTest`; 3 GameTests migrated to `Disturbance`.

# Phase 2-3 — Slice D: rituals read Act + resolve against the place (hybrid)

`RitualActivation` now resolves each cast against state instead of just matching a recipe. Named rites
are kept (the matched recipe is the intended effect), but whether it FIRES is a function of the place.

- **Act assembly** (`buildRitualAct`): the rite's `Act` = the sacrifice's `ReagentDescriptor`
  (`ReagentRegistry`) + each ring rune's `RuneSymbol.domain` (binding 0.2, magnitude 0.3) — so the
  **rune symbols now MEAN domains** in the rite (the diegetic-language payoff).
- **Resolution** (`RitualResolver.resolve`, pure, tested): `Resolution.success/classify` over the
  dominant domain's disturbance + the place's total disturbance, neutral caster standing for now (no
  player handle at the entry point — a witch's debt/loyalty folds in later).
- **Botch feeds the loop** (Грамматика §7): a domain disturbed past ~50% resists — the cast still spent
  its sacrifice and essence, so instead of a free no-op it writes `BOTCH_DISTURBANCE=10` to that domain,
  plays a smoke/fizzle telegraph, and returns `Result.FAILED` without performing. SUCCESS in calm/low-
  disturbance places (normal play unaffected).
- **Payment is essence** — `consumePower` already debits the altar's earned reservoir after Slice B.
  All knobs `[UNVERIFIED]`; in-world ritual flow needs a playtest.

# Sealed Amulets slice (retire the flat charm system → the "запечатать" verb)

The three flat combat charms (`ward_charm`/`bloodlust_charm`/`hexbane_charm` + `CharmItem`/`CharmDef`/
`CharmDefs`/`CharmCharge`/`CharmTickHandler`) were a **parallel system** the Constitution forbids. They are
replaced by **one** `hexerei:amulet` item that carries a `Bond` in its NBT — the "запечатать" verb (Модель §7).
A sealed amulet is obtained only from a **sealing rite**; worn in a charm pouch, it grants a domain-derived
effect at the price of accruing soul-debt that grinds its seal until it shatters.

- **One item, domain-driven** (mirrors the brew one-item-tinted-by-content pattern). `AmuletItem` stores the
  whole `Bond` under NBT key `hexerei:sealed_bond` via `Bond.CODEC` + `NbtOps` — Forge 1.20.1 has no
  `DataComponentType` (that is 1.20.5+/NeoForge), so NBT is the item-data path (same as the old charge).
- **Effect = f(domain)** (`SealedAmulet.effectFor`, pure): FOREST→Resistance (always), DEATH→Weakness aura to
  hostiles within 5, THRESHOLD→Strength at ≤6 hearts. `ActiveMode` is reframed diegetically as the spirit's
  *nature* (a threshold-spirit stirs near death), not a separate axis — preserving all three old behaviours.
- **Cost model replaced**: there is **no altar recharge**. Each second worn, `bond.disposition.debt += DRAW_RATE`
  and resentment (`max(0, debt − loyalty)`) grinds `seal.integrity` (`−= GRIND_RATE·resentment`). At
  integrity ≤ 0 the amulet **shatters** (item removed, `BREAK_DISTURBANCE` written, the Mark conceptually
  persists). This is the Article-III cost — the witch's own debt, not nature-power.
- **`SealedAmulet`** is pure + unit-tested, including a **lifespan invariant** (an un-tended amulet breaks
  within a bounded band — no permanent free power; loyalty keeps it intact). Constants all `[UNVERIFIED]`,
  tuned via that band: `DRAW_RATE = GRIND_RATE = 0.0005` → ~47 min lifespan, debt ≈ 1.4 at break.
- **`AmuletTickHandler`** (replaces `CharmTickHandler`, same 20-tick hook) scans the carried pouch, accrues
  debt/grinds each sealed amulet, applies its effect, breaks on 0, and **reconciles `PlayerSoulData.amulets`
  + `totalDebt`** with the pouch (Модель §3) so future dream/living-world readers see the worn bonds.
- **Three sealing rites** (`RitualRecipes`: `seal_amulet_forest`/`_death`/`_threshold`, SMALL ring, cost 80),
  one `SealAmuletRite(domain)` class (SpawnItemRite-style instances). Each sacrifice is a domain reagent **no
  other rite uses** (celandine / crowseye_berry / garlic), so `RitualRecipes.match` stays unambiguous.
- **`CharmPouchItem`** now validates slots against `AmuletItem.isSealed` and shows a mean-seal-**integrity**
  bar (was a charge bar). `bone_charm` (altar artefact) is untouched.

## Known `[UNVERIFIED]` / out of scope
- All `SealedAmulet` constants (DRAW/GRIND rates, severities, disturbances) are first-pass; tune by playtest.
- **Owner-binding at creation** is deferred (the `Rite` contract has no player handle) — the amulet drops
  un-owned and binds on pouch, matching `BoundBeastRite`'s caveat.
- **The deep Article-III payoff** (accrued `debt`/`Mark` → presences turn on the witch) needs the unbuilt
  `PresenceEntity`; this slice only records `totalDebt` + `Mark` for it. The v1 bite is the finite lifespan.
- **APPEASE/FEED rites** (loyalty to offset debt) and **dream rendering** of worn-bond debt — later verbs.
- **Patchouli guidebook** `entries/charms/*` still describes the retired charms — a content follow-up.
- **In-world GameTests** (`AmuletGameTests`, the sealing-rite test in `RitualExpansionGameTests`) are written
  but the `runGameTestServer` boot currently fails on a **pre-existing Patchouli mixin** error
   (`AccessorSmithingTrimRecipe`) unrelated to this slice; pure unit tests + `build` are green.

# Curses slice (the "create free bond" verb, weaponized — taglock + echo)

Inspired by HEE's 11-type curse system (verified from the jar: `mechanics/curse/CurseType` — TELEPORTATION/
CONFUSION/TRANQUILITY/SLOWNESS/WEAKNESS/BLINDNESS/DEATH/DECAY/VAMPIRE/REBOUND/LOSS, delivered as a
projectile + technical-curse entity ticking `uses` times). Hexerei's realisation: a curse is a free,
un-sealed `Bond` laid on a player's `PlayerSoulData.marks`; the spirit's grip (`disposition.fear`) ticks
down each second and the curse departs when `fear ≤ 0`.

- **Taglock** — right-click a player to store their UUID+name in the taglock's NBT (Witchery's taglock).
  Dropped on a curse rite's circle alongside the domain reagent, it **targets** the victim.
- **Three curse rites** (`STONE→Clumsiness`/`THRESHOLD→Unluckiness`/`DEATH→Weakness`), one `CurseRite(domain)`
  class. Sacrifices are domain reagents **no other rite uses** (sandwort / glowing_spore / blood_moss).
- **The echo (anti-spam)** — every successful curse cast also lays a **milder copy (~20% strength, one tier
  down) on the caster** via `RitualContext.currentCaster()` (the new player-handle ThreadLocal). Cast five
  curses → bear five echoes. The echo is distinguished by `spiritType` suffix `_echo`; `CurseTickHandler`
  reads it to pick FULL vs ECHO modifier/potion values. Self-target → no echo (no double-dip).
- **`Curse`** is pure + unit-tested (effectFor domain×strength, FULL/ECHO modifier values, grip/isSpent/
  finiteness, `isEcho`). Lifetime: `CURSE_TICKS = 600` (one tick/sec → ~10 min). All `[UNVERIFIED]`.
- **`CurseTickHandler`** (same 20-tick boundary) applies effects **self-correctingly**: each tick removes
  every known curse modifier UUID from the four attributes (BLOCK/ENTITY_REACH, ENTITY_GRAVITY, LUCK),
  then re-adds exactly the still-active set — no stale modifier survives a lifted curse. Potions (Weakness/
  Unluck) are refreshed and lapse ~2s after the last active curse.
- **Clumsiness (STONE):** `ForgeMod.BLOCK_REACH`×0.60, `ENTITY_REACH`×0.60, `ENTITY_GRAVITY`×1.40 (attr).
  ECHO: ×0.88 / ×0.88 / ×1.12 (~20% of the debuff reduction).
- **Unluckiness (THRESHOLD):** `Attributes.LUCK` −2 + Unluck II (full) / −0.4 + Unluck I (echo).
- **Weakness (DEATH):** Weakness II (amp 1, full) / Weakness I (amp 0, echo).
- **The caster handle is no longer deferred** — `RitualContext` gained a nullable `caster` UUID +
  `currentCaster()`; `RitualActivation.tryPerform(level, pos, player)` overload threads it from
  `RitualSigilBlock.use`; backward-compatible (`tryPerform(level, pos)` delegates with null).
  **Side benefit:** unblocks amulet/bound-beast owner-binding in a one-line follow-up.

## Known `[UNVERIFIED]` / out of scope
- All `Curse` constants (CURSE_TICKS, modifier values) are first-pass; tune by playtest.
- **Crit-chance reduction** (requested) — not an attribute in MC 1.20.1; substituted by luck/drop reduction.
- **Cleansing rite** (the counter), **offline-target curses**, **curse projectile**, **fear-scaled
  strength**, the remaining HEE curses — follow-up slices.
- **`runGameTestServer`** still blocked by the pre-existing Patchouli mixin error.
