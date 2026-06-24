# Hexerei Overhaul — Master Roadmap

Date: 2026-06-25
Status: SYNTHESIS (design only — no code/assets touched). Consolidates the five subsystem specs:

- `2026-06-25-overhaul-cauldron.md` — Witch's Cauldron block visuals + filled/boiling states + particles
- `2026-06-25-overhaul-brews.md` — per-brew item rendering (drop the vanilla potion overlay)
- `2026-06-25-overhaul-ritual.md` — Ritual Chalk / Sigil / Rune rework (sigil rename, rune connection block, draw+bind)
- `2026-06-25-overhaul-altar.md` — Altar Artefacts (Slice E) + Taint Punishment ladder (Rung 1)
- `2026-06-25-overhaul-herbs.md` — herbs/mushrooms roster + full texture regen (snowbell→hellebore rename)

Companion: `2026-06-25-overhaul-textures.json` — the merged, flat texture manifest for batch generation.

---

## 0. The five subsystems at a glance

| # | Subsystem | Breaking? | Touches shared files? | Texture count | Risk |
|---|-----------|-----------|-----------------------|--------------:|------|
| C | Cauldron visuals | No | `HexereiClient`, `HexereiParticles` (additive) | 8 | low — purely additive |
| B | Brews item render | No | `HexereiClient` (removes tint handler) | 7 | low — data-driven model swap |
| R | Ritual chalk/sigil/rune | **YES** (id renames) | `HexereiBlocks/Items/BlockEntities/CreativeTabs/lang` | 8 | high — id rename + new block + new BE |
| A | Altar artefacts + taint | No | `HexereiItems/CreativeTabs/lang/HexereiLevelEvents` | 3 | med — ThreadLocal context + rite refactor |
| H | Herbs/mushrooms | **YES** (snowbell→hellebore) | `HexereiBlocks/Items/Crops/CreativeTabs/lang/AltarBlockEntity` | 98 | med — large surface, but mostly mechanical |

Two subsystems carry **breaking registry renames** (R: `ritual_circle`→`ritual_sigil`, `ritual_glyph`→`rune`;
H: `snowbell`→`hellebore`). Both are dev-acceptable per CLAUDE.md ("breaking saves, acceptable in dev"). They
should land **before** anything that depends on the renamed ids, and their lang/asset renames must be serialized
against the other lang edits (see §2).

---

## 1. Recommended BUILD ORDER (with dependency chains)

The five subsystems are mostly independent. The only **hard** code dependency is internal to R (rename → block →
draw-logic). The only **cross-subsystem** coupling is the shared-file edit list (§2) and a soft note that the
altar taint helper (A) and ritual destruction penalty (R) both call `ChunkTaintData.addTaint` + `sendTaintSync`
(no ordering constraint — neither edits the other's class).

Build order, justified:

### Phase 1 — Independent, low-risk, ship first (parallelizable)

**1. Cauldron visuals (C)** — fully additive. New particles, new models, two new BooleanProperties on
`CauldronBlock`, a client ticker, and a block-color provider. Touches `HexereiClient` (add two particle
providers + `registerBlockColors`) and `HexereiParticles` (additive). No registry renames, no dependency on any
other subsystem. Good first slice to validate the texture pipeline on block + particle sprites.

**2. Brews item render (B)** — also additive except it **removes** the `registerItemColors` handler in
`HexereiClient`. Because both C and B edit `HexereiClient`, serialize them (see §2): do C's `HexereiClient` edits
first or in the same pass, since C *adds* `registerBlockColors` while B *removes* `registerItemColors` and *adds*
the `ItemProperties.register` in `onClientSetup`. They touch different methods, so a single coordinated edit of
`HexereiClient` covering both is cleanest. No registry changes; pure data-driven model swap.

> C and B can be developed in parallel branches but their `HexereiClient` edits must be merged in one serialized
> pass (§2). Recommend: do C, then B, on the same branch.

### Phase 2 — The big breaking rename (do before any consumer)

**3. Ritual chalk/sigil/rune (R)** — this is the **largest single-subsystem change** and carries a registry
rename that ripples through `HexereiBlocks/Items/BlockEntities/CreativeTabs`, GameTests, blockstates, models,
loot, and lang. **Internal build order is mandatory and must be respected:**

```
R.1  Rename ritual_circle -> ritual_sigil at registry id + class (RitualCircleBlock -> RitualSigilBlock),
     update HexereiBlocks/Items/CreativeTabs/loot/blockstate/model/lang.  (compile green, old behaviour intact)
R.2  Add RitualSigilBlockEntity + register in HexereiBlockEntities; make the sigil a flat 2px decal hosting it.
R.3  Add RuneBlock (8 connection BooleanProperties) + RuneShapes pure helper; register RUNE (replaces
     ritual_glyph); delete RitualGlyphBlock + its assets; retarget RitualActivation predicate to RUNE.
R.4  Rewrite RitualChalkItem.useOn -> draw-ALL-runes + bind on the sigil BE; overdraw guard; repair-on-redraw.
R.5  Destruction penalty in RitualSigilBlock.onRemove + RuneBlock.onRemove (taint + debuff).
R.6  Update GameTest holders to place RUNE ring + RITUAL_SIGIL center.
```

Rationale for ordering R before A's lang edits and before H: R deletes/renames the most lang+asset keys; doing it
first gives a clean base for the other lang appends. R is the riskiest slice, so isolating it on its own branch
before stacking A/H reduces merge-conflict surface in `HexereiBlocks/Items/CreativeTabs/lang`.

### Phase 3 — Altar artefacts + taint punishment (A)

**4. Altar artefacts + taint (A)** — depends on no other subsystem's *code*, but it edits `HexereiItems`,
`HexereiCreativeTabs`, both lang files, and `HexereiLevelEvents`. Do it **after R** so the lang/CreativeTabs files
are in their post-rename shape (fewer conflicts). Internal order:

```
A.1  ArtefactDef + ArtefactDefs + ArtefactItem (pure data + item).  Register in HexereiItems + CreativeTabs.
A.2  RitualContext (ThreadLocal) + Rites.addRitualTaint shared helper.
A.3  Refactor the 5 rites (VerdantRite/TempestRite/WaningMoonRite/SpawnItemRite/BoundBeastRite) to call
     Rites.addRitualTaint; VerdantRite reads effectMul for its growth budget.
A.4  AltarBlockEntity: artefact slot + taintMul/effectMul NBT + applyArtefact/coreBe/accessors.
A.5  AltarBlock.use place/swap/retrieve; onRemove drop; payPower returns @Nullable IPowerSource;
     RitualActivation wraps perform in RitualContext.begin/end.
A.6  WorldTaintAura.punishPlayers + pure applyLadder; HexereiLevelEvents calls it under gt % 200.
A.7  Temporary crafting recipes + lang + DESIGN-NOTES.
```

> A's rite refactor (A.3) touches `TempestRite` — note that R's destruction penalty also references
> TempestRite's `addTaint(25f)` *pattern* but does **not** edit TempestRite. No file collision. If A lands
> before R, R simply reuses `ChunkTaintData.addTaint`/`sendTaintSync` directly (as its spec already states) and
> need not route through `Rites.addRitualTaint` (the destruction penalty is not an artefact-scaled rite). **No
> ordering constraint between A and R on the taint helper** — keep R's penalty on the raw `ChunkTaintData` call.

### Phase 4 — Herbs/mushrooms roster (H)

**5. Herbs/mushrooms (H)** — largest *texture* surface (99 PNGs) but mechanically the most mechanical/independent.
Do it **last** because: (a) it edits `HexereiBlocks/Items/Crops/CreativeTabs/lang` and `AltarBlockEntity`, all of
which R and A also touch — landing H last means it appends onto the settled post-R, post-A versions of those
files; (b) the snowbell→hellebore rename should be serialized after R's rename churn so the two breaking renames
don't interleave in lang/blockstate edits; (c) it has zero dependency on C/B/R/A code. Internal order:

```
H.1  snowbell -> hellebore rename (HexereiCrops field+id, asset renames, lang).
H.2  4 new farmland crops (crowseye, celandine, hops[wormwood=true], sandwort[big=false]) via crop(...).
H.3  New produce items (CROWSEYE_BERRY/CELANDINE/HOPS/SANDWORT/MISTLETOE_SPRIG/GLOWING_SPORE) in HexereiItems.
H.4  MistletoeBlock (WitchCropBlock subclass, log/leaf mayPlaceOn) registered inline.
H.5  BloodMossBlock (CarpetBlock) + AltarBlockEntity resolveDynamic branch (4/20) + loot JSON.
H.6  WitchMushroomBlock (shared) + ZEVANTY/PUFFBALL/WEBCAP + loot JSONs (zevanty +25% glowing_spore pool).
H.7  CreativeTabs accepts, blockstates/models, lang, DESIGN-NOTES.
```

### Dependency chain summary (the only hard arrows)

```
R.1 (sigil rename) ─► R.2 (sigil BE) ─► R.4 (draw+bind needs the BE)
R.3 (rune block + RuneShapes) ─► R.4 (draw places runes) ─► R.5 (penalty scans runes/sigil)
A.1 (ArtefactDef) ─► A.4 (BE stores it) ─► A.5 (use places it)
A.2 (RitualContext+Rites) ─► A.3 (rites call it) ─► A.5 (activation sets the context)
H.3 (produce items) ─► H.2/H.4 (crop produce suppliers reference them)
H.5 BloodMossBlock ─► AltarBlockEntity resolveDynamic branch
```

Cross-subsystem: **none are hard.** C/B/R/A/H can each be built and tested in isolation. The serialization in §1
(C+B → R → A → H) is chosen to minimize **shared-file merge conflicts** (§2), not because of code dependencies.

---

## 2. SHARED-FILE collision plan

These files are edited by more than one subsystem. To avoid clobbering, edit them in the **serialized order C → B
→ R → A → H** (matching the build order), and within each file make only that subsystem's documented edits.

### 2.1 `client/HexereiClient.java` — edited by **C, B**
- **C** adds: two particle sprite-set providers in `registerParticles`; a new `registerBlockColors`
  (`RegisterColorHandlersEvent.Block`) returning `CauldronBlockEntity.getColor()` for tintindex 0.
- **B** removes: `registerItemColors` + the `RegisterColorHandlersEvent.Item` import + the `BrewItem.color`
  usage; adds `ItemProperties.register(BREW, hexerei:brew, modelIndex/10f)` inside `onClientSetup` enqueueWork.
- **Collision:** different methods (`registerParticles`/new `registerBlockColors` vs `registerItemColors`/
  `onClientSetup`). **Plan:** do C and B in one coordinated pass on `HexereiClient`. Net result: the file gains a
  block-color handler + two particle providers + an item-property registration, and loses the item-color handler.

### 2.2 `registry/HexereiBlocks.java` — edited by **R, H**
- **R**: rename `RITUAL_CIRCLE`→`RITUAL_SIGIL` (id `ritual_sigil`, flat decal props); replace `RITUAL_GLYPH`→
  `RUNE` (`RuneBlock`, flat decal props).
- **H**: add `BLOOD_MOSS` (`BloodMossBlock`), `ZEVANTY`/`PUFFBALL`/`WEBCAP` (`WitchMushroomBlock`), `MISTLETOE`
  (`MistletoeBlock`). (The 4 farmland crops register via `HexereiCrops.crop(...)`, not directly here.)
- **Collision:** disjoint RegistryObjects. **Plan:** R first (renames in place), then H appends new blocks. No
  same-line conflict.

### 2.3 `registry/HexereiItems.java` — edited by **R, A, H**
- **R**: rename `RITUAL_CIRCLE`→`RITUAL_SIGIL`, `RITUAL_GLYPH`→`RUNE` BlockItems.
- **A**: add `BONE_CHARM`/`WAX_POPPET`/`OBSIDIAN_SKULL` (`ArtefactItem`).
- **H**: add produce items + mushroom/moss BlockItems + mistletoe seed.
- **Collision:** disjoint. **Plan:** R (renames) → A (append artefacts) → H (append herbs/produce). Append-only
  after R, so no conflict.

### 2.4 `registry/HexereiCreativeTabs.java` — edited by **R, A, H**
- **R**: swap `RITUAL_CIRCLE`→`RITUAL_SIGIL`, `RITUAL_GLYPH`→`RUNE` in `displayItems` accepts.
- **A**: add the three artefacts after the charms.
- **H**: accept new produce + mushroom/moss BlockItems (seeds auto via `SEED_ITEMS` loop).
- **Collision:** all three append/replace different `output.accept(...)` lines. **Plan:** R (swap) → A (insert
  after charms) → H (insert in the herbs section). Keep each subsystem's accepts grouped to keep diffs clean.

### 2.5 `assets/hexerei/lang/en_us.json` + `ru_ru.json` — edited by **R, A, H** (highest conflict risk)
JSON object edits are the most clobber-prone. **Plan — strictly serialized, append-or-rename only:**
1. **R first**: rename `block.hexerei.ritual_circle`→`ritual_sigil`, `ritual_glyph`→`rune`; add
   `hexerei.ritual.destroy_first`, `item.hexerei.ritual_chalk.tip3`. (Deletes the old two keys.)
2. **A next**: add 3 artefact item names + 4 artefact tooltip keys. (Pure append.)
3. **H last**: remove `snowbell` keys; add `hellebore` + all new crop/seed/produce/mushroom/moss keys. (Mixed
   remove + append — do last so the file is otherwise settled.)
- **Rule:** never let two subsystems edit the lang files in overlapping passes; each subsystem completes its lang
  block fully (both en_us and ru_ru) before the next starts. The renames (R's ritual keys, H's snowbell keys) are
  the only deletions — keep them in their own subsystem's pass.

### 2.6 `blockentity/AltarBlockEntity.java` — edited by **A, H**
- **A**: artefact ItemStack field + `taintMul`/`effectMul` NBT + `applyArtefact`/`coreBe`/accessors; `onLoad`
  recompute; `setCore(null)` drop + reset.
- **H**: add a `bs.is(HexereiBlocks.BLOOD_MOSS)` branch in `resolveDynamic` → 4/20. `[UNVERIFIED]` balance.
- **Collision:** A touches NBT/save/load/setCore; H touches only `resolveDynamic`. Different methods. **Plan:**
  A first (the bigger change), then H adds the one `resolveDynamic` branch.

### 2.7 `HexereiLevelEvents.java` — edited by **A** only
- **A**: under `gt % 200`, also call `WorldTaintAura.punishPlayers(sl)` alongside the existing `pulse`.
- No other subsystem touches it. **No collision.** (Listed because the task named it as a shared-file candidate;
  it is in fact single-owner.)

### 2.8 `HexereiMod.java` — edited by **none** (no collision)
The task named `HexereiMod` as a candidate. None of the five specs edit it: new particles register through the
existing `HexereiParticles.PARTICLES.register(modBus)` already wired in `HexereiMod`; new BE types through the
existing `HexereiBlockEntities` register; the new `RitualSigilBlockEntity` registers in `HexereiBlockEntities`,
not `HexereiMod`. **No `HexereiMod` edit required by any slice.** (Flag for the implementer: if `HexereiParticles`
or `HexereiBlockEntities` is *newly* created — it is not, both exist — only then would `HexereiMod` need a
`.register(modBus)` line. Both DeferredRegisters already exist and are wired.)

### 2.9 `HexereiParticles.java` — edited by **C** only
Additive (`CAULDRON_BUBBLE`, `CAULDRON_STEAM`). Single-owner. No collision.

### 2.10 GameTest holders — edited by **R** only
`RitualGameTests`, `RitualExpansionGameTests`, `TempestRiteTaintGameTest` get the rune/sigil substitution. A adds
*new* GameTest files (`AltarArtefactGameTests`, `TaintPunishGameTests`) and does not edit R's. No collision.

### Serialized edit order for every shared file (one line)

```
HexereiClient.java ........ C+B (coordinated single pass)
HexereiBlocks.java ........ R, then H
HexereiItems.java ......... R, then A, then H
HexereiCreativeTabs.java .. R, then A, then H
en_us.json / ru_ru.json ... R, then A, then H   (each subsystem completes both langs fully before the next)
AltarBlockEntity.java ..... A, then H
HexereiLevelEvents.java ... A (single owner)
HexereiParticles.java ..... C (single owner)
HexereiBlockEntities.java . R (single owner — new RITUAL_SIGIL BE type)
```

---

## 3. Consolidated [DECISION] list for the human (each with recommended default)

The implementer should confirm these before building. Defaults are the spec authors' recommendations; they are
internally consistent and buildable as-is.

### 3.1 Texture resolution — **DEFAULT: HD where the pipeline says so, per asset class**
The pipeline renders **item icons best at 64×64** (remove_bg=true, 32-grid) and **block faces at 32×32**
(remove_bg=false). The five specs already follow this:
- **Item icons** (brews, artefacts, chalk, seeds, produce, glowing_spore): **64×64, remove_bg=true, grid 32.**
- **Block faces** (cauldron sides, sigil, blood_moss, mushroom block sprites): **32×32, remove_bg=false** —
  except `ritual_sigil.png` which the ritual spec authors at **64×64** (a detailed pentacle reads better at HD)
  and `cauldron_liquid_boiling.png` which is **32×64** (a 2-frame vertical animation strip).
- **Crop stage cross-sprites**: **32×32, remove_bg=true, pixel_grid 16** (vanilla crops are 16-native; authored
  at 32 for crispness but on a 16-grid so the billboard reads as a plant).
- **Particle sprites** (cauldron_bubble/steam): **8×8, remove_bg=true** (tiny, like vanilla bubble/cloud).
> **Recommendation:** accept the per-asset-class defaults as encoded in `2026-06-25-overhaul-textures.json`. The
> one genuine knob is whether to bump block faces from 32→64 globally for a sharper HD look; default **keep 32**
> for blocks (matches the existing on-disk crop/altar art and the pipeline's stated sweet spot), reserve 64 for
> the two callouts above. If the human wants uniform HD blocks, change `width/height` to 64 for the block-face
> entries — the prompts already say "fills the square" so they regenerate cleanly at either size.

### 3.2 `ritual_circle` → `ritual_sigil` registry rename — **DEFAULT: rename at the id level (breaking)**
[DECISION] Rename the **registry id** (not just the display name): `hexerei:ritual_circle` → `hexerei:ritual_sigil`,
plus the `RITUAL_CIRCLE`→`RITUAL_SIGIL` RegistryObject and `RitualCircleBlock`→`RitualSigilBlock` class.
- **Breaks saves** (placed `ritual_circle` becomes air) — acceptable in dev per CLAUDE.md.
- **Why default:** a half-rename (new label, old id) is a permanent footgun across Blocks/Items/recipes/loot/
  GameTests. One clean rename now is cheaper than perpetual mismatch.
- **Alternative (rejected):** keep id `ritual_circle`, only change the en_us/ru_ru display string. Non-breaking,
  but leaves a stale id forever. Choose this only if a live world must survive the update (not the case in dev).

### 3.3 `snowbell` → `hellebore` registry rename — **DEFAULT: rename id + assets (breaking)**
[DECISION] Rename `snowbell`→`hellebore` (field, id, blockstate/model/texture/seed asset names, lang). Breaks any
placed `snowbell`. **Keep** the internal `WitchCropBlock` `snowbell` *boolean* constructor param (now documented
as "the hellebore bonus-drop flag") to avoid wide `CropDrops`/`WitchCropBlock` churn.
- **Drops:** [DECISION-HELLEBORE-DROP] **KEEP** vanilla `snowball` produce + 20% `icy_needle` bonus (Option A).
  Re-theming to a `hellebore_petal` item is **rejected** — it breaks `WITCHS_SIGHT`/`WITHERING_BILE` brew recipes
  that reference `icy_needle` and adds an item for no mechanical gain.

### 3.4 Rune vs Glyph relationship — **DEFAULT: runes REPLACE glyphs**
[DECISION] The new `hexerei:rune` block **replaces** the deleted `hexerei:ritual_glyph`. Runes ARE the new ring
decals. `RitualActivation`'s completeness predicate is retargeted `RITUAL_GLYPH`→`RUNE`; because completeness
tests block *presence* only (never the connection blockstate), no activation logic changes and the pure geometry
tests stay green.
- **Connection model:** 8 BooleanProperties (n/e/s/w/ne/se/sw/nw, 256 states) + multipart blockstate with
  Y-rotations, mirroring vanilla `redstone_wire`/`tripwire` — **not** a hand-maintained shape enum. Boolean→shape
  logic factored into pure `RuneShapes` for unit tests.
- **Off-sigil free-draw dropped:** runes only exist as part of a sigil ring; off-sigil chalk-use returns PASS.

### 3.5 Draw + bind interaction model — **DEFAULT: plain right-click draws+binds; Shift+Scroll selects rite**
[DECISION] Plain right-click on a sigil draws ALL ring runes in one use and **binds** (riteId + CircleSize) to the
sigil BE. Same rite+size redraw = **repair** (re-place missing runes, no re-bind, no warning). A different
rite/size is **rejected** with the action-bar "Destroy the circle first". Rite **selection** stays **Shift+Scroll**
(keeps the existing `CycleRiteC2SPacket`, gated on `isShiftKeyDown`) so plain right-click can become the draw
action without hijacking hotbar scroll.

### 3.6 Destruction penalty magnitude — **DEFAULT: taint = boundRite.powerCost/4 + 5s Weakness I & Mining Fatigue I**
[DECISION] Breaking a bound sigil OR a ring rune fires: `taint = boundRite.powerCost/4` (Tempest 25, Verdant 15,
Bound-Beast 30, Waning-Moon 37.5; capped at 100 with a 10% floor by `ChunkTaintData.addTaint`) + Weakness I and
Mining Fatigue I for 100t on players within r=4, plus a `WITCH` particle burst and `WITHER_DEATH` sound.
`[UNVERIFIED]` — tune debuff length in-game.

### 3.7 Cauldron visuals: blockstate booleans vs BlockEntityRenderer — **DEFAULT: blockstate booleans + model swap + block-color provider**
[DECISION] Use two derived BooleanProperties (`filled`, `boiling`) + a model swap + a client `BlockColor` provider
on tintindex 0 reading `CauldronBlockEntity.getColor()` — **not** a `BlockEntityRenderer`. Booleans are pure
functions of waterLevel/heatTicks so they can't desync. A BER is only justified later for a true per-waterLevel
rising liquid (waterLevel is currently effectively binary). Boil animation = a 2-frame `.mcmeta` selected by the
`boiling` model variant (zero runtime logic).

### 3.8 Cauldron particles: bespoke vs vanilla — **DEFAULT: bespoke CAULDRON_BUBBLE + CAULDRON_STEAM**
[DECISION] New `cauldron_bubble`/`cauldron_steam` particles (matching the wisp/ash custom-particle pipeline) so
the bubble can be **tinted by the brew color** (RGB passed via `addParticle` xd/yd/zd). Emitted from a new client
ticker reading synced BE state (like `AltarBlockEntity.clientTick`), **not** `serverLevel.sendParticles`.
**Fallback:** vanilla `BUBBLE_COLUMN_UP`/`CLOUD` if the tint is judged not worth two custom textures — flagged so
the implementer can drop the two particle PNGs and skip the two particle classes.

### 3.9 Brew rendering mechanism — **DEFAULT: ItemProperties override predicate (1-of-6 by NBT)**
[DECISION] `ItemProperties.register(hexerei:brew, …)` predicate = `Brews.indexOf(BrewId)/10f` + ascending
`overrides[]` in `brew.json` → one child model per brew; index 0 / no-NBT falls through to a `brew_empty`
fallback (no magenta). **Rejected:** CustomLoader/BakedModel (overkill) and 6 separate items (breaks the
single-NBT-item design). NBT key is the existing `"BrewId"` string; `BrewItem.modelIndex` honors `hasTag()`
before read. Keep `BrewItem.color()` (marked `// FUTURE`) — still used by cauldron blend logic.

### 3.10 Altar artefact slot count — **DEFAULT: one slot per altar this slice**
[DECISION] One artefact slot (purifier OR amplifier, not both — avoids confusing multiplier nets). NBT/render
designed so a 2nd slot is purely additive. **Rejected:** 2 slots now (stacking balance unverified).

### 3.11 Artefact multiplier routing — **DEFAULT: server-thread RitualContext ThreadLocal**
[DECISION] The taint/effect multipliers reach `Rite.perform` (which has no altar handle) via a server-thread
`RitualContext` ThreadLocal set by `RitualActivation` around `perform` — chosen over changing the `Rite.perform`
signature (reserved for the Living-magic player-handle work). Level ticks are single-threaded server-side, so the
ThreadLocal is safe. `payPower` changes from `boolean` to `@Nullable IPowerSource` so the multiplier is read from
exactly the altar that funded the rite.

### 3.12 Artefact scope — **DEFAULT: activate enhancementLevel + taintMul/effectMul ONLY**
[DECISION] This slice activates the dormant `enhancementLevel` field + adds `taintMul`/`effectMul`; it deliberately
does **not** raise `powerScale`/`rechargeScale`/`rangeScale` (those reshape the power economy with many downstream
consumers — reserved for the #19 Coven Power follow-up). `effectMul` is advisory/opt-in — only `VerdantRite` reads
it this slice (growth-budget demonstrator); taint scaling is universal.

### 3.13 Taint punishment ladder — **DEFAULT: vanilla Hunger/Weakness/Wither on the 200t pulse**
[DECISION] LOW→Hunger I; MEDIUM→Hunger I + Weakness I; HIGH→Hunger I + Weakness I + Wither I. Vanilla effects
(zero-dependency, recognizable HUD icons), 220t duration on the existing 200t pulse (duration>cadence so the
debuff never gaps, lapses ~1s after leaving). Creative/spectator immune; iterates **players** keyed on their own
chunk (so taint bites anywhere it accumulated). Wither I at HIGH **can** chip to death if ignored indefinitely —
intended for the deepest corruption; the (out-of-scope) Cleansing Loop is the named cure. Punishment is tuned
survivable so shipping before the cure is non-lethal except at sustained HIGH.

### 3.14 Blood Moss placement type — **DEFAULT: CarpetBlock ground decoration, non-spreading v1**
[DECISION-BLOODMOSS] `BloodMossBlock extends CarpetBlock` (flat 1px, needs support below), **not** a
`WitchCropBlock`. Ships **non-spreading** v1 (deterministic, no extra GameTest burden); drops self via loot JSON;
wired to altar power **4/20** reusing the reserved EMBER_MOSS tier `[UNVERIFIED balance]`. Taint-gated spread is a
named follow-up.

### 3.15 Mistletoe placement type — **DEFAULT: WitchCropBlock subclass, on-top-of-wood v1**
[DECISION-MISTLETOE] `MistletoeBlock extends WitchCropBlock` overriding only `mayPlaceOn` to
`BlockTags.LOGS`/`LEAVES`. v1 = place on TOP of a log/leaf (keeps inherited `canSurvive`); true side-hanging
directional attachment deferred. Registered inline (not via `crop(...)`) but added to
`CROP_BLOCKS`/`SEED_ITEMS`/`SEED_BY_CROP` so creative tab + the `instanceof WitchCropBlock` 4/20 altar synergy
fire automatically.

### 3.16 Mushroom placement type — **DEFAULT: shared WitchMushroomBlock (vanilla mushroom behaviour)**
[DECISION-MUSHROOMS] All 3 (zevanty, puffball, webcap) = one shared `WitchMushroomBlock extends BushBlock`
(vanilla dark/sky survival, small-mushroom shape, single cross-sprite, NO growth stages). Drops via loot JSON
(zevanty +25% `glowing_spore` second pool); zevanty emits light 8 via `.lightLevel`. No huge-mushroom variant and
no altar power contribution in v1.

### 3.17 Sandwort soil — **DEFAULT: farmland like every other crop (no sand enum) v1**
[DECISION-SANDWORT-SOIL] Sandwort plants on farmland for v1 (no per-crop sand placement). `big=false` so bonemeal
gives +1 (slow hardy desert herb). A future `sand` boolean on `WitchCropBlock.mayPlaceOn` (mirroring the `water`
branch) is noted but out of scope.

### 3.18 Hops behaviour — **DEFAULT: reuse wormwood tall/self-stacking (wormwood=true)**
Hops passes `wormwood=true` to `crop(...)` — no new class; the mature bottom grows a second segment reusing the
`hops_stage_4` sprite, matching current wormwood behaviour.

---

## 4. Texture pipeline note

All five subsystems regenerate textures through the ComfyUI + custom MCP pipeline. The merged, flat manifest is
`2026-06-25-overhaul-textures.json` (one array, every asset, with `{id, path, width, height, remove_bg,
pixel_grid, prompt, subsystem}`). **Total: 124 generated textures (PNGs)** across the five subsystems:

| subsystem | textures (PNG) |
|-----------|---------------:|
| cauldron (C) | 8 |
| brews (B) | 7 |
| ritual (R) | 8 |
| altar (A) | 3 |
| herbs (H) | 98 |
| **total** | **124** |

The herbs count (98) = 52 existing-crop regens (44 stage sprites across the 8 crops: belladonna/mandrake/artichoke/
hellebore/wormwood/mindrake = 5 each = 30, wolfsbane = 8, garlic = 6; + 8 seed icons) + 24 new farmland crops (20
stage sprites across crowseye/celandine/hops/sandwort + 4 seeds) + 6 mistletoe (5 stages + 1 seed) + 6 new produce
items + 6 existing-produce regens + 3 mushroom block sprites + 1 blood_moss block face = 98.

Note on the cauldron: the manifest's 8 cauldron entries are all PNGs. There is **one additional non-PNG sidecar**
file — `assets/hexerei/textures/block/cauldron_liquid_boiling.png.mcmeta` (the 2-frame animation metadata) — which
is authored by hand in the cauldron slice, NOT generated by the pipeline, so it is intentionally excluded from the
124-PNG manifest. Counting it, the cauldron slice ships 8 PNGs + 1 mcmeta.

> The build-verify gate (visual screenshot) applies after each subsystem: confirm no magenta missing-texture, no
> tinted-vanilla overlay on brews, the cauldron fills/boils/tints, runes connect into a continuous chalk loop, and
> the new crops/mushrooms render as plants (not magenta cubes).
</content>
</invoke>
