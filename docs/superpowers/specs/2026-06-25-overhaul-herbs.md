# Overhaul — Herbs & Mushrooms (textures + new content)

Slice owner: Herbs/Crops. Status: **DESIGN**. Date: 2026-06-25.

This spec (a) regenerates **every** existing crop texture (8 crops: stages + seed icon), (b) renames
`snowbell` → `hellebore` ("Морозник"), and (c) adds **6 new herbs + 3 mushrooms** as full buildable content
(registry id, block class, seed item, maxAge, soil rule, drops, produce, brewing/altar role, texture assets).

Grounded in the real code:
- `HexereiCrops.crop(name, maxAge, water, big, mindrake, snowbell, wormwood, produce, bonus)` is the **only**
  registration entry point. It registers `HexereiBlocks.BLOCKS.register(name, …WitchCropBlock)` + an
  `ItemNameBlockItem` seed via `HexereiItems.ITEMS.register(seedName(name), …)`. (`HexereiCrops.java:52-66`)
- `WitchCropBlock` is a `BushBlock + BonemealableBlock` (NOT vanilla `CropBlock`); per-crop AGE max via
  `ageProperty(maxAge)` cache. `mayPlaceOn` = farmland (or water if `water`, or self if `wormwood`).
  Drops come from the overridden `getDrops` → `CropDrops.roll`, **not** loot JSON. (`WitchCropBlock.java`)
- Crops render as **vanilla cross-models**: every stage model parents `minecraft:block/crop` with a single
  `"crop"` texture; blockstate maps `age=N` → `hexerei:block/<name>_stage_N`. Seeds parent
  `minecraft:item/generated` with `layer0 = hexerei:item/<seedname>`. (verified on belladonna/wormwood/mindrake)
- Crops contribute **4/20** to altar power via `AltarBlockEntity.resolveDynamic` `instanceof WitchCropBlock`
  branch — so **every** crop below (any `WitchCropBlock` subclass) automatically gives altar power, no wiring needed.
  (`AltarBlockEntity.java:232-233`)
- Seeds are surfaced in the creative tab by `HexereiCrops.SEED_ITEMS.forEach(...)`
  (`HexereiCreativeTabs.java:30`) — automatic for anything registered through `crop(...)`.
- Existing brew ingredient ids (for stating brewing roles, **not** designing brews here):
  `hexerei:mandrake_root, belladonna_flower, wolfsbane, wormwood, icy_needle, artichoke`
  (`BrewRecipes.java:13-27`).

**Texture pipeline reminder** (from the build): crop stages are **block cross-model sprites** — they MUST have a
transparent background so the X-billboard reads as a plant, so they are generated with `remove_bg=true`,
`pixel_grid=16` (vanilla crop sprites are 16×16-native; we author at **32×32** for a slightly crisper sprite that
still tiles on the cross), `width=height=32`. Seeds are **64×64 item icons**, `remove_bg=true`, `pixel_grid=32`.
Ground/decoration mushroom & moss **block faces** fill the square: `remove_bg=false`, `pixel_grid=32`,
`width=height=32`. Logical ids below map 1:1 to `target_path`; all paths are under
`hexerei/src/main/resources/assets/hexerei/`.

> NOTE on stage count: a crop with `maxAge = M` has **M+1** stages (`age=0..M`). The texture/model/blockstate
> count below follows that. Belladonna/Mandrake/Artichoke/Hellebore/Mindrake `maxAge=4` ⇒ **5** stages;
> Garlic `maxAge=5` ⇒ **6**; Wolfsbane `maxAge=7` ⇒ **8**. (Matches the files already on disk.)

---

## PART 1 — Redo existing crop textures (8 crops)

No code changes for this part except the Hellebore rename (Part 2). Every entry below **overwrites** an existing
placeholder PNG at the same path. Models/blockstates already exist and are correct — do not touch them.

For each crop: N stage sprites (block cross sprite) + 1 seed icon. Stage 0 = freshly-sprouted tiny sprout; the
final stage = mature flowering/harvest-ready plant; intermediate stages interpolate height + flower/fruit reveal.

### Per-crop visual identity (drives prompts)
| crop | stages | seed | mature look |
|------|--------|------|-------------|
| belladonna | 5 | `seeds_belladonna` | deadly nightshade: dark green leaves, drooping purple bell flowers, glossy black berries |
| mandrake | 5 | `seeds_mandrake` | leafy green rosette, hints of a humanoid root at base, dull yellow berries |
| artichoke | 5 | `seeds_artichoke` | water plant: broad blue-green serrated leaves, central spiky green artichoke head |
| hellebore (was snowbell) | 5 | `seeds_hellebore` | winter rose: frost-blue/white five-petal flowers, dark serrated leaves, faint frost rime |
| wormwood | 5 | `seeds_wormwood` | tall silvery-green feathery wormwood, small yellow flower clusters at top |
| mindrake | 5 | `mindrake_bulb` (dual seed/produce) | small dark thorny plant, glowing pale-violet bulb at base |
| wolfsbane | 8 | `seeds_wolfsbane` | tall stalk, deep indigo hooded monkshood flowers up the stem |
| garlic | 6 | `garlic` (dual seed/produce) | low green garlic shoots, white papery bulb partly exposed at soil |

### Asset entries — STAGE SPRITES (block cross, remove_bg=true, 32×32, pixel_grid=16)
The texture textures array at the bottom of this spec enumerates every PNG. To keep this section readable, the
rule is uniform:
- `target_path = textures/block/<crop>_stage_<n>.png` for `n` in `0..maxAge`.
- Prompt template: *"Minecraft pixel-art crop cross-sprite, transparent background, <crop visual at stage n/M
  maturity>, side view billboard, ~16-color palette, chunky pixels."*

### Asset entries — SEED ICONS (item, remove_bg=true, 64×64, pixel_grid=32)
- belladonna → `textures/item/seeds_belladonna.png`
- mandrake → `textures/item/seeds_mandrake.png`
- artichoke → `textures/item/seeds_artichoke.png`
- hellebore → `textures/item/seeds_hellebore.png` (**renamed**, see Part 2; old `seeds_snowbell.png` deleted)
- wormwood → `textures/item/seeds_wormwood.png`
- wolfsbane → `textures/item/seeds_wolfsbane.png`
- mindrake → `textures/item/mindrake_bulb.png` (dual seed/produce)
- garlic → `textures/item/garlic.png` (dual seed/produce)

### Produce icons to also regenerate (existing produce items; placeholder PIL art)
`belladonna_flower.png`, `mandrake_root.png`, `wormwood.png`, `wolfsbane.png`, `artichoke.png`, `icy_needle.png`
(all `textures/item/`, 64×64, remove_bg=true, pixel_grid=32). The Hellebore drop change (Part 2) may retire
`icy_needle` — see [DECISION-HELLEBORE-DROP].

(Full enumerated list in the `textures` array.)

---

## PART 2 — Rename snowbell → hellebore ("Морозник" / "Hellebore")

### Registry rename (recommended, code change owned by implement slice)
In `HexereiCrops.java` rename the field + id:
```java
public static final RegistryObject<Block> HELLEBORE = crop("hellebore", 4, false, true, false, true, false,
        () -> Items.SNOWBALL, () -> HexereiItems.ICY_NEEDLE.get());   // see [DECISION-HELLEBORE-DROP]
```
The `snowbell` boolean flag on `WitchCropBlock` (the constructor's `snowbell` param, which drives the +20%
`bonus` drop in `CropDrops.roll`) **stays semantically "the hellebore one"** — keep passing `true` so the bonus
drop still fires. (Do **not** rename that boolean to avoid a wide `CropDrops`/`WitchCropBlock` churn; it is an
internal flag, not user-visible. Note this in DESIGN-NOTES.)

### Asset/file renames (block uses cross-model sprites; all paths under assets/hexerei/)
- `blockstates/snowbell.json` → `blockstates/hellebore.json` (rewrite the 5 model refs to `hexerei:block/hellebore_stage_N`)
- `models/block/snowbell_stage_{0..4}.json` → `models/block/hellebore_stage_{0..4}.json` (texture ref → `hexerei:block/hellebore_stage_N`)
- `textures/block/snowbell_stage_{0..4}.png` → `textures/block/hellebore_stage_{0..4}.png` (regenerated, Part 1)
- `models/item/seeds_snowbell.json` → `models/item/seeds_hellebore.json` (layer0 → `hexerei:item/seeds_hellebore`)
- `textures/item/seeds_snowbell.png` → `textures/item/seeds_hellebore.png` (regenerated)
- Seed item id: `seeds_snowbell` → `seeds_hellebore` (auto, because `seedName("hellebore")` = `seeds_hellebore`)

### Lang
- en_us: `block.hexerei.hellebore` = "Hellebore"; `item.hexerei.seeds_hellebore` = "Hellebore Seeds".
  Remove `block.hexerei.snowbell` / `item.hexerei.seeds_snowbell`.
- ru_ru: `block.hexerei.hellebore` = "Морозник"; `item.hexerei.seeds_hellebore` = "Семена морозника".
  Remove the old `snowbell` keys.

### [DECISION-HELLEBORE-DROP] keep icy_needle/snowball, or re-theme?
- **Option A (chosen):** KEEP the existing drops — mature drop = vanilla `minecraft:snowball` as produce (already
  wired via `() -> Items.SNOWBALL`) + 20% `hexerei:icy_needle` bonus. Rationale: zero code/recipe churn, the
  frost theme already fits "Морозник" (a frost-rose), and `icy_needle` is already a brew ingredient
  (`WITCHS_SIGHT`, `WITHERING_BILE`). Just regenerate the two icons with a frost-rose look.
- Option B (rejected): re-theme drops to a new `hellebore_petal` produce item. Rejected: breaks two existing
  brew recipes that reference `icy_needle` and adds a produce item for no mechanical gain this slice.

> The mature produce being vanilla `snowball` is an existing quirk (DESIGN-NOTES "Herbs"); we preserve it.

---

## PART 3 — New herbs (6)

All farmland-planted staged crops register through the existing `crop(...)` helper unless a NEW variant is needed
(mistletoe, blood moss). New produce items are registered in `HexereiItems`. Brewing roles are stated as
**ingredient potential only** (brews are designed elsewhere — `2026-06-25-overhaul-brews.md`).

### 3.1 Вороний глаз — Crow's Eye / Herb-Paris — `crowseye`
- **registry id:** block `hexerei:crowseye`, seed `hexerei:seeds_crowseye`
- **block class:** `WitchCropBlock` via `crop("crowseye", 4, false, true, false, false, false, () -> HexereiItems.CROWSEYE_BERRY.get(), null)`
- **maxAge:** 4 (⇒ 5 stages)
- **soil:** farmland only (default rule)
- **drops:** immature → 1 seed; mature → 3+fortune seed rolls + 1 `crowseye_berry` produce (standard `CropDrops`).
- **produce (NEW item):** `hexerei:crowseye_berry` — `new Item(new Item.Properties())`, **non-edible** (poisonous).
- **brewing role:** a "poison/blinding" reagent (pairs naturally with belladonna). Altar power 4/20 (automatic).
- **look:** single whorl of four broad leaves with one glossy black berry at the centre (true herb-paris).

### 3.2 Ласточкина трава — Celandine / Swallow-wort — `celandine`
- **registry id:** block `hexerei:celandine`, seed `hexerei:seeds_celandine`
- **block class:** `WitchCropBlock` via `crop("celandine", 4, false, true, false, false, false, () -> HexereiItems.CELANDINE.get(), null)`
- **maxAge:** 4 (⇒ 5 stages)
- **soil:** farmland only
- **drops:** standard (3+fortune seeds + 1 `celandine` produce when mature)
- **produce (NEW item):** `hexerei:celandine` — `new Item(...)`, non-edible. (Bright-yellow flower; "warm/light"
  reagent — folkloric eye/wart cure ⇒ a cleansing/healing brew ingredient.)
- **brewing role:** healing / cleansing reagent (counter-theme to crowseye poison). Altar 4/20.
- **look:** slender stems, small four-petal bright-yellow flowers, lobed green leaves; bleeds orange sap motif.

### 3.3 Хмель — Hops — `hops` (tall, like wormwood)
- **registry id:** block `hexerei:hops`, seed `hexerei:seeds_hops`
- **block class:** `WitchCropBlock` with the **wormwood "big/tall" behaviour** — pass `wormwood=true`:
  `crop("hops", 4, false, true, false, false, /*wormwood*/ true, () -> HexereiItems.HOPS.get(), null)`
  This reuses the existing two-tall self-stacking logic (mature bottom grows an upper segment via `randomTick`,
  `mayPlaceOn` allows standing on self). **No new class needed.**
- **maxAge:** 4 (⇒ 5 stages). The mature upper segment uses the SAME `hops_stage_4` sprite (vanilla cross),
  matching how wormwood reuses its top sprite.
- **soil:** farmland; mature plant grows a 2nd block upward (wormwood exception path).
- **drops:** standard. The upper segment is a separate `WitchCropBlock` at maxAge ⇒ also drops produce when broken
  (same as wormwood today — acceptable, matches existing behaviour).
- **produce (NEW item):** `hexerei:hops` — `new Item(...)`, non-edible. (Soporific cones — a sleep/calm reagent,
  thematically reinforcing the Sleeping Draught line.)
- **brewing role:** sedative reagent (pairs with mandrake_root). Altar 4/20.
- **look:** tall climbing bine, papery green cone "hops" hanging near the top.

### 3.4 Кровавый мох — Blood Moss — `blood_moss` — **[DECISION] ground decoration block, NOT a staged crop**
- **[DECISION-BLOODMOSS]:** Implement as a **non-staged carpet-style ground decoration**, like vanilla moss
  carpet — NOT a `WitchCropBlock`. Rationale: "moss" reads as a flat ground cover with no growth stages; a
  cross-sprite billboard would look wrong. This needs a **new minimal block class**.
- **registry id:** block `hexerei:blood_moss`, item `hexerei:blood_moss` (a plain `BlockItem`, registered in
  `HexereiItems`, NOT through `crop(...)` — so it is a decoration item, not a seed).
- **block class (NEW):** `BloodMossBlock extends CarpetBlock` (vanilla `CarpetBlock` gives the 1-pixel-tall flat
  shape + "needs support below" survival). No BlockEntity, no NBT.
  - `BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).noCollission().instabreak().sound(SoundType.MOSS_CARPET).randomTicks()`
  - **spread (optional, mirrors moss):** in `randomTick`, low chance to convert an adjacent bare `dirt/stone` top
    into blood_moss within radius 1 — **gate behind taint** for flavour: only spread on chunks with
    `ChunkTaintData.getTaint(chunk) > 0` (taint subsystem already exists; `ChunkTaintData` is the SavedData).
    If keeping it simple, ship **non-spreading** v1 and note spread as a follow-up. **[DECISION]: ship
    non-spreading v1** (deterministic, no extra GameTest burden).
- **placement rule:** placeable on any solid top face (CarpetBlock's `canSurvive`). Thematically "grows on taint",
  but mechanically place-anywhere like a carpet for v1.
- **drops:** itself — needs a **loot JSON** `data/hexerei/loot_tables/blocks/blood_moss.json` (drop self), because
  unlike `WitchCropBlock` this block does NOT override `getDrops`. (Follow the `charred_stone.json` loot pattern.)
- **altar power:** does NOT auto-contribute (not a `WitchCropBlock`). The backlog reserves an `EMBER_MOSS 4/20`
  table slot; reuse the moss-tier idea — add an `AltarPowerTable` entry + a `bs.is(HexereiBlocks.BLOOD_MOSS)`
  branch in `resolveDynamic` giving **4/20** (FUTURE-SLICE-consistent). [DECISION]: wire 4/20 now since the table
  entry concept already exists; flag `[UNVERIFIED]` for balance.
- **produce/brewing role:** the moss block itself is the reagent (break to collect). "Blood" reagent for
  strength/blood-themed brews (pairs with mandrake_root for BLOODWORT_TONIC family). No separate produce item.
- **textures (BLOCK FACE, remove_bg=false, 32×32, pixel_grid=32):** single texture `blood_moss.png` used as the
  carpet's `wool`/`pattern` faces. Model: a carpet model parenting `minecraft:block/carpet` with
  `"wool": "hexerei:block/blood_moss"`. Blockstate: single variant. Item model parents the block model (carpet).

### 3.5 Песчанка — Sandwort — `sandwort`
- **registry id:** block `hexerei:sandwort`, seed `hexerei:seeds_sandwort`
- **block class:** `WitchCropBlock` via `crop("sandwort", 4, false, false /*small, not bonemeal-big*/, false, false, false, () -> HexereiItems.SANDWORT.get(), null)`
  - **[DECISION-SANDWORT-SOIL]:** Sandwort is a sand-dweller. Vanilla `mayPlaceOn` only allows farmland. Rather
    than add a per-crop soil enum, **plant it on farmland like every other crop for v1** (player tills sand-area
    soil), and note a future `sand`-placement variant. Reusing the farmland rule keeps it inside the existing
    `crop(...)` helper with zero `WitchCropBlock` changes. (If a sand rule is wanted later, add a `sand` boolean
    to `WitchCropBlock.mayPlaceOn` mirroring the `water` branch — out of scope here.)
  - `big=false` ⇒ bonemeal gives +1 per use (a slow, hardy desert herb), matching the wolfsbane/mindrake feel.
- **maxAge:** 4 (⇒ 5 stages)
- **drops:** standard (3+fortune seeds + 1 `sandwort` produce mature)
- **produce (NEW item):** `hexerei:sandwort` — `new Item(...)`, non-edible. ("Dry/earth" reagent — fits
  resistance/grounding brews; pairs with wolfsbane.)
- **brewing role:** resistance/anchoring reagent. Altar 4/20.
- **look:** low cushion of tiny needle-leaves with small white star flowers (true sandwort).

### 3.6 Омела — Mistletoe — `mistletoe` — **[DECISION] grows on logs/leaves, NOT farmland**
- **[DECISION-MISTLETOE]:** Mistletoe is a **parasitic plant on wood**. It must NOT plant on farmland. This needs
  a `WitchCropBlock`-style staged crop but with a **wood-attachment placement rule** → introduce a small NEW
  subclass rather than overloading the `crop(...)` flag soup.
- **block class (NEW):** `MistletoeBlock extends WitchCropBlock` overriding only `mayPlaceOn`:
  ```java
  @Override protected boolean mayPlaceOn(BlockState g, BlockGetter w, BlockPos p) {
      return g.is(BlockTags.LOGS) || g.is(BlockTags.LEAVES);
  }
  ```
  Everything else (AGE property, growth, bonemeal, drops via `CropDrops`) is inherited. Register it directly in
  `HexereiBlocks`/`HexereiItems` (NOT through `crop(...)`, since `crop(...)` hard-codes `new WitchCropBlock(...)`).
  Add a parallel registration helper or register inline:
  ```java
  RegistryObject<Block> MISTLETOE = HexereiBlocks.BLOCKS.register("mistletoe",
      () -> new MistletoeBlock(4, /*water*/false, /*big*/true, /*mindrake*/false, /*snowbell*/false,
            /*wormwood*/false, seedSup, () -> HexereiItems.MISTLETOE_SPRIG.get(), null, cropProps()));
  RegistryObject<Item> SEEDS_MISTLETOE = HexereiItems.ITEMS.register("seeds_mistletoe",
      () -> new ItemNameBlockItem(MISTLETOE.get(), new Item.Properties()));
  ```
  (mirror `HexereiCrops.crop` body; also add to `CROP_BLOCKS`/`SEED_ITEMS`/`SEED_BY_CROP` so creative tab + altar
  synergy pick it up. Because it still `instanceof WitchCropBlock`, the 4/20 altar branch fires automatically.)
  - **`canSurvive`:** inherited override already routes through `mayPlaceOn(below)` — but mistletoe should hang
    on the **side/under** of wood. For v1, keep the inherited "supported below by log/leaves" rule (plant on top
    of a log/leaf block) to avoid touching `canSurvive` geometry. **[DECISION]: v1 = on-top-of-wood placement**;
    true side-hanging attachment (a directional block) is a follow-up.
- **registry id:** block `hexerei:mistletoe`, seed `hexerei:seeds_mistletoe`
- **maxAge:** 4 (⇒ 5 stages). `big=true` ⇒ bonemeal jumps (a fast-spreading parasite).
- **drops:** standard. Mature → 3+fortune seeds + 1 `mistletoe_sprig`.
- **produce (NEW item):** `hexerei:mistletoe_sprig` — `new Item(...)`, non-edible. (Classic witch reagent —
  protection/Druidic; pairs with garlic for ward-flavoured brews / charm fuel.)
- **brewing role:** protection/ward reagent. Altar 4/20.
- **look:** clump of paired oval evergreen leaves with white pearl berries.

---

## PART 4 — Mushrooms (3)

### [DECISION-MUSHROOMS] vanilla-style mushroom blocks vs staged crops
All three are implemented as **vanilla-style `MushroomBlock`-equivalents** (a `BushBlock` that survives in
darkness on solid ground), **NOT staged crops**. Rationale: mushrooms have no growth stages in vanilla, place on
dark solid ground (not farmland), and reuse the small mushroom shape. They render as a **single cross-sprite**
(one texture each, no `_stage_N`), like vanilla `red_mushroom`. This means **one new tiny block class**
`WitchMushroomBlock` shared by all three, plus per-mushroom textures.

- **block class (NEW, shared):** `WitchMushroomBlock extends BushBlock` (optionally `BonemealableBlock` to grow a
  huge variant — **omit huge-variant for v1**; bonemeal does nothing). Behaviour mirrors vanilla `MushroomBlock`:
  - `getShape` = small mushroom box (`Block.box(5,0,5,11,6,11)`).
  - `mayPlaceOn` / survival: place on any solid-top block (dirt, stone, mycelium, podzol, nylium, blood_moss);
    survive only where `getRawBrightness < 13` OR full sky access (mirror vanilla mushroom rule via
    `MushroomBlock`-style check). **[DECISION]:** copy vanilla mushroom survival (dark or sky-lit). Keep simple.
  - One per-instance field for the optional glow (zevanty).
  - Drops: override `getDrops` to drop **itself** (the mushroom item), OR ship a loot JSON. **[DECISION]: ship a
    loot JSON** `data/hexerei/loot_tables/blocks/<mushroom>.json` (drop self) — consistent with non-crop blocks
    and avoids a `getDrops` override. Glowing zevanty additionally has a small chance to drop a `glowing_spore`
    produce — handled by a second loot pool, NOT code.
- **items:** each mushroom block gets a `BlockItem` in `HexereiItems` (placeable mushroom item). These are the
  reagents.
- **altar power:** mushrooms are NOT `WitchCropBlock` ⇒ no auto power. Leave them off the altar table for v1
  (they are reagents, not nature-power blocks). [DECISION]: no altar contribution.

### 4.1 Зеванты — Zevanty (mod-specific glowing mushroom) — `zevanty`
- **registry id:** block `hexerei:zevanty`, item `hexerei:zevanty` (BlockItem). RU name kept: "Зеванты".
- **class:** `new WitchMushroomBlock(/*glow*/ true, props)` — `props` adds
  `.lightLevel(s -> 8)` so the placed mushroom emits light 8 (the "glowing" identity).
- **placement:** dark solid ground (vanilla mushroom rule). Glows, so it can self-illuminate cave farms.
- **drops:** itself + 25% `hexerei:glowing_spore` (NEW produce item) via a second loot pool.
- **produce (NEW item):** `hexerei:glowing_spore` — `new Item(...)`. Brewing role: a **light/night-vision** reagent
  (pairs with icy_needle for WITCHS_SIGHT family) and a glow/charge component.
- **textures (BLOCK cross, remove_bg=true, 32×32, pixel_grid=16):** `zevanty.png` (single sprite — pale-violet
  glowing cap with luminous gills). Plus `glowing_spore.png` (item, 64×64, remove_bg=true, pixel_grid=32).
- **model:** cross model parenting `minecraft:block/cross` with `"cross": "hexerei:block/zevanty"`. Blockstate:
  single variant. Item model: `minecraft:item/generated` layer0 = `hexerei:block/zevanty` (vanilla mushroom item
  pattern uses the block sprite as the icon).

### 4.2 Дождевики — Puffball — `puffball`
- **registry id:** block `hexerei:puffball`, item `hexerei:puffball`. RU: "Дождевики".
- **class:** `new WitchMushroomBlock(/*glow*/ false, props)`.
- **placement:** dark/grassy solid ground (vanilla mushroom rule).
- **drops:** itself (loot JSON). Optional: breaking a mature puffball could spawn a spore puff particle — cosmetic,
  out of scope.
- **produce/brewing role:** the puffball block is the reagent — a **poison/spore** reagent (pairs with crowseye /
  belladonna for WITHERING_BILE family).
- **textures (BLOCK cross, remove_bg=true, 32×32, pixel_grid=16):** `puffball.png` (round off-white puffball on a
  short stalk). Item icon reuses the block sprite (generated, layer0 = block sprite).

### 4.3 Паутинник — Webcap — `webcap`
- **registry id:** block `hexerei:webcap`, item `hexerei:webcap`. RU: "Паутинник".
- **class:** `new WitchMushroomBlock(/*glow*/ false, props)`.
- **placement:** dark solid ground (vanilla mushroom rule).
- **drops:** itself (loot JSON).
- **produce/brewing role:** the webcap is the reagent — a **deliriant/confusion** reagent (a nausea/blindness
  component; pairs with mandrake_root for a hallucinogen brew family). Webcaps are famously toxic — non-edible.
- **textures (BLOCK cross, remove_bg=true, 32×32, pixel_grid=16):** `webcap.png` (rusty-orange domed cap with a
  web-veil pattern on a slender stalk). Item icon reuses block sprite.

---

## PART 5 — Registration & lang touch-list (for the implement slice)

### `HexereiItems.java` — NEW items
- Produce: `CROWSEYE_BERRY`, `CELANDINE`, `HOPS`, `SANDWORT`, `MISTLETOE_SPRIG`, `GLOWING_SPORE` — all
  `new Item(new Item.Properties())` (non-edible, brew reagents).
- BlockItems: `BLOOD_MOSS` (BlockItem on `HexereiBlocks.BLOOD_MOSS`), `ZEVANTY`, `PUFFBALL`, `WEBCAP`
  (BlockItems on their mushroom blocks).
- Seed for mistletoe is registered alongside the mistletoe block (see 3.6).
- Hellebore: the `seeds_hellebore` id flows automatically from the `crop("hellebore",…)` rename.

### `HexereiBlocks.java` — NEW blocks
- `BLOOD_MOSS` (`BloodMossBlock`), `ZEVANTY`/`PUFFBALL`/`WEBCAP` (`WitchMushroomBlock`), `MISTLETOE`
  (`MistletoeBlock`). The 5 farmland crops (crowseye, celandine, hops, sandwort) come from `crop(...)` calls in
  `HexereiCrops.java`, which register their blocks into `HexereiBlocks.BLOCKS` internally.

### `HexereiCrops.java` — NEW crop rows
```java
public static final RegistryObject<Block> CROWSEYE  = crop("crowseye", 4, false, true, false, false, false, () -> HexereiItems.CROWSEYE_BERRY.get(), null);
public static final RegistryObject<Block> CELANDINE = crop("celandine",4, false, true, false, false, false, () -> HexereiItems.CELANDINE.get(), null);
public static final RegistryObject<Block> HOPS      = crop("hops",     4, false, true, false, false, true,  () -> HexereiItems.HOPS.get(), null);
public static final RegistryObject<Block> SANDWORT  = crop("sandwort", 4, false, false,false, false, false, () -> HexereiItems.SANDWORT.get(), null);
// hellebore: rename of SNOWBELL row (Part 2). Mistletoe: separate registration (Part 3.6).
```

### Creative tab
- Crop seeds auto-appear (`SEED_ITEMS`). New produce items + mushroom/moss BlockItems must be **explicitly added**
  to the relevant accept-list in `HexereiCreativeTabs.java` (the seeds loop only covers `SEED_ITEMS`; produce
  items are added elsewhere — follow the existing pattern that adds `BELLADONNA_FLOWER` etc.).

### Lang (en_us.json + ru_ru.json) — NEW keys
| key | en | ru |
|-----|----|----|
| block.hexerei.hellebore | Hellebore | Морозник |
| item.hexerei.seeds_hellebore | Hellebore Seeds | Семена морозника |
| block.hexerei.crowseye | Crow's Eye | Вороний глаз |
| item.hexerei.seeds_crowseye | Crow's Eye Seeds | Семена вороньего глаза |
| item.hexerei.crowseye_berry | Crow's Eye Berry | Ягода вороньего глаза |
| block.hexerei.celandine | Celandine | Ласточкина трава |
| item.hexerei.seeds_celandine | Celandine Seeds | Семена ласточкиной травы |
| item.hexerei.celandine | Celandine | Ласточкина трава |
| block.hexerei.hops | Hops | Хмель |
| item.hexerei.seeds_hops | Hops Seeds | Семена хмеля |
| item.hexerei.hops | Hops | Хмель |
| block.hexerei.sandwort | Sandwort | Песчанка |
| item.hexerei.seeds_sandwort | Sandwort Seeds | Семена песчанки |
| item.hexerei.sandwort | Sandwort | Песчанка |
| block.hexerei.mistletoe | Mistletoe | Омела |
| item.hexerei.seeds_mistletoe | Mistletoe Seeds | Семена омелы |
| item.hexerei.mistletoe_sprig | Mistletoe Sprig | Веточка омелы |
| block.hexerei.blood_moss | Blood Moss | Кровавый мох |
| item.hexerei.blood_moss | Blood Moss | Кровавый мох |
| block.hexerei.zevanty | Zevanty | Зеванты |
| item.hexerei.zevanty | Zevanty | Зеванты |
| item.hexerei.glowing_spore | Glowing Spore | Светящаяся спора |
| block.hexerei.puffball | Puffball | Дождевик |
| item.hexerei.puffball | Puffball | Дождевик |
| block.hexerei.webcap | Webcap | Паутинник |
| item.hexerei.webcap | Webcap | Паутинник |

Remove: `block.hexerei.snowbell`, `item.hexerei.seeds_snowbell` (both langs).

### Loot tables (NEW JSON, drop-self pattern from `charred_stone.json`)
- `data/hexerei/loot_tables/blocks/blood_moss.json`
- `data/hexerei/loot_tables/blocks/zevanty.json` (+ 2nd pool: 25% `glowing_spore`)
- `data/hexerei/loot_tables/blocks/puffball.json`
- `data/hexerei/loot_tables/blocks/webcap.json`
- (mistletoe & the 4 new farmland crops drop via `WitchCropBlock.getDrops` — **no** loot JSON, matching existing crops.)

### Blockstates / models (NEW)
- Farmland crops (crowseye, celandine, hops, sandwort) + hellebore: blockstate `age=0..4` → stage models;
  each stage model parents `minecraft:block/crop`; seed item model parents `minecraft:item/generated`.
  (Copy belladonna's JSON shape verbatim, swap the name.)
- Mushrooms (zevanty, puffball, webcap): single-variant blockstate → one cross model parenting
  `minecraft:block/cross`; item model `minecraft:item/generated` layer0 = block sprite.
- Blood moss: single-variant blockstate → carpet model parenting `minecraft:block/carpet`
  (`"wool": "hexerei:block/blood_moss"`); item model parents that block model.

### DESIGN-NOTES additions (the implement slice must record)
- snowbell→hellebore rename; the internal `snowbell` boolean kept (now "hellebore bonus-drop flag").
- 4 new farmland crops use existing `crop(...)` flags (hops = wormwood-tall; sandwort = small/bonemeal+1).
- 2 new `WitchCropBlock` subclasses: `MistletoeBlock` (log/leaf placement), and `BloodMossBlock extends
  CarpetBlock` (decoration). `WitchMushroomBlock` (shared, vanilla-mushroom behaviour, non-staged).
- Blood moss altar power 4/20 `[UNVERIFIED]` (reuses the reserved EMBER_MOSS table tier).
- Mushroom survival = vanilla dark/sky rule; no huge variant v1; no altar power.
- All 6 new produce items + glowing_spore are non-edible brew reagents (brew recipes designed in overhaul-brews).

---

## Brewing-potential summary (NO brews designed here — ingredient roster only)
| new reagent | thematic role | natural partner |
|-------------|---------------|-----------------|
| crowseye_berry | poison / blinding | belladonna_flower |
| celandine | healing / cleansing | (counter to poison) |
| hops | sedative / sleep | mandrake_root |
| sandwort | resistance / anchoring | wolfsbane |
| mistletoe_sprig | protection / ward | garlic |
| blood_moss (block) | blood / strength | mandrake_root |
| zevanty / glowing_spore | light / night-vision / charge | icy_needle |
| puffball (block) | spore / poison | crowseye_berry |
| webcap (block) | deliriant / nausea | mandrake_root |
