# Hexerei — Curses (taglock + curse rite; Bonds laid on a target, echoed on the caster)

**Date:** 2026-06-26
**Extends:** `soul/` (the "create a free bond" verb of Модель §7, laid on a player's `marks`); the ritual
+ `PlayerSoulData` infrastructure. Inspired by HEE's 11-type curse system (verified from the jar:
`mechanics/curse/CurseType` — TELEPORTATION/CONFUSION/TRANQUILITY/SLOWNESS/WEAKNESS/BLINDNESS/DEATH/
DECAY/VAMPIRE/REBOUND/LOSS, delivered by a projectile + technical-curse entity ticking `uses` times).
**Skill:** mc-mod-ideate → hands off to mc-mod-implement.

---

## Constitution gate (litmus — all pass)

| # | Question | Verdict |
|---|---|---|
| 1 | Выводится из Закона? | ✅ A curse = binding a spirit onto a target = the "create free bond" verb (Модель §7). |
| 2 | Цена + общий ресурс? | ✅ Caster pays essence + **bears an echo of the very curse** (~20%, one tier weaker — a Bond on the caster) + disturbance. Each cast stacks another echo → spam is self-punishing (Article III: power source = danger source). |
| 3 | Несколько применений? | ✅ One mechanism = N curses by domain; 3 ship now. |
| 4 | Предсказуемо? | ✅ Curse type = f(domain reagent); echo strength fixed; lifetime = f(fear ticks). No dice. |
| 5 | Питает петлю? | ✅ Casting raises disturbance; the curse's finite grip decays; the caster's echo decays with it. |
| 6 | Диегетично? | ✅ A spirit sent to haunt a named victim — and it touches the sender too (the echo). |
| 7 | Свободно от запрещённого? | ✅ Not free power (caster bears a real debuff); not a parallel system (reuses `Bond`/`PlayerSoulData`). |
| 9 | Присутствия — из состояния? | ✅ Curse lives in `marks`; effect on/off = f(fear>0); strength = f(full vs echo). |

**The echo is the anti-spam.** You cannot fling curses for free: every successful cast plants a milder
copy of the same curse on *you*. Cast five → bear five mild curses. The weapon's edge points both ways.

---

## Player Loop

- **Capture (taglock):** right-click a **player** with a `hexerei:taglock` → stores that player's UUID +
  name in the taglock's NBT. (Witchery's taglock; a "piece" of the victim.)
- **Cast (rite):** drop the taglock **and** a domain reagent on a small ritual circle; right-click the sigil.
  The curse rite consumes the reagent, scans for the taglock, resolves the target (must be **online** v1),
  and lays a **full** curse `Bond` in the victim's `PlayerSoulData.marks` **and** an **echo** curse Bond
  (~20% strength) in the **caster's** `marks`. No taglock / target offline → **botch** (reagent + taglock
  consumed, disturbance written, no curse, no echo) — failure feeds the loop, never a free no-op.
- **Cost (caster):** essence (recipe powerCost) + the **echo curse on the caster** + disturbance. (The echo
  replaces the earlier "deferred caster Mark" — bearing the curse itself is a stronger, more diegetic cost.)
- **Effect:** each second, `CurseTickHandler` applies the domain debuff (full on the victim, weakened on the
  caster) and ticks each spirit's grip down.
- **Duration:** `CURSE_TICKS` seconds (fear field), same for victim and echo; at fear ≤ 0 the spirit departs.
- **Feedback:** cast = soul-fire burst + curse sound; the afflicted see the debuff (shorter reach / icons).

---

## The enabling change: the caster handle (no longer deferred)

`Rite.perform(ServerLevel, BlockPos)` carries no player — the gap that blocked amulet owner-binding too.
Solved minimally by **threading the activating player through the existing `RitualContext` ThreadLocal**:
- `RitualContext` gains a nullable `caster` (UUID) + `currentCaster()`; `begin(taintMul, effectMul, caster)`.
- `RitualActivation.tryPerform(ServerLevel, BlockPos, @Nullable Player)` overload sets the caster in context
  around `perform`; the existing `tryPerform(level, pos)` delegates with `null` (GameTest-compatible).
- `RitualSigilBlock.use` calls the new overload, passing `player` (the right-clicker — already in scope).
- Any `Rite` may now read `RitualContext.currentCaster()`. **Side benefit:** unblocks amulet/bound-beast
  owner-binding without any further signature change.

---

## Registry Objects

| Kind | id | Notes |
|---|---|---|
| Item | `hexerei:taglock` | **New.** Right-click a player → capture UUID+name into NBT. |
| RitualRecipe | `hexerei:curse_clumsiness` | SMALL, sacrifice `hexerei:sandwort` (STONE), cost 100, `new CurseRite(STONE)`. |
| RitualRecipe | `hexerei:curse_unluckiness` | SMALL, sacrifice `hexerei:glowing_spore` (THRESHOLD), cost 100, `new CurseRite(THRESHOLD)`. |
| RitualRecipe | `hexerei:curse_weakness` | SMALL, sacrifice `hexerei:blood_moss` (DEATH), cost 100, `new CurseRite(DEATH)`. |
| Rite | `CurseRite` | **New.** Domain in ctor (the SealAmuletRite pattern). |

All three sacrifices are unused by any other rite (verify against `RitualRecipes.ALL` post-amulet-slice);
`blood_moss` is a registered `BlockItem`. All carry a `ReagentDescriptor` of the right domain.

---

## Mechanics (server-side)

### 1. `TaglockItem extends Item` (`item/TaglockItem.java`, new)
- NBT: `hexerei:target_uuid` (UUID string), `hexerei:target_name` (String).
- `interactLivingEntity`: if target is a `Player` → write UUID+name into the held stack (server), play a
  sound, SUCCESS. Tooltip shows "Bound to %s" / else "Use on a player to bind." Not consumed on capture.

### 2. `Curse` (`item/Curse.java`, new) — **pure, unit-tested first (TDD)**
```
enum Strength { FULL, ECHO }
record CurseEffect(Kind kind, Strength strength)      // kind ∈ {CLUMSY, UNLUCKY, WEAK}

static CurseEffect effectFor(Correspondence domain, Strength s):
    STONE     -> CLUMSY   ; THRESHOLD -> UNLUCKY ; DEATH -> WEAK   (others -> null)

// the modifier values (FULL vs ECHO ~20%):
CLUMSY  FULL: reach×0.60, gravity×1.40      ECHO: reach×0.88, gravity×1.12
UNLUCKY FULL: luck −2.0,  Unluck II         ECHO: luck −0.4,  Unluck I
WEAK    FULL: Weakness II (amp 1)           ECHO: Weakness I  (amp 0)

CURSE_TICKS = 600                          // fear at cast; ~10 min at one tick/sec  [UNVERIFIED]
static float initialGrip()                 -> CURSE_TICKS
static float gripAfter(float fear)         -> fear - 1
static boolean isSpent(float fear)         -> fear <= 0f
static boolean isEcho(Bond b)              -> b.spiritType().getPath().endsWith("_echo")
```

### 3. `CurseRite implements Rite` (`ritual/CurseRite.java`, new)
```
ctor(domain)
perform(ServerLevel level, BlockPos center):
    ItemEntity taglockEntity = nearest ItemEntity in SACRIFICE_RADIUS that is a bound TaglockItem
    if none: botch("no taglock"); return
    ServerPlayer victim = server.getPlayerList().getPlayer(TaglockItem.getTarget(taglockEntity.stack))
    if victim == null: botch("target offline"); taglockEntity.discard(); return
    // FULL curse on the victim
    layCurse(victim, spiritType=domain+"_curse",       strength=FULL)
    // ECHO curse on the caster (the anti-spam) — identified via the new RitualContext handle
    UUID caster = RitualContext.currentCaster()
    if caster != null && (casterPlayer = server.getPlayerList().getPlayer(caster)) != null && caster != victim.uuid:
        layCurse(casterPlayer, spiritType=domain+"_curse_echo", strength=ECHO)
    taglockEntity.discard()
    Rites.addRitualTaint(level, new ChunkPos(center), domain, CURSE_DISTURBANCE)
    sound SOUL_ESCAPE; particles SOUL_FIRE_FLAME burst
```
`layCurse(player, spiritType, strength)`: builds a `Bond` (domain, `seal=null` free bond,
`disposition.fear = Curse.initialGrip()`, spiritType as given) and `addMark`s it to that player's
`PlayerSoulData`. The caster does **not** get an echo if they targeted themselves or are offline.

### 4. `CurseTickHandler` (`item/CurseTickHandler.java`, new) — `HexereiLevelEvents`, 20-tick boundary
Per online player, per curse Bond in their `marks` whose domain maps to a curse:
```
Strength s = Curse.isEcho(bond) ? ECHO : FULL
CurseEffect fx = Curse.effectFor(bond.domain(), s)
applyOrRefresh(p, fx)            // idempotent each second
fear = bond.disposition().fear() - 1
if Curse.isSpent(fear): removeDebuff(p, fx); mark bond for removal
else: write bond back with disposition.fear = fear
```
- `CLUMSY`: `AttributeModifier`s (fixed UUIDs per kind×strength) on `ForgeMod.BLOCK_REACH`/`ENTITY_REACH`
  (×factor) and `ForgeMod.ENTITY_GRAVITY` (×factor). ECHO uses the milder factors.
- `UNLUCKY`: `AttributeModifier` on `Attributes.LUCK` + `MobEffects.UNLUCK` (FULL→amp1, ECHO→amp0).
- `WEAK`: `MobEffects.WEAKNESS` (FULL→amp1, ECHO→amp0). Refreshed like the amulet effect (duration 40).

---

## Persistence

- **Curse = a `Bond` in `PlayerSoulData.marks`** (Модель §3 "free bonds the world laid on the witch").
  Persists across relog **and death** (death does not pay a curse). The spirit's remaining grip = `disposition.fear`.
- **Taglock binding** = taglock item NBT. **No new capability, no new `SavedData`, no new `Bond` field.**
- **`spiritType` ending in `_echo`** is the sole marker distinguishing the caster's milder copy.

## Client/Server

- Server-authoritative tick + rite. Attribute modifiers and potion effects are vanilla-synced → the victim
  and caster both see/feel their debuffs with **no new packet**. Taglock tooltip reads NBT client-side.

---

## Assets

- `items/taglock.png` (16×16, a phial/lock of hair — generate via PIL); `items/taglock.json` (`minecraft:item/generated`).
- Lang (en_us + ru_ru): `item.hexerei.taglock` / `.bound` / `.unbound`; `ritual.hexerei.curse_clumsiness` /
  `curse_unluckiness` / `curse_weakness` (en + ru).

---

## Balance (first-pass `[UNVERIFIED]`)

| Number | Value | Justification |
|---|---|---|
| Recipe `powerCost` | 100 | Above amulet seals (80); a hostile act costs more than a ward. |
| `CURSE_TICKS` | 600 (1 tick/sec → ~10 min) | Finite, painful, not permanent. The echo lasts the same window. |
| Clumsy FULL / ECHO | reach ×0.60/×0.88, grav ×1.40/×1.12 | Big reach cut + low jump; echo ~20% of the effect. |
| Unlucky FULL / ECHO | luck −2/−0.4, Unluck II/I | Meaningful on luck-gated tables; echo mild. |
| Weak FULL / ECHO | Weakness II / I | One tier down for the echo (no fractional potion amps). |
| `CURSE_DISTURBANCE` | 40 | Weaponizing a spirit disturbs its domain. |

**Anti-spam invariant:** every successful cast lays exactly one echo on the caster (unit-tested); casting
N curses stacks N echoes (each independently decaying). **Finiteness invariant:** every curse reaches
`isSpent` in exactly `CURSE_TICKS` ticks (unit-tested).

---

## Test Plan

### Pure unit tests (`src/test/java`) — `CurseTest`
- `effectFor` maps the 3 domains × {FULL, ECHO} → the right kind/strength; others → null.
- The FULL-vs-ECHO modifier values differ (~20%: echo reach reduction ≤ 30% of full, etc.).
- `gripAfter`/`isSpent` arithmetic + boundary; finiteness reaches spent in exactly `CURSE_TICKS` steps.
- `isEcho` true iff spiritType path ends `_echo`.

### GameTests (`src/main/java/.../test/`) — `CurseGameTests`
- **taglock capture**: `interactLivingEntity` on a mock victim → NBT holds the victim's UUID.
- **curse + echo**: small circle + glowing_spore + a bound taglock (target = mock victim B) + powered altar,
  activated by mock player A → assert B's `marks` hold a FULL THRESHOLD curse **and** A's `marks` hold an
  ECHO THRESHOLD curse; taglock consumed; 100 power debited.
- **offline botch**: bound taglock to a nobody-online UUID → botch (disturbance, no marks, taglock consumed).
- **self-target**: A curses A → victim gets FULL, caster gets **no** echo (no self-punishment double-dip).
- **applies + lifts**: drive `processPlayer` on a cursed mock with fear=2 → after 2 ticks the mark is gone
  and the Weakness effect is cleared.

(`runGameTestServer` currently fails on a pre-existing Patchouli mixin error unrelated to this slice;
these GameTests compile under `compileJava` and run once that env issue is resolved.)

---

## Out of Scope (explicit cuts — follow-up slices)

- **Cleansing rite** (the active counter — a positive-reciprocity act paying the grip down fast).
- **Offline-target curses** (queued/retroactive application) — v1 requires the target online.
- **Curse projectile / `PresenceEntity` delivery** (HEE-faithful thrown curse) — alternative vector later.
- **Cursed brews / cursed blocks** (Witchery-style). **Fear-scaled strength** within a tier (v1 = fixed).
- **Crit-chance reduction** (requested) — not an attribute in MC 1.20.1; substituted by luck/drop reduction.
- The remaining HEE curses (VAMPIRE/DECAY/LOSS/REBOUND/TELEPORTATION/…) — data once the mechanism exists.
- Generalizing the `RitualContext` caster handle into amulet owner-binding (the plumbing lands here; wiring
  it into `SealAmuletRite` is a one-line follow-up).
