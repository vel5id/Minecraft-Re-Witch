# Hexerei — Ritual Circles Slice: Design Spec

**Date:** 2026-06-22
**Mod:** Hexerei — original Forge 1.20.1 witchcraft mod (`com.vel5id.hexerei`)
**Builds on:** Altar (power) + Herbs (sacrifice ingredients).
**Reference:** [`../research/2026-06-22-ritual-analysis.md`](../research/2026-06-22-ritual-analysis.md)

## 1. Goal

Add **ritual circles** — Hexerei's fourth slice: draw a chalk **glyph circle** around a **ritual circle**
center block, place a **sacrifice** item, draw on **altar power**, and right-click the center to perform a
**rite**. Our own design inspired by classic witch-ritual mechanics; values are our choices.

## 2. The ritual loop (player-facing)

1. Place a **Ritual Circle** block (the center / activator).
2. Surround it with a ring of **Ritual Glyph** blocks (a small circle — 12 glyphs at radius 2).
3. Drop the ritual's **sacrifice** item near the center.
4. Have an **altar** with enough power within range.
5. Right-click the Ritual Circle → if the circle is complete + the sacrifice is present + altar power is
   available → the **rite fires**: it consumes the sacrifice + altar power and produces its effect.

## 3. Starter ritual (this slice)

| Ritual | Circle | Sacrifice | Power | Effect |
|---|---|---|---|---|
| **Rite of the Tempest** | small (12 glyphs) | 1× `hexerei:mandrake_root` | 100 | summons a thunderstorm (`setWeatherParameters`, ~10 min) |

(One ritual proves the full loop. More rites + larger circles + glyph colors + timed multi-step rites = future.)

## 4. Circle geometry (pure, from analysis)

Small circle = 12 glyph positions at radius 2 around the center, on the center's Y-layer
(offsets `(0,±2),(±1,±2),(±2,±1),(±2,0)`). All 12 must be Ritual Glyph blocks. This is a pure
function `RitualCircle.isSmallComplete(predicate isGlyph, BlockPos center)` — unit-testable.

## 5. Components

```
block/ritual/
  RitualGlyphBlock     flat chalk slab (no collision, top render, requires solid block below, drops nothing)
  RitualCircleBlock    center activator; use() -> RitualActivation.tryPerform
ritual/
  RitualCircle         pure: small-circle completeness check over a BlockPos predicate (unit-testable)
  Rite                 interface: perform(ServerLevel, BlockPos center)
  TempestRite          sets a thunderstorm
  RitualRecipe         record: smallCircleRequired, sacrificeItemId, powerCost, Rite
  RitualRecipes        registry + match(boolean circleComplete, String sacrificeId) -> Optional<RitualRecipe> (pure)
  RitualActivation     static: scan circle + find sacrifice ItemEntity + match + consume power+sacrifice + rite.perform
registry/  + RITUAL_GLYPH, RITUAL_CIRCLE blocks/items; (no BlockEntity — the slice's rite is instant)
power/     reuse AltarPowerManager.closest(level,pos) -> consumePower
```

**Activation (`RitualActivation.tryPerform(ServerLevel, BlockPos center)`):**
1. `circleComplete = RitualCircle.isSmallComplete(p -> level.getBlockState(p).is(RITUAL_GLYPH), center)`.
2. find a sacrifice `ItemEntity` within a small radius of the center; read its item id.
3. `RitualRecipes.match(circleComplete, sacrificeId)` → recipe, else fail.
4. consume `recipe.powerCost` from the nearest in-range altar (`AltarPowerManager` query/consume); fail if none can pay.
5. consume one sacrifice item; `recipe.rite().perform(level, center)`; spawn a success particle/sound.

The rite is **instant** this slice (no BlockEntity/ticking needed). Timed multi-step rites are future.

## 6. 1.20.1 implementation notes
- `RitualGlyphBlock extends Block`: flat `VoxelShape` (1/16 tall), `noCollission()`, `RenderShape.MODEL`,
  `canSurvive` requires the block below to have a sturdy top face (`isFaceSturdy(..., UP)`); empty loot via
  drop-self loot table or `noLootTable()` (it's chalk — drop nothing is fine, use `noLootTable()`).
- `RitualCircleBlock extends Block` with `use()` → on server call `RitualActivation.tryPerform`; return
  `sidedSuccess`. (No BE.)
- Sacrifice scan: `level.getEntitiesOfClass(ItemEntity, AABB around center, alive & non-empty)`.
- Power: `AltarPowerManager.get(server).query(level, center)`, first-fit `consumePower(cost)`.
- Rite weather: `serverLevel.setWeatherParameters(0, durationTicks, true, true)`.

## 7. Testing
1. **Unit** (`RitualCircleTest`, `RitualRecipesTest`): small-circle completeness over a position set (complete
   vs missing-one vs wrong-block); recipe match (complete+right sacrifice → rite; incomplete or wrong sacrifice → none).
2. **GameTest** (`hexerei:ritual_*`): place center + 12 glyphs on a floor; drop a mandrake_root; inject a powered
   source (deterministic, as with the cauldron); call `RitualActivation.tryPerform`; assert it returns success,
   `level.isThundering()` is true, the sacrifice ItemEntity is gone, and power was consumed. Negatives: incomplete
   circle → no storm; no power → no storm.
3. **Build** green; **server smoke**: place the two blocks, verify registration + no errors.
4. **Adversarial review** of the slice; fix confirmed findings.

## 8. Out of scope (future)
Chalk item (draw/erase glyphs), 3 glyph colors + color-count matching, medium/large circles, timed multi-step
rites (column raise, cook, summon, teleport), cauldron-triggered rituals, coven/witch scaling, misfortune/backfire,
the full rite catalog. Only the **center block + glyph + small-circle detection + 1 instant rite** here.
