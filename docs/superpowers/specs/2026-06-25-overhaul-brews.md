# Overhaul: Brews as Modded Items (per-brew textures)

Date: 2026-06-25
Subsystem: Brewing — item rendering
Status: design, ready for `mc-mod-implement`

## Goal

Replace the vanilla potion-bottle + tinted-overlay rendering of `BrewItem` with **six distinct,
hand-themed item textures**, one per brew, while keeping the single registered item
(`hexerei:brew`) whose brew identity lives in `ItemStack` NBT. No new items are registered; no
recipe/effect logic changes.

## Ground truth (real code read)

- One registered item: `HexereiItems.BREW` = `ITEMS.register("brew", () -> new BrewItem(new
  Item.Properties().stacksTo(16)))` — `registry/HexereiItems.java:44`.
- Brew identity NBT: `BrewItem` stores the brew id under the key string **`"BrewId"`**
  (`private static final String BREW_ID = "BrewId"`), set in `BrewItem.of(...)` via
  `stack.getOrCreateTag().putString(BREW_ID, brew.id())`. `BrewItem.brewOf(...)` reads it via
  `stack.getTag()` (null-checked). — `item/BrewItem.java:32,40,46`.
- Six brews and their string ids (`brewing/Brews.java`): `sleeping_draught`, `frailty`,
  `witchs_sight`, `bloodwort_tonic`, `hags_swiftness`, `withering_bile`. Their `color()` ints are
  currently consumed only by the tint handler.
- Current model `assets/hexerei/models/item/brew.json` = `minecraft:item/generated` with
  `layer0 = minecraft:item/potion_overlay` (tinted) + `layer1 = minecraft:item/potion`.
- Current tint: `HexereiClient.registerItemColors` registers a color handler returning
  `0xFF000000 | BrewItem.color(stack)` for `tintIndex == 0` on `HexereiItems.BREW.get()`
  (`client/HexereiClient.java:28-33`).

## Chosen rendering approach — `ItemProperties` override (clean 1.20.1 Forge)

Use a single **`ItemProperties.register`** float predicate keyed on the brew id, plus an
`overrides[]` block in the parent `brew.json` that maps each discrete predicate value to a per-brew
child model. This is the standard vanilla mechanism (same machinery as `CompassItem`/`bow#pulling`)
— no `CustomLoader`/`IModelLoader`, no datagen, no baked-model code, and it composes with the
existing single NBT item.

### Why over the alternatives

- **CustomLoader / `BakedModel`**: heaviest path; needs a registered loader + JSON `loader` field
  + render code. Overkill for "pick 1 of 6 static models by NBT". `[DECISION]` rejected.
- **6 separate registered items**: violates the design ("brew identity lives in ItemStack NBT,
  single 'brew' item"), breaks existing `BrewItem.of`/recipe/collection code, and needs 6 creative
  tab entries. Rejected.
- **Keep the tint + overlay**: this is exactly what we are removing.

### Predicate contract

Register one property `hexerei:brew` whose float value is the **index of the brew** in a fixed
order. Indices are assigned by the same iteration order as `Brews.BY_ID` (a `LinkedHashMap`, so
order is stable and matches declaration order):

| index | brew id            | predicate value |
|-------|--------------------|-----------------|
| 0     | (unknown / no NBT) | `0.0`           |
| 1     | `sleeping_draught` | `0.1`           |
| 2     | `frailty`          | `0.2`           |
| 3     | `witchs_sight`     | `0.3`           |
| 4     | `bloodwort_tonic`  | `0.4`           |
| 5     | `hags_swiftness`   | `0.5`           |
| 6     | `withering_bile`   | `0.6`           |

Use **0.1 steps** (not raw ordinals) so the `predicate >= value` matching in vanilla
`ItemOverrides` selects exactly the intended bucket (vanilla picks the highest override whose
threshold `<= actual`; 0.1 granularity keeps the floats exact enough and leaves headroom for new
brews at 0.7, 0.8, …). A stack with no/unknown `BrewId` returns `0.0` and falls through to the base
model (a generic bottle — see fallback model below).

`[DECISION]` Index source. Add a small helper `Brews.indexOf(String id)` returning the 1-based
position in `BY_ID` insertion order (or `0` if unknown), so the predicate and the model `overrides`
stay in lock-step with one list. This keeps the float mapping out of the client class and unit-testable.

## Files

### New Java

**`Brews.indexOf` helper** — edit `brewing/Brews.java` (pure, unit-testable). Add an ordered id
list derived from `BY_ID.keySet()` and:

```java
/** 1-based position of a brew id in catalog order, or 0 if unknown (drives the item-model override). */
public static int indexOf(String id) {
    int i = 1;
    for (String key : BY_ID.keySet()) {
        if (key.equals(id)) return i;
        i++;
    }
    return 0;
}
```

### Edit: `item/BrewItem.java`

Add a tiny accessor used by the client predicate (keeps NBT-key knowledge inside `BrewItem`,
matching the existing `color(...)` accessor pattern, and honors the "hasTag() then read" rule):

```java
/** 0 when no/unknown brew NBT, else 1..N catalog index — drives the item-model override predicate. */
public static int modelIndex(ItemStack stack) {
    if (!stack.hasTag()) return 0;            // never bare getTag(); guard first
    return Brews.indexOf(stack.getTag().getString(BREW_ID));
}
```

Leave `color(ItemStack)` in place for now (still referenced) but it is no longer wired to a tint
handler; flag it `// FUTURE` — may be reused later for a particle/GUI accent. `[DECISION]` keep vs.
delete `color()`: keep, low cost, avoids touching `Brew`/`BrewColor` and the cauldron blend logic
that legitimately uses `Brew.color()`.

### Edit: `client/HexereiClient.java`

1. **Remove** the `registerItemColors` method entirely (and its `RegisterColorHandlersEvent.Item`
   import) — the tinted overlay is gone, so the color handler is dead. (`BrewItem.color` import in
   that file goes too; `BrewItem` is still imported for `modelIndex`.)
2. **Add** an `ItemProperties` registration inside `onClientSetup`'s `event.enqueueWork(...)` block
   (must run on the client thread, after item registration):

```java
ItemProperties.register(HexereiItems.BREW.get(),
        new ResourceLocation(HexereiMod.MODID, "brew"),
        (stack, level, entity, seed) -> BrewItem.modelIndex(stack) / 10.0f);
```

   Imports: `net.minecraft.client.renderer.item.ItemProperties`,
   `net.minecraft.resources.ResourceLocation`. The lambda returns `0.0, 0.1 … 0.6` matching the
   table above. `ItemProperties.register` is `@OnlyIn(Dist.CLIENT)`; it is already inside the
   `FMLClientSetupEvent` handler in a `Dist.CLIENT` event-bus-subscriber class, so no extra
   `DistExecutor` guard is needed.

### Edit: `assets/hexerei/models/item/brew.json` (parent + overrides + fallback)

Replace the tinted-potion model with a plain generated model that is itself the **fallback bottle**
(used for index 0 / unknown NBT, e.g. a `/give` with no `BrewId`), plus an `overrides[]` selecting
the six child models:

```json
{
  "parent": "minecraft:item/generated",
  "textures": {
    "layer0": "hexerei:item/brew/brew_empty"
  },
  "overrides": [
    { "predicate": { "hexerei:brew": 0.1 }, "model": "hexerei:item/brew/sleeping_draught" },
    { "predicate": { "hexerei:brew": 0.2 }, "model": "hexerei:item/brew/frailty" },
    { "predicate": { "hexerei:brew": 0.3 }, "model": "hexerei:item/brew/witchs_sight" },
    { "predicate": { "hexerei:brew": 0.4 }, "model": "hexerei:item/brew/bloodwort_tonic" },
    { "predicate": { "hexerei:brew": 0.5 }, "model": "hexerei:item/brew/hags_swiftness" },
    { "predicate": { "hexerei:brew": 0.6 }, "model": "hexerei:item/brew/withering_bile" }
  ]
}
```

Overrides must be listed in **ascending predicate order** (vanilla requirement). The fallback uses
its own `brew_empty` texture (a clear/uncolored witch bottle) so an NBT-less stack still renders a
sensible bottle rather than a magenta missing model.

### New: six child item models

Put them in a `brew/` subfolder to keep the item-model directory tidy and namespace the textures
the same way. Each is a flat `item/generated` model with a single layer pointing at its texture:

- `assets/hexerei/models/item/brew/sleeping_draught.json`
- `assets/hexerei/models/item/brew/frailty.json`
- `assets/hexerei/models/item/brew/witchs_sight.json`
- `assets/hexerei/models/item/brew/bloodwort_tonic.json`
- `assets/hexerei/models/item/brew/hags_swiftness.json`
- `assets/hexerei/models/item/brew/withering_bile.json`

Each file (substitute id):

```json
{
  "parent": "minecraft:item/generated",
  "textures": { "layer0": "hexerei:item/brew/<id>" }
}
```

### New: seven textures

Six themed brews + one neutral fallback (`brew_empty`). All under
`assets/hexerei/textures/item/brew/`. Item icons → `64x64`, `remove_bg true`, `pixel_grid 32`,
chunky Minecraft pixel-art, ~16-color palette. Common silhouette: a small round-bellied
cork-stopped **witch's potion bottle** (same family across all six so they read as one set), only
the glass tint, liquid, and a tiny themed motif change.

(See "Texture asset entries" — these are mirrored into the StructuredOutput `textures[]`.)

## Texture asset entries

| logical_id | target_path | theme |
|---|---|---|
| `brew_sleeping_draught` | `assets/hexerei/textures/item/brew/sleeping_draught.png` | indigo sleep |
| `brew_frailty` | `assets/hexerei/textures/item/brew/frailty.png` | sickly green |
| `brew_witchs_sight` | `assets/hexerei/textures/item/brew/witchs_sight.png` | glowing teal eye |
| `brew_bloodwort_tonic` | `assets/hexerei/textures/item/brew/bloodwort_tonic.png` | deep red |
| `brew_hags_swiftness` | `assets/hexerei/textures/item/brew/hags_swiftness.png` | bright yellow-green |
| `brew_withering_bile` | `assets/hexerei/textures/item/brew/withering_bile.png` | black-green decay |
| `brew_empty` | `assets/hexerei/textures/item/brew/brew_empty.png` | clear fallback |

All: width 64, height 64, remove_bg true, pixel_grid 32.

### Prompts

- **sleeping_draught** — "Minecraft pixel-art item icon, small round witch's potion bottle with a
  cork stopper, glass filled with deep indigo-violet liquid, a soft swirling spiral of pale lilac
  inside suggesting sleep, faint moon-blue glints on the glass, chunky 32px grid, ~16 color
  palette, crisp outline, transparent background, centered."
- **frailty** — "Minecraft pixel-art item icon, small round witch's potion bottle with a cork
  stopper, glass filled with murky sickly olive-green liquid, a few dull bubbles and a thin scummy
  film at the top suggesting weakness and decay, dull desaturated palette, chunky 32px grid, ~16
  colors, crisp outline, transparent background, centered."
- **witchs_sight** — "Minecraft pixel-art item icon, small round witch's potion bottle with a cork
  stopper, glowing luminous teal-cyan liquid, a single pale eye motif faintly visible inside the
  glass, soft cyan glow halo around the bottle, mystical, chunky 32px grid, ~16 colors, crisp
  outline, transparent background, centered."
- **bloodwort_tonic** — "Minecraft pixel-art item icon, small round witch's potion bottle with a
  cork stopper, rich deep crimson-red liquid like fresh blood, a warm scarlet highlight on the
  glass, a tiny dark red leaf or root sprig hinted inside, chunky 32px grid, ~16 colors, crisp
  outline, transparent background, centered."
- **hags_swiftness** — "Minecraft pixel-art item icon, small round witch's potion bottle with a
  cork stopper, vivid bright yellow-green liquid, fizzing energetic bubbles and tiny upward speed
  streaks inside, lively saturated palette, chunky 32px grid, ~16 colors, crisp outline,
  transparent background, centered."
- **withering_bile** — "Minecraft pixel-art item icon, small round witch's potion bottle with a
  cork stopper, oily black-green sludge liquid, sickly toxic glow, thin wisps of dark vapor rising
  from the neck, ominous, chunky 32px grid, ~16 colors, crisp outline, transparent background,
  centered."
- **brew_empty** — "Minecraft pixel-art item icon, small round empty witch's potion bottle with a
  cork stopper, clear pale-grey glass with faint blue-water highlights, no colored liquid, simple
  and neutral, chunky 32px grid, ~16 colors, crisp outline, transparent background, centered."

## Lang

No new lang keys. Brew display names already come from `Brew.nameKey()` via
`BrewItem.getName(...)` (`brew.hexerei.*` keys already in `en_us.json` / `ru_ru.json`). Verify the
six keys exist; do not add new ones for this slice.

## Verification

- **Unit** (`Brews` is pure): `BrewsIndexTest` — `indexOf` returns 1..6 in declaration order and 0
  for `null`/unknown; assert each known id maps to the exact predicate float used in `brew.json`
  (`indexOf(id)/10.0f`).
- **`BrewItem.modelIndex`** test (pure-ish; build a stack via `BrewItem.of`): asserts a no-tag
  stack → 0, each brew stack → its catalog index.
- **In-world / manual**: client launch, give each brew via cauldron collection (or
  `/give @p hexerei:brew{BrewId:"witchs_sight"}`), confirm each renders its own texture and the
  no-NBT `hexerei:brew` renders `brew_empty` — **no magenta**, no tinted vanilla overlay. (Model
  swap is data-driven; covered by manual visual check per the build-verify UI rule, not by a
  GameTest.)

## Out of scope / deferred

- Animated/`overlay` second layer per brew (e.g. glow on witchs_sight) — single flat layer this
  slice; revisit if the eye/glow needs an emissive overlay (`layer1` + a second texture).
- Reusing `Brew.color()` for a drink particle accent — left as `// FUTURE` in `BrewItem`.
- Held/3rd-person model tweaks — child models inherit `item/generated` defaults; no custom
  `display` transforms.
