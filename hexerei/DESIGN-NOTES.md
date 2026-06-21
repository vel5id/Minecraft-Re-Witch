# Design Notes

Hexerei is built slice-by-slice. Every numeric value in the code is a deliberate design choice,
documented here per slice — never invented on the fly. Values flagged `[UNVERIFIED]` still need
in-game confirmation.

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
