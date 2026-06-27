# Hexerei — Dream World (the `read` verb)

**Date:** 2026-06-28
**Extends:** soul vector core (`Bond` / `PlayerSoulData` / `ChunkSoulData`), domain `THRESHOLD`

## Constitution verdict
Litmus PASSES. Dream = **read** (one of the four verbs create/modify/seal/read over the Bond record).
A dream **writes nothing to State** — it reads accumulated State and presents it legibly. Deterministic
(clarity/dread = functions of State, never dice; mirrors `Resolution`). Feeds Article III (debt/disturbance →
nightmares → waking cost → pressure to settle the ledger) *and* deepens understanding. No parallel economy;
any waking penalty is applied through the normal `Act` channel, preserving "State only through Act".

## Confirmed framing (2026-06-28, brainstorm)
- **Factions = `Correspondence` domains.** The dream world is a dimension of islands: **THRESHOLD = the
  central island** (the threshold / the in-between), the other five (FOREST/STONE/WATER/DEATH/SKY) ring it.
  An island IS a domain made geography — its terrain/mood = that domain's `disturbance`; what stands on it =
  the player's `Bond`s rooted in that domain. This keeps the dimension Law-conformant (the dictionary of the
  Law made land), NOT a content-pack with a parallel faction/reputation economy.
- **Entry = a Sleep Brew (зелье сна), not a bed.** Drinking it crosses the threshold into the dream.
- **Build order (slices):** (1) Sleep-Brew entry → dreaming state [THIS SLICE]; (2) dimension skeleton +
  teleport, placeholder islands; (3) procedural island layout/relief from `disturbance`. Only pure cores are
  deepseek-delegable; dimension/teleport/render/procgen are Claude + GameTest.

## Slice 1 — Sleep Brew → dreaming state + omen (FINALIZED 2026-06-28)
Result of this slice: drinking the Sleep Brew puts the witch into a **dreaming state** (a timed MobEffect set
+ a domain-tinted omen); there is **no dimension yet** (that is Slice 2). Pure cores already built & gate-verified.

### Player loop
Brew `SLEEP_BREW` in the cauldron from `{mandrake_root, wormwood}` (both THRESHOLD reagents; recipe = exact
multiset of item ids). Drink → server-side hook runs the dream pipeline:
1. Read State: `PlayerSoulData`(essence, totalDebt, marks) + the player chunk's `ChunkSoulData` (disturbance).
2. Normalize → `DreamResolver.read(debtN, marksN, disturbance)` → `DreamReading`.
3. `DreamOnset.onDrink(essence, reading)` → `DreamOutcome`.
4. `!entered` (essence < SCRY_COST) → the brew **fizzles**: no effect, fail feedback, bottle returned.
5. `entered` → `spendEssence(SCRY_COST)`; apply the "dreaming" MobEffect set:
   - calm: **Night Vision + Slowness** (peaceful seeing); nightmare: **Weakness + Nausea + Slowness** (restless).
   Duration `DREAM_TICKS`.
6. Omen feedback: particles / action-bar tinted by `reading.dominantDomain()` (full screen overlay = later).
7. `nightmare` → BOTH writes to State:
   - **fear mark:** `psd.addMark(new Bond(THRESHOLD, Disposition(0,0,dreadPenalty,0), seal=null, …))` (CurseRite precedent);
   - **place disturbance:** `csd.apply(Act{domain: THRESHOLD → dreadPenalty * DISTURB_SCALE, …})` + `chunk.setUnsaved(true)`.

**Article III loop:** nightmare → +disturbance[THRESHOLD] → next dream murkier → more nightmares; plus a personal
fear mark to tend. No parallel economy (essence + the existing Bond/disturbance State).

### Build map
- **Done (deepseek, gate-verified):** `soul/DreamReading`, `soul/DreamResolver`, `soul/DreamOnset`, `soul/DreamOutcome`.
- **Still delegable to deepseek (pure):** `soul/DreamNormalize` — `debtN(totalDebt)` = `clamp01(totalDebt/DEBT_FULL)`,
  `marksN(List<Bond> marks)` = `clamp01(Σ mark.disposition().fear() / MARKS_FULL)`. (Fear carried by the marks — a
  nightmare's fear mark feeds straight back into `marksN`, closing the loop; `resentmentFloor()` would read 0 for a
  fresh mark and break it.) Pure (Bond is a pure record) → JUnit gate.
- **NOT delegable (Claude + GameTest):** `Brews.SLEEP_BREW` + `BrewRecipes` entry; the `BrewItem.finishUsingItem`
  hook, kept thin by extracting a server class `soul/DreamEntry.onDrink(ServerPlayer, ServerLevel)` that runs the
  pipeline (read → resolve → spend essence → effects → mark + chunk Act); the MobEffect sets; particle/sound feedback.

### Balance numbers (→ DESIGN-NOTES, `[UNVERIFIED]` until in-game)
`SCRY_COST = 1.0f` (set; ~⅓ of one mandrake take). `DEBT_FULL = 10f`, `MARKS_FULL = 5f` (normalization saturation).
`DISTURB_SCALE = 20f` (a max nightmare adds 20 of 100 THRESHOLD disturbance). `DREAM_TICKS = 600` (30 s).

### Test plan
- **Unit (pure):** `DreamResolverTest`, `DreamOnsetTest` (green); + new `DreamNormalizeTest` (saturation + clamp corners).
- **GameTest (in-world):** seed a `ServerPlayer`/chunk and assert — (a) essence < SCRY_COST → no effect, essence unchanged;
  (b) calm state → essence debited by SCRY_COST, Night Vision present, no new mark; (c) nightmare state (high debt +
  THRESHOLD disturbance) → a fear mark added, chunk disturbance[THRESHOLD] increased, Nausea present.

## Player Loop
- **Trigger** → the witch sleeps in a bed (vanilla night skip). On wake, the night's dream is resolved.
- **Cost** → scrying the dream spends a little `essence` (shared currency); a **nightmare** (born of high
  debt/marks/disturbance) imposes a waking penalty (a small `fear`/`debt` nudge via an `Act`, + no rested bonus).
- **Effect** → the dream READS state: how legible it is (`clarity`), which domain most disturbs the land around
  you (`dominantDomain` — where to act), and how much it curdles (`dread` / `nightmare`).
- **Duration** → instant (resolved at wake); the read is a snapshot, repeatable each sleep.
- **Feedback** → a sleep/overlay vignette tinted by the dominant domain; nightmare = darker tint + a sting sound.

## Registry Objects
| Kind | id | Notes |
|---|---|---|
| (none new for the delegated slice) | — | pure logic only; dimension/overlay deferred (see Out of Scope) |

## Mechanics (delegated pure core — `soul/DreamResolver` + `soul/DreamReading`)
Deterministic read, no Minecraft runtime. Inputs are caller-normalized player terms in [0,1] plus the raw
chunk disturbance map (0..`MAX_DISTURBANCE`).
- `ambient(Map<Correspondence,Float>)` = `clamp01(maxDomainValue / DISTURBANCE_FULL)` (loudest single domain; 0 if empty).
- `clarity(debtN, disturbance)` = `clamp01(1 - 0.5*debtN - 0.5*ambient)` — a calm soul in calm land dreams clearly.
- `dread(debtN, marksN, disturbance)` = `clamp01(0.4*debtN + 0.3*marksN + 0.3*ambient)` — weights sum to 1.
- `dominantDomain(disturbance)` = argmax by value, **iterating `Correspondence.values()` order so ties are
  deterministic** (earlier enum constant wins); `null` if the map is empty or every value ≤ 0.
- `read(debtN, marksN, disturbance)` → `DreamReading(clarity, dread, nightmare, dominantDomain)` where
  `nightmare = dread >= 0.5 && clarity < 0.4`.

`DreamReading` is a pure `record(float clarity, float dread, boolean nightmare, Correspondence dominantDomain)`.

## Persistence
None for the pure core. (Integration layer, deferred: last-dream snapshot may ride `PlayerSoulData` NBT.)

## Client/Server
Pure core is server-agnostic math. (Deferred: the wake-time overlay is client-visual, synced by a new
`DreamReadingS2CPacket` via `HexereiNetwork`; the essence/`Act` cost is server-authoritative.)

## Assets
None for the pure core. (Deferred: overlay texture + sleep/nightmare sounds + lang keys en_us **and** ru_ru.)

## Balance
- `clarity` murk weights 0.5 debt / 0.5 ambient — either alone at max halves clarity; both max → mute.
- `dread` weights 0.4/0.3/0.3 (sum 1) — debt dominates (what you *owe* haunts most), marks & land share the rest.
- `nightmare` gates `dread ≥ 0.5` AND `clarity < 0.4` — only a burdened *and* murky dream curdles.
- `DISTURBANCE_FULL = 100f` mirrors `ChunkSoulData.MAX_DISTURBANCE`.

## Test Plan
- **Unit (this slice, the deepseek gate):** `DreamResolverTest` — clarity/dread formulas at the corners,
  `ambient` from the loudest domain, `dominantDomain` argmax + deterministic enum-order tie-break + null cases,
  `nightmare` boundary, `read` bundling.
- **GameTest (deferred):** sleep in an arena with seeded `PlayerSoulData`/`ChunkSoulData` → assert essence
  debited and (on a curdled state) a `fear` Act applied on wake.

## Out of Scope (NOT delegated to deepseek — needs in-world / visual verification)
- The dream **dimension or overlay** presentation (client render, dimension reg, packet) — GUI/visual.
- The **bed-sleep trigger** event + applying the essence/`Act` waking cost — needs MC events + GameTest.
- These follow via `mc-mod-implement` + GameTests; only the deterministic read-core is gate-able now.

---

## Slice 2 — `hexerei:dream` dimension + teleport (FINALIZED 2026-06-28)
Result: a successful crossing (the slice-1 `entered` path) now **teleports** the witch into a custom
`hexerei:dream` dimension — a barren central THRESHOLD island — and she is **returned** to the overworld
on waking. The dream becomes a place, still transient and costed (no parallel economy).

### Dimension mechanism — approach A (datapack void + code platform)
Register `hexerei:dream` declaratively: a `dimension_type` (no skylight, fixed dim time, dreamlike) + a
`dimension` whose generator is **void/empty** (no terrain). The central island is a small **barren**
platform the mod places in code at a fixed anchor on first entry — NOT worldgen, NOT loot/mobs. Slice 3
swaps the platform-placer for a `disturbance`-driven island generator; the dimension JSON stays.

### State machine
- **Entry** (extend `DreamEntry`, only on `outcome.entered()`): save return state → ensure the island
  platform exists at the anchor → `player.teleportTo(dreamLevel, anchorSpawn)`. The fizzle path and the
  slice-1 nightmare writes (mark + disturbance, on-drink) are unchanged.
- **Wake — trigger 1 (timer):** a server `PlayerTickEvent` — when `level.getGameTime() >= DreamState.wakeTick`
  while the player is in `hexerei:dream`, return them. The timer is the stored `wakeTick`, NOT a vanilla
  effect, so it can't be cut short by milk and doesn't depend on the slice-1 effect *set*.
- **Wake — trigger 2 (death):** `LivingDeathEvent` (or the damage hook) — if a player in `hexerei:dream`
  would die, **cancel** it and wake them: teleport back + set health to `DEATH_WAKE_HEALTH` (≈ 2 hearts).
  Not a free escape/heal.
- **Return:** teleport to the saved `(returnDim, returnPos)`; clear `dreaming`. On **login** while `dreaming`
  → if in `hexerei:dream` past `wakeTick`, wake normally; if NOT in `hexerei:dream` (a real death slipped
  past the handler, or a stale flag), just clear `dreaming` so the player is never stranded mid-state.

### State storage
New per-player attachment `soul/DreamState` (NeoForge `AttachmentType`, serializable, registered in
`HexereiAttachments`): `{ ResourceKey<Level> returnDim, BlockPos returnPos, long wakeTick, boolean dreaming }`.
`wakeTick = entryGameTime + DREAM_TICKS`. The death-wake handler runs before a real death; the login
recovery above clears any stale `dreaming` so a missed death never strands the player.

### Constitution verdict
Litmus PASSES: the dimension is a **transient projection** (always returned), entry costs `essence`, the
island is barren (no loot/mob/ore economy), it IS the THRESHOLD reading (Law made geography). Death-wake at
low health prevents "dream as a free escape/heal." Deterministic; diegetic (crossing the threshold).

### Build map
- **deepseek-delegable (pure):** `soul/DreamReturn` — `resolveTarget(DreamState, fallbackDim, fallbackPos)`:
  return the saved `(dim,pos)` if present/valid, else the fallback (overworld spawn). Pure → JUnit gate.
- **NOT delegable (Claude):** the `hexerei:dream` datapack JSON (`dimension_type` + `dimension`), the
  `DreamState` attachment, extending `DreamEntry` with save-return + platform-place + teleport, the wake
  tick/effect watcher, the `LivingDeathEvent` wake handler, the login-recovery handler.

### Testing
- **Unit (deepseek gate):** `DreamReturnTest` — saved target returned; missing/invalid → fallback.
- **Dedicated-server SMOKE test (authoritative), NOT GameTest:** drink → assert player now in `hexerei:dream`
  on the platform with `DreamState.dreaming==true` and return state saved; effect-expiry → back at return pos;
  death-in-dream → woken (alive, low health), not dead. Cross-dimension teleport is unreliable in the shared
  multi-arena GameTest world (same rationale as the altar power scan), so a smoke test is the real gate.

### Balance `[UNVERIFIED]`
Island spawn anchor `(0,64,0)`; platform 5×5 barren; `DEATH_WAKE_HEALTH = 4f` (2 hearts); dream duration
reuses `DreamEntry.DREAM_TICKS = 600`.

### Out of scope (Slice 2)
Procedural island terrain, the outer domain islands, the sealed dream inventory, and client sky/render — all
moved into Slice 3 below.

---

## Slice 3 — soul-state as a sealed archipelago (FINALIZED 2026-06-28)
Result: the barren 5×5 platform becomes an **archipelago that *is* the dreamer's overworld chunk soul-state**.
At entry we already hold the 6-domain `disturbance` map (`csd.disturbanceView()` in `DreamEntry.onDrink`); we
build islands from it. **THRESHOLD** = the central island (spawn); the five other domains (**FOREST / STONE /
WATER / DEATH / SKY**) = five islands ringing it at **fixed compass directions**, each sized by *that domain's*
disturbance. A calm soul → small flat barren islands; a resentful domain → a tall jagged island in its quarter.
This is the `read` verb made walkable: the witch walks her own unrest and reads which domains the world around
her resents, and how badly. The dream is **fully interactive but sealed** — nothing material crosses the
threshold (the `seal` verb as a property of the place).

### Sub-slice decomposition (one doc, four ordered plans)
| # | Deliverable | Pure core → deepseek JUnit gate | Claude (not gateable) |
|---|---|---|---|
| **3a** | **Sealed dream inventory** — enter sealed, wake restores verbatim; crash/logout-safe | *(none — MC `ItemStack`/registry runtime; GameTest + smoke)* | snapshot→persist→clear on entry; restore on **every** wake path; discard dream items |
| **3b** | **Central THRESHOLD island** from `disturbance[THRESHOLD]`, replacing the flat platform; regen-per-entry | `DreamTerrain` — `radiusFor` + `heightAt` (deterministic dome) | `buildDreamScape` placement, clear-region, anchor, wire into `DreamEntry` |
| **3c** | **Five outer domain islands**, sized per domain, fixed angles | `DreamLayout.sites(ringR)` → 6 stable sites | per-domain vanilla palette + placement |
| **3d** | **Dreamlike client sky/fog** tinted by the dominant domain | *(none — visual, manual verify)* | `DimensionSpecialEffects` client registration |

Order: **3a first** (the seal makes interactivity safe before any mineable terrain exists), then terrain 3b/3c,
then render 3d.

### 3a — Sealed dream inventory (the `seal` verb)
On a successful crossing the player enters the dream **empty-handed**; the waking inventory is restored verbatim
on wake. *(Entering empty — bring nothing across the threshold — is the strongest Law fit and the safest against
dupes; flipping to "enter with a disposable copy" is a one-field change if playtesting wants tools in-dream.)*

- **Storage:** extend `soul/DreamState` with a serialized inventory snapshot (`ListTag invSnapshot`, `boolean
  sealed`). It already persists with player data and is `copyOnDeath`, so it survives logout/crash.
- **Entry (`DreamEntry.onDrink`, only on `entered`):** snapshot main+armor+offhand → **`setData` to persist the
  snapshot FIRST** → then clear the live inventory → teleport. Ordering matters: if anything fails after the
  snapshot persists, login-recovery still restores. XP is **not** touched (out of scope; flag only).
- **Restore (every wake path):** `DreamWorld.wake` restores the snapshot, clears any dream-acquired items, clears
  `sealed`+`invSnapshot`. The **death-wake** path (death cancelled, so vanilla never drops) and the
  **login-recovery** path must *also* restore — login-recovery restores whenever `sealed` is set, **regardless of
  dimension**, so a missed death or stale flag never strands the player without their items.
- **Not deepseek-gateable:** `ItemStack` (de)serialization needs a registry `HolderLookup.Provider`, so this is
  GameTest + dedicated-server smoke, Claude-side.

### What the terrain reads (forks set by the user)
Raw **6-axis per-domain `disturbance` map**, read at entry (the same map slice 1 already pulls for the omen).
`disturbanceN(domain) = clamp01(value / 100)`. No new data path, no persistence — handed straight to
`buildDreamScape` at entry and discarded.

### Terrain cores (deepseek-delegable, pure)
- **`soul/DreamTerrain`** — one island's shape, deterministic (seeded by integer coord hash, **no RNG**):
  - `int radiusFor(float disturbanceN)` → `MIN_R=2` (calm ≈ old platform) … `MAX_R=8` (max), monotonic.
  - `int heightAt(int dx, int dz, int radius, float disturbanceN)` → `0` outside `radius`, `≥1` inside, a dome
    whose peak grows with `disturbanceN` (≤ `PEAK_BUMP=10` above base).
  - **Gate (`DreamTerrainTest`):** `radiusFor` monotonic & bounded `[MIN_R,MAX_R]`; `heightAt`==0 outside radius;
    `≥1` at center; peak(disturbanceN=1) > peak(disturbanceN=0); determinism (same args → same value).
- **`soul/DreamLayout`** — `List<IslandSite> sites(int ringRadius)` → 6 `IslandSite(Correspondence domain,int cx,
  int cz)`: THRESHOLD at `(0,0)`, the five others equidistant (`RING_R=24`) at 72° with a **fixed per-domain
  angle** (legibility: FOREST is always the same direction).
  - **Gate (`DreamLayoutTest`):** exactly 6 sites; THRESHOLD at origin; five outer equidistant (±1 rounding);
    all distinct; per-domain coords stable for a given `ringRadius`; outer islands non-overlapping (chord
    `2·RING_R·sin36° ≈ 28 > 2·MAX_R`).

### World placement (Claude, not gateable)
`DreamWorld.buildDreamScape(ServerLevel dream, Map<Correspondence,Float> disturbance)` replaces `preparePlatform`:
clear the bounding box (`RING_R+MAX_R+2` around origin, base-Y band) to air → for each `DreamLayout` site, place a
`DreamTerrain` dome with that domain's `disturbanceN` and **vanilla palette** → return the center-top spawn Y.
`DreamEntry.onDrink` passes the already-read `disturbance` map and spawns the player on the center island top.
Palettes (reuse vanilla art): THRESHOLD=tuff, FOREST=dirt/moss_block+oak_leaves, STONE=stone/cobbled_deepslate,
WATER=packed_ice/blue_ice, DEATH=bone_block/soul_soil, SKY=calcite/quartz_block.

### 3d — Client sky/render (Claude, manual verify)
Register a `DimensionSpecialEffects` for `hexerei:dream` client-side (gated by `Dist.CLIENT`): no sun/moon, void
sky, fog color tinted by the **dominant domain** of the reading. Not deepseek-gateable (visual); verified by the
smoke procedure with a screenshot, not a GameTest.

### Constitution verdict
Litmus **PASSES** — the `read` verb over the chunk record, now also the `seal` verb (nothing material crosses the
threshold). Cost = `essence` (slice 1; no new currency). **Transient**: regenerated and cleared each entry.
**Barren**: no loot/mob/ore. **No parallel economy by construction**: the sealed inventory makes extraction
*impossible*, not merely discouraged. **Legible**: terrain encodes disturbance — an instrument, not decoration;
the render presents the reading, it is not a mechanic posing as content. Deterministic; diegetic; feeds Art. III
(accrued disturbance-debt made walkable → deepens understanding).

### Known limitation (flagged, not fixed here)
The dream region is shared and rebuilt per entry → **one dreamer at a time** is assumed; concurrent dreamers
overwrite each other's archipelago. A per-player coordinate offset (UUID-hash stride) or per-player instance is
future work.

### Balance `[UNVERIFIED]`
`MIN_R=2`, `MAX_R=8`, `PEAK_BUMP=10`, `RING_R=24`, base Y=60 (domes rise toward the `ANCHOR` y=64 spawn band),
`disturbanceN = clamp01(value/100)`, clear-box radius `RING_R+MAX_R+2 = 34`. Vanilla palettes as above.

### Testing
- **Unit (deepseek gates):** `DreamTerrainTest`, `DreamLayoutTest` (assertions above).
- **GameTest:** sealed-inventory round-trip in a single arena where feasible (snapshot→clear→restore on a
  same-dimension mock), plus the existing dream cases.
- **Dedicated-server SMOKE (authoritative for cross-dimension + visual):** drink → archipelago matches the
  chunk's disturbance (disturbed domain = bigger island in its quarter); mine in-dream then wake → real inventory
  intact, dream loot gone; death-in-dream and logout-in-dream → woken with inventory restored; the dream sky
  reads dreamlike. Cross-dimension teleport/inventory is unreliable in the shared GameTest world (altar-scan
  rationale), so smoke is the real gate.
