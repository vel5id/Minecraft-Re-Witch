# Overhaul — Witch's Cauldron Rework (block visuals + states)

Date: 2026-06-25
Slice: Cauldron / Brewing (visual rework). Subsystem owner: cauldron block + BE.
Status: DESIGN (buildable). No code/assets changed by this doc.

## Problem

The current cauldron renders with `models/block/cauldron.json = { "parent": "minecraft:block/cauldron" }`
and a flat blockstate `{ "variants": { "": { "model": "hexerei:block/cauldron" } } }`. Consequences:

- **No witch identity** — it is literally the vanilla iron cauldron silhouette.
- **Empty and filled look identical** — `CauldronBlockEntity` tracks `waterLevel 0..MAX_WATER(3)`,
  `color` (the blended brew tint via `BrewColor.blend`), `heatTicks`, `isBoiling()`, but **none of it is
  visible**. The vanilla cauldron parent draws no interior liquid for our block (we never set the
  `minecraft:level_cauldron` content), so a full cauldron of glowing brew looks bone dry.
- **No boiling feedback** — `isBoiling()` flips at `heatTicks >= 100` while filled, but there is zero
  visual or particle cue, so the player cannot tell when ingredient absorption / collection is live.

This rework gives the cauldron (1) a bespoke witch-cauldron model + textures, (2) a **filled** state that
shows tinted liquid inside driven by the BE's `color`, and (3) a **boiling** cue via an animated liquid
surface (`.mcmeta`) plus client-side bubble/steam particles emitted from the BE.

## Grounding (real code this touches)

- `block/cauldron/CauldronBlock.java` — `Block implements EntityBlock`. `getRenderShape = MODEL`.
  Custom `SHAPE` (full block minus interior box `2,4,2 → 14,16,14`). `getTicker` currently returns
  **null on the client** and a server ticker on the server. `use()` handles water-bucket/empty-bucket/bottle.
- `blockentity/CauldronBlockEntity.java` — fields `waterLevel`, `heatTicks`, `ingredients`, `color`,
  `powered`, `ticks`. Public reads already exist: `isFilled()`, `isBoiling()`, `getColor()`,
  `getWaterLevel()`, `isReady()`. `serverTick` does heat/boil/absorb/power. NBT keys
  `Water/Heat/Color/Powered/Ingredients`. Syncs via `sendBlockUpdated(..., flag 3)` +
  `getUpdateTag()`/`handleUpdateTag()` (so the client BE always has fresh `waterLevel/color/heat`).
- `registry/HexereiParticles.java` — `DeferredRegister<ParticleType<?>>`; existing
  `WISP_LOW/WISP_MEDIUM/WISP_HIGH/ASH`, each `new SimpleParticleType(false)`. Registered on the mod bus
  in `HexereiMod` via `HexereiParticles.PARTICLES.register(modBus)`.
- `client/HexereiClient.registerParticles(RegisterParticleProvidersEvent)` — `event.registerSpriteSet(type, Provider::new)`.
- Particle defs live in `assets/hexerei/particles/<name>.json = {"textures":["hexerei:<sprite>"]}`,
  sprites in `assets/hexerei/textures/particle/<sprite>.png`.
- `AltarBlockEntity` is the template for a **client ticker that emits particles**: `AltarBlock.getTicker`
  returns `(lvl,pos,st,be) -> ((AltarBlockEntity)be).clientTick(...)` on `level.isClientSide`, and the
  method gates on `gt % period` and calls `level.addParticle(...)`.
- `brewing/BrewColor.java` — `WATER = 0x3F76E4`; `blend(List<Integer>)` returns 0xRRGGBB.

## Approach overview

1. **Blockstate** gains two boolean properties: `filled` and `boiling`. They are **derived from the BE**,
   not from placement — the BE writes them into the blockstate on its sync points so the model selector
   and any future state queries are consistent. (Liquid *tint* stays BE-driven via a block color provider,
   not a blockstate value, because color is a 24-bit int and cannot be a blockstate property.)
2. **Model split**: `empty` model (witch cauldron, no interior liquid) vs `filled` / `filled_boiling`
   models that add an interior liquid quad whose texture is tinted by `tintindex 0`.
3. **Block color provider** (client) reads `CauldronBlockEntity.getColor()` and returns it for
   `tintindex 0`, so the same liquid texture renders as water-blue, red, green, etc. per brew. Empty water
   (no ingredients) uses `BrewColor.WATER` which the BE already stores as `color`.
4. **Animation**: the liquid-surface texture is a 2-frame `.mcmeta` strip; the `filled_boiling` model
   points its surface at the animated texture, the calm `filled` model at a static surface — i.e. the boil
   animation is a **model swap**, no per-state texture switching needed.
5. **Particles**: a new client ticker on `CauldronBlock` calls `CauldronBlockEntity.clientTick`, which —
   when `isBoiling()` — emits `CAULDRON_BUBBLE` (rising, tinted) and `CAULDRON_STEAM` (drifting up/out)
   from the liquid surface, gated on `gt % N` exactly like the altar wisps.

---

## 1. Blockstate properties + BlockState <-> BE sync

### 1.1 New BooleanProperties on CauldronBlock

```java
public static final BooleanProperty FILLED  = BooleanProperty.create("filled");
public static final BooleanProperty BOILING = BooleanProperty.create("boiling");
```

- `createBlockStateDefinition(Builder)`: `builder.add(FILLED, BOILING);`
- Constructor: `registerDefaultState(stateDefinition.any().setValue(FILLED, false).setValue(BOILING, false));`
- `getStateForPlacement` is **not** overridden — a freshly placed cauldron is empty+not-boiling (the
  default), matching `waterLevel == 0`.

### 1.2 BE drives the two booleans (single source of truth = the BE numeric fields)

The BE already syncs on the right edges (`fillWater`, `drain`, `collectBrew`, and in `serverTick` when
`heatTicks` crosses `BOIL_TICKS`, when boil stops, when power flips). Add a helper that reconciles the
blockstate to the BE's current truth, called wherever the BE currently calls `setChanged(); sync();`:

```java
// CauldronBlockEntity
private void syncState() {                 // server-only; updates the blockstate booleans
    if (level == null || level.isClientSide) return;
    BlockState st = getBlockState();
    boolean wantFilled  = isFilled();      // waterLevel >= MAX_WATER
    boolean wantBoiling = isBoiling();     // heatTicks >= BOIL_TICKS && isFilled()
    if (st.getValue(CauldronBlock.FILLED) != wantFilled
            || st.getValue(CauldronBlock.BOILING) != wantBoiling) {
        level.setBlock(worldPosition,
            st.setValue(CauldronBlock.FILLED, wantFilled).setValue(CauldronBlock.BOILING, wantBoiling),
            3);                            // flag 3 = update + notify; preserves the BE
    }
}
```

- Call `syncState()` from the existing `sync()` method (so every place that already syncs the BE also
  reconciles the model) **or** call it immediately before each `sync()`. Recommended: fold it into the
  private `sync()` so there is exactly one path:

  ```java
  private void sync() {
      if (level != null && !level.isClientSide) {
          syncState();                                   // reconcile blockstate first
          level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
      }
  }
  ```

- **Why a blockstate boolean and not pure model-from-BE?** A blockstate variant is the cheapest, most
  idiomatic way to pick a model in 1.20.1 (no `BlockEntityRenderer` needed for a static interior quad).
  The booleans are pure functions of `waterLevel`/`heatTicks`, so they cannot desync from the BE.
- **Recursion guard:** `setBlock` with flag 3 fires `onPlace`/`updateShape`, but `CauldronBlock`
  overrides neither beyond shape, and the BE is preserved (same block, same BE type), so there is no
  reset. (Mirrors the altar's `joined`-flag guard rationale — here we simply don't react to our own
  state in `onPlace`.)

> [DECISION] **Blockstate booleans vs. BlockEntityRenderer.** Alternative: keep one model and draw the
> liquid + boil with a `BlockEntityRenderer` (full control, animated mesh). Chosen: **blockstate
> booleans + model swap + block color provider** — far less code, no BER registration, reuses the existing
> sync, and a 2-frame `.mcmeta` covers the boil animation. A BER is only worth it if we later want a true
> rising/falling liquid level per `waterLevel` (currently `waterLevel` is binary in practice: fill sets it
> straight to `MAX_WATER`). Revisit if partial fill becomes a mechanic.

### 1.3 Blockstate JSON

`assets/hexerei/blockstates/cauldron.json`:

```json
{
  "variants": {
    "filled=false,boiling=false": { "model": "hexerei:block/cauldron" },
    "filled=true,boiling=false":  { "model": "hexerei:block/cauldron_filled" },
    "filled=false,boiling=true":  { "model": "hexerei:block/cauldron" },
    "filled=true,boiling=true":   { "model": "hexerei:block/cauldron_boiling" }
  }
}
```

(`filled=false,boiling=true` is unreachable because `isBoiling()` requires `isFilled()`, but listing it
keeps the variant map total so no missing-variant magenta can occur.)

---

## 2. Models

All models are **bespoke** (we stop parenting `minecraft:block/cauldron`). Geometry = a 16-wide cauldron:
solid outer walls/legs + a hollow interior matching the BE `SHAPE` (interior void `2,4,2 → 14,16,14`),
plus — for the filled/boiling variants — one horizontal **liquid-surface** quad near the rim.

### 2.1 `models/block/cauldron.json` (EMPTY — base geometry, no liquid)

A from-scratch `elements` model (don't parent vanilla, so our witch textures map cleanly). Texture slots:
`#side`, `#top` (rim/inner-top ring), `#bottom`, `#inside` (visible interior wall when empty),
`#particle = #side`.

```json
{
  "parent": "block/block",
  "textures": {
    "particle": "hexerei:block/cauldron_side",
    "side":   "hexerei:block/cauldron_side",
    "top":    "hexerei:block/cauldron_top",
    "bottom": "hexerei:block/cauldron_bottom",
    "inside": "hexerei:block/cauldron_inside"
  },
  "elements": [
    {  "from": [0,0,0], "to": [16,4,16],
       "faces": {
         "down":  {"texture":"#bottom","cullface":"down"},
         "up":    {"texture":"#inside"},
         "north": {"texture":"#side"}, "south": {"texture":"#side"},
         "west":  {"texture":"#side"}, "east": {"texture":"#side"}
       }},
    {  "from": [0,4,0], "to": [16,16,2],  "faces": {
         "north":{"texture":"#side","cullface":"north"},"south":{"texture":"#inside"},
         "up":{"texture":"#top"},"down":{"texture":"#side"},
         "west":{"texture":"#side"},"east":{"texture":"#side"}}},
    {  "from": [0,4,14], "to": [16,16,16], "faces": {
         "south":{"texture":"#side","cullface":"south"},"north":{"texture":"#inside"},
         "up":{"texture":"#top"},"down":{"texture":"#side"},
         "west":{"texture":"#side"},"east":{"texture":"#side"}}},
    {  "from": [0,4,2], "to": [2,16,14],  "faces": {
         "west":{"texture":"#side","cullface":"west"},"east":{"texture":"#inside"},
         "up":{"texture":"#top"},"down":{"texture":"#side"}}},
    {  "from": [14,4,2], "to": [16,16,14], "faces": {
         "east":{"texture":"#side","cullface":"east"},"west":{"texture":"#inside"},
         "up":{"texture":"#top"},"down":{"texture":"#side"}}}
  ]
}
```

(Walls are 2px thick to match `SHAPE`; the `4..16` band is the hollow bowl. Bottom slab `0..4` is solid so
items rest at y=4, exactly where `SHAPE` lets them fall.)

### 2.2 `models/block/cauldron_filled.json` (FILLED, calm)

`parent` the empty model and **add one liquid quad** at `y=13` (just under the rim) spanning the interior
`2..14`. Liquid texture is tinted via `tintindex 0`.

```json
{
  "parent": "hexerei:block/cauldron",
  "textures": { "liquid": "hexerei:block/cauldron_liquid_still" },
  "elements": [
    { "from": [2,13,2], "to": [14,13,14],
      "faces": { "up": { "texture": "#liquid", "tintindex": 0 } } }
  ]
}
```

> NOTE: in 1.20.1 a child model that declares `elements` **replaces** the parent's elements. So either
> (a) repeat the parent geometry inside `cauldron_filled` and append the liquid quad, or (b) keep these as
> **flat (non-parented) models** that inline all elements. Recommended (b) for clarity: `cauldron_filled`
> and `cauldron_boiling` each inline the full empty geometry **plus** the liquid quad, differing only in
> the `#liquid` texture (`..._still` vs `..._boiling`). The snippet above shows the delta for readability;
> the shipped JSON inlines all five wall elements + the liquid quad.

### 2.3 `models/block/cauldron_boiling.json` (FILLED, boiling)

Identical to `cauldron_filled` except the liquid quad uses the **animated** surface:

```json
"textures": { "liquid": "hexerei:block/cauldron_liquid_boiling" }
```

(`cauldron_liquid_boiling.png` is a vertical 2-frame strip with a `.mcmeta`, §3.) The bubbling reads both
from the animated surface texture AND the particles in §4.

### 2.4 `models/item/cauldron.json`

Stays `{ "parent": "hexerei:block/cauldron" }` (the empty witch cauldron is the inventory icon). No change
needed beyond the new base geometry flowing through.

---

## 3. Liquid texture tint + animation

### 3.1 Block color provider (client)

Register a `BlockColor` so `tintindex 0` on the liquid quad picks up the BE's `color`:

```java
// in HexereiClient (FMLClientSetupEvent or a RegisterColorHandlersEvent.Block handler)
@SubscribeEvent
public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
    event.register((state, level, pos, tintIndex) -> {
        if (tintIndex != 0 || level == null || pos == null) return -1;
        if (level.getBlockEntity(pos) instanceof CauldronBlockEntity be) return be.getColor();
        return BrewColor.WATER;
    }, HexereiBlocks.CAULDRON.get());
}
```

- `-1` (white) for non-liquid faces leaves walls/top/bottom untinted.
- The client BE always has fresh `color` because `getUpdateTag()` includes `Color` and the BE re-renders
  on `sendBlockUpdated`. After a brew color change the BE already calls `sync()`, which calls
  `sendBlockUpdated` → the chunk re-tints. (If a color-only change ever stops re-rendering, force a
  re-render in `handleUpdateTag` via `requestModelDataUpdate()`/`level.setBlocksDirty` — not expected to
  be needed since the blockstate booleans usually change alongside.)

### 3.2 `.mcmeta` animation for the boiling surface

`assets/hexerei/textures/block/cauldron_liquid_boiling.png.mcmeta`:

```json
{ "animation": { "frametime": 6, "interpolate": true, "frames": [0, 1] } }
```

- 2-frame vertical strip (texture is `W x 2W`); `frametime 6` ticks (~0.3s/frame) gives a gentle roil;
  `interpolate` smooths it. The **still** surface (`cauldron_liquid_still.png`) is single-frame, no `.mcmeta`.

---

## 4. Boiling particles (server-gated trigger via client ticker)

### 4.1 Two new particle types

Add to `HexereiParticles`:

```java
public static final RegistryObject<SimpleParticleType> CAULDRON_BUBBLE =
        PARTICLES.register("cauldron_bubble", () -> new SimpleParticleType(false));
public static final RegistryObject<SimpleParticleType> CAULDRON_STEAM  =
        PARTICLES.register("cauldron_steam",  () -> new SimpleParticleType(true)); // overrideLimiter=true: steam visible at distance
```

Definitions:
- `assets/hexerei/particles/cauldron_bubble.json` → `{ "textures": ["hexerei:cauldron_bubble"] }`
- `assets/hexerei/particles/cauldron_steam.json`  → `{ "textures": ["hexerei:cauldron_steam"] }`

Providers in `HexereiClient.registerParticles`, reusing the existing sprite-set pattern. The bubble can
reuse the simple lifecycle of `WispParticle` (short-lived, rises a little, fades); steam is a longer-lived,
slower, slightly-expanding rise. Concretely:

```java
event.registerSpriteSet(HexereiParticles.CAULDRON_BUBBLE.get(), CauldronBubbleParticle.Provider::new);
event.registerSpriteSet(HexereiParticles.CAULDRON_STEAM.get(),  CauldronSteamParticle.Provider::new);
```

> [DECISION] **New particle classes vs. reuse vanilla `ParticleTypes.BUBBLE_POP` / `CLOUD`.** Reusing
> vanilla needs no textures/registration and is tempting. Chosen: **bespoke `cauldron_bubble` +
> `cauldron_steam`** because (a) the wisp/ash particle pipeline already exists and is the project
> convention ("witch" identity, custom sprites), and (b) we want the bubble **tinted by the brew color**
> (a green brew bubbles green) — vanilla `BUBBLE` is fixed-color. The bubble provider reads the per-spawn
> color we pass through the velocity-as-color trick or via `addParticle` + a colored sprite set; simplest:
> spawn with `level.addParticle(type, x,y,z, r,g,b)` and have the particle ctor read `xd/yd/zd` as RGB
> (the AltarBlockEntity already passes motion through `addParticle`). If tint is judged not worth the
> custom particle, fall back to vanilla `BUBBLE_COLUMN_UP`/`CLOUD` and drop the two textures — flagged so
> the implementer can choose.

### 4.2 Client ticker on CauldronBlock

Currently `CauldronBlock.getTicker` returns **null** on the client. Change it to mirror `AltarBlock`:

```java
@Override
public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
    if (type != HexereiBlockEntities.CAULDRON.get()) return null;
    if (level.isClientSide) {
        return (lvl, pos, st, be) -> ((CauldronBlockEntity) be).clientTick(lvl, pos, st);
    }
    return (lvl, pos, st, be) -> CauldronBlockEntity.serverTick(lvl, pos, st, (CauldronBlockEntity) be);
}
```

### 4.3 `CauldronBlockEntity.clientTick`

```java
public void clientTick(Level level, BlockPos pos, BlockState state) {
    if (!isBoiling()) return;                 // reads synced waterLevel + heatTicks
    long gt = level.getGameTime();
    var rnd = level.random;

    // Bubbles: 0..2 per emission, every 4 ticks, from the liquid surface (y ~= 13/16).
    if (gt % 4L == 0L) {
        int n = rnd.nextInt(3);
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8)  & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        for (int i = 0; i < n; i++) {
            double px = pos.getX() + 0.30 + rnd.nextDouble() * 0.40;
            double pz = pos.getZ() + 0.30 + rnd.nextDouble() * 0.40;
            double py = pos.getY() + 0.78;     // ~= 12.5/16, at the surface quad
            level.addParticle(HexereiParticles.CAULDRON_BUBBLE.get(), px, py, pz, r, g, b);
        }
    }

    // Steam: 1 every 10 ticks, rises out of the rim and drifts.
    if (gt % 10L == 0L) {
        double px = pos.getX() + 0.5 + (rnd.nextDouble() - 0.5) * 0.5;
        double pz = pos.getZ() + 0.5 + (rnd.nextDouble() - 0.5) * 0.5;
        double py = pos.getY() + 0.95;
        level.addParticle(HexereiParticles.CAULDRON_STEAM.get(), px, py, pz,
                0.0, 0.03 + rnd.nextDouble() * 0.02, 0.0);
    }
}
```

- **Why client-side emission (not `serverLevel.sendParticles`)?** The altar already emits ambient
  particles from a **client ticker** reading synced BE state — same pattern, cheaper than per-tick S2C
  packets, and `isBoiling()` is derivable client-side because `Heat`/`Water` are in `getUpdateTag()`.
  (The rites use `sendParticles` because they are one-shot server events with no client BE; the cauldron
  has a persistent synced BE, so the altar pattern fits.)
- **Color passthrough:** bubble RGB is passed as the `xd/yd/zd` args and read in the particle ctor (the
  `addParticle(type, x,y,z, xd,yd,zd)` overload). Steam stays white and uses real velocity. This matches
  how `AltarBlockEntity` passes `vy` through `addParticle`.
- Gate cadences (`%4`, `%10`) and counts mirror the altar's ambient-particle budget (1–3 particles per
  tens of ticks) so we don't flood. `[UNVERIFIED]` — confirm density in-game; tune `frametime`/counts.

### 4.4 (Optional) ambient boiling sound

Not required by the task, but the natural home is the same `clientTick` gate: every ~60 ticks while
boiling, `level.playLocalSound(pos, SoundEvents.... , BLOCKS, 0.2f, 0.8f, false)`. Left out of scope;
noted so it isn't reinvented.

---

## 5. Textures (asset entries for the generator)

Items render best at 64x64 (remove_bg); **blocks fill the square, no bg removal**. Cauldron faces are
block textures (32-grid, 32x32 or 64x64, `remove_bg:false`). The liquid surface is a small tinted tile.
Everything below is regenerated (current art is placeholder / vanilla-parented).

| logical_id | target_path | w | h | remove_bg | grid | prompt |
|---|---|---|---|---|---|---|
| cauldron_side | assets/hexerei/textures/block/cauldron_side.png | 32 | 32 | false | 32 | Minecraft block texture, 32x32 pixel art, side of a heavy cast-iron witch's cauldron, dark blackened riveted iron with subtle vertical seams and a slight belly curve, faint green-tarnished highlights, two small forged handle lugs hinted at the upper edge, top-down even lighting, fills the square edge to edge, tileable horizontally, no background |
| cauldron_top | assets/hexerei/textures/block/cauldron_top.png | 32 | 32 | false | 32 | Minecraft block texture, 32x32 pixel art, top rim of a cast-iron witch's cauldron seen from above, thick dark iron ring with a hollow black interior opening in the center, rivets along the rim, worn metal, even lighting, fills the square, no background |
| cauldron_bottom | assets/hexerei/textures/block/cauldron_bottom.png | 32 | 32 | false | 32 | Minecraft block texture, 32x32 pixel art, underside of a cast-iron cauldron, dark blackened iron, soot-stained center where fire scorches it, faint concentric forge marks, even lighting, fills the square, no background |
| cauldron_inside | assets/hexerei/textures/block/cauldron_inside.png | 32 | 32 | false | 32 | Minecraft block texture, 32x32 pixel art, inner wall of an empty iron cauldron, dark sooty curved metal, slightly lighter than the outside, faint vertical drip stains, even lighting, fills the square, no background |
| cauldron_liquid_still | assets/hexerei/textures/block/cauldron_liquid_still.png | 32 | 32 | false | 32 | Minecraft block texture, 32x32 pixel art, calm flat liquid surface for a potion brew, smooth grayscale-to-light tones so it can be color-tinted in engine, very subtle round meniscus near the edges and a faint specular glint, mostly mid-gray neutral so any tint reads true, fills the square, no background |
| cauldron_liquid_boiling | assets/hexerei/textures/block/cauldron_liquid_boiling.png | 32 | 64 | false | 32 | Minecraft block texture animation strip, two stacked 32x32 frames (total 32 wide by 64 tall), a roiling boiling potion surface, neutral grayscale tones for in-engine color tint, frame 1 shows small round bubbles rising with light ripples, frame 2 shows the bubbles shifted and bursting with brighter foam specks, consistent neutral mid-gray base in both frames, seamless loop between the two frames, no background |
| cauldron_bubble | assets/hexerei/textures/particle/cauldron_bubble.png | 8 | 8 | true | 8 | Minecraft particle sprite, 8x8 pixel art, a single small round potion bubble, soft white-gray rim with a translucent lighter center and a tiny top highlight, neutral tones for in-engine color tint, transparent background |
| cauldron_steam | assets/hexerei/textures/particle/cauldron_steam.png | 8 | 8 | true | 8 | Minecraft particle sprite, 8x8 pixel art, a soft wisp of pale steam/smoke, fluffy semi-transparent gray-white cloud puff with feathered edges, transparent background |

Notes:
- The liquid textures are deliberately **neutral gray** so the block color provider's `tintindex 0`
  multiply produces accurate per-brew colors (water-blue for plain water, red/green/etc. for brews).
- `cauldron_liquid_boiling.png` MUST be a 32x64 vertical 2-frame strip to match the `.mcmeta` `frames [0,1]`.
- Particle sprites are tiny (8x8) like vanilla bubble/cloud; `remove_bg:true` because particles need alpha.

---

## 6. New files / edits summary

### New asset files
- `assets/hexerei/models/block/cauldron_filled.json`
- `assets/hexerei/models/block/cauldron_boiling.json`
- `assets/hexerei/particles/cauldron_bubble.json`
- `assets/hexerei/particles/cauldron_steam.json`
- `assets/hexerei/textures/block/cauldron_side.png`
- `assets/hexerei/textures/block/cauldron_top.png`
- `assets/hexerei/textures/block/cauldron_bottom.png`
- `assets/hexerei/textures/block/cauldron_inside.png`
- `assets/hexerei/textures/block/cauldron_liquid_still.png`
- `assets/hexerei/textures/block/cauldron_liquid_boiling.png`
- `assets/hexerei/textures/block/cauldron_liquid_boiling.png.mcmeta`
- `assets/hexerei/textures/particle/cauldron_bubble.png`
- `assets/hexerei/textures/particle/cauldron_steam.png`

### Edited asset files
- `assets/hexerei/blockstates/cauldron.json` — flat variant → `filled`/`boiling` variant map (§1.3).
- `assets/hexerei/models/block/cauldron.json` — drop `minecraft:block/cauldron` parent → bespoke
  `elements` geometry with `#side/#top/#bottom/#inside` (§2.1).
- `assets/hexerei/models/item/cauldron.json` — unchanged content, now inherits the new geometry.

### Edited Java
- `block/cauldron/CauldronBlock.java` — add `FILLED`/`BOILING` BooleanProperties +
  `createBlockStateDefinition` + `registerDefaultState`; change `getTicker` to return a **client ticker**
  (`clientTick`) alongside the existing server ticker (§4.2).
- `blockentity/CauldronBlockEntity.java` — add `syncState()` (reconcile blockstate booleans) folded into
  the private `sync()`; add `clientTick(level,pos,state)` emitting bubble/steam particles (§4.3).
- `registry/HexereiParticles.java` — register `CAULDRON_BUBBLE`, `CAULDRON_STEAM` (§4.1).
- `client/HexereiClient.java` — `registerParticles`: register the two sprite-set providers; add
  `registerBlockColors` (`RegisterColorHandlersEvent.Block`) for the liquid `tintindex 0` (§3.1, §4.1).

### New Java (client particles)
- `client/particle/CauldronBubbleParticle.java` — rising, short-lived, color from `xd/yd/zd` (RGB).
- `client/particle/CauldronSteamParticle.java` — slow rising white wisp, longer life.
  (Both follow the existing `WispParticle`/`AshParticle` structure: a `TextureSheetParticle` subclass +
  a static `Provider implements ParticleProvider<SimpleParticleType>` using the sprite set.)

### Lang
- No new user-facing strings required: `block.hexerei.cauldron` already exists in `en_us.json`/`ru_ru.json`.
  (Particles and blockstate variants are not translated.) If a future ambient-sound subtitle is added,
  register a `subtitles.hexerei.cauldron.boil` key — **out of scope** here.

---

## 7. Tests (per project two-tier strategy)

- **Pure unit (`src/test/java`):**
  - `BrewColor.blend` is already covered; add an assertion that the bubble RGB unpack
    (`(color>>16)&0xFF`, etc.) round-trips a known `color` int (pure arithmetic, no MC runtime). Put this
    in a small pure helper `CauldronVisuals.rgb(int)` so it is testable without the BE.
- **GameTest (`src/main/java/.../test/CauldronGameTests.java`, existing file):**
  - `cauldron_fill_sets_filled_state`: place cauldron, `fillWater()`, assert
    `level.getBlockState(pos).getValue(CauldronBlock.FILLED) == true`.
  - `cauldron_boil_sets_boiling_state`: fill + heat source below + tick past `BOIL_TICKS`, assert
    `BOILING == true` and `isBoiling()`.
  - `cauldron_drain_clears_states`: after `drain()`, assert `FILLED == false && BOILING == false`.
  - These assert the **server-authoritative blockstate booleans**, not particles (particles are
    client-only and untestable headless — verified visually per the build-verify gate).
- **Regression:** run the full `./gradlew --no-daemon test` + `runGameTestServer`; the existing cauldron
  GameTests (fill/heat/absorb/collect) must still pass since BE numeric behavior is unchanged — only the
  blockstate booleans + client visuals are added.

## 8. Visual verification (build-verify gate)

After implementation: build the jar, launch, place a cauldron, and confirm by screenshot:
(1) empty cauldron looks dark/hollow (no liquid), (2) water bucket → blue liquid surface appears,
(3) add ingredients → surface re-tints to the brew color, (4) heat below → after ~5s the surface animates
and bubble/steam particles rise, (5) `drain` / `collectBrew` → liquid disappears, particles stop.
