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

## Slice 1 — Sleep Brew entry (current)
Player loop: brew the Sleep Brew (cauldron, a THRESHOLD-domain reagent) → drink → **cross the threshold**:
spend `SCRY_COST` essence; if you can't afford it you don't cross (the brew fizzles). On crossing,
`DreamResolver.read(debtN, marksN, disturbance)` resolves the dream; a **nightmare** imposes a `dread`-scaled
waking penalty applied through a `fear` `Act` (State only through Act). Dominant domain foreshadows the island.

- **Delegable pure core (deepseek gate):** `soul/DreamOnset` + `soul/DreamOutcome` — `onDrink(essence,
  DreamReading) → DreamOutcome(entered, essenceSpent, nightmare, dreadPenalty)`. `SCRY_COST = 1.0f` essence;
  `dreadPenalty = nightmare ? reading.dread() : 0f` (already [0,1]; the caller turns it into the fear Act).
- **NOT delegable (Claude + GameTest):** the `Brew` registration + recipe, the drink hook, debiting essence /
  applying the `Act`, the "dreaming" MobEffect + duration, particles/sound.
- **Normalization (caller side, integration):** `debtN = clamp01(totalDebt / DEBT_FULL)`,
  `marksN = clamp01(Σ mark.severity / MARKS_FULL)` — `DEBT_FULL`/`MARKS_FULL` to be set in DESIGN-NOTES `[UNVERIFIED]`.

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
