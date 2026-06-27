# Hexerei — Dream World (the `read` verb)

**Date:** 2026-06-28
**Extends:** soul vector core (`Bond` / `PlayerSoulData` / `ChunkSoulData`), domain `THRESHOLD`

## Constitution verdict
Litmus PASSES. Dream = **read** (one of the four verbs create/modify/seal/read over the Bond record).
A dream **writes nothing to State** — it reads accumulated State and presents it legibly. Deterministic
(clarity/dread = functions of State, never dice; mirrors `Resolution`). Feeds Article III (debt/disturbance →
nightmares → waking cost → pressure to settle the ledger) *and* deepens understanding. No parallel economy;
any waking penalty is applied through the normal `Act` channel, preserving "State only through Act".

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
