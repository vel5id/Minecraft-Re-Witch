# Lunar Phases + Blood Moon Event + Eclipse Rite — Design

Status: DESIGN ONLY. No code/assets written. Grounded in the real `hexerei/` sources read on
2026-06-25 (ritual system, `ChunkTaintData` SavedData shape, `HexereiNetwork`/`TaintSyncS2CPacket`,
`ClientTaintCache`, `HexereiLevelEvents.onLevelTick`, `RitualContext`/`Rites.addRitualTaint`).
Implements innovation-backlog **#10 Lunar Phase Gating** and a scoped slice of **#18 Taint World Events**.

---

## 1. Premise

Three connected pieces, smallest-to-largest:

1. **Lunar Phases** — a pure `LunarPhase` helper classifies Minecraft's 8 moon phases and yields a
   per-phase taint/effect modifier. That modifier rides the **existing** `RitualContext` multiplier
   path (alongside the altar artefact multipliers), so every rite is cheaper/stronger on a full moon
   and dirtier on a new moon — an 8-day "wait for the right night" layer with **zero new persistence**.
   The current phase + its effect is surfaced in the **Ritual Chalk tooltip** and an **action-bar** line.

2. **Blood Moon** — a rare night event held in a new `BloodMoonData` SavedData (per `ServerLevel`,
   mirroring `ChunkTaintData`). While active: rituals are amplified, taint accrues faster, and hostiles
   near players get a small server-side buff (gated on the existing 200-tick pulse). It is ignited
   (a) by a small random chance on *any* rite, or (b) deterministically by the Eclipse Rite, and clears
   at dawn. The client tints the sky/fog **red** for the duration via a synced flag.

3. **Eclipse Rite** — a new `Rite` + `RitualRecipe` that sets night, **forces** a blood moon, and writes
   heavy taint. The capstone "I summon the dark" rite; the most expensive entry in `RitualRecipes.ALL`.

Design throughline: pure, testable time/phase math (mirroring `WaningMoonRite.midnightOf`), state in a
`SavedData` shaped exactly like `ChunkTaintData`, and a client read-only mirror cache exactly like
`ClientTaintCache` fed by an S2C packet exactly like `TaintSyncS2CPacket`.

---

## 2. Mechanics

### 2.1 Lunar phase model (pure)

Minecraft exposes the moon phase as `level.dimensionType().moonPhase(level.getDayTime())` (server) or
`ClientLevel.getMoonPhase()`. Index meaning (vanilla): **0 = FULL**, 4 = NEW, the rest waxing/waning
gibbous/quarter/crescent. New `LunarPhase` (pure enum + lookup, no MC types in the math):

| index | constant       | ritualMul (effect) | taintMul (taint) | rationale |
|------:|----------------|-------------------:|-----------------:|-----------|
| 0 | `FULL`            | 1.25 | 0.80 | the witch's high tide — strongest rite, cleanest land |
| 1 | `WANING_GIBBOUS`  | 1.10 | 0.90 | |
| 2 | `LAST_QUARTER`    | 1.00 | 1.00 | neutral baseline (== no-modifier behaviour) |
| 3 | `WANING_CRESCENT` | 0.95 | 1.10 | waning toward the dark |
| 4 | `NEW`             | 0.80 | 1.30 | the dark of the moon — weakest, dirtiest |
| 5 | `WAXING_CRESCENT` | 0.95 | 1.10 | |
| 6 | `FIRST_QUARTER`   | 1.00 | 1.00 | neutral baseline |
| 7 | `WAXING_GIBBOUS`  | 1.10 | 0.90 | |

- Pure API (unit-testable, **no Minecraft runtime**):
  - `static LunarPhase fromIndex(int moonPhase)` — `Math.floorMod(idx, 8)` guard so a bad index never throws.
  - `float effectMul()` / `float taintMul()` — the table values.
  - `boolean isFull()` / `boolean isNew()` — for tooltip colour + flavour, and for the blood-moon
    ignite-chance bump (§2.3).
  - `String nameKey()` — `"hexerei.moon.<lowercase>"` lang key (§7).
- Symmetry by design: `effectMul * taintMul ≈ 1.0` at the extremes (FULL 1.25×0.80 = 1.0;
  NEW 0.80×1.30 = 1.04) so a phase is a *trade-shaped* choice, never a free lunch.

### 2.2 Wiring the phase modifier into rites (no `Rite.perform` signature change)

`RitualActivation.tryPerform` already wraps `recipe.rite().perform(...)` in
`RitualContext.begin(taintMul, effectMul) … end()`, where the two muls come from the funding altar's
artefact. **The phase modifier folds into those same two arguments** — the cleanest possible insertion,
re-using the entire existing ThreadLocal path (`Rites.addRitualTaint` reads `currentTaintMul()`,
`VerdantRite` reads `currentEffectMul()`):

```
LunarPhase phase = LunarPhase.fromIndex(level.dimensionType().moonPhase(level.getDayTime()));
float bloodMul   = BloodMoonData.get(level).isActive() ? BloodMoonData.RITUAL_EFFECT_MUL : 1f; // §2.3
float taintMul   = artefactTaintMul * phase.taintMul()  * (bloodMoonActive ? BLOOD_TAINT_MUL : 1f);
float effectMul  = artefactEffectMul * phase.effectMul() * bloodMul;
RitualContext.begin(taintMul, effectMul);
```

- **[DECISION-COMBINE] Multipliers multiply (recommended default).** `artefact × phase × bloodmoon`.
  A purifier `bone_charm` (0.5) on a FULL moon (0.80) → 0.40 taint; an `obsidian_skull` (2.0) on a
  NEW moon (1.30) → 2.60. This composes the existing artefact lever with the new phase lever cleanly,
  and `1.0 × 1.0 × 1.0` reproduces today's behaviour exactly. No clamping in v1 (the artefact slice
  already chose un-clamped multipliers).
- The phase lookup is **server-side** in `tryPerform`; no client involvement for gameplay.

### 2.3 Blood Moon event

`BloodMoonData` SavedData holds: `boolean active`, `long endsAtDay` (the *game-day* at whose dawn it
clears). While active:

- **Rituals amplified:** `RITUAL_EFFECT_MUL = 1.5` and `BLOOD_TAINT_MUL = 1.5` fold into the
  `RitualContext` muls (§2.2). A blood-moon rite is markedly stronger *and* markedly dirtier — power
  with a price, matching the taint spine.
- **Taint accrues faster:** the `WorldTaintAura.pulse` corruption pass (200-tick) bumps its mutation
  probabilities by a flat factor while active (e.g. grass 0.15→0.225, flowers 0.20→0.30) **OR** adds a
  small ambient taint tick to each tracked altar's chunk. **[DECISION-BMTAINT] Recommended: ambient
  taint tick** — `Rites.addRitualTaint(level, altarChunk, BLOOD_AMBIENT_TAINT=0.5f)` per active altar
  per pulse (re-uses the synced taint write; reads naturally as "the land sickens under the blood
  moon"), rather than reaching into `pulse`'s probability constants. ~9 taint/in-game-night near an
  altar — meaningful but not instantly HIGH.
- **Hostiles emboldened:** on the **same 200-tick pulse**, for each survival/adventure player, monsters
  within a radius get a refreshed short buff. **[DECISION-MOBBUFF] Recommended: Strength I + Speed I**
  for `PULSE+slack` ticks (mirror the `TaintPunishment.REFRESH_TICKS = 220` invariant so the buff never
  gaps between pulses), applied to `Monster`s within radius 24 of each player. No spawn-rate hooks in
  v1 (spawn manipulation is invasive and hard to GameTest); a buff is server-only, deterministic, and
  reuses the exact `addEffect(ambient, hidden particles, hidden icon)` shape already in `WorldTaintAura`.
- **Lifecycle:** ignited → `setActive(true, endsAtDay = currentDay)` and time is shoved to night via the
  `WaningMoonRite.midnightOf` style (a pure `BloodMoonData.nightStart(long dayTime)` helper, see §2.4).
  Cleared at the next **dawn**: the 200-tick pulse checks `if (active && dawnReached(level)) setActive(false)`.

#### Triggers

- **(a) Random on any rite.** In `RitualActivation`, on a `SUCCESS` (after the rite performs), roll
  `level.getRandom().nextFloat() < igniteChance`. **[DECISION-IGNITE] `igniteChance = 0.02` (2%)**,
  bumped to **0.05 on a NEW moon** and suppressed to **0** if already active or if the Eclipse Rite is
  the one that just ran (it ignites deterministically). Justification: ~1-in-50 rites is rare enough to
  feel like an *event* (a player casting a handful of rites a session sees one occasionally), the NEW-moon
  bump ties the rare event to the dark of the moon thematically, and it is a single pure-comparable
  probability constant documented in DESIGN-NOTES.
- **(b) Eclipse Rite (§4).** Deterministic `BloodMoonData.get(level).ignite(level)`.

### 2.4 Pure time helpers (mirror `WaningMoonRite.midnightOf`)

Two new pure static helpers (unit-tested, no MC):

- `BloodMoonData.dayOf(long dayTime)` → `Math.floorDiv(dayTime, 24000L)` — the integer game-day index.
- `BloodMoonData.nightStart(long dayTime)` → next tick that lands on **13000** (vanilla nightfall),
  never moving backward — copied structurally from `midnightOf` but with `NIGHT = 13000L`:
  `base = dayTime - floorMod(dayTime, 24000) + 13000; return base >= dayTime ? base : base + 24000;`
- `BloodMoonData.endedBy(long currentDayTime, long endsAtDay)` → `dayOf(currentDayTime) > endsAtDay`
  — pure "has dawn of the next day passed?" check, so the clear-at-dawn logic is unit-testable without
  a live level. (Equivalently `dayOf(now) >= endsAtDay + 1`.)

The Eclipse Rite / random-ignite both `level.setDayTime(nightStart(level.getDayTime()))` so a blood moon
visibly *starts at night*, exactly like `WaningMoonRite` drags to midnight.

---

## 3. Registry / new files

### New Java files

| File | Purpose |
|------|---------|
| `ritual/LunarPhase.java` | **Pure** enum (8 constants) + `fromIndex`/`effectMul`/`taintMul`/`isFull`/`isNew`/`nameKey`. No MC imports. |
| `power/BloodMoonData.java` | `SavedData` (per `ServerLevel`); `active`, `endsAtDay`; `get/save/load/setDirty` mirroring `ChunkTaintData`; `ignite(ServerLevel)`, `clear(ServerLevel)`, `isActive()`, pure `dayOf/nightStart/endedBy`. Constants `RITUAL_EFFECT_MUL`, `BLOOD_TAINT_MUL`, `BLOOD_AMBIENT_TAINT`. |
| `ritual/EclipseRite.java` | `Rite` impl: set night, `BloodMoonData.ignite`, heavy taint via `Rites.addRitualTaint`, particles/sound. |
| `ritual/BloodMoonPulse.java` | Server helper called from the 200-tick pulse: ambient taint + mob buff + dawn-clear check. Keeps `HexereiLevelEvents` thin (parallels `WorldTaintAura`). |
| `network/BloodMoonSyncS2CPacket.java` | record packet `(boolean active)`, mirrors `TaintSyncS2CPacket` encode/decode/handle → `ClientBloodMoonCache`. |
| `client/ClientBloodMoonCache.java` | `@OnlyIn(CLIENT)` single boolean mirror, mirrors `ClientTaintCache` (`set/get/clear`). |
| `client/BloodMoonSkyHandler.java` | `@Mod.EventBusSubscriber(Bus.FORGE, Dist.CLIENT)` — tints sky/fog red while the cache flag is set (§6). |

### New test files (`src/test/java`)

| File | Covers |
|------|--------|
| `ritual/LunarPhaseTest.java` | `fromIndex` all 8 + wrap-around (`-1`, `8`, `16`); FULL/NEW classification; mul table values; symmetry sanity. |
| `power/BloodMoonDayMathTest.java` | `dayOf`, `nightStart` (mirror the `WaningMoonRiteTest` midnight cases), `endedBy` boundaries. |

### New GameTest (`src/main/java/.../test`)

| File | Covers |
|------|--------|
| `test/EclipseRiteGameTest.java` (or a method on the existing ritual GameTest holder) | Build a sigil + small ring, drop the sacrifice, fire activation, assert `BloodMoonData.get(level).isActive()` is true and `level.getDayTime()` is at night. Mirrors the Tempest GameTest's "assert raw data flag" approach. |

No new blocks/items beyond the rite recipe (which adds **no** new registry object — a `RitualRecipe` is
plain data appended to `RitualRecipes.ALL`). **No new textures or item icons** (see §7).

---

## 4. Eclipse Rite — concrete spec

```
public static final RitualRecipe ECLIPSE = new RitualRecipe(
        "hexerei:eclipse", CircleSize.MEDIUM, "<sacrifice>", 220,
        new EclipseRite(), "ritual.hexerei.eclipse");
```

- **id:** `hexerei:eclipse`  •  **nameKey:** `ritual.hexerei.eclipse` ("Rite of the Eclipse").
- **CircleSize:** `MEDIUM` (reuses the existing large ring; the heaviest rite gets the bigger circle,
  consistent with `WANING_MOON` being MEDIUM).
- **Sacrifice item — [DECISION-ECLIPSE-SACRIFICE]:** must be a **real existing item id** and must **not**
  collide with another MEDIUM-circle recipe's sacrifice (matching is `circle complete && sacrificeId
  equals`; `WANING_MOON` already uses `hexerei:belladonna_flower` on MEDIUM). Recommended default:
  **`hexerei:wither_root`** if it exists; otherwise a flagged dependency.
  **[DEPENDENCY]** — confirm the exact sacrifice id against `HexereiItems` before implementation. Safe
  fallbacks that are confirmed to exist as ritual sacrifices today: `hexerei:wolfsbane` (used by
  `BOUND_BEAST` but that is SMALL, so **no collision** on MEDIUM) or `hexerei:mandrake_root`
  (Tempest/SMALL — also no MEDIUM collision). **Recommended concrete default: `hexerei:wolfsbane`**
  (thematically "dark", confirmed real, distinct from `WANING_MOON`'s belladonna on the same MEDIUM ring).
- **Power cost: 220.** Justification: the existing ladder runs 40 (manifest) → 60 (verdant) → 100
  (tempest) → 120 (bound beast) → 150 (waning moon). Eclipse is the new top — forcing a world event
  warrants the highest cost; 220 ≈ 1.5× the previous top (150) and stays a clean round number. Power is
  debited **before** the sacrifice (existing `payPower` order), so a failed funding leaves inputs intact.
- **`perform(level, center)` effect:**
  1. `level.setDayTime(BloodMoonData.nightStart(level.getDayTime()))` — visibly darkens to night.
  2. `BloodMoonData.get(level).ignite(level)` — sets `active=true`, `endsAtDay = dayOf(now)`, marks
     dirty, and sends `BloodMoonSyncS2CPacket(true)` to tracking players (§5).
  3. Heavy taint: `Rites.addRitualTaint(level, new ChunkPos(center), 40f)` — above Tempest's 25 and at
     the top of the rite range, scaled by the live `RitualContext` mul (which on a blood-moon eclipse is
     itself amplified — the rite *self-amplifies* its own dirtiness, a deliberate "this is the dark
     one" signal). Floor accrues via the existing `addTaint` 0.1× floor rule.
  4. Cosmetic: a `WITCH`/`SOUL` particle burst on the MEDIUM ring + a low-pitch
     `SoundEvents.WITHER_SPAWN` (mirrors the existing rites' particle+sound pattern).
- **Position in `RitualRecipes.ALL`:** appended **last** (after `WANING_MOON`), preserving "gentle/cheap
  first, expensive/aggressive last" and keeping `TEMPEST` at index 0 (saved-NBT default + GameTest
  expectations). New list:
  `List.of(TEMPEST, VERDANT, MANIFEST_CHALK, BOUND_BEAST, WANING_MOON, ECLIPSE)`.

---

## 5. Persistence

- **`BloodMoonData extends SavedData`**, key `"hexerei_bloodmoon"`, fetched
  `level.getDataStorage().computeIfAbsent(BloodMoonData::load, BloodMoonData::new, KEY)` — identical shape
  to `ChunkTaintData.get`.
  - `save(CompoundTag)`: `putBoolean("active", active); putLong("endsAtDay", endsAtDay);`
  - `load(CompoundTag)`: read both back.
  - Every mutator (`ignite`, `clear`) calls `setDirty()`.
- **No new chunk persistence.** Lunar phase is derived live from `level.getDayTime()` every time — it is
  *not* stored (this is the "zero new persistence" win from backlog #10). Only the blood-moon flag is
  saved, and it is a single per-level boolean + long.
- **Edge case [DECISION-RESTART]:** on world reload, if `active` is still true but `endedBy(now, endsAtDay)`
  is already true (server was off across a dawn), the next 200-tick pulse's dawn check clears it
  immediately and re-syncs `false`. Recommended: **no special load-time logic** — let the pulse converge.

---

## 6. Client / Server split

- **Server-authoritative:** all phase math, all `BloodMoonData` mutation, the mob buff, the ambient
  taint, and the dawn-clear run server-side only (in `tryPerform` and the 200-tick pulse). Mirrors the
  rule that world state lives off the client.
- **Client read-only mirror:** `ClientBloodMoonCache` (one boolean) fed by `BloodMoonSyncS2CPacket`,
  exactly like `ClientTaintCache` + `TaintSyncS2CPacket`.
- **Client render hook — [DECISION-RENDER]:** **`ViewportEvent.ComputeFogColor`** (Forge 1.20.1,
  `Bus.FORGE`, `Dist.CLIENT`) is the chosen, simplest, feasible method. While
  `ClientBloodMoonCache.isActive()`:
  - In `BloodMoonSkyHandler.onFogColor(ViewportEvent.ComputeFogColor e)`, lerp the fog RGB toward a deep
    red (`r≈0.45, g≈0.05, b≈0.05`) by a fixed factor (e.g. 0.6) — this reddens the horizon/sky-fog
    band and the whole ambient cast, which reads unmistakably as "blood moon" at night without touching
    sky geometry or the moon sprite.
  - Pair it with **`ViewportEvent.ComputeFogColor` only** for v1. A true red **moon-texture swap** or a
    `DimensionSpecialEffects` override is explicitly **rejected for v1** as too invasive (re-texturing the
    vanilla moon phases atlas / overriding dimension effects risks breaking other mods and vanilla sky
    rendering). A red sky/fog tint + red ambiance is the accepted, agreed-acceptable visual.
  - **Optional cheap add (recommended):** a `RenderLevelStageEvent` (stage
    `AFTER_SKY`) is *not* needed; the fog tint alone is enough. Keep v1 to the single fog hook.
- **How the client knows it is active:** the `BloodMoonSyncS2CPacket(active)` is sent (i) on ignite to
  all players tracking the level, and (ii) on a player joining a level where `active` is true (a
  `PlayerEvent.PlayerLoggedIn` / `ChunkWatch`-style send, mirroring how `onChunkWatch` re-sends taint),
  and (iii) `false` on clear at dawn. `ClientBloodMoonCache.clear()` on disconnect.

---

## 7. Networking & Assets / lang

### Networking (single shared `SimpleChannel`, append in `HexereiNetwork.register`)

```
CHANNEL.registerMessage(nextId++, BloodMoonSyncS2CPacket.class,
        BloodMoonSyncS2CPacket::encode, BloodMoonSyncS2CPacket::decode, BloodMoonSyncS2CPacket::handle);
```
- New `static void sendBloodMoonSync(ServerLevel level, boolean active)` in `HexereiNetwork`, sending
  `PacketDistributor.DIMENSION.with(level::dimension)` (a whole-level event, unlike taint's
  `TRACKING_CHUNK`). Handler enqueues `ClientBloodMoonCache.set(active)` via `DistExecutor`, identical to
  `TaintSyncS2CPacket.handle`.

### Assets / lang

**No new textures, models, or item icons.** Only lang keys (both `en_us.json` and `ru_ru.json`):

| key | en_us | ru_ru (suggested) |
|-----|-------|-------------------|
| `ritual.hexerei.eclipse` | "Rite of the Eclipse" | "Обряд Затмения" |
| `hexerei.moon.full` | "Full Moon" | "Полнолуние" |
| `hexerei.moon.waning_gibbous` | "Waning Gibbous" | "Убывающая Луна" |
| `hexerei.moon.last_quarter` | "Last Quarter" | "Последняя четверть" |
| `hexerei.moon.waning_crescent` | "Waning Crescent" | "Старый месяц" |
| `hexerei.moon.new` | "New Moon" | "Новолуние" |
| `hexerei.moon.waxing_crescent` | "Waxing Crescent" | "Молодой месяц" |
| `hexerei.moon.first_quarter` | "First Quarter" | "Первая четверть" |
| `hexerei.moon.waxing_gibbous` | "Waxing Gibbous" | "Растущая Луна" |
| `item.hexerei.ritual_chalk.moon` | "Moon: %s (%s)" | "Луна: %s (%s)" |
| `hexerei.moon.effect.strong` | "rites strengthened" | "обряды усилены" |
| `hexerei.moon.effect.weak` | "rites weakened" | "обряды ослаблены" |
| `hexerei.moon.effect.neutral` | "rites neutral" | "обряды нейтральны" |
| `hexerei.bloodmoon.active` | "A Blood Moon rises…" | "Восходит Кровавая Луна…" |

### Tooltip + action-bar (the "interesting to see" surface)

- **Ritual Chalk tooltip** (`RitualChalkItem.appendHoverText`): the tooltip currently has the `Level`
  param (`@Nullable Level level`). When `level != null`, append a line:
  `item.hexerei.ritual_chalk.moon` = "Moon: <phaseName> (<effect>)" where `<effect>` is one of the three
  `hexerei.moon.effect.*` strings chosen by `phase.effectMul()` (>1 strong / <1 weak / ==1 neutral),
  coloured `GREEN` for strong, `RED` for weak, `GRAY` for neutral; FULL/NEW additionally bolded. Phase
  read via `level.dimensionType().moonPhase(level.getDayTime())` (works client-side too — the client
  level has the same moon-phase formula, so no sync needed for the tooltip).
- **Action-bar** (reuse the existing `displayClientMessage(..., true)` pattern from
  `CycleRiteC2SPacket`/`RitualChalkItem`): when the chalk is used to bind a rite, also show the current
  moon line so the player reads "why tonight is a good/bad night". **[DECISION-ACTIONBAR] Recommended:
  show the moon line on bind only** (not every scroll) to avoid action-bar spam; the persistent read is
  the tooltip.

---

## 8. Balance — justified numbers (for DESIGN-NOTES)

| constant | value | justification |
|----------|------:|---------------|
| `LunarPhase` FULL effectMul / taintMul | 1.25 / 0.80 | strongest, cleanest; product 1.0 → trade not free lunch |
| `LunarPhase` NEW effectMul / taintMul | 0.80 / 1.30 | weakest, dirtiest; product 1.04 ≈ neutral |
| quarter phases | 1.00 / 1.00 | exact baseline == today's no-modifier behaviour |
| `igniteChance` (rite → blood moon) | 0.02 | ~1-in-50 rites; rare enough to be an *event* |
| `igniteChance` on NEW moon | 0.05 | dark-of-moon bump ties the event to its theme |
| `RITUAL_EFFECT_MUL` (blood moon) | 1.5 | rites markedly stronger during the event |
| `BLOOD_TAINT_MUL` (blood moon) | 1.5 | …and markedly dirtier — power with a price |
| `BLOOD_AMBIENT_TAINT` per altar per 200t | 0.5 | ~9 taint/night near an altar — meaningful, not instant HIGH |
| mob buff radius / level / duration | 24 / I / 220t | mirrors `TaintPunishment.REFRESH_TICKS`; no gap, lapses ~1s after pulse |
| Eclipse power cost | 220 | new top of 40→150 ladder; ~1.5× prior top |
| Eclipse taint | 40 | above Tempest's 25; top of rite range |
| Blood-moon night start | 13000 | vanilla nightfall (pairs with `midnightOf`'s 18000 style) |

All flagged `[UNVERIFIED]` until in-game balance confirms (per CLAUDE.md convention).

---

## 9. Test plan

### Pure unit tests (JUnit 5, no MC runtime)

- **`LunarPhaseTest`**
  - `fromIndex(0..7)` maps to the 8 constants in order; `fromIndex(-1)==WAXING_GIBBOUS`,
    `fromIndex(8)==FULL`, `fromIndex(16)==FULL` (wrap-around via `floorMod`).
  - `FULL.isFull()` true & `isNew()` false; `NEW.isNew()` true; others both false.
  - Mul table exact values; assert `FULL.effectMul() > 1 && FULL.taintMul() < 1` and the inverse for NEW;
    quarter phases `== 1.0f`.
- **`BloodMoonDayMathTest`** (mirror `WaningMoonRiteTest`)
  - `nightStart(1000L)==13000`; `nightStart(13000L)==13000`; `nightStart(14000L)==37000` (next night,
    never backward); across-days case.
  - `dayOf(0)==0`, `dayOf(23999)==0`, `dayOf(24000)==1`, `dayOf(50000)==2`.
  - `endedBy`: `endedBy(now=dayOf 5 mid, endsAtDay=5)` false; `endedBy(now=dayOf 6, endsAtDay=5)` true;
    boundary at the exact day rollover.
- *(Optional)* a `RitualContext`-style multiplication test asserting
  `artefact × phase × bloodmoon` compose as documented (pure arithmetic, like `RitualTaintScalingTest`).

### GameTest (in-world, `runGameTestServer`)

- **`eclipseRiteIgnitesBloodMoon`**: place a Ritual Sigil, draw the MEDIUM ring (or `setBlock` the runes),
  drop the Eclipse sacrifice as an `ItemEntity` in range, fund via a placed altar (or use a free path /
  pre-seed power), call `RitualActivation.tryPerform`. Assert:
  1. result `SUCCESS`,
  2. `BloodMoonData.get(level).isActive()` is **true** (the authoritative SavedData flag — mirrors the
     Tempest GameTest asserting the raw weather-data flag, not an interpolated client value),
  3. `level.getDayTime()` is at/after night (`% 24000 >= 13000`),
  4. `ChunkTaintData.get(level).getTaint(center chunk) >= 40f` (heavy taint written).
  Mark `required = false` if it depends on the flaky large altar power scan, per the project's GameTest
  guidance; the pure tests carry the logic, the GameTest is the integration smoke.

---

## 10. Files touched (summary)

### New files
- `ritual/LunarPhase.java`, `power/BloodMoonData.java`, `ritual/EclipseRite.java`,
  `ritual/BloodMoonPulse.java`, `network/BloodMoonSyncS2CPacket.java`,
  `client/ClientBloodMoonCache.java`, `client/BloodMoonSkyHandler.java`
- Tests: `LunarPhaseTest.java`, `BloodMoonDayMathTest.java`, `EclipseRiteGameTest.java`

### Edited (shared) files — note the cross-cutting touches
- **`ritual/RitualRecipes.java`** — add `ECLIPSE` recipe; append to `ALL` (last).
- **`ritual/RitualActivation.java`** — fold `LunarPhase` + blood-moon muls into the existing
  `RitualContext.begin(taintMul, effectMul)`; add the post-`SUCCESS` random-ignite roll.
- **`HexereiLevelEvents.java`** — call `BloodMoonPulse.tick(sl)` inside the existing `gt % 200` block
  (alongside `WorldTaintAura.pulse`/`punishPlayers`).
- **`network/HexereiNetwork.java`** — register `BloodMoonSyncS2CPacket`; add `sendBloodMoonSync`.
- **`item/RitualChalkItem.java`** — add the moon line to `appendHoverText`; show moon on bind (action-bar).
- **`client/HexereiClient.java`** (or new `BloodMoonSkyHandler`) — register the `ViewportEvent.ComputeFogColor`
  Forge-bus client listener; clear `ClientBloodMoonCache` on disconnect.
- **`lang/en_us.json`** + **`lang/ru_ru.json`** — the keys in §7.
- **`DESIGN-NOTES.md`** — a new "Lunar Phases + Blood Moon" section documenting every §8 number.

---

## 11. Out of scope (v1)

- Real moon-texture re-skin / `DimensionSpecialEffects` override (fog tint stands in for the moon).
- Spawn-rate manipulation during blood moon (buff-only; spawn hooks are invasive + hard to GameTest).
- Lunar gating of brewing or crop growth (rites only this slice; crops/cauldron are a follow-up).
- Per-dimension blood moons beyond the Overworld; multi-night blood moons (single night, clears at dawn).
- A dedicated blood-moon sound ambiance / music loop.
- Cleansing counter-play specific to blood-moon taint (the existing Cleansing Loop / taint decay apply
  unchanged; a bespoke cure is a separate slice, per backlog "A before #18" sequencing).
```
