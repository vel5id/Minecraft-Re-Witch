# Overhaul Slice — Altar Artefacts + Taint Punishment

Date: 2026-06-25
Subsystem: Altar progression (artefact items on the altar) + Taint Consequence Ladder (player punishment).
Backlog refs: `2026-06-25-innovation-backlog.md` #5 "Altar Tiers (Artefacts)" (Slice E) and #2 "Taint
Consequence Ladder, Rung 1" (Slice B). This spec ships **both halves as one slice**: the AMPLIFIER/PURIFIER
artefacts that scale a ritual's taint, and the punishment that finally makes that taint bite.

This is a **design-only** document. No code/assets are modified. Forks flagged `[DECISION]`.

---

## 0. Grounding (what already exists — verified in code)

- `AltarBlockEntity` (`blockentity/AltarBlockEntity.java`) already declares the **dormant** scale fields
  `powerScale / rechargeScale / rangeScale / enhancementLevel` (lines 44-47), persists them in NBT
  (`saveAdditional`/`load`, lines 365-382), syncs via `ClientboundBlockEntityDataPacket`, and resets them to
  `1/1/1/0` when the altar de-forms (`setCore(null)`, lines 147-152). DESIGN-NOTES §"Altar slice → Deferred"
  confirms: *"Artefact bonuses … → powerScale/rechargeScale/rangeScale/enhancementLevel stay at 1/0 this
  slice. (PowerSource fields + NBT are already in place for forward-compat.)"*
- The altar is a 1-wide (BlockPos-flood-filled) multiblock with a designated **core**
  (`AltarFormation.findCore`). `corePos()` / `isCore()` already route reads/writes to the core BE.
- `AltarBlock.use` (lines 81-94) currently: client → opens the read-only `AltarScreen`; server →
  `revalidateAndUpdate()`. **There is no item-placement path today.** This slice adds one.
- The taint write currently lives **inside each `Rite.perform`** as `addTaint(cp, TAINT_COST/4f)` where
  `TAINT_COST` mirrors the recipe's `powerCost` (Verdant 60, BoundBeast 120, Waning 150, SpawnItem 40;
  Tempest writes a flat `25f`). `Rite.perform(ServerLevel, BlockPos)` has **no altar handle** — the
  multiplier cannot reach it without a wiring change (designed in §2).
- `RitualActivation.tryPerform` already does `AltarPowerManager.get(level).query(level, center)` for the
  `payPower` debit (lines 68-78) — so it can identify **the same altar that paid** and read its multipliers.
- `WorldTaintAura.pulse(ServerLevel)` runs every **200 ticks** from `HexereiLevelEvents.onLevelTick`
  (gt % 200), iterating `AltarPowerManager.allSources()` and mutating blocks in a `RADIUS=5` box. This is
  the loop the punishment rung hooks into.
- `CharmTickHandler` establishes the **refresh trick**: `EFFECT_DURATION = 40` ticks, re-applied on a
  20-tick cadence so a buff lapses ~1 s after the source goes away. The punishment reuses this idea on the
  200-tick pulse cadence (duration must exceed the pulse period — see §6).
- `TaintLevel.fromValue`: NONE <15, LOW ≥15, MEDIUM ≥40, HIGH ≥70 (taint clamped to 100 in `ChunkTaintData`).
- `tainted_ground` / `charred_stone` already exist as registered blocks (`HexereiBlocks.TAINTED_GROUND` /
  `CHARRED_STONE`) and are the in-world markers WorldTaintAura spreads.

---

## PART A — ALTAR ARTEFACTS

## 1. Item model & storage

### 1.1 Two archetypes, three concrete items

Artefacts are plain `Item`s (NOT `BlockItem`s — they live in the altar BE slot, never as world blocks),
each carrying an immutable `(taintMul, effectMul, enhancement)` triple. Pure data, like `Brew`/`CharmDef`.

| id | archetype | `taintMul` | `effectMul` | `enhancement` | thematic role |
|----|-----------|-----------:|------------:|--------------:|---------------|
| `hexerei:bone_charm` | **PURIFIER** | **0.5** | 1.0 | 0 | dampens the corruption a rite leaves — halve the taint, no power change |
| `hexerei:wax_poppet` | **PURIFIER** (deep) | **0.25** | 0.9 | 0 | strongest cleanser; slightly *weakens* the rite as the cost of mercy |
| `hexerei:obsidian_skull` | **AMPLIFIER** | **2.0** | **1.5** | 1 | greed: +50 % rite effect, but doubles taint; also raises `enhancementLevel` to 1 |

Numbers grounded in DESIGN-NOTES taint convention (`taint = powerCost/4`): with `bone_charm`, a Tempest
(flat 25 taint) drops to 12.5; with `obsidian_skull` it rises to 50. A purifier therefore roughly doubles
how many rituals a chunk tolerates before MEDIUM (40); an amplifier roughly halves it. `[DECISION]` —
`effectMul` is **advisory** this slice: only rites that opt in (see §2.4) read it. Taint scaling is
universal and needs no per-rite change.

`enhancement` on `obsidian_skull` reuses the **dormant `enhancementLevel`** field (the backlog's L3 gating
kicker): placing it raises the core's `enhancementLevel` to `max(current, artefact.enhancement)`, exposed
through the existing `IPowerSource.getEnhancementLevel()`. Heavier future rites gate on this (out of scope to
add a gated rite here; we only *activate the field*).

`[DECISION]` Slot count. **One artefact slot per altar** this slice (simplest, legible: purifier *or*
amplifier, not both — they would otherwise multiply to confusing nets). The NBT/render design below is
written so a second slot is a pure additive follow-up. Rejected: 2 slots now (multiplier-stacking balance is
unverified and the backlog explicitly scopes "2-3 to start").

### 1.2 ArtefactDef (pure, unit-tested)

New final class `item/ArtefactDef.java`:

```
public record ArtefactDef(String id, float taintMul, float effectMul, int enhancement) {}
```

New registry of defs `item/ArtefactDefs.java` (mirrors `CharmDefs`): three `public static final ArtefactDef`
constants + a `byItemId(String)` / `of(ItemStack)` lookup. Pure → unit-tested in
`src/test/java/.../item/ArtefactDefsTest.java` (asserts the three triples and the lookup).

New `item/ArtefactItem.java extends Item`: holds its `ArtefactDef`, adds a tooltip (translated
`tooltip.hexerei.artefact.taint_mul` / `effect_mul`, formatted `×0.5`). No NBT on the item itself — the def
is identity-by-item-type, like `CharmItem`'s `defOf`.

### 1.3 Storage on the altar (BlockEntity NBT)

The artefact is stored on the **core** `AltarBlockEntity` as a single `ItemStack`:

- New field `private ItemStack artefact = ItemStack.EMPTY;` on `AltarBlockEntity`.
- `saveAdditional`: `if (!artefact.isEmpty()) tag.put("Artefact", artefact.save(new CompoundTag()));`
- `load`: `artefact = tag.contains("Artefact") ? ItemStack.of(tag.getCompound("Artefact")) : ItemStack.EMPTY;`
- Cleared in `setCore(null)` alongside the scale-field reset (drop it to the world there — see §1.5).
- Always routed to the core: add `coreBe()` helper (returns the `AltarBlockEntity` at `corePos()`), and have
  the placement/removal API operate on `coreBe()` so a non-core block right-click still targets one shared slot.

### 1.4 Interaction (place / swap / retrieve)

Extend `AltarBlock.use` (server branch). Today the server branch only calls `revalidateAndUpdate()`; insert
artefact handling **before** that, gated on a formed altar:

```
server side, be.isCore()||be has core:
  ItemStack held = player.getItemInHand(hand);
  AltarBlockEntity core = be.coreBe();
  if (held.getItem() instanceof ArtefactItem) {
     // place or swap: give back the old artefact, store the new (count 1)
     ItemStack prev = core.setArtefact(held.copyWithCount(1));
     held.shrink(1);
     if (!prev.isEmpty()) giveOrDrop(player, prev);
     core.applyArtefact();           // recompute scale/enhancement (§1.6)
     return InteractionResult.SUCCESS;
  }
  if (held.isEmpty() && player.isShiftKeyDown() && !core.getArtefact().isEmpty()) {
     giveOrDrop(player, core.setArtefact(ItemStack.EMPTY));
     core.applyArtefact();
     return InteractionResult.SUCCESS;
  }
  // else fall through to existing revalidateAndUpdate() + open screen path
```

- **Place with empty hand + no shift** keeps the existing GUI-open behavior (so the screen still opens).
- **Shift + empty hand** retrieves the artefact (clear, return to player).
- Holding an artefact **swaps** (returns the previous one) — no dead-end.
- `giveOrDrop` = `player.getInventory().add` else `player.drop(stack,false)` (vanilla pattern).
- Client branch unchanged (still opens `AltarScreen`; the screen can later show the artefact, out of scope).

`[DECISION]` Place requires a **formed** altar (`core != null`). On an unformed single block, fall through
(no slot). Rationale: scale fields only mean anything on a core, and `setCore(null)` resets them.

### 1.5 Drop-on-break / de-form

The artefact must not vanish:
- In `AltarBlock.onRemove` (already calls `updateMultiblock(pos)`), if the removed block's BE held the
  artefact (only the core does), spawn it as an `ItemEntity` at `pos` before super.
- In `setCore(null)` (de-form path), `Block.popResource(level, worldPosition, artefact)` then clear.
  Mirror the existing scale-field reset block already there.

This is the same "don't trap inputs" principle as the cauldron drain decision in DESIGN-NOTES.

### 1.6 How multipliers reach the scale fields & the rite

`AltarBlockEntity.applyArtefact()` (core-only) — single source of truth, called after any artefact change
and on `onLoad`:

```
ArtefactDef d = ArtefactItem.defOf(artefact);   // null when empty
this.taintMul   = d == null ? 1f : d.taintMul();
this.effectMul  = d == null ? 1f : d.effectMul();
this.enhancementLevel = d == null ? 0 : d.enhancement();
setChanged(); sync();
```

- New persisted floats `taintMul` / `effectMul` (default 1.0) on the BE, saved/loaded next to the scale ints
  (reuse the same NBT block). They are **derived** from the artefact but persisted too so a chunk-load before
  `onLoad` recompute is still correct (defensive; `onLoad` recomputes regardless).
- Public read accessors on `AltarBlockEntity` (route to core, like `getCurrentPower`):
  `public float taintMultiplier()` and `public float effectMultiplier()`.
- `[DECISION]` We deliberately do **not** touch `powerScale`/`rechargeScale`/`rangeScale` from artefacts in
  this slice (the backlog's "raise the dormant scale fields" L1 idea). Reason: those change the *power
  economy* (max power, recharge rate, reach) which has many downstream consumers (charm recharge, ritual
  affordability, coven range) and would balloon the slice. We activate **`enhancementLevel`** (the gating
  seam #19/heavier-rites need) and add the **taint/effect multipliers** (this slice's mandate). Raising
  power/recharge/range is the explicit follow-up rider (#19 Coven Power), and the fields stay wired for it.

### 1.7 Routing the multiplier into the rite's taint write

The taint write lives in `Rite.perform`, which has no altar handle. Wire it via a **per-thread ritual
context** set by `RitualActivation` around the `perform` call — minimal, no `Rite` signature change, fully
server-side and single-threaded (level ticks run on the server thread):

New `ritual/RitualContext.java`:
```
public final class RitualContext {
    private static final ThreadLocal<RitualContext> CURRENT = new ThreadLocal<>();
    private final float taintMul, effectMul;
    private RitualContext(float t, float e){ taintMul=t; effectMul=e; }
    static void begin(float t, float e){ CURRENT.set(new RitualContext(t,e)); }
    static void end(){ CURRENT.remove(); }
    public static float taintMul(){ RitualContext c=CURRENT.get(); return c==null?1f:c.taintMul; }
    public static float effectMul(){ RitualContext c=CURRENT.get(); return c==null?1f:c.effectMul; }
}
```

New shared helper `ritual/Rites.java` (or a static on `RitualContext`):
```
public static void addRitualTaint(ServerLevel level, ChunkPos cp, float base) {
    ChunkTaintData.get(level).addTaint(cp, base * RitualContext.taintMul());
    HexereiNetwork.sendTaintSync(level, cp);
}
```

**Each existing rite** replaces its two-line
`ChunkTaintData.get(level).addTaint(cp, X); HexereiNetwork.sendTaintSync(level, cp);`
with `Rites.addRitualTaint(level, cp, X);` (X = the existing `TAINT_COST/4f` or Tempest's `25f`). This is a
mechanical refactor across `VerdantRite`, `TempestRite`, `WaningMoonRite`, `SpawnItemRite`, `BoundBeastRite`
— behavior unchanged when no artefact is present (`taintMul()` returns 1.0 with no context).

`RitualActivation.tryPerform` — wrap the perform with the paying altar's multipliers. Capture the altar
during `payPower` (return it instead of a boolean), then:
```
float tMul = altar != null ? altarTaintMul(altar) : 1f;   // altar is IPowerSource → AltarBlockEntity
float eMul = altar != null ? altarEffectMul(altar) : 1f;
RitualContext.begin(tMul, eMul);
try { recipe.rite().perform(level, center); }
finally { RitualContext.end(); }
```
`payPower` changes from `boolean` to `@Nullable IPowerSource` (the altar that paid), so the multiplier is
read from **exactly the altar that funded the rite** (not a different in-range one). `altarTaintMul` casts
`IPowerSource` to `AltarBlockEntity` and calls `taintMultiplier()` (returns 1.0 for any non-altar source).

`effectMul` is read by opt-in rites only (§2.4 below) via `RitualContext.effectMul()`; this slice wires
`VerdantRite` as the demonstrator (multiply `MAX_GROWTHS`) and leaves the rest using effectMul=1.0.

### 1.8 Why not reuse the `AltarScreen` for placement?

The placement is a world right-click, not a container — there is no inventory slot networking to add (the
artefact lives in BE NBT, synced by the existing block-update packet). The read-only `AltarScreen` can show
the held artefact icon later (cosmetic, deferred). No new packet needed — consistent with the altar slice's
"no custom packet" decision in DESIGN-NOTES.

## 2. Rite effect-multiplier hook (minimal, demonstrator only)

### 2.4 VerdantRite demonstrator

`VerdantRite.perform`: `int budget = Math.round(MAX_GROWTHS * RitualContext.effectMul());` then loop to
`budget` instead of the constant `MAX_GROWTHS`. With `obsidian_skull` (effectMul 1.5) → 18 growths;
with `wax_poppet` (0.9) → 11; default → 12. Taint already scales via §1.7. This proves the seam end-to-end
in one rite; other rites opt in later by reading `RitualContext.effectMul()` where they have a natural
magnitude (Tempest duration, SpawnItem count) — **not changed in this slice** to keep balance legible.

## 3. Render-or-not

`[DECISION]` **No world render of the placed artefact this slice.** Rationale: the altar model is a fixed
multiblock; adding a floating item render needs a `BlockEntityRenderer` (client-only, `@OnlyIn`) which is a
separable cosmetic. The artefact's presence is conveyed by (a) the swap/tooltip feedback, (b) the
`AltarScreen` icon (deferred), and (c) the actual taint/effect change. Listed as a follow-up: a
`AltarBlockEntityRenderer` floating the artefact item ~0.9 above the core, gated behind `DistExecutor`.

---

## PART B — TAINT PUNISHMENT (Consequence Ladder)

## 4. Where it lives

A **new method** `WorldTaintAura.punishPlayers(ServerLevel)` invoked from the **same 200-tick pulse** (call
it at the top of `pulse`, or as a sibling call in `HexereiLevelEvents.onLevelTick` under `gt % 200`).
Server-side only (`ServerLevel`, `ServerPlayer`). `[DECISION]` Iterate **players**, not altars: punishment
must apply to any player standing in a tainted chunk **even with no altar nearby** (taint outlives the altar
that caused it). The existing `pulse` altar-loop only mutates blocks near altars; player punishment is a
separate per-player scan keyed on the player's own chunk.

```
public static void punishPlayers(ServerLevel level) {
    ChunkTaintData data = ChunkTaintData.get(level);
    for (ServerPlayer p : level.players()) {
        if (p.isCreative() || p.isSpectator()) continue;          // creative/spectator immune
        TaintLevel tl = data.getLevel(new ChunkPos(p.blockPosition()));
        applyLadder(level, p, tl);
    }
}
```

## 5. The ladder (thresholds, effects, durations)

Effects use the **refresh trick**: duration **220 ticks** (one pulse period 200 + ~1 s slack) so the debuff
persists between pulses and lapses ~1 s after the player leaves a tainted chunk. All effects are applied with
`new MobEffectInstance(effect, 220, amplifier, /*ambient*/ true, /*visible*/ false, /*showIcon*/ true)` —
matching `CharmTickHandler.instance` (ambient, hidden particles, visible HUD icon) so the player sees *why*.

| chunk `TaintLevel` | taint value | effect(s) applied each pulse | amplifier | rationale |
|--------------------|------------:|------------------------------|----------:|-----------|
| **NONE** (<15) | — | none | — | clean land is safe |
| **LOW** (≥15) | 15-39 | `HUNGER` | 0 (Hunger I) | first bite: drains food bar slowly; survivable, signals "something's wrong" |
| **MEDIUM** (≥40) | 40-69 | `HUNGER` + `WEAKNESS` | 0 | tainted land saps strength; combat/farming penalized |
| **HIGH** (≥70) | 70-100 | `HUNGER` + `WEAKNESS` + `WITHER` | Hunger 0, Weakness 0, **Wither 0 (Wither I)** | miasma: net HP loss unless you leave or cleanse |

- **Survivability** (per the brief — Cleansing Loop is the intended counter, out of scope here): Wither I
  ticks ~1 dmg/40t ≈ 0.5 hearts/2 s; with the 220-tick refresh a player in a HIGH chunk loses ~2.5 hearts
  before the next pulse, fully regenerable by leaving. It is a strong "get out" signal, not instant death.
  `[DECISION]` Wither (not the project's Withering-Bile brew effect) — vanilla `MobEffects.WITHER` is
  zero-dependency and renders the recognizable black-heart HUD. Note: Wither cannot kill via natural
  regen-off at I on full hunger? — it **can** chip to death if the player ignores it indefinitely; that is
  intended for HIGH (the deepest corruption) and the backlog names the Cleansing Loop as the cure.
- **No stacking beyond the table**: re-applying the same effect each pulse just refreshes its 220-tick timer
  at the same amplifier (vanilla replaces equal-or-stronger). No runaway.
- **Tainted-ground standing bonus** `[DECISION] — deferred to keep Rung 1 chunk-based`: the L1 "stand
  *on* `tainted_ground`/`charred_stone`" sub-trigger is folded into the chunk check (a HIGH chunk is where
  those blocks spread anyway). A block-under-feet check (`level.getBlockState(p.blockPosition().below())`
  is `TAINTED_GROUND`/`CHARRED_STONE` → bump one rung) is an easy additive follow-up; not required for Rung 1.

`applyLadder` (pure-ish, the effect set per level is a `switch` on `TaintLevel`) is factored so the
LOW/MEDIUM/HIGH → effect-list mapping is **unit-testable without a server** (return a `List<MobEffect-key>`
from a pure helper, applied by the thin server method). Matches the project's "factor decidable logic into a
pure class" rule.

## 6. Cadence & duration interplay (the magic numbers)

- Pulse period: **200 ticks** (existing, `gt % 200` in `HexereiLevelEvents`).
- Effect duration: **220 ticks** = 200 + 20 (one second of slack > pulse jitter). This is the punishment
  analogue of `CharmTickHandler`'s `EFFECT_DURATION = 40` (which sits on a 20-tick cadence). The invariant
  is **duration > cadence** so the buff never gaps while you remain; the +20 makes the lapse-after-leaving
  feel responsive (~1 s) rather than abrupt.
- Add to DESIGN-NOTES: `TAINT_PUNISH_REFRESH = 220`, ladder amplifiers (all 0), pulse-shared cadence 200.

## 7. Verification

**Pure unit tests** (`src/test/java/`):
- `ArtefactDefsTest` — the three triples + lookup by item id + null on unknown.
- `RitualTaintScalingTest` — given base taint `B` and `taintMul ∈ {0.25,0.5,1.0,2.0}`, assert the value
  written equals `B*mul` (test against a fake `ChunkTaintData` or assert the helper's arithmetic). Confirms
  Verdant 15 → 7.5 (bone_charm) / 30 (skull); Tempest 25 → 12.5 / 50.
- `TaintLadderTest` — `applyLadder` pure mapping: NONE→{}, LOW→{HUNGER}, MEDIUM→{HUNGER,WEAKNESS},
  HIGH→{HUNGER,WEAKNESS,WITHER}.

**GameTests** (`src/main/java/com/vel5id/hexerei/test/`):
- `AltarArtefactGameTests` — form a 1-block altar, right-click with `bone_charm` (assert BE `taintMultiplier()`
  ==0.5 and item consumed), shift-right-click empty hand (assert returned + multiplier back to 1.0), break
  the altar (assert artefact `ItemEntity` dropped). Mark the multiblock-scan-dependent assertions
  `required = false` per the DESIGN-NOTES flakiness rule.
- `TaintPunishGameTests` — set a chunk's taint to 75 via `ChunkTaintData.addTaint`, place a survival mock
  player in it, call `WorldTaintAura.punishPlayers`, assert the player has WITHER+WEAKNESS+HUNGER with
  duration≈220. (Drive `punishPlayers` directly like `CharmTickHandler.processPlayer` is driven in tests.)
- Integration: a `RitualActivation.tryPerform` GameTest with an `obsidian_skull` on the altar asserts the
  chunk taint rose by `base*2` after a Tempest (via the `perform()` path, like the existing
  `TempestRiteTaintGameTest`).

---

## 8. Registration & assets checklist (conventions)

### 8.1 Items — `registry/HexereiItems.java`
```
public static final RegistryObject<Item> BONE_CHARM = ITEMS.register("bone_charm",
        () -> new ArtefactItem(new Item.Properties(), ArtefactDefs.BONE_CHARM));
public static final RegistryObject<Item> WAX_POPPET = ITEMS.register("wax_poppet",
        () -> new ArtefactItem(new Item.Properties(), ArtefactDefs.WAX_POPPET));
public static final RegistryObject<Item> OBSIDIAN_SKULL = ITEMS.register("obsidian_skull",
        () -> new ArtefactItem(new Item.Properties(), ArtefactDefs.OBSIDIAN_SKULL));
```

### 8.2 Creative tab — `registry/HexereiCreativeTabs.java`
Add the three after the charms block (before the brews):
```
output.accept(HexereiItems.BONE_CHARM.get());
output.accept(HexereiItems.WAX_POPPET.get());
output.accept(HexereiItems.OBSIDIAN_SKULL.get());
```

### 8.3 Item models — `assets/hexerei/models/item/<id>.json`
Standard `item/generated` with `layer0: hexerei:item/<id>` for each of the three. (No blockstate/block model/
loot table — these are items, not blocks. No block render.)

### 8.4 Lang — `assets/hexerei/lang/en_us.json` + `ru_ru.json`
```
"item.hexerei.bone_charm":      "Bone Charm" / "Костяной оберег"
"item.hexerei.wax_poppet":      "Wax Poppet" / "Восковая куколка"
"item.hexerei.obsidian_skull":  "Obsidian Skull" / "Обсидиановый череп"
"tooltip.hexerei.artefact.taint_mul":  "Taint ×%s" / "Скверна ×%s"
"tooltip.hexerei.artefact.effect_mul": "Effect ×%s" / "Эффект ×%s"
"tooltip.hexerei.artefact.purifier":   "Purifier" / "Очиститель"
"tooltip.hexerei.artefact.amplifier":  "Amplifier" / "Усилитель"
```
No new lang for the punishment (it uses vanilla effect names on the HUD icon). Optional actionbar warning
on first entering a HIGH chunk is **deferred** (would need per-player transient state).

### 8.5 Recipes
`[DECISION]` Acquisition deferred — like the altar's `altar_placeholder.json`, ship a temporary vanilla
crafting recipe per artefact (`data/hexerei/recipes/<id>.json`) so they are obtainable for testing
(e.g. bone_charm = bone + string; wax_poppet = honeycomb + string; obsidian_skull = obsidian + wither
skeleton skull). The "earned via ritual" path (a `SpawnItemRite` recipe, mirroring backlog #3) is a thin
follow-up once the rituals-crafted-charms slice lands.

---

## 9. Files

### New
- `item/ArtefactDef.java` — record `(id, taintMul, effectMul, enhancement)`.
- `item/ArtefactDefs.java` — the three defs + `of(ItemStack)`/`byItemId`.
- `item/ArtefactItem.java` — `Item` holding a def + tooltip.
- `ritual/RitualContext.java` — ThreadLocal taint/effect multiplier context.
- `ritual/Rites.java` — `addRitualTaint(level, cp, base)` shared helper (applies `RitualContext.taintMul`).
- `assets/hexerei/models/item/{bone_charm,wax_poppet,obsidian_skull}.json`
- `assets/hexerei/textures/item/{bone_charm,wax_poppet,obsidian_skull}.png`
- `data/hexerei/recipes/{bone_charm,wax_poppet,obsidian_skull}.json` (temporary, like altar placeholder)
- Tests: `src/test/java/.../item/ArtefactDefsTest.java`, `.../ritual/RitualTaintScalingTest.java`,
  `.../ritual/TaintLadderTest.java`; GameTests `test/AltarArtefactGameTests.java`,
  `test/TaintPunishGameTests.java`.

### Edited
- `blockentity/AltarBlockEntity.java` — `artefact` ItemStack field + NBT; `taintMul`/`effectMul` floats +
  NBT; `coreBe()`, `getArtefact()`, `setArtefact()`, `applyArtefact()`, `taintMultiplier()`,
  `effectMultiplier()`; call `applyArtefact()` in `onLoad`; drop artefact + reset muls in `setCore(null)`.
- `block/AltarBlock.java` — `use()` server branch: place/swap/retrieve artefact before `revalidateAndUpdate`;
  `onRemove()` drop the artefact.
- `ritual/RitualActivation.java` — `payPower` returns the paying `IPowerSource`; wrap `perform` in
  `RitualContext.begin/end` with that altar's multipliers.
- `ritual/VerdantRite.java`, `TempestRite.java`, `WaningMoonRite.java`, `SpawnItemRite.java`,
  `BoundBeastRite.java` — replace the inline `addTaint`+`sendTaintSync` pair with `Rites.addRitualTaint(...)`;
  VerdantRite additionally scales its growth budget by `RitualContext.effectMul()`.
- `ritual/WorldTaintAura.java` — add `punishPlayers(ServerLevel)` + pure `applyLadder` mapping.
- `HexereiLevelEvents.java` — under `gt % 200`, call `WorldTaintAura.punishPlayers(sl)` (alongside `pulse`).
- `registry/HexereiItems.java`, `registry/HexereiCreativeTabs.java` — register the three artefacts + tab.
- `assets/hexerei/lang/en_us.json`, `ru_ru.json` — item names + artefact tooltips.
- `DESIGN-NOTES.md` — new "Altar Artefacts + Taint Punishment" section with every magic number above.

---

## 10. Texture assets (item icons — 64×64, remove_bg true, 32 grid)

| logical_id | target_path | w | h | remove_bg | grid | prompt |
|------------|-------------|---|---|-----------|------|--------|
| `bone_charm` | `assets/hexerei/textures/item/bone_charm.png` | 64 | 64 | true | 32 | Minecraft pixel-art item icon: a small protective charm of pale bleached bone fragments bound with grey twine into a loop, a single dark rune scratched on the largest piece, soft off-white palette ~14 colors, chunky 32px pixel grid, transparent background, slight top-left lighting, flat handheld-item style |
| `wax_poppet` | `assets/hexerei/textures/item/wax_poppet.png` | 64 | 64 | true | 32 | Minecraft pixel-art item icon: a crude humanoid poppet doll of pale cream wax with stubby arms, a single straight-pin head and a faint stitched seam, muted ivory and tan palette ~12 colors, chunky 32px pixel grid, transparent background, gentle top lighting, flat handheld-item style |
| `obsidian_skull` | `assets/hexerei/textures/item/obsidian_skull.png` | 64 | 64 | true | 32 | Minecraft pixel-art item icon: a small carved skull of glossy black obsidian with faceted sharp edges and dim violet light glowing in the eye sockets, near-black purple-tinged palette ~16 colors with one bright magenta accent, chunky 32px pixel grid, transparent background, hard rim highlight top-left, ominous flat handheld-item style |

(No block/GUI textures — artefacts are items with no world render this slice; the altar/taint blocks already
have their textures.)
