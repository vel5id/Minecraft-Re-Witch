# Hexerei — Taint & Atmosphere System

**Date:** 2026-06-22
**Branch:** feat/witchery-altar-port
**Scope:** World taint, altar visual progression, ritual spectacle, chalk ritual selection

---

## Goals

Make the player feel like a genuine witch: the world around the altar changes as rituals are performed, rituals look dramatic, and the chalk communicates which rite is being prepared.

---

## 1. ChunkTaintData

`ChunkTaintData extends SavedData` — server-side, one per `ServerLevel`.

**Storage:**
- `Map<ChunkPos, Float> taint` — current taint (0–100), decays over time
- `Map<ChunkPos, Float> permanentFloor` — accumulated floor, never decays. Grows at 10% of each `addTaint()` call.

**API:**
```java
ChunkTaintData.get(ServerLevel)           // load or create
void addTaint(ChunkPos, float amount)      // adds to taint + 10% to floor; caps at 100
float getTaint(ChunkPos)                   // current value
TaintLevel getLevel(ChunkPos)             // enum from value
void decayTick(ServerLevel)               // called every 60s; taint -= 0.5, floor stays
```

**Decay:** every 60 real-seconds via `LevelTickEvent` (check `level.getGameTime() % 1200 == 0`).
Taint decreases by 0.5 per tick, floored at `permanentFloor` for that chunk.

**Persistence:** standard `SavedData.save()` / `load()`, keyed `"hexerei_taint"`.

---

## 2. TaintLevel

```java
public enum TaintLevel {
    NONE(0, 15),
    LOW(15, 40),
    MEDIUM(40, 70),
    HIGH(70, 100);

    public static TaintLevel fromValue(float v) { ... }
}
```

---

## 3. Altar Visual Progression

### BlockState property
`AltarBlock` gets an `IntegerProperty TAINT_LEVEL` (0–3).

4 blockstate variants → 4 model files → 4 top-face textures (already generated):
- `altar_none.png` — plain stone
- `altar_low.png` — faint purple rune dots
- `altar_medium.png` — glowing violet runes
- `altar_high.png` — deep crimson runes

### Server tick (AltarBlockEntity, every 40 ticks)
```
1. Query ChunkTaintData.getLevel(chunkPos)
2. If level changed from stored → level.setBlock(pos, state.setValue(TAINT_LEVEL, ordinal), 3)
```

### Client particle emission (AltarBlockEntity clientTick)
| TaintLevel | Particle | Color | Behaviour |
|---|---|---|---|
| NONE | — | — | silent |
| LOW | WispParticle | purple `#6E14BE` | slow rise, fades |
| MEDIUM | WispParticle ×2 | dark violet `#46009B` | orbits altar slowly |
| HIGH | WispParticle ×3 + AshParticle | crimson `#A0004A` | trembles, sparks fall |

`WispParticle` uses sprite `hexerei:particle/wisp_low` / `wisp_medium` / `wisp_high` (textures already in assets).

---

## 4. WorldTaintAura

`LevelTickEvent` (server, every 200 ticks = 10s).

For each registered `AltarBlockEntity` in the level:
1. Get `TaintLevel` for altar's chunk.
2. If `< LOW`: skip.
3. Scan radius 5 around altar (Y ±1):
   - `GRASS_BLOCK` → `hexerei:tainted_ground` (prob 15% per block, max 6 per pulse)
   - `STONE` / `COBBLESTONE` → `hexerei:charred_stone` only at `HIGH` (prob 5%, max 2)
   - `DANDELION` / `POPPY` → `WITHER_ROSE` at `MEDIUM+` (prob 20%, max 2)

Mutations are permanent (blocks stay until broken/replaced).

---

## 5. Ritual Visual Signatures

### Burst on activation (RitualCircleBlock.use(), before tryPerform)
```
level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, BLOCK, 0.8f, 1.8f)
20× PORTAL particle along glyph ring positions, staggered 1 tick apart
```

### TempestRite.perform() additions
```
Sound:    SoundEvents.WITHER_BOSS_DEATH, pitch=0.5, volume=0.6
Particles: 60× WITCH from center, radius 3, burst
           8× lightning-style particle at random glyph ring positions
Blocks:   3–5 random blocks inside the circle radius → charred_stone (permanent)
Taint:    ChunkTaintData.addTaint(chunkPos, 25f)
```

All future `Rite` implementations call `ChunkTaintData.addTaint()` with `powerCost / 4f`.

---

## 6. Ritual Chalk — Rite Selection

### NBT
Item stores `hexerei:rite` (String) — the ID of the selected `RitualRecipe`.
Default: first entry in `RitualRecipes.ALL` (currently `hexerei:tempest`).

### Shift+Scroll cycling
```
Client: InputEvent.MouseScrollingEvent
        → player holds RITUAL_CHALK + player.isShiftKeyDown()
        → fire CycleRiteC2SPacket(delta = sign(scroll))

Server: read current rite ID from item NBT
        → advance index in RitualRecipes.ALL by delta (wraps)
        → write new ID to NBT
        → send action bar: Component.translatable(recipe.nameKey()) + " · " + circleSize
```

### Drawing behaviour
`useOn` (RMB on RITUAL_CIRCLE, not sneaking) → reads `hexerei:rite` NBT → looks up `RitualRecipe.circleSize()` → draws one glyph of the corresponding ring per click.

Currently all recipes use `CircleSize.SMALL` (12 glyphs). When a `MEDIUM` recipe is added, the chalk automatically draws a different geometry — no changes to `RitualChalkItem` needed.

### Tooltip
```
item.hexerei.ritual_chalk.rite   → "Обряд: {riteName}"
item.hexerei.ritual_chalk.circle → "Малый круг · 12 глифов"
item.hexerei.ritual_chalk.tip2   → "[Shift+Скролл] Сменить обряд"
```

### Texture
`assets/hexerei/textures/item/ritual_chalk.png` — cream body, purple magic tip (generated, 16×16).

### CircleSize enum
```java
public enum CircleSize {
    SMALL(12, RitualCircle::smallRing);
    // MEDIUM and LARGE added when new rites require them

    final int glyphCount;
    final Function<BlockPos, List<BlockPos>> ringFn;
}
```
`RitualRecipe` gains a `CircleSize circleSize()` field (default `SMALL`).
`RitualRecipes.ALL` is an ordered `List<RitualRecipe>` (in addition to the existing `BY_ID` map) — required for chalk cycling by index.

---

## 7. New Blocks

| Block | ID | Properties | Texture |
|---|---|---|---|
| Tainted Ground | `hexerei:tainted_ground` | strength 0.6, gravel sound, purple MapColor | `tainted_ground.png` |
| Charred Stone | `hexerei:charred_stone` | strength 1.5, stone sound, requiresCorrectTool | `charred_stone.png` |

Both already registered in `HexereiBlocks`, `HexereiItems`, creative tab, and loot tables.

---

## 8. Network Packets

| Packet | Direction | Payload | Handler |
|---|---|---|---|
| `CycleRiteC2SPacket` | Client→Server | `int delta` (+1 / -1) | update item NBT, send action bar |
| `TaintSyncS2CPacket` | Server→Client | `ChunkPos, float taint` | update client-side taint cache for particle decisions |

`TaintSyncS2CPacket` is sent when taint changes (after `addTaint`) and when a player loads a chunk.

---

## 9. File Checklist

```
New Java:
  power/ChunkTaintData.java
  power/TaintLevel.java
  ritual/WorldTaintAura.java
  network/CycleRiteC2SPacket.java
  network/TaintSyncS2CPacket.java
  network/HexereiNetwork.java
  client/particle/WispParticle.java
  client/particle/AshParticle.java
  registry/HexereiParticles.java

Modified Java:
  block/AltarBlock.java               — add TAINT_LEVEL property
  blockentity/AltarBlockEntity.java   — tick: read taint, update state, emit particles
  block/ritual/RitualCircleBlock.java — burst effect before tryPerform
  ritual/TempestRite.java             — particles + sound + charred blocks + addTaint
  ritual/RitualRecipe.java            — add circleSize() field
  item/RitualChalkItem.java           — read rite NBT, draw correct pattern, tooltip
  registry/HexereiBlocks.java         — TAINTED_GROUND, CHARRED_STONE (done)
  registry/HexereiItems.java          — same (done)
  registry/HexereiCreativeTabs.java   — same (done)
  HexereiMod.java                     — register network channel, WorldTaintAura event

New assets:
  textures/item/ritual_chalk.png      (done)
  textures/block/tainted_ground.png   (done)
  textures/block/charred_stone.png    (done)
  textures/particle/wisp_low.png      (done)
  textures/particle/wisp_medium.png   (done)
  textures/particle/wisp_high.png     (done)
  textures/block/altar_none.png       → rename existing altar_top
  textures/block/altar_low.png        (done)
  textures/block/altar_medium.png     (done)
  textures/block/altar_high.png       (done)
  particles/wisp.json
  particles/ash.json
  models/block/altar_taint_0..3.json
  blockstates/altar.json              — add taint_level variants
```

---

## 10. Out of Scope

- Sound design beyond vanilla SoundEvents (custom sounds need `.ogg` files)
- Shader-based glow effects
- Taint visual effects on the HUD
- Taint affecting mob spawning or hostile mob behaviour
