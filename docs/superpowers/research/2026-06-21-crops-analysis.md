# Hexerei Herb (Crop) System — Design Reference

## 1) Crop Mechanic Overview

The Hexerei herb system is built on a single shared crop block class parameterized per herb, and a single shared seed item class. Every herb crop is an instance of the shared crop block parameterized by three values: `waterPlant` (water vs land), `growthStages` (max age, default 4), and `canFertilize` (bonemeal behavior, default true). All harvest produce — except the Snowbell (which yields vanilla snowballs) and Garlic/Mindrake (which reuse their own seed item) — are distinct registered ingredient items.

Key architectural facts:
- The crop block is a non-collidable bush-type block with `Material.PLANTS`, hardness 0, grass step sound, and no block entity. Bounding box `(0,0,0)–(1,0.25,1)`. It is not added to a creative tab directly. It is bonemealable.
- The seed item back-registers BOTH the seed prototype and the crop/harvest prototype onto the block in the seed's constructor. This is how the block knows what to drop.
- Age ranges `0..growthStages` **inclusive** → `growthStages + 1` distinct states. The crop is mature when `age == growthStages` (NOT `growthStages − 1`).
- Drop logic lives in the Forge `getDrops` list, not the vanilla item-drop path. The vanilla single-item-drop methods are mostly vestigial.

## 2) Exact Per-Crop Table

| Crop | Registry ID | waterPlant | growthStages | canFertilize | Seed Item | Harvest Item |
|------|-------------|------------|--------------|--------------|-----------|--------------|
| CROP_BELLADONNA | `hexerei:belladonna` | false | 4 | true | SEEDS_BELLADONNA (`hexerei:seedsbelladonna`) | belladonnaFlower |
| CROP_MANDRAKE | `hexerei:mandrake` | false | 4 | true | SEEDS_MANDRAKE (`hexerei:seedsmandrake`) | mandrakeRoot — *may spawn a live Mandrake entity instead* |
| CROP_ARTICHOKE | `hexerei:artichoke` | **true** | 4 | true | SEEDS_ARTICHOKE (`hexerei:seedsartichoke`, waterPlant=true) | artichoke (Edible food=20 sat=0.0) |
| CROP_SNOWBELL | `hexerei:snowbell` | false | 4 | true | SEEDS_SNOWBELL (`hexerei:seedssnowbell`) | **vanilla minecraft:snowball** — *+20% Icy Needle bonus* |
| CROP_WORMWOOD | `hexerei:wormwood` | false | 4 | true | SEEDS_WORMWOOD (`hexerei:seedswormwood`) | wormwood — *stacking/tall plant* |
| CROP_MINDRAKE | `hexerei:mindrake` | false | 4 | **false** | SEEDS_MINDRAKE (`hexerei:seedsmindrake`, icon `ingredient.mindrakebulb`); cropItem=null→copies seed | **= seed item (Mindrake Bulb)** — *special drop logic, slow growth* |
| CROP_WOLFSBANE | `hexerei:wolfsbane` | false | **7** | **false** | SEEDS_WOLFSBANE (`hexerei:seedswolfsbane`) | wolfsbane |
| CROP_GARLIC | `hexerei:garlicplant` (texture base `hexerei:garlic`) | false | **5** | true | SEEDS_GARLIC (`hexerei:garlic`); cropItem=null→copies seed | **= seed item (Garlic)** |

Notes preserved exactly:
- `growthStages` is the **max age value**, so number of stage textures = `growthStages + 1`: belladonna/mandrake/artichoke/snowbell/mindrake = 5 textures (0–4), garlic = 6 textures (0–5), wolfsbane = 8 textures (0–7).
- Garlic and Mindrake pass `cropItemStack = null`; setting the crop item to null defaults the crop prototype to a copy of the seed, so those two crops drop their own seed as the "harvest."
- Constructor mapping: belladonna/mandrake/artichoke/snowbell = crop block with `waterPlant` only (defaults 4, true). Mindrake = `(false,4,false)`. Wolfsbane = `(false,7,false)`. Garlic = `(false,5,true)`.

## 3) Growth: Exact Chance Formula + Conditions + Fertilize

**Random-tick growth conditions:**
1. Calls super (bush-type random tick — validates the plant can stay).
2. Requires world light level at `(x, y+1, z) >= 9` (`MIN_LIGHT_LEVEL`).
3. Only advances if `age < growthStages`.

**Growth chance formula** (preserve integer truncation exactly):
```
f = getGrowthRate(world, x, y, z)   // float, base 1.0
if (rand.nextInt((int)(25.0F / f) + 1) == 0) { age += 1; }
```
- Per-tick probability ≈ `1 / ((int)(25/f) + 1)`. With `f = 1.0` → **1/26**. Richer soil raises `f` (faster); Mindrake divides rate by 1.5 (slower).
- **`getGrowthRate`** weighting (must be preserved exactly): base 1.0, increased by `canSustainPlant`/`isFertile` of the 3×3 of blocks **below** (center weighted full, neighbors `/4`, up to ~3× on fertile farmland), **halved** if crowded by same-type neighbors in the 3×3, **divided by 1.5** for Mindrake. The C-style `(int)` truncation of `25.0F/f` **before** `+1` must be preserved or growth speed will differ.

**Fertilize / bonemeal** (server-side only, returns false on client):
```
stages = growthStages; current = age;
if (current == stages) return false;             // already mature → no effect
if (!canFertilize) l = current + 1;              // +1 step only
else l = current + MathHelper.getRandomIntegerInRange(rand, 2, stages);  // +[2..stages]
l = min(l, stages);                              // clamp
setAge(l, flag 2); return true;
```
- **CRITICAL NAMING INVERSION:** `canFertilize == true` → BIG random jump `+[2..growthStages]`. `canFertilize == false` → still allows `+1` per bonemeal use. Easy to implement backwards.
- Bonemealable wiring: canUseBonemeal returns `age != growthStages`. Server shouldGrow returns `true` always. Grow calls `fertilize(...)`.

**Soil validity:**
- waterPlant → only still water.
- land plant → grass, dirt, farmland, this same block, or `CROP_WORMWOOD`. **Land crops do NOT require farmland**; plain grass/dirt works (fertile farmland only boosts rate via `isFertile`).
- Plant type returns Water if waterPlant, else Plains (from the bush base).

## 4) Drop Logic — Mature vs Immature + Special Cases

Drops computed in `getDrops(world, x, y, z, age, fortune)`. The harvest method is overridden to force the chance/fortune param to **0** (suppresses the vanilla harvest multiplier); real drops only via `getDrops`. The drop-as-item method is overridden to spawn item entities with `delayBeforeCanPickup = 10`.

**IMMATURE (`age < growthStages`):**
- Drops the default single seed item → exactly **1 seed** (`getSeedItemStack`). No crop, no special drops.

**MATURE (`age >= growthStages`):** (wrapped by the Mandrake peaceful/daytime gate below)

*CASE Mindrake* (`cropItemPrototype item == SEEDS_MINDRAKE`):
- Always add **1 seed** (`getSeedItemStack`).
- With **1/4 chance** (`rand.nextInt(4) == 0`) also add **1 crop item** (`getCropItemStack`).
- Does NOT use the normal `0..3+fortune` seed loop or the `+1` crop.

*CASE normal* (everything else):
- **Seeds:** loop `n` from `0` to `(3 + fortune)` exclusive; each iteration adds 1 seed if `rand.nextInt(15) <= 7` (≈ **8/15 ≈ 53.3%** per roll). So 0..(3+fortune) seeds, each ~53%.
- **Crop items:** add the default single crop item (= **1**).
- **Snowbell extra:** if `seedItemPrototype item == SEEDS_SNOWBELL` and `rand.nextDouble() <= 0.2` (20%), also add **1 Icy Needle**.

**Special cases:**

- **Mindrake** (`cropItemPrototype.getItem() == SEEDS_MINDRAKE`): Growth rate divided by 1.5 (slower). Mature drop: always 1 seed + 1/4 chance of 1 crop item (does NOT use the normal 0..3+fortune seed loop or the +1 crop). Dropped mindrake item entities get a forced lifespan of 3 seconds (`TimeUtil.secsToTicks(3)`) so they despawn quickly.

- **Mandrake root** (`cropItemPrototype matches mandrakeRoot`): When harvested mature, there is a chance the root pulls itself out as a live Mandrake entity instead of dropping items. The gate: drops items normally only if `(difficulty == PEACEFUL)` OR `(daytime AND rand.nextDouble() > 0.9)` OR `(night AND rand.nextDouble() > 0.1)`. Otherwise (NOT peaceful, and: daytime with 90% chance, or night with 10% chance) it spawns a Mandrake entity at block center `(0.5+x, 0.05+y, 0.5+z)` server-side, adds it to the world, and sends an EXPLODE particle packet to nearby players (no item drop). `DAY_MANDRAKE_SPAWN_CHANCE = 0.9`, `NIGHT_MANDRAKE_SPAWN_CHANCE = 0.1`. So the live-mandrake escape happens 90% in day, 10% at night, never on peaceful.

- **Snowbell** (`seedItemPrototype.getItem() == SEEDS_SNOWBELL`): On mature harvest (normal branch), in addition to seeds+crop, a 20% chance (`rand.nextDouble() <= 0.2`) to also drop 1 Icy Needle.

- **Wormwood** (`this == CROP_WORMWOOD`): Tall/stacking crop. When already at max age, on random tick it grows a new wormwood block above (age 0) if it is the bottom segment (block below is not wormwood) and the space above is air. Also is valid soil for itself (`CROP_WORMWOOD` is in the canSustainPlant list and in the soil-validity check). Uses render type 1 (cross/cutout) instead of 6.

- **Garlic / Mandrake (planted):** No garlic-specific branch exists in the crop class. Garlic is just a normal crop using the default drop logic (no special case; it drops its own seed since cropItem==seed). Mandrake's only special logic is the live-entity spawn described above; there is no separate 'mandrake leaf' handling here.

- **Render:** returns 1 (cross/standard cutout) for `CROP_SNOWBELL`, `CROP_WOLFSBANE`, `CROP_WORMWOOD`; returns 6 (vanilla crops/hash render) for all other crops.

## 5) Implementation Mapping (Forge 1.20.1, Mojmaps)

| Construct | 1.20.1 (Forge/NeoForge, Mojmaps) Target |
|------------------|------------------------------------------|
| Shared crop block (bush base) | `CropBlock` (or `BushBlock` if vanilla CropBlock semantics are too constraining) with a custom `IntegerProperty AGE = 0..growthStages` (NOT vanilla 0..7). Implement `BonemealableBlock`. |
| Age (`0..growthStages` inclusive) | `IntegerProperty AGE`, max = `growthStages`, mature when `state.getValue(AGE) == growthStages`. Per-crop AGE max differs (4/5/7) — define one block subclass instance per crop or a constructor-parameterized block. |
| Shared seed item | Custom `Item` (or `ItemNameBlockItem`) implementing `IPlantable` with a `BlockItem`-style `useOn`: place the crop block at AGE 0 on valid soil; waterPlant ⇒ allow placement on water surface and report `PlantType.WATER`. |
| Harvest ingredient items | Individual registered `Item`s (one per harvest product). Only the unlocalizedName/texture-id strings carry over. |
| Drops via Forge list | Custom `Block.getDrops(BlockState, LootParams.Builder)` override OR a data-driven loot table. **NOT vanilla CropBlock loot** — the seed-loop (`rand.nextInt(15) <= 7`, count `3+fortune`) and special cases must be replicated exactly. Recommend `getDrops` override (Java) because loot tables cannot express the day/night/difficulty Mandrake gate or the Mindrake/Snowbell conditional logic cleanly. |
| Random-tick growth | `randomTick(BlockState, ServerLevel, BlockPos, RandomSource)`; preserve light≥9 check at pos.above(), `(int)(25.0F/f)+1` formula, and `getGrowthRate` 3×3 weighting. |
| Bonemeal | `BonemealableBlock`: `isValidBonemealTarget` ⇔ `AGE != max`; `isBonemealSuccess` ⇔ true; `performBonemeal` ⇔ `fertilize(...)` preserving the canFertilize inversion. |
| `canSustainPlant` / `isFertile` of block below | Forge `BlockState.canSustainPlant(...)` / `isFertile(...)` equivalents in 1.20.1. |
| Render type 1 vs 6 | `RenderType.cutout()` for all crops; for type-6 crops use a `crop`-style flat-cross model, for type-1 crops (snowbell/wolfsbane/wormwood) use a `cross` model. |
| Mandrake entity / EXPLODE particle packet | Implement the Mandrake entity first (dependency), then spawn it server-side at `(x+0.5, y+0.05, z+0.5)` with `ParticleTypes.EXPLOSION` broadcast. |

## 6) Harvest / Seed / Crop Items — Texture + Lang IDs

**Seed items** (texture id → unlocalized):
| Seed | Texture | Unlocalized | Lang (fresh) |
|------|---------|-------------|--------------|
| SEEDS_BELLADONNA | `hexerei:ingredient.seedsBelladonna` | `hexerei:seedsbelladonna` | Belladonna Seeds |
| SEEDS_MANDRAKE | `hexerei:ingredient.seedsMandrake` | `hexerei:seedsmandrake` | Mandrake Seeds |
| SEEDS_ARTICHOKE | `hexerei:ingredient.seedsArtichoke` | `hexerei:seedsartichoke` | Water Artichoke Seeds |
| SEEDS_SNOWBELL | `hexerei:ingredient.seedsSnowbell` | `hexerei:seedssnowbell` | Snowbell Seeds |
| SEEDS_WORMWOOD | `hexerei:ingredient.seedswormwood` | `hexerei:seedswormwood` | Wormwood Seeds |
| SEEDS_WOLFSBANE | `hexerei:ingredient.seedswolfsbane` | `hexerei:seedswolfsbane` | Wolfsbane Seeds |
| SEEDS_MINDRAKE | `hexerei:ingredient.mindrakebulb` | `hexerei:seedsmindrake` | Mindrake Bulb |
| SEEDS_GARLIC | `hexerei:garlic` (NOT under `ingredient.*`) | `hexerei:garlic` | Garlic |

**Harvest items** (texture id → index / unlocalized):
| Item | Texture | Index | Lang |
|------|---------|------|------|
| belladonnaFlower | `hexerei:ingredient.belladonna` | 21 | Belladonna Flower |
| mandrakeRoot | `hexerei:ingredient.mandrakeRoot` | 22 | Mandrake Root |
| artichoke (Edible food=20 sat=0.0 alwaysEdible=false) | `hexerei:ingredient.artichoke` | 69 | Water Artichoke Globe |
| icyNeedle (snowbell bonus) | `hexerei:ingredient.icyNeedle` | 78 | (Icy Needle) |
| wormwood | `hexerei:ingredient.wormwood` | 111 | Wormwood |
| wolfsbane | `hexerei:ingredient.wolfsbane` | 156 | Wolfsbane |
| Mindrake Bulb (= SEEDS_MINDRAKE) | `hexerei:ingredient.mindrakebulb` | — | (= seed) |
| Garlic (= SEEDS_GARLIC) | `hexerei:garlic` | — | Garlic |
| Snowball (snowbell produce) | `minecraft:snowball` (vanilla) | — | (vanilla) |

**Block lang (`tile.*`):** `hexerei:belladonna`=Belladonna, `hexerei:mandrake`=Mandrake, `hexerei:artichoke`=Water Artichoke, `hexerei:snowbell`=Snowbell, `hexerei:wormwood`=Wormwood, `hexerei:wolfsbane`=Wolfsbane, `hexerei:mindrake`=Mindrake, `hexerei:garlicplant`=Garlic.
**Entity lang:** `entity.hexerei.mandrake.name`=Mandrake, `entity.hexerei.mindrake.name`=Mindrake.

## 7) Assets Inventory + Source Folder

**Asset root:** `assets/hexerei/textures/{blocks,items}/`.

**Crop stage block textures (44 total)** — non-uniform counts (code MUST match these maximums):
- belladonna 0–4 (5), mandrake 0–4 (5), artichoke 0–4 (5), snowbell 0–4 (5), mindrake 0–4 (5) → AGE max 4
- garlic 0–5 (6) → AGE max 5
- wolfsbane 0–7 (8) → AGE max 7

All at `assets/hexerei/textures/blocks/<crop>_stage_<i>.png`.

**Seed item textures (7):** `ingredient.seedsBelladonna`, `ingredient.seedsMandrake`, `ingredient.seedsArtichoke`, `ingredient.seedsSnowbell`, `ingredient.seedsTreefyd`, `ingredient.seedswolfsbane`, `ingredient.seedswormwood` (all `.png` under `assets/hexerei/textures/items/`).
- **[UNVERIFIED] Note:** the asset list shows `ingredient.seedsTreefyd` but NOT `ingredient.mindrakebulb` or `garlic` in that specific array; however the notes confirm `ingredient.seedsSnowbell.png` and `mindrakebulb`/`garlic` exist (they appear in the harvest list). Treefyd is a seed-only entity spawner with NO crop stages — out of scope for the staged-crop slice.

**Harvest item textures (7):** `ingredient.belladonna`, `ingredient.mandrakeRoot`, `ingredient.wormwood`, `ingredient.wolfsbane`, `ingredient.artichoke`, `ingredient.mindrakebulb`, `garlic` (all under `assets/hexerei/textures/items/`).

**NOT yet included (out of scope but present):** entity textures `mandrake.png`/`mindrake.png`, `statuemandrake.png`, garlic garland decoratives, hunter-armor "garlicked" variants, `ingredient.icyNeedle.png` (referenced in code/lang but not listed among harvest textures — **flag to add**).

## 8) Risks & Open Questions

1. **canFertilize naming inversion** — `true` = big random `+[2..stages]` jump, `false` = `+1`. Verify against exact `MathHelper.getRandomIntegerInRange(rand,2,stages)`. Easy to invert.
2. **Age semantics** — `growthStages+1` states (0..growthStages), mature == growthStages (not −1). Use `IntegerProperty AGE 0..growthStages`; do NOT assume vanilla 0..7. Per-crop max differs (4/5/7).
3. **Growth formula truncation** — `(int)` truncation of `25.0F/f` BEFORE `+1`, then `nextInt(...)==0`. Preserve integer truncation exactly or growth speed differs.
4. **getGrowthRate weighting** — 3×3 below (center full, neighbors `/4`), crowding halving, Mindrake `/1.5`. Use `canSustainPlant`/`isFertile` 1.20.1 equivalents; preserve all weights exactly.
5. **Drop logic is `getDrops`, not vanilla loot** — seed loop `rand.nextInt(15) <= 7` (~53.3%), count `3+fortune`. Implement as custom `getDrops`/loot; the vanilla single-item-drop methods are vestigial.
6. **Mandrake live-spawn gate** — probabilistic, difficulty/time dependent; does NOT drop the root item when it spawns. Mandrake entity + EXPLODE packet must exist (dependency: Mandrake entity first). Spawn position `0.05+y` (slightly above block).
7. **Snowbell Icy Needle** — checked on `seedItemPrototype` (not crop), 20% via `nextDouble() <= 0.2`. `ingredient.icyNeedle` texture flagged as possibly missing (item 78).
8. **Wormwood stacking** — grows upward when mature, self-as-soil, changes valid-soil set. Render types differ per crop (1 vs 6) → map to 1.20.1 RenderType.cutout + correct model (cross vs flat-cross).
9. **Mindrake item lifespan** — dropped items get 3s lifespan + short pickup delay (gameplay-relevant; forces quick pickup).
10. **Harvest method forces fortune/chance = 0** — vanilla harvest multiplier suppressed; real drops only via `getDrops`.
11. **Cross-references (need verification):** exact identity/registration of `CROP_WORMWOOD`, `SEEDS_MINDRAKE`, `SEEDS_SNOWBELL`, `mandrakeRoot`, `icyNeedle`, and the bush base behavior (placement validation, `canBlockStay`, plant type) — referenced but defined elsewhere.
12. **[UNVERIFIED] Artichoke food value** — `food=20, sat=0.0` is the hunger/saturation value (20 half-hunger is the entire bar — very high). Re-tune for 1.20.1 `FoodProperties`; likely intended ~ a few hunger, not full. Verify against in-game balance.
13. **[UNVERIFIED] Harvest item indices (21,22,69,78,111,156)** — historical indices; map each to its own registered Item; only unloc/texture strings carry over.
14. **Garlic seed==crop and Mindrake seed==crop** — both share one item used as seed AND produce (`cropItem==null` defaults to seed copy). Use a single item for both roles per crop.
15. **Snowbell produce** — uses vanilla `minecraft:snowball`, not a custom ingredient. Keep using vanilla snowball.
16. **[UNVERIFIED] Snowbell harvest texture** — no distinct snowbell harvest `ingredient.*` texture (produce is vanilla snowball), consistent with code; confirm no snowbell-specific produce item is expected.
17. **Treefyd** — seed-only entity spawner (`ingredient.seedsTreefyd`, "Treefyd Seed"), NOT a staged crop (no stage textures). Out of scope for this slice but shares the seed/lang namespace — do not accidentally include as a crop block.
