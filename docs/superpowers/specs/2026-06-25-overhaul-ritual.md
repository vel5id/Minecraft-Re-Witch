# Ritual Chalk / Sigil / Runes Overhaul — Design Spec

Date: 2026-06-25
Slice: Ritual Circles (rework of the 2026-06-22 ritual slice)
Status: design only — no code/assets touched here.

This is the biggest mechanics change to the ritual subsystem. It replaces the "Shift+Scroll
to select, plain right-click draws all 12 glyphs at once" interaction with a **bound-sigil**
model: a plain right-click draws the selected rite's pattern as connected **Rune** blocks and
**binds** that rite to the sigil's BlockEntity; you cannot overdraw a bound sigil; breaking a
bound sigil costs taint + a debuff.

Everything below is grounded in the real code under
`hexerei/src/main/java/com/vel5id/hexerei` (read first: `RitualChalkItem`, `RitualCircleBlock`,
`RitualGlyphBlock`, `CircleSize`, `RitualCircle`, `RitualRecipes`, `RitualRecipe`,
`RitualActivation`, `CycleRiteC2SPacket`, `ChunkTaintData`, `TempestRite`, `WorldTaintAura`,
`HexereiNetwork`, `HexereiLevelEvents`, `HexereiBlocks/Items/BlockEntities/CreativeTabs`).

---

## 0. Terminology and the rename

| Concept | Old | New | Registry id |
|---------|-----|-----|-------------|
| Center block | "Ritual Circle" | **Ritual Sigil** / **Ритуальный сигил** | `hexerei:ritual_sigil` (RENAMED) |
| Ring decal block | "Ritual Glyph" (vanilla chiseled stone decal) | **Rune** / **Руна** | `hexerei:rune` (NEW, replaces `ritual_glyph`) |
| Drawing tool | "Ritual Chalk" (bone-meal art) | **Ritual Chalk** (own coloured-chalk art) | `hexerei:ritual_chalk` (kept) |

### [DECISION] Rename `ritual_circle` → `ritual_sigil` at the registry level (recommended, planned)
The prompt requires the display name "Ritual Sigil". We also rename the **registry id**
`ritual_circle` → `ritual_sigil`. This breaks existing saves (any placed `hexerei:ritual_circle`
becomes air on load), which is acceptable in dev per CLAUDE.md ("breaking saves, acceptable in
dev"). Rationale: a half-renamed block (new label, old id) is a long-lived footgun — every
`HexereiBlocks.RITUAL_CIRCLE.get()` call, the GameTest templates, the creative tab, recipes and
loot tables would carry the stale id forever. One clean rename now is cheaper than perpetual
mismatch. The Java `RegistryObject` constant is also renamed `RITUAL_CIRCLE` → `RITUAL_SIGIL`
and the class `RitualCircleBlock` → `RitualSigilBlock` (file move).

### [DECISION] Runes REPLACE RitualGlyph (runes ARE the new glyphs)
The old `ritual_glyph` block is deleted; `hexerei:rune` takes its place as the ring decal.
`RitualActivation`'s completeness check tests a **glyph predicate** (`p -> level.getBlockState(p)
.is(...)`) — we keep that contract intact by pointing the predicate at the Rune block instead:

```java
Predicate<BlockPos> isGlyph = p -> level.getBlockState(p).is(HexereiBlocks.RUNE.get());
```

Because `RitualCircle.isSmallComplete` / `isMediumComplete` only test **presence** at the ring
offsets (never the connection-shape blockstate), the activation logic needs no other change. The
rune's connection booleans are purely cosmetic to the matcher. This keeps the pure geometry tests
(`RitualCircle`) untouched and green.

Migration touch-list for the rename (no code written here, but the implementer must hit all):
`HexereiBlocks` (2 RegistryObjects), `HexereiItems` (2 BlockItems), `HexereiBlockEntities` (new
sigil BE type), `HexereiCreativeTabs` (swap accepts), `RitualChalkItem`, `RitualActivation`,
`RitualCircleBlock`→`RitualSigilBlock`, `CycleRiteC2SPacket` (rename), `RitualRecipes.MANIFEST_CHALK`
(unchanged — still drops chalk), GameTest holders (`RitualGameTests`, `RitualExpansionGameTests`,
`TempestRiteTaintGameTest`), blockstates/models/loot/lang for both blocks.

---

## 1. Ritual Chalk gets its own texture

Today `models/item/ritual_chalk.json` parents `minecraft:item/generated` with
`layer0: minecraft:item/bone_meal`. Replace `layer0` with a new hexerei texture
`hexerei:item/ritual_chalk`. Model JSON becomes:

```json
{ "parent": "minecraft:item/generated", "textures": { "layer0": "hexerei:item/ritual_chalk" } }
```

The chalk stays a 64-durability `RitualChalkItem`. Texture entry: see §8 (`chalk_item`).

---

## 2 & 6. Sigil + Rune are FLAT decals (rail/string/tripwire-like)

### Sigil block (`RitualSigilBlock`, ex-`RitualCircleBlock`)
Today the sigil is a full cube (`cube_bottom_top`, solid, `requiresCorrectToolForDrops`). Make it
a **thin 2px slab decal** like the runes, so the whole drawn circle reads as chalk on the floor:

- Shape: `Block.box(0, 0, 0, 16, 2.0, 16)` (2px tall — slightly proud of the runes' 1px so the
  center reads as the focus). `noCollission()`, `noOcclusion()`.
- It now hosts a **BlockEntity** (`RitualSigilBlockEntity`, see §3) to store the bound rite, so it
  can NOT be `instabreak` — keep `strength(0.6F)` (chalk-soft, no tool requirement). Drop itself via
  loot table.
- `canSurvive` / `updateShape`: require a sturdy face below (copy `RitualGlyphBlock`'s pattern) so
  the sigil pops if its floor is removed — and §5's destruction penalty fires through the same path.

Properties (in `HexereiBlocks`):
```java
RegistryObject<Block> RITUAL_SIGIL = BLOCKS.register("ritual_sigil",
    () -> new RitualSigilBlock(BlockBehaviour.Properties.of()
        .mapColor(MapColor.COLOR_BLACK)
        .strength(0.6F)
        .noCollission()
        .noOcclusion()
        .sound(SoundType.SAND)));   // chalky, matches the SAND_PLACE draw sound
```

### Rune block (§7) is the 1px decal — described in full in §4/§7.

---

## 3. Circle-draw logic: plain right-click DRAWS + BINDS

### New `RitualSigilBlockEntity`
NBT-persisted per-block state (BlockEntity NBT convention):

| NBT key | Type | Meaning |
|---------|------|---------|
| `hexerei:BoundRite` | String | bound rite id (e.g. `hexerei:tempest`), empty = unbound |
| `hexerei:BoundSize` | String | `SMALL` / `MEDIUM` (the `CircleSize` name) |

API:
```java
boolean isBound();                       // BoundRite non-empty
String boundRiteId();
CircleSize boundSize();                  // CircleSize.valueOf, defaults SMALL
void bind(String riteId, CircleSize sz); // setChanged() + level.sendBlockUpdated(...)
void unbind();                           // clears, setChanged()
```
Register in `HexereiBlockEntities`:
```java
RegistryObject<BlockEntityType<RitualSigilBlockEntity>> RITUAL_SIGIL =
    BLOCK_ENTITIES.register("ritual_sigil",
        () -> BlockEntityType.Builder.of(RitualSigilBlockEntity::new, HexereiBlocks.RITUAL_SIGIL.get()).build(null));
```
No ticker needed (rites are instant; activation is on right-click of the sigil with bare hand).

### `RitualChalkItem.useOn` rewrite (server-authoritative)
When the clicked block is a Ritual Sigil:

1. Resolve the selected rite from chalk NBT (`getSelectedRecipe`, unchanged — reads
   `hexerei:rite`, defaults to `RitualRecipes.ALL.get(0)` = TEMPEST).
2. Read the sigil BE.
3. **GUARD (see §4):** if `be.isBound()` and (`be.boundRiteId()` differs from selected rite id OR
   `be.boundSize()` differs from selected rite's `circleSize()`) → action-bar warn "Destroy the
   circle first", play `FIRE_EXTINGUISH`, return `sidedSuccess`. (If it is bound to the SAME
   rite+size, treat as a no-op "redraw/repair" — re-place any missing runes, do not re-bind, no
   warning. This makes the chalk also a repair tool.)
4. If unbound (or repairing): compute `ring = rite.circleSize().ringPositions(sigilPos)`. For each
   ring cell that `canPlaceRune` (air over sturdy face), `setBlock` a **Rune** (default state; its
   own `updateShape`/neighborChanged then computes connection booleans — §4). Place ALL missing
   cells in one use (not one-per-click as today) so a circle is one action, then `damage` the chalk
   by 1, play `SAND_PLACE`.
5. After placing, if the ring is now complete (`rite.circleSize().isComplete(isRune, sigilPos)`),
   call `be.bind(rite.id(), rite.circleSize())`. Partial draws (some cells blocked) leave it
   unbound and re-drawable.

> The chalk no longer draws a "single glyph on top of an arbitrary block" (old fallback lines
> 57–66). [DECISION] Drop that free-draw path — runes only exist as part of a sigil circle; a
> stray decal block has no gameplay role and would complicate the connection logic at chunk edges.
> Returning `PASS` off-sigil lets the chalk do nothing harmful.

### Rite SELECTION — how the player picks the rite
[DECISION] **Sneak + scroll cycles the rite; plain scroll is left to the hotbar.** Plain
right-click (no sneak) is now the load-bearing DRAW action, so selection must move off plain
scroll to avoid hijacking normal hotbar scrolling whenever chalk is held. We keep the existing
`CycleRiteC2SPacket` + client mouse-scroll hook but gate it on `player.isShiftKeyDown()` (it is
already Shift+Scroll per the current `tip2` lang). This is the smallest, least surprising change:
the only behavioural delta is that the *draw* trigger moved from "selection was scroll, draw was
click" to "selection stays Shift+Scroll, click now draws+binds". The tooltip lines
(`ritual_chalk.rite`, `circle.<size>`, `tip2`) are unchanged in meaning; `tip2` stays
"[Shift+Scroll] Change rite" but we add a second line (see lang §9): "[Right-click sigil] Draw &
bind".

Rename note: `CycleRiteC2SPacket` may stay named as-is (it cycles *rites*, not circles) — no
behaviour change. The client scroll hook in `HexereiClient` keeps its `isShiftKeyDown()` gate.

---

## 4. GUARD against overdraw

A bound sigil holds (riteId, size). The chalk-use check in §3 step 3 rejects any draw whose
(selected riteId, selected size) differs from the bound pair:

```java
if (be.isBound()
        && (!be.boundRiteId().equals(rite.id()) || be.boundSize() != rite.circleSize())) {
    player.displayClientMessage(Component.translatable("hexerei.ritual.destroy_first"), true); // action bar
    level.playSound(null, sigilPos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 0.8F);
    return InteractionResult.sidedSuccess(level.isClientSide);
}
```

So a SMALL 12-rune Tempest cannot be overdrawn into a MEDIUM 20-rune Waning-Moon. The player must
break the circle first (break a rune, or break the sigil — both trigger §5 and clear the bind).

Why compare both rite id AND size (not just size): two rites can share a `CircleSize` (e.g. all
SMALL rites). Binding to the **rite id** lets the chalk's same-rite redraw act as a repair while
still rejecting a *different SMALL rite* (which would silently change what the circle does on
activation — surprising). Same-id+same-size = repair; anything else = reject.

> Note: activation (`RitualActivation`) still matches by **circle completeness + sacrifice id**, so
> the bound rite id is advisory for the draw/guard layer, not the source of truth for which rite
> fires. The sacrifice you throw still selects among same-size rites at activation. The bind exists
> to (a) gate overdraw and (b) drive the destruction penalty's "was a circle here" check. This is
> intentional and keeps `RitualActivation` untouched.

---

## 5. Destruction penalty

Breaking a **bound** sigil, or breaking any **rune** belonging to a bound sigil's ring, applies an
area penalty. Implement in the block's `onRemove`/`playerWillDestroy` path (server-only).

### Trigger points
- **Sigil broken while bound** (`RitualSigilBlock.onRemove`, when `!state.is(newState.getBlock())`
  and the BE was bound): penalty fires, BE is gone with the block.
- **Rune broken** (`RuneBlock.onRemove`): scan the 4 cardinal + diagonal neighbourhood up to radius
  3 for a `ritual_sigil`; if found and that sigil's BE `isBound()`, fire the penalty AND call
  `be.unbind()` (the circle is now broken, so it must be re-drawable). Then let the sigil's own
  `updateShape` leave the remaining runes as a partial ring (they'll re-bind on next full redraw).

### Penalty amounts (grounded in `powerCost/4` convention)
The prompt anchors taint to `powerCost/4`. Use the **bound rite's** `powerCost`:

```java
RitualRecipe r = RitualRecipes.BY_ID.get(be.boundRiteId());
int cost = (r != null) ? r.powerCost() : 40;   // 40 = MANIFEST_CHALK floor if id unknown
float taint = cost / 4f;                        // Tempest 100 -> 25 ; Verdant 60 -> 15 ;
                                                // Bound-Beast 120 -> 30 ; Waning-Moon 150 -> 37.5
ChunkPos cp = new ChunkPos(sigilPos);
ChunkTaintData.get(level).addTaint(cp, taint);
HexereiNetwork.sendTaintSync(level, cp);        // reuse TempestRite's exact pattern
```

`taint = 25f` for Tempest matches `TempestRite`'s own `addTaint(cp, 25f)` exactly — breaking a
bound Tempest circle is as "dirty" as firing it once, which reads right. `ChunkTaintData.addTaint`
caps at 100 and writes a 10% permanent floor automatically (no extra work).

### Player debuff (taint-consequence pattern)
On break, apply a brief debuff to players within radius 4 of the sigil (`getEntitiesOfClass(Player
.class, AABB.inflate(4))`), server-side `addEffect`:

- `MobEffects.WEAKNESS` (Слабость), amplifier 0, `100` ticks (5 s)  — the chalk-dust "backlash".
- `MobEffects.DIG_SLOWDOWN` (Усталость/Mining Fatigue), amplifier 0, `100` ticks — hands fouled.

Plus a sensory cue: `level.sendParticles(ParticleTypes.WITCH, ...)` burst at the sigil and
`SoundEvents.WITHER_DEATH` at low pitch (mirrors `TempestRite`'s aesthetic). Magnitude is
deliberately small (5 s, level I) — a nuisance discouraging careless teardown, not a punishment;
documented `[UNVERIFIED]` for in-game tuning.

### DESIGN-NOTES addendum (numbers to record)
```
# Ritual Overhaul slice
- ritual_circle renamed -> ritual_sigil (id + class + RegistryObject); ritual_glyph removed,
  replaced by `rune` (runes ARE glyphs). Breaking saves, dev-acceptable.
- Sigil is now a flat 2px decal BlockEntity (BoundRite/BoundSize NBT). Runes are flat 1px decals
  with 8-neighbour connection shapes.
- Draw model: plain right-click chalk on sigil draws ALL ring runes in one use + binds rite; same
  rite+size redraw = repair; differing draw rejected ("Destroy the circle first"); rite selection
  stays Shift+Scroll.
- Destruction penalty (break bound sigil OR a ring rune): taint = boundRite.powerCost/4
  (Tempest 25, Verdant 15, Bound-Beast 30, Waning-Moon 37.5; floor cap via ChunkTaintData) +
  Weakness I & Mining Fatigue I for 100t on players within r=4. [UNVERIFIED] tune debuff length.
```

---

## 7. New Rune block — connected-decal blockstate

A **Rune** is a flat 1px decal smaller than the sigil that connects to neighbour runes like rails,
**orthogonally AND diagonally**, with a different texture per connection shape.

### Block class `RuneBlock` (extends `Block`)
- Shape: `Block.box(2, 0, 2, 14, 1.0, 14)` — 12×12 inset, 1px tall, smaller than the 16×16 sigil.
  `noCollission()`, `noOcclusion()`.
- `canSurvive` / `updateShape`: sturdy face below (copy `RitualGlyphBlock`) → pops if floor removed.
- Connection booleans recomputed on placement and on any of the 8 neighbours changing.

### [DECISION] Blockstate = 8 connection booleans + computed model via multipart
Use 8 boolean properties for the 8 neighbours, and select the model with a **multipart**
blockstate (each property gates a model element rotation). This is simpler to keep correct than a
hand-maintained `shape` enum across 2^8 cases and mirrors vanilla `redstone_wire`/`tripwire`'s
multipart connection approach.

Properties (`BooleanProperty`):
```
n, e, s, w            (orthogonal)
ne, se, sw, nw        (diagonal)
```
8 booleans = 256 states; acceptable (vanilla redstone_wire has 1296). Connection rule: a neighbour
in direction d sets that boolean true iff `level.getBlockState(pos.relative-or-offset(d)).is(RUNE)`.
Diagonals use `pos.offset(±1,0,±1)`.

`getStateForPlacement` and `neighborChanged`/`updateShape` both call a shared
`withConnections(level, pos, state)` that scans the 8 neighbours and sets all 8 booleans. Register
the properties in `createBlockStateDefinition`.

> The activation completeness predicate (`isRune`) ignores these booleans (tests `.is(RUNE)` only),
> so geometry tests stay valid regardless of connection state.

### Model variants per connection SHAPE (the texture set)
Rather than one model per 256-state combo, define **6 base decal models** (one per shape class)
and compose them with multipart **rotations** (Y 0/90/180/270). The six shape textures:

| logical shape | when | texture |
|---------------|------|---------|
| **endpoint** | exactly one ortho neighbour (or zero) | `rune_end` |
| **straight** | two opposite ortho (n+s or e+w) | `rune_line` |
| **corner** | two adjacent ortho (n+e etc.) | `rune_corner` |
| **t** | three ortho | `rune_t` |
| **cross** | four ortho | `rune_cross` |
| **diagonal** | a diagonal neighbour present with no ortho on that side | `rune_diagonal` overlay |

Multipart structure (sketch — implementer expands all rotations):
```json
{
  "multipart": [
    { "when": { "n": "true", "s": "true", "e": "false", "w": "false" },
      "apply": { "model": "hexerei:block/rune_line" } },
    { "when": { "e": "true", "w": "true", "n": "false", "s": "false" },
      "apply": { "model": "hexerei:block/rune_line", "y": 90 } },
    { "when": { "n": "true", "e": "true" },
      "apply": { "model": "hexerei:block/rune_corner" } },
    { "when": { "e": "true", "s": "true" },
      "apply": { "model": "hexerei:block/rune_corner", "y": 90 } },
    { "when": { "s": "true", "w": "true" },
      "apply": { "model": "hexerei:block/rune_corner", "y": 180 } },
    { "when": { "w": "true", "n": "true" },
      "apply": { "model": "hexerei:block/rune_corner", "y": 270 } },
    { "when": { "n": "true", "e": "true", "s": "true", "w": "false" },
      "apply": { "model": "hexerei:block/rune_t" } },
    /* …three more T rotations… */
    { "when": { "n": "true", "e": "true", "s": "true", "w": "true" },
      "apply": { "model": "hexerei:block/rune_cross" } },
    /* diagonal overlays, one per corner: */
    { "when": { "ne": "true" }, "apply": { "model": "hexerei:block/rune_diagonal" } },
    { "when": { "se": "true" }, "apply": { "model": "hexerei:block/rune_diagonal", "y": 90 } },
    { "when": { "sw": "true" }, "apply": { "model": "hexerei:block/rune_diagonal", "y": 180 } },
    { "when": { "nw": "true" }, "apply": { "model": "hexerei:block/rune_diagonal", "y": 270 } }
  ]
}
```
Plus a fallback `OR`-guarded `rune_end` for the isolated/single-neighbour case (vanilla pattern:
add the dot for each direction with no connection, or a single `when: {}`-less default element).
For the ring geometry (radius-2 ortho-and-diagonal ring), neighbours connect along the ring so the
visible result is a continuous chalk loop with corner/diagonal pieces at the 12 positions.

Each base model is a flat decal element on the up face:
```json
// models/block/rune_line.json
{ "parent": "block/thin_block",
  "textures": { "particle": "hexerei:block/rune_line", "rune": "hexerei:block/rune_line" },
  "elements": [ { "from": [2,0,2], "to": [14,1,14], "faces": { "up": { "texture": "#rune", "tintindex": -1 } } } ] }
```
(corner/t/cross/diagonal/end identical but with their own texture id). Item model for the Rune
BlockItem: `parent minecraft:item/generated, layer0: hexerei:block/rune_end` (the standalone dot
reads best as the inventory icon).

### Rune registration
```java
RegistryObject<Block> RUNE = BLOCKS.register("rune",
    () -> new RuneBlock(BlockBehaviour.Properties.of()
        .mapColor(MapColor.SAND)
        .strength(0.2F)
        .noCollission()
        .noOcclusion()
        .sound(SoundType.SAND)));
// HexereiItems: BlockItem RUNE ; HexereiBlockEntities: none (no BE on runes)
```
Loot table `data/hexerei/loot_tables/blocks/rune.json` → drops `hexerei:rune` (copy
`ritual_glyph.json`, rename). Note: §5 makes a *bound-circle* rune break apply the penalty before
the drop; the loot table still returns the rune item so it's reusable.

---

## 8 & 9. Wiring details

### Creative tab (`HexereiCreativeTabs`)
Swap `RITUAL_CIRCLE` → `RITUAL_SIGIL`, `RITUAL_GLYPH` → `RUNE` in the `displayItems` accepts;
order unchanged (sigil then rune then chalk).

### Loot tables
- `loot_tables/blocks/ritual_sigil.json` (new; copy `ritual_circle.json`, rename id).
- `loot_tables/blocks/rune.json` (new; copy `ritual_glyph.json`, rename id).
- Delete `ritual_circle.json`, `ritual_glyph.json`.

### Blockstates / models to delete vs add
Delete: `blockstates/ritual_circle.json`, `blockstates/ritual_glyph.json`,
`models/block/ritual_circle.json`, `models/block/ritual_glyph.json`,
`models/item/ritual_circle.json`, `models/item/ritual_glyph.json`.
Add: `blockstates/ritual_sigil.json` (single variant → `block/ritual_sigil`),
`blockstates/rune.json` (multipart, §7), `models/block/ritual_sigil.json` (flat decal, up-face
`hexerei:block/ritual_sigil`), `models/block/rune_{end,line,corner,t,cross,diagonal}.json`,
`models/item/ritual_sigil.json` & `models/item/rune.json` (item/generated icons),
`models/item/ritual_chalk.json` (edit layer0 → `hexerei:item/ritual_chalk`).

---

## Texture asset entries (concrete, for the ComfyUI/MCP pipeline)

Blocks: `remove_bg=false`, fills the square. The chalk item: `remove_bg=true`, transparent bg.
All Minecraft pixel-art style, ~16-colour palette. Sigil/rune textures read as **white/pale chalk
lines on a dark slate-grey square** so they sit on any floor.

### chalk_item (item icon, transparent)
- path: `assets/hexerei/textures/item/ritual_chalk.png`
- 64×64, remove_bg=true, pixel_grid=32
- prompt: "Minecraft pixel-art item icon of a short stick of chalk, held diagonally, white-to-pale-blue
  chalk with a worn rounded tip and faint dust, a thin paper wrapper band near the base, chunky 32px
  pixel grid, ~16 colour palette, crisp dark outline, transparent background, item-icon framing"

### sigil_top (block decal top, opaque square)
- path: `assets/hexerei/textures/block/ritual_sigil.png`
- 64×64, remove_bg=false, pixel_grid=32
- prompt: "Minecraft pixel-art top-down ritual sigil drawn in white chalk on dark slate-grey stone,
  a central pentacle inside a double ring with small star points, faint purple glow on the lines,
  fills the whole tile edge-to-edge, 32px pixel grid, ~16 colour palette, no background transparency"

### rune_end (standalone dot / single-connection terminus)
- path: `assets/hexerei/textures/block/rune_end.png`
- 32×32, remove_bg=false, pixel_grid=32
- prompt: "Minecraft pixel-art top-down small chalk rune terminus on dark slate-grey stone, a single
  short white chalk stroke ending in a small glyph dot, centred, faint purple tint, fills the tile,
  32px grid, ~16 colours, opaque"

### rune_line (straight, runs north–south by default)
- path: `assets/hexerei/textures/block/rune_line.png`
- 32×32, remove_bg=false, pixel_grid=32
- prompt: "Minecraft pixel-art top-down straight chalk line crossing the tile vertically on dark
  slate-grey stone, white chalk with faint purple glow and tiny tick marks, edge-to-edge so it tiles
  seamlessly with neighbours, 32px grid, ~16 colours, opaque"

### rune_corner (L-bend, north→east by default)
- path: `assets/hexerei/textures/block/rune_corner.png`
- 32×32, remove_bg=false, pixel_grid=32
- prompt: "Minecraft pixel-art top-down white chalk line bending ninety degrees from the top edge to
  the right edge on dark slate-grey stone, smooth chalk corner with faint purple glow, lines reach
  the two tile edges for seamless tiling, 32px grid, ~16 colours, opaque"

### rune_t (T-junction, branches n/e/s by default)
- path: `assets/hexerei/textures/block/rune_t.png`
- 32×32, remove_bg=false, pixel_grid=32
- prompt: "Minecraft pixel-art top-down white chalk T-junction on dark slate-grey stone, three chalk
  arms reaching the top, right and bottom edges meeting at a small central glyph node, faint purple
  glow, seamless tiling at the three edges, 32px grid, ~16 colours, opaque"

### rune_cross (4-way junction)
- path: `assets/hexerei/textures/block/rune_cross.png`
- 32×32, remove_bg=false, pixel_grid=32
- prompt: "Minecraft pixel-art top-down white chalk four-way cross on dark slate-grey stone, four arms
  reaching all four edges with a small star glyph at the centre, faint purple glow, seamless tiling on
  every edge, 32px grid, ~16 colours, opaque"

### rune_diagonal (diagonal stroke, corner-to-centre, ne by default)
- path: `assets/hexerei/textures/block/rune_diagonal.png`
- 32×32, remove_bg=false, pixel_grid=32
- prompt: "Minecraft pixel-art top-down white chalk diagonal stroke running from the top-right corner
  toward the tile centre on dark slate-grey stone, thin dashed chalk line with faint purple glow, mostly
  transparent-feeling dark field so it overlays a base rune, 32px grid, ~16 colours, opaque"

---

## Lang keys (en_us / ru_ru)

Edit (rename block keys, drop glyph→rune):
```
"block.hexerei.ritual_sigil"  : "Ritual Sigil"            / "Ритуальный сигил"
"block.hexerei.rune"          : "Rune"                     / "Руна"
```
Remove: `block.hexerei.ritual_circle`, `block.hexerei.ritual_glyph`.

Add:
```
"hexerei.ritual.destroy_first": "Destroy the circle first" / "Разрушьте круг"
"item.hexerei.ritual_chalk.tip3": "[Right-click sigil] Draw & bind"
                                / "[ПКМ по сигилу] Нарисовать и связать"
```
Keep unchanged: `item.hexerei.ritual_chalk`, `.rite`, `.circle.small`, `.circle.medium`, `.tip2`,
all five `ritual.hexerei.*` rite names.

---

## Tests

Pure (JUnit, `src/test/java`):
- `RuneConnectionTest` — a pure `RuneShapes.connections(Predicate<dir8> hasNeighbour)` helper that
  maps the 8 booleans to the (shape, rotation) the multipart should pick; assert straight/corner/T/
  cross/diagonal/endpoint cases. Factor the boolean→shape logic into a pure final class so it's
  testable without a level (CLAUDE.md two-tier strategy).
- Reuse existing `RitualCircleTest` geometry tests verbatim (offsets unchanged).
- `RitualSigilBindTest` — pure helper over the bind/guard predicate: given (boundRite, boundSize)
  and (selectedRite, selectedSize), assert allow-draw / repair / reject.

GameTests (`src/main/java/.../test`, `@GameTestHolder(MODID)`):
- `RuneDrawBindGameTest` — place sigil, give chalk Tempest, right-click → 12 runes present, BE
  bound to `hexerei:tempest`/`SMALL`.
- `OverdrawGuardGameTest` — bound SMALL Tempest, select MEDIUM Waning-Moon, right-click → no MEDIUM
  runes placed, BE still bound to Tempest.
- `DestructionPenaltyGameTest` — break a bound sigil → `ChunkTaintData.getTaint` increased by
  ~powerCost/4; nearby player has Weakness. (Mark `required=false` if the multi-arena taint state
  proves flaky, per CLAUDE.md; back it with the pure bind test + a smoke check.)
- Update existing `RitualGameTests`/`RitualExpansionGameTests`/`TempestRiteTaintGameTest` to place
  `RUNE` ring + `RITUAL_SIGIL` center instead of glyph/circle; activation predicate now `.is(RUNE)`.

---

## Summary of forks ([DECISION]s)
1. **Rename `ritual_circle`→`ritual_sigil` at the registry id level** (not just display) — breaking
   saves, dev-acceptable; avoids perpetual id/label mismatch.
2. **Runes REPLACE RitualGlyph** (runes ARE glyphs) — activation predicate retargeted to `RUNE`;
   geometry/completeness logic untouched.
3. **Rite selection stays Shift+Scroll**; plain right-click becomes draw+bind — minimal delta, no
   hotbar-scroll hijack.
4. **Drop the off-sigil free-draw fallback** — runes only exist as part of a sigil ring.
5. **8-boolean multipart blockstate** for rune connections (not a hand-rolled shape enum) — mirrors
   vanilla redstone_wire/tripwire, fewer correctness traps.
6. **Same-rite redraw = repair, different = reject** — bind on rite id (not just size) so same-size
   rites can't silently swap a circle's behaviour.
