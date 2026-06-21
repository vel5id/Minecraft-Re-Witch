# Hexerei — Altar Core: Design Reference (Power Mechanic)

## 1. Overview of the Altar Mechanic

The **Altar** is a multiblock structure that acts as the central power source for Hexerei's magic systems (brewing, distilling, and rituals/rites). A complete altar is exactly **6 altar blocks** arranged in a flat **2×3 (or 3×2)** slab footprint. One of those six blocks becomes the **core** — the only block that does work, holds power, and registers as a power source.

**How power works:**

- The core periodically **scans a 29×29×29 cube** around itself, counting "nature" blocks (saplings, logs, leaves, crops, flowers, water, mushrooms, plus Hexerei's own plant blocks and a few high-value vanilla blocks like the dragon egg).
- Each matching block type contributes `min(count, limit) × factor` power. The sum across all sources defines **`maxPower`**.
- The core then **passively recharges** its current `power` toward `maxPower × powerScale` at a fixed cadence (every 20 ticks).
- **Artefacts** placed on/above the altar blocks (candelabra, chalice, wand, pentacle, infinity egg, skulls/heads) apply multipliers and bonuses to recharge speed, power, range, and enhancement level.
- Other features (cauldron, brazier, spinning wheel, crystal ball, rites) **query the nearest altar within range** and **atomically debit** power via `consumePower`. Features never add power; only the altar recharges itself.

Power is exposed to the player through a **read-only GUI** and visually via green nature particles drifting from nearby plants toward an active altar.

---

## 2. Exact Data Tables

### 2.1 Block Properties

| Property | Value | Notes |
|---|---|---|
| Hardness | **2.0F** | set in block constructor |
| Material | **stone/rock** | |
| Resistance / light / step sound | **none set** (rock defaults) | — |
| Render type | INVISIBLE (block entity, custom rendered) | |
| Textures | `altar` (side), `altar_top`, `altar_joined`, `altar_joined_top` | texture swap by joined state |

### 2.2 Scan Radius

| Constant | Value | Notes |
|---|---|---|
| **`SCAN_DISTANCE`** | **14** | Power scan: x,y,z each from `core−14` to `core+14` → **29×29×29 box**, radius 14 in ALL axes including vertical. Up to ~24,000 block reads. |
| Particle effect range (cosmetic only) | **RADIUS=16, VERT=4** | drives ambient particles; NOT the power scan. |
| `getRange()` (usable power radius) | **16 × rangeScale** (default 16) | brewing/rite reach; unrelated to the 14-block power scan. |

### 2.3 COMPLETE Block → Power Value Table

`getPower()` per source = `min(count, limit) × factor`. "Max" column = `limit × factor`.

#### Tag-based sources (wrapped in try/catch)

| Source | factor | limit | max | Notes |
|---|---|---|---|---|
| every registered sapling | 4 | 20 | 80 | |
| every registered log | 2 | 50 | 100 | |
| every registered leaves | 3 | 100 | 300 | |

#### Vanilla block sources

| Source (block) | factor | limit | max |
|---|---|---|---|
| grass block | 2 | 80 | 160 |
| dirt | 1 | 80 | 80 |
| farmland | 1 | 100 | 100 |
| tallgrass / grass+fern | 3 | 50 | 150 |
| dandelion | 4 | 30 | 120 |
| poppy etc. (red flower) | 4 | 30 | 120 |
| wheat crops | 4 | 20 | 80 |
| water still | 1 | 50 | 50 |
| brown_mushroom | 3 | 20 | 60 |
| red_mushroom | 3 | 20 | 60 |
| cactus | 3 | 50 | 150 |
| sugar cane | 3 | 50 | 150 |
| pumpkin block | 4 | 20 | 80 |
| pumpkin_stem | 3 | 20 | 60 |
| brown_mushroom_block | 3 | 20 | 60 |
| red_mushroom_block | 3 | 20 | 60 |
| melon_block | 4 | 20 | 80 |
| melon_stem | 3 | 20 | 60 |
| vine | 2 | 50 | 100 |
| mycelium | 1 | 80 | 80 |
| **dragon_egg** | **250** | **1** | **250** |
| cocoa | 3 | 20 | 60 |
| carrots crop | 4 | 20 | 80 |
| potatoes crop | 4 | 20 | 80 |

#### Hexerei block sources

| Source (Hexerei block) | factor | limit | max |
|---|---|---|---|
| `DEMON_HEART` | 40 | 2 | 80 |
| `CROP_BELLADONNA` | 4 | 20 | 80 |
| `CROP_MANDRAKE` | 4 | 20 | 80 |
| `CROP_ARTICHOKE` | 4 | 20 | 80 |
| `CROP_SNOWBELL` | 4 | 20 | 80 |
| `EMBER_MOSS` | 4 | 20 | 80 |
| `LEAVES` (Hexerei's own; separate from the vanilla leaves tag) | 4 | 50 | 200 |
| `LOG` (Hexerei's own; separate from the vanilla logs tag) | 3 | 100 | 300 |
| `SPANISH_MOSS` | 3 | 20 | 60 |
| `GLINT_WEED` | 2 | 20 | 40 |
| `CRITTER_SNARE` | 2 | 10 | 20 |
| `BLOOD_ROSE` | 2 | 10 | 20 |
| `GRASSPER` | 2 | 10 | 20 |
| `WISPY_COTTON` | 3 | 20 | 60 |
| **`INFINITY_EGG`** | **1000** | **1** | **1000** |

#### Catch-all category

| Source | factor | limit | max | Notes |
|---|---|---|---|---|
| Any other block that is a flower or crop **and not already in the table** | 2 | 4 | 8 | Computed once and cached by iterating the block registry. |

> There is **no separate global max-power cap** beyond the summed per-source `min(count,limit)×factor`.

### 2.4 Multiblock Rule

| Item | Value |
|---|---|
| **`ELEMENTS_IN_COMPLETE_ALTAR`** | **6** |
| Shape | flat **2×3 / 3×2** slab |
| Detection | server-side BFS flood-fill from the placed/broken block over **4 horizontal neighbours** (N/S/E/W) where the neighbour block is also an altar block |
| Validity rule | for every visited coord (except the excluded one), its count of same-block horizontal neighbours must be **≥2 AND ≤3** (corners=2, edge-centers=3) |
| Core selection | `newCore = (valid && visited.size()==6) ? visited.get(0) : null` |
| Apply | every visited block entity gets `setCore(newCore)`; the joined-state flag is written for each |
| Triggers | placement, break (exclude=self), explosion. A neighbour change only re-scans artefacts. Right-click re-checks validity. |
| Joined-state meaning | **unjoined** = not part of valid altar (core==null), plain textures. **joined** = part of complete altar (core!=null), `_joined` textures + nature particles. |

### 2.5 Tick Cadence & Max Power

| Item | Value |
|---|---|
| Worker | only the **core** block entity does work, server-side only |
| `basePowerPerUpdate` | **10.0F** |
| Recharge cadence | every **20 ticks** (`ticks % 20 == 0`), i.e. once/second |
| Recharge amount | `power = (int)min(power + 10.0F × rechargeScale, maxPower × powerScale)` — floored to int |
| Over-cap clamp | if `power > maxPower×powerScale` and `ticks%20==0`, clamp down |
| `maxPowerScaled` | `maxPower × powerScale` |
| Power scan throttle | full rescan runs only if `(ticks − lastPowerUpdate <= 0)` OR `(ticks − lastPowerUpdate > 100)` → at most ~once per **100 ticks (5s)** |
| Power field type | **float** store, floored to int on recharge; `maxPower` is float sum (mind off-by-one in display) |

### 2.6 Artefact Bonuses (above each altar block, offset 0,1,0)

| Artefact | Effect |
|---|---|
| vanilla torch | +1 recharge (candle slot) |
| `CANDELABRA` | +2 recharge (candle slot) |
| placed `ARTHANA` | +1 range |
| placed `MYSTIC_BRANCH` wand | +1 enhancement |
| placed koboldite pentacle | sets `pentacleFound` |
| `CHALICE` | +1 power (+2 if filled) |
| `INFINITY_EGG` block on top | sets `infinityFound` |
| Skull/head: skeleton | +1 power / +1 recharge |
| Skull/head: wither skeleton | **+3 power / +3 recharge** (intentionally shares the player bonus) |
| Skull/head: player | +3 power / +3 recharge |
| Post-scan: `pentacleFound` | `rechargeScale *= 2` |
| Post-scan: `infinityFound` | `rechargeScale *= 10` AND `powerScale *= 10` |

> Design note: the wither-skull bonus deliberately matches the player-skull bonus (+3/+3) rather than sitting between skeleton and player. This is an intentional balance choice — keep both at +3/+3.

### 2.7 NBT Keys

`Core`, `Power`, `MaxPower`, `PowerScale`, `RechargeScale`, `RangeScale`, `EnhancementLevel`

> [UNVERIFIED — decide the exact serialized form of the core coordinate: a single `Core` key versus separate `CoreX/CoreY/CoreZ` keys. Fix one form and keep it consistent across saves.]

---

## 3. Power API Contract

### 3.1 Power Source Interface

| Method | Meaning |
|---|---|
| `Level getLevel()` | world/dimension the altar lives in; reference-identity scoping in queries |
| `BlockPos getLocation()` | block pos of the altar **core**; used for distance + dedupe |
| `boolean isLocationEqual(BlockPos location)` | true if pos is exactly this block entity's pos |
| `boolean consumePower(float requiredPower)` | **atomic all-or-nothing debit**: if core power ≥ required, subtract and return true; else return false unchanged. Server-only (no-op/false on client). Delegates to core. |
| `float getCurrentPower()` | current stored core power (float). Sentinels: **−1.0F** if no core, **−2.0F** if queried on client. |
| `float getRange()` | reach in blocks = **16 × rangeScale** (default 16); squared, compared to `distanceSq`. This (not the caller's radius arg) is the real range filter. |
| `int getEnhancementLevel()` | altar tier from artefacts; read-only |
| `boolean isPowerInvalid()` | true if the source is no longer valid; used to evict dead sources |

**Related types:**
- **Null/suppression source** — a dead-zone marker (e.g. void bramble) with a fixed `getRange()` of **32.0F**. `isAreaNulled(level,pos)` returns true if a point is within range² of ANY null source. Note: it must filter by world identity (see Risks). Not part of the altar core slice — implement only if needed.
- **`RelativePowerSource`** — transient query-time wrapper: `distanceSq`, `rangeSq = getRange()²`, `isInWorld(level)` = `getLevel()==level` (reference identity), `isInRange()` = `distanceSq <= rangeSq`, `source()`. Squared distances only (no sqrt). Sorted ascending by distanceSq.

### 3.2 Registry Semantics (`AltarPowerManager`)

- The registry holds only the active altars, **server-side only** — all register/remove calls are gated behind a server-side check, so the client never holds altars. It should be keyed **per-`ServerLevel`** (not process-global) to avoid cross-world leakage — the key design hazard.
- **Registration (server-only)** in 3+ places: on core load, when a valid core is established, and on re-register when the core points at itself. **Removal**: on block entity removal and when the core is cleared (which also resets power fields to defaults: powerScale/rechargeScale/rangeScale=1, enhancementLevel=0).
- **`registerPowerSource`** dedupes: evicts entries that are null, invalid, or have the **same `getLocation()`** (a re-registered altar at the same coords evicts the stale entry), then adds.
- **`removePowerSource`** removes the passed source, then sweeps dropping null / invalid / entries whose live block entity at that pos no longer matches.
- **Query**: `get(Level, BlockPos, int radius)` → sorted `List<RelativePowerSource>`. The actual filter is each source's own `getRange()`, NOT the `radius` argument (see Risks). Filters by `isInWorld` + `isInRange`, sorts by distanceSq ascending, returns (possibly empty, never null). All consumers call `get(level, pos, 16)` then take the closest source as "the" altar.

### 3.3 Consumption Flow

- Consumers: sacrifice power, rite of protection circle, rite of hell on earth, rite of infusion/recharge, kettle, brazier, spinning wheel, crystal ball, cauldron, closest-power-source lookup.
- Read availability via `getCurrentPower()`, spend via `consumePower(required)` (boolean). Rites abort if it returns false. No public `addPower`/deposit method exists — altars only self-recharge.
- Note: keep the brew-cost descriptor (a trivial `int power` cost wrapper with `accumulate(other)`, used only to declare brew costs at registration) separate from the runtime float power store.

### 3.4 `AltarPowerManager` Design

- A **server-side, per-`ServerLevel` transient manager**, rebuilt purely from block entity load/unload events. Persistence is unnecessary (altar power already lives in block entity NBT and sources self-register on load) and risky (stale coords) — so **prefer in-memory over `SavedData`**.
- Each `AltarBlockEntity` on load (server only) calls `manager.register(this)`; on `setRemoved()`/`onChunkUnload` calls `manager.unregister(this)`. Key by `BlockPos`; preserve the **self-healing sweep** that evicts entries whose live block entity at that pos no longer matches (block entity identity changes across chunk reload).
- Query API → `get(Level, BlockPos, radius)` returning a distanceSq-sorted list of in-range, in-world sources (filter by each source's own `getRange()`).
- **Capabilities**: not a good fit for the spatial registry (capabilities answer "what can this block do", not "enumerate altars near a point"). But per-altar power MAY be cleanly surfaced via a `BlockCapability<IAltarPower>` for `getCurrentPower`/`consumePower`/`getRange`/`getEnhancementLevel`.
- **World-scoping**: `isAreaNulled` must filter by world; decide whether to honor the `radius` arg or document "altar's own range governs"; keep server-only sentinels.

---

## 4. Registration & Recipe

### 4.1 Registry / Naming

| Item | Value |
|---|---|
| Registry id | **`hexerei:altar`** |
| Block name | **`hexerei:altar`** |
| BlockItem | default `BlockItem`, shares id `hexerei:altar` |
| Creative tab | Hexerei creative tab |
| BlockEntity | altar block entity, registered as a `BlockEntityType` under `hexerei:altar` |

Use explicit `DeferredRegister` for Block, BlockItem, and BlockEntityType, with a deterministic registration order so the `BlockEntityType` exists first.

### 4.2 Crafting Recipe (SHAPED, output **3×** `hexerei:altar`)

```
Pattern:
  a b c
  x y x
  x y x
```

| Key | Item | Count |
|---|---|---|
| a | `hexerei:breath_of_the_goddess` | 1 |
| b | `minecraft:potion` (water bottle form) | 1 |
| c | `hexerei:exhale_of_the_horned_one` | 1 |
| x | `minecraft:stone_bricks` | 4 |
| y | `hexerei:witchlog` (Hexerei witch-wood log) | 2 |

**Output count: 3** (easy to miss).

Recipe JSON:
```json
{
  "type": "minecraft:crafting_shaped",
  "pattern": [ "abc", "xyx", "xyx" ],
  "key": {
    "a": { "item": "hexerei:breath_of_the_goddess" },
    "b": { "item": "minecraft:potion" },
    "c": { "item": "hexerei:exhale_of_the_horned_one" },
    "x": { "item": "minecraft:stone_bricks" },
    "y": { "item": "hexerei:witchlog" }
  },
  "result": { "item": "hexerei:altar", "count": 3 }
}
```

> Ingredient **b** is intended to be a plain water bottle. In 1.20.1 `minecraft:potion` carries potion data via components, so a bare `minecraft:potion` ingredient **may not match exactly**. **[UNVERIFIED which concrete potion variant is intended — likely a plain Water Bottle/Glass Bottle; test in-game, may need a component-aware ingredient.]**
>
> **[UNVERIFIED]** Confirm the final registry ids for `hexerei:breath_of_the_goddess` / `hexerei:exhale_of_the_horned_one` once those items are implemented.

---

## 5. Implementation Notes (1.20.1 Forge)

| Subsystem | Approach |
|---|---|
| **Registration** | `DeferredRegister<Block>` + `<Item>` (BlockItem) + `<BlockEntityType>`; creative tab via `BuildCreativeModeTabContentsEvent`; deterministic registration order so the `BlockEntityType` exists first. |
| **Block state (joined flag)** | `BlockState` boolean property (e.g. `JOINED`) drives texture choice and the "is part of multiblock" flag; multiblock + power logic set/read it; blockstate JSON variants for joined/standalone. |
| **Block Entity** | `Block implements EntityBlock`; `newBlockEntity(pos,state)` returns the block entity directly; ticking via `BlockEntityTicker`; `RenderShape.INVISIBLE` via model. Tiny base behavior: tick counter + once-only `initiate()` + `setChanged` + `sendBlockUpdated`. |
| **NBT** | keys `Core, Power, MaxPower, PowerScale, RechargeScale, RangeScale, EnhancementLevel` in `saveAdditional`/`load`; `BlockPos` serialization for the core coord. |
| **Sync packet** | `getUpdatePacket()` → `ClientboundBlockEntityDataPacket.create(this)` + `getUpdateTag()`/`handleUpdateTag`; `setChanged()` + `level.sendBlockUpdated(...)`. |
| **GUI** | client-only read-only `Screen` (no menu/slots) opened via `Minecraft.setScreen` (or a packet); read the 3 power values from synced block entity state; `GuiGraphics.blit` of a 176×88 background + `drawCenteredString`. |
| **Recipe** | datapack JSON `crafting_shaped` (see §4.2). |
| **Models / textures** | author JSON blockstate + block/item models + textures; cube using `altar.png` sides / `altar_top.png` top; joined variant uses the `*_joined*` textures. |
| **Power table sources** | block **tags** (`minecraft:saplings`, `minecraft:logs`, `minecraft:leaves`) or custom tags, preserving per-tag factor/limit; `ForgeRegistries.BLOCKS` iteration; `instanceof CropBlock/FlowerBlock` (cached). |
| **Power registry** | per-`ServerLevel` transient manager, server-only, rebuilt from block entity load/unload. |
| **Direction** | vanilla `Direction`. |
| **Position helper** | `BlockPos`. |
| **Logging / config** | mod SLF4J logger; simple debug flag. |
| **Particles** | green ambient particle in `randomDisplayTick` (only when joined); a vanilla particle is acceptable (cosmetic, no gameplay impact). |

---

## 6. Component Closure

| Component | Decision | Rationale |
|---|---|---|
| Altar block + block entity + inner power source | **BUILD** | The altar slice itself: multiblock detection, power scan, artefact scan, NBT, power-source impl. |
| Base entity-block behavior | **BUILD** | Maps to `EntityBlock` + `DeferredRegister`. |
| Tick/init base | **BUILD** | Tick counter + once-only `initiate()` + update → modern block entity (`BlockEntityTicker`, `setChanged` + `sendBlockUpdated`). |
| Altar info GUI | **BUILD** | Read-only info `Screen`; open via client `setScreen` or packet. |
| Power source interface | **BUILD** | Small interface implemented by the altar block entity, consumed by the registry. |
| Power manager / closest-source lookup | **BUILD (trimmed)** | The active-altar registry; can drop the null-source half for an altar-only slice. |
| Logging | **BUILD** | Mod SLF4J logger + simple debug flag. |
| Creative tab | **BUILD** | Registered `CreativeModeTab`; use a placeholder icon. |
| Null/suppression source | **DEFER** | Not part of altar behaviour; implement only if void bramble / dead-zones are in scope. |
| Nature particle | **BUILD (cosmetic)** | Vanilla particle or omit. |
| Decorative wolf-altar model | **DEFER** | Not referenced by altar block/BE/GUI logic. Add later only if desired. |
| Companion blocks/items (CHALICE, CANDELABRA, placed-item display, INFINITY_EGG, DEMON_HEART, crops, ARTHANA, MYSTIC_BRANCH, koboldite pentacle, …) | **STUB (null-tolerant)** | Identity comparisons for power/artefact bonuses; keep vanilla sources working and skip the comparisons when the referenced block/item isn't built yet; wire up as those slices land. |

---

## 7. Assets

### Textures

| File | Purpose |
|---|---|
| `assets/hexerei/textures/block/altar.png` | main altar block side |
| `assets/hexerei/textures/block/altar_top.png` | altar top |
| `assets/hexerei/textures/block/altar_joined.png` | multiblock-joined side |
| `assets/hexerei/textures/block/altar_joined_top.png` | joined top |
| `assets/hexerei/textures/block/wolfaltar.png` | Wolf Altar entity-model skin (decor only) |
| `assets/hexerei/textures/gui/altar.png` | altar GUI background (blit 176×88) |

### Models

- Author JSON blockstate/block/item models by hand: a 16×16 cube using `altar.png` sides + `altar_top.png` top; the joined variant uses the `*_joined*` textures.

### Lang (`assets/hexerei/lang/en_us.json`)

| Key | Value |
|---|---|
| `block.hexerei.altar` | `Altar` |
| `block.hexerei.wolfaltar` | `Wolf Altar` |
| `hexerei.book.altarpower` | `Altar power` |
| `hexerei.rite.missingpowersource` | `No altar nearby.` |
| `hexerei.rite.insufficientpower` | `Altar has insufficient power.` |
| `hexerei.prediction.nopower` | `The crystal ball cannot get enough power from a nearby altar.` |
| `hexerei.brewing.ingredientpowercost` | `Altar power cost: %d (for rituals: %d)` |

---

## 8. Risks & Open Questions (Ranked)

1. **Power-manager world scoping (HIGH).** With an integrated server and multiple `ServerLevel`s, a process-global static would cause cross-world leakage and client/server confusion. **Approach:** per-`ServerLevel`, server-only, transient manager rebuilt from block entity load/unload; preserve the self-healing sweep (block entity identity changes across chunk reload — essential).

2. **Multiblock joined flag + exact BFS rule (HIGH).** The joined flag is a `BlockState` boolean (`JOINED`). The BFS flood-fill neighbour-count rule (each visited coord has **≥2 and ≤3** same-block horizontal neighbours, total **==6**, core = `visited.get(0)`) must be reproduced **exactly** or altar shapes change.

3. **Power table tag mapping (HIGH).** Saplings (4/20), logs (2/50), leaves (3/100) → block tags (`minecraft:saplings/logs/leaves` or custom), preserving per-tag factor/limit. The "extra nature" catch-all (flowers/crops, 2/4) → `instanceof CropBlock/FlowerBlock` (or a tag), **cached**.

4. **Vanilla block split/rename (HIGH).** Flowers are many distinct blocks; tallgrass is split; `pumpkin` vs `carved_pumpkin`; skull/head types are distinct head block variants. **Each must be remapped block-by-block** to keep the power table accurate.

5. **Wither-skull bonus (MEDIUM — decision recorded).** The wither skull deliberately grants **+3/+3** (matching the player skull), not +2/+2. Keep this intentional value.

6. **Artefact companion dependency closure (MEDIUM).** A complete artefact scan needs `CANDELABRA`, the placed-item display, `CHALICE`, `ARTHANA`, `MYSTIC_BRANCH`, koboldite pentacle, `INFINITY_EGG`, `DEMON_HEART`, the crop blocks, and skulls/heads to exist. For an altar-only slice, **stub null-tolerantly** (skip when absent) and wire up as slices land.

7. **Networking + GUI (MEDIUM).** Block entity sync via `getUpdatePacket`/`ClientboundBlockEntityDataPacket` + `getUpdateTag`/`handleUpdateTag`; GUI opened via client `Screen` (`setScreen`/packet), reading the 3 power values from synced block entity state. **Verify the readout updates live** — easy to break.

8. **`radius` argument in `get()` (MEDIUM).** The `radius` arg to `get(level,pos,radius)` is not the real filter; each altar's own `getRange()` governs. Decide whether to honor `radius` or document that the altar's own range governs — silently changing this alters which altars match.

9. **`isAreaNulled` world check (LOW — latent issue).** Null-source matching must filter by world. Only relevant if null sources are in scope (not the altar core slice).

10. **Float power with int-rounded recharge (LOW).** `power` is a float store but recharge floors via `(int)Math.min(...)` every 20 ticks; `maxPower` is a float sum. **Keep float store + int rounding** to preserve balance and avoid off-by-one in the displayed value.

11. **Recipe ingredient `b` ambiguity (LOW — [UNVERIFIED]).** A bare `minecraft:potion` in 1.20.1 carries component data and may not match a plain water bottle. **Test in-game; may need a component-aware ingredient.**

12. **Companion item ids (LOW — [UNVERIFIED]).** Confirm the final ids for `breath_of_the_goddess` / `exhale_of_the_horned_one` once those items are implemented.

13. **NBT core-coord key form (LOW — [UNVERIFIED]).** Decide between a single `Core` key and separate `CoreX/CoreY/CoreZ` keys; fix one and keep it consistent.

14. **Performance of 29³ scan (LOW).** Up to ~24k block reads per scan; use a `Level`/`ChunkAccess`-friendly scan, **avoid forcing chunk loads**, and preserve the `>100 ticks` throttle.
