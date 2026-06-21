# Hexerei — Herbs (Crops) Slice: Design Spec

**Date:** 2026-06-21
**Status:** Approved (direction), pending spec review
**Mod:** Hexerei — original Forge 1.20.1 witchcraft mod
**Builds on:** the Altar slice (`docs/superpowers/specs/2026-06-21-altar-design.md`).

## 1. Goal

Add Hexerei's **herb crops** (herb-crop family) to Forge 1.20.1 as the second slice: the base reagents for the future brewing system, and content that lights up the altar power table already stubbed in the Altar slice. All values fixed by the Hexerei herb design notes.

## 2. Scope

### In scope — 8 crops + their items

| Crop | block id | water | maxAge | bonemeal | seed item | harvest item |
|---|---|---|---|---|---|---|
| Belladonna | `belladonna` | no | 4 | big | `seeds_belladonna` | `belladonna_flower` |
| Mandrake | `mandrake` | no | 4 | big | `seeds_mandrake` | `mandrake_root` |
| Artichoke | `artichoke` | **yes** | 4 | big | `seeds_artichoke` | `artichoke` (food) |
| Snowbell | `snowbell` | no | 4 | big | `seeds_snowbell` | vanilla `minecraft:snowball` (+20% `icy_needle`) |
| Wormwood | `wormwood` | no | 4 | big | `seeds_wormwood` | `wormwood` |
| Mindrake | `mindrake` | no | 4 | **+1** | `mindrake_bulb` (seed==produce) | `mindrake_bulb` |
| Wolfsbane | `wolfsbane` | no | **7** | **+1** | `seeds_wolfsbane` | `wolfsbane` |
| Garlic | `garlic` | no | **5** | big | `garlic` (seed==produce) | `garlic` |

- **bonemeal "big"** = `+random(2..maxAge)`; **"+1"** = `+1` (the documented fertilize inversion).
- New items: 6 seeds (`seeds_*`), 2 dual seed/produce (`mindrake_bulb`, `garlic`), 5 produce ingredients (`belladonna_flower`, `mandrake_root`, `wormwood`, `wolfsbane`, `icy_needle`), 1 food (`artichoke`). Snowbell produce = vanilla snowball.

### In scope — mechanics (by design)
- **Growth** (randomTick): requires light ≥ 9 above; rate `f = getGrowthRate(...)` (1.0 base, +fertile-soil bonus over the 3×3 below, −crowding, Mindrake `/1.5`); grows one age when `rand.nextInt((int)(25.0F/f)+1) == 0`. Integer truncation preserved.
- **Soil** (`canSurvive`/`mayPlaceOn`): land crops on grass/dirt/farmland/self/wormwood; water crop (artichoke) on water.
- **Bonemeal** (`BonemealableBlock`): `+random(2..maxAge)` or `+1`.
- **Drops** (custom `getDrops`, not loot JSON): immature → 1 seed; mature normal → `3+fortune` rolls of a seed each at `nextInt(15)≤7` (~53%) + 1 produce + (Snowbell 20% `icy_needle`); Mindrake → 1 seed + 25% produce.
- **Wormwood** stacks: when mature, grows a second wormwood in the air above.
- **Altar synergy**: `WitchCropBlock` contributes 4/20 to altar power (wires the `// FUTURE SLICE` CROP entry).

### Out of scope (future, documented)
- **Mandrake/Mindrake live entities** (the screaming mob spawn-on-harvest) — for now Mandrake always drops its root, no entity. Mindrake drops as specified, no entity.
- **Treefyd** (seed-only entity spawner, not a staged crop).
- Seed **acquisition** via Mutandis/tallgrass (seeds come from the crop drop + creative tab for now).
- Mindrake dropped-item 3s lifespan nicety.

## 3. Architecture

```
block/crop/
  WitchCropBlock        BushBlock + BonemealableBlock; per-crop AGE (0..maxAge), growth, soil, drops, bonemeal, wormwood stacking
  CropGrowth            pure: getGrowthRate weighting + the (int)(25/f)+1 step test (unit-testable)
  CropDrops             pure: given (mature?, fortune, flags, RandomSource) -> which item-roles to emit (unit-testable)
registry/
  HexereiCrops         table of the 8 crops (params + seed/produce suppliers) driving registration
  HexereiBlocks        + the 8 crop blocks (extends existing)
  HexereiItems         + seeds (ItemNameBlockItem), produce items, artichoke food
power/AltarPowerTable   + CROP entry (4/20)
blockentity/AltarBlockEntity.resolveDynamic   + instanceof WitchCropBlock -> CROP
```

**Key design decisions:**
- **`WitchCropBlock extends BushBlock implements BonemealableBlock`** (not `CropBlock`): per-crop AGE maxima (4/5/7) need their own `IntegerProperty`; vanilla `CropBlock` hardcodes `AGE_7`. AGE property is created/cached per max via a static-stash passed before `super()` (block init is single-threaded, safe).
- **Seeds = `ItemNameBlockItem(cropBlock, …)`** — vanilla seed placement (places the crop at age 0 where it can survive). Mindrake-bulb & Garlic are this same item, also usable as ingredients.
- **Drops via overridden `getDrops(BlockState, LootParams.Builder)`** — the probabilistic logic is awkward in loot JSON and has per-crop specials; centralised in code. Pure helpers (`CropGrowth`, `CropDrops`) are unit-tested.
- **Registration via a `HexereiCrops` table** — one row per crop drives block + seed + produce, avoiding 8× boilerplate.

## 4. Design decisions

| Topic | Naive approach | Decision |
|---|---|---|
| age metadata 0..stages | block metadata | per-crop `IntegerProperty AGE` 0..maxAge |
| drops | `getDrops(...)` ArrayList | overridden `getDrops` (same math); fortune from `LootContextParams.TOOL` |
| Mandrake harvest | may spawn a Mandrake entity | **always drops root** this slice (entity = FUTURE SLICE) |
| Artichoke food | `food=20, sat=0.0` | `FoodProperties` nutrition 20, saturation 0.0 **[UNVERIFIED — high; may retune]** |
| seed/produce identity (Mindrake, Garlic) | one item both roles | one `ItemNameBlockItem` per such crop |
| Snowbell produce | vanilla snowball | vanilla `minecraft:snowball` |
| growth rate weighting | the growth-rate function 3×3 | replicate the weights exactly (center fertile full, neighbours /4, crowding halve, Mindrake /1.5) |

## 5. Testing
1. **Unit** (`CropGrowthTest`, `CropDropsTest`): the `(int)(25/f)+1` step probability boundaries; bonemeal `+random(2..max)` vs `+1`; drop role-sets for immature / mature-normal / mindrake / snowbell across seeded RandomSource.
2. **GameTest** (`hexerei:crops_*`): plant `seeds_belladonna` on farmland → force random-ticks to max age → break → assert produce + ≥0 seeds dropped; assert a mature crop contributes to a nearby altar's `maxPower`; assert an immature crop drops only a seed.
3. **Build** green; **dedicated-server smoke**: `/setblock` a mature crop, `/loot`/break readback, no errors.

## 6. Risks ( , mitigated)
- Per-crop AGE maxima (4/5/7) — static-stash cached property; GameTest covers a max-4 and the max-7 (wolfsbane) crop.
- Growth-rate truncation `(int)(25/f)` — pure `CropGrowth`, unit-tested at boundaries.
- `canFertilize` inversion — encoded explicitly in the table + unit test.
- Artichoke water placement — included but flagged `[needs-verify]`; if `ItemNameBlockItem` won't place on water cleanly, a small custom placement or defer-artichoke fallback (documented).
- `icy_needle` texture possibly not extracted — re-extract in the assets task; if truly absent, use a placeholder + note.

## 7. Deliverables
8 crop blocks + 14 items in `hexerei`, wired to the altar; passing unit + GameTest + server smoke with evidence; analysis + spec + plan committed; `DESIGN-NOTES.md` updated (crops done; Mandrake entity / Treefyd / Mutandis deferred).
