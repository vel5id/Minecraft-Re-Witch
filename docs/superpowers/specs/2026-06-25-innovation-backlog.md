# Hexerei — Innovation Backlog (synthesized)

**Date:** 2026-06-25
**Synthesizes:** 25 raw ideas from three ideation lenses (Extend-the-pillars · Genre-mechanics · Systemic/cross-cutting)
into one deduped, themed, ranked backlog.
**Spine:** every survivor plugs into the **altar-power economy** and/or the **taint system** ("more powerful magic
dirties the land"), and reuses existing registration / persistence / networking / GameTest conventions.

This is a **design/ranking document only** — it changes no code, no balance number, and no per-slice spec. Each
referenced spec (altar, cauldron, crops, ritual, taint-atmosphere, charm-pouch, witch-brew-expansion, rituals-expansion,
guidebook, three-aspect-roadmap) remains the source of truth for its own mechanics.

Two facts grounded against the real tree drive the whole ranking:
- **`Rite.perform(ServerLevel, BlockPos)`** has **no player handle** (verified in `ritual/Rite.java`). This single
  signature gap blocks a whole cluster of genre features (familiars, poppets, coven, astral, lycanthropy). It is the
  highest-leverage refactor in the backlog.
- **`ChunkTaintData`** only ever **adds** taint and **decays toward a permanent floor** — there is **no `removeTaint` /
  `lowerFloor`** (verified in `power/ChunkTaintData.java`). Taint is a one-way ratchet today. The Cleansing counter-loop
  is the keystone that makes every other taint idea a real economy instead of a slow death.

---

## 1. Dedupe log (overlapping ideas merged across lenses)

| Merged idea | Came from | Resolution |
|---|---|---|
| **Altar Tiers / Artefacts** | L1 "Altar Artefacts (PowerSource scale fields)" + L3 "Altar Tiers — activate dormant power-scale fields" | **MERGED → "Altar Tiers (Artefacts)."** Identical mechanic: place artefact blocks detected in `AltarBlockEntity.resolveDynamic`, raise the already-declared-but-pinned `powerScale/rechargeScale/rangeScale/enhancementLevel`. L3 adds the *gating* kicker (`enhancementLevel>=N` unlocks heavier rites via `IPowerSource.getEnhancementLevel()`). Ship L1's artefact-detection as the base; L3's gating as a thin follow-up rider. |
| **Taint hurts the player** | L1 "Tainted Ground Consequences" + L2 (implied by astral/lycanthropy) + L3 "Taint Consequence Ladder" | **MERGED → "Taint Consequence Ladder."** L1 is the **S-sized first rung** (stand on `tainted_ground`/`charred_stone` or be in MEDIUM+ chunk → brief Hunger/Weakness, refreshed each pulse). L3 is the **full M-sized ladder** (LOW→standing debuff, MEDIUM→hostile-spawn nudge, HIGH→miasma). Ship L1 as Rung 1; L3's MEDIUM/HIGH tiers as Rung 2. Both live inside `WorldTaintAura.pulse`'s existing 200t loop. |
| **Cleansing counter-loop** | L3 "Cleansing Loop (Rite + Brew)" (rite half also implied by L2 Blessing's local decay and L2 Astral's harvest) | **KEPT as L3's spec.** The rite half (`removeTaint`/`lowerFloor` + `CleansingRite`) has **no unbuilt dependency**; the brew half needs the live `BrewEffect` path. L2 "Blessed Brand" local-decay is a *different surface* of the same `ChunkTaintData.removeTaint` primitive — folded in as a stretch on Cleansing. |
| **Owned familiar** | L1 (not present) + L2 "Familiar Binding" — and the already-shipped **un-owned** `BoundBeastRite` | **KEPT as L2 "Familiar Binding."** This is the explicitly-deferred owner-aware Bound Beast (roadmap D4c). It is the natural *first* consumer of the `Rite.perform` player-handle change. |
| **Coven (players)** | L2 "Coven Resonance (group ritual amplification)" + L3 "Coven Power (altars reinforce each other)" | **KEPT as two distinct ideas.** L2 = *players in the circle* amplify a rite and split taint (needs player-LIST handle). L3 = *altars near each other* form a power cluster (needs Altar Tiers' scale fields to amplify). Different triggers, different dependencies — not duplicates, but both labelled "Coven." |
| **Ritual BlockEntity** | L1 "Multi-Step Timed Rituals (LARGE circle + ritual BE)" | **KEPT.** Introduces the BlockEntity the rituals-expansion spec explicitly cut ("the circle block has no BlockEntity"). Distinct from everything else; it is the gateway to staged/timed rites. |
| **Splash + Modifiers + Cursed/Blessed brews** | L1 "Throwable Splash Brews" + L1 "Brew Potency Modifiers" + L2 "Cursed & Blessed Brands" | **KEPT as three** — all in the Brewing theme, all reuse the now-live `BrewEffect` path, but distinct surfaces (delivery vs potency vs new effect types). Sequenced: Modifiers before more brew *types*. |

Net: **25 raw → 21 distinct survivors** after 4 merges.

---

## 2. Themes

1. **Brewing depth** — extend the cauldron now that `BrewEffect` applies real `MobEffectInstance`s.
2. **Ritual depth** — more rites, staged/timed rituals, and the circle BlockEntity.
3. **Living magic / bound entities** — familiars, poppets, broom; all gated on the `Rite.perform` player-handle.
4. **Taint as a real system** — the counter-loop, the consequence ladder, world events, lunar gating.
5. **Altar & witch progression** — artefact tiers, coven scaling, per-player "Craft" mastery, guidebook unlocks.

---

## 3. Ranked backlog (all 21 survivors)

Sorted by recommended build priority (top = build sooner). **Effort** S=few files, M=subsystem, L=multi-slice.
**Dep** = the chief unbuilt blocker.

| # | Idea | Theme | Eff | Impact | Reuses | Dependency | One-line pitch |
|---|---|---|---|---|---|---|---|
| 1 | **Cleansing Loop (Rite + Brew)** | Taint | M | high | `ChunkTaintData`, `RitualActivation`+new `CleansingRite` (mirrors `VerdantRite`), `RitualCircle.smallRing`, `BrewRecipes`, `WorldTaintAura` reverse-mutation, `sendTaintSync` | brew half needs live `BrewEffect`; **rite half: none** | The only way to *lower* taint — closes the open half of the spine; without it taint is a one-way death. |
| 2 | **Taint Consequence Ladder** (Rung 1 = L1 "Tainted Ground") | Taint | S→M | high | `WorldTaintAura.pulse` 200t loop, `TaintLevel`, `tainted_ground`/`charred_stone`, `CharmTickHandler` refresh trick | none | Makes taint finally *bite* — stand on tainted ground → Hunger/Weakness; the consequence half of the spine. |
| 3 | **Ritual-Crafted Charms** (Charm Slice 2) | Ritual | S | med | `SpawnItemRite` (ships in Rituals-Exp), `RitualRecipes.ALL`, `RitualChalkItem` cycling, `CharmItem`/`CharmDefs` | Charms Slice 1 + Rituals-Exp both landed (per roadmap) | Pure wiring: one `RitualRecipe` per charm turns creative-tab placeholders into real progression. |
| 4 | **Throwable Splash Brews** | Brewing | M | high | `Brew`/`BrewEffect`/`Brews.BY_ID`, `BrewItem`, `CauldronBlockEntity.collectBrew`, `BrewColor` tint | live `BrewEffect` path | Right-click-throw any brew as a flask; AABB sweep applies its effects — already paid for at collection. |
| 5 | **Altar Tiers (Artefacts)** (L1+L3 merge) | Altar prog. | M | high | `AltarBlockEntity.resolveDynamic` scan, the **already-declared** `powerScale/rechargeScale/rangeScale/enhancementLevel` + NBT, `AltarPowerTable`, `IPowerSource.getEnhancementLevel` | artefact models/textures (PIL or vanilla-parented) | Place skull/candelabra/chalice → finally raise the dormant scale fields; `enhancementLevel` gates heavier rites. |
| 6 | **Brew Potency Modifiers (Reagents)** | Brewing | M | med | `BrewRecipes` multiset match, `Brew`/`BrewEffect`, `BrewItem` NBT, cauldron power path | live `BrewEffect` path | Drop a reagent into a finished mix → `+1` amplifier or `×2` duration before bottling; power scales with the boost. |
| 7 | **Familiar Binding (owner-aware Bound Beast)** | Living magic | M | med | `BoundBeastRite` (ships), `RitualActivation`, `ChunkTaintData` trickle, `SavedData` pattern, `HexereiLevelEvents` tick | **`Rite.perform` player-handle change** | Tame the summoned wolf to *you*; passive ward while it lives; a long bond trickles taint — the reason to do the refactor. |
| 8 | **Multi-Step Timed Rituals (LARGE circle + ritual BE)** | Ritual | L | high | `RitualCircle`/`CircleSize`, `Rite`, `RitualActivation`, `SpawnItemRite`, `ChunkTaintData.addTaint`, `sendTaintSync` | new ritual BlockEntity in `HexereiBlockEntities` (the cut "circle has no BE" decision) | First non-instant rite: a radius-4 circle that ticks stages, re-fed a sacrifice each stage, taint per stage. |
| 9 | **Reactive Thorns Charm (Charm Slice 3)** | Living magic | M | med | `CharmDef`, `ActiveMode` (extend +REACTIVE), `CharmTickHandler` economy, `CharmCharge` NBT, pouch find-logic | `LivingHurtEvent` wiring (the deferred Slice 3) | Charm reflects damage on hit, draining a charge per proc — the damage-event charm the pouch spec named. |
| 10 | **Lunar Phase Gating** | Taint | S | med | `WaningMoonRite.midnightOf` pure-time precedent, `RitualActivation.tryPerform`, `RitualRecipe` (+phase field), chalk hover, `ChunkTaintData` (×taint) | none | Full moon = cheaper taint / stronger rites; new moon = costlier — an 8-day "wait for the right night" layer, zero new persistence. |
| 11 | **Crop Mutation (Mutandis)** | Brewing/Crops | M | med | `WitchCropBlock`, `HexereiCrops` RegistryObjects, drop-table pure logic, `getDrops`, `SpawnItemRite` (optional acquisition) | one Mutandis item registration | Bonemeal-like dust re-rolls a planted crop to a random other; chance to discover deferred Mindrake/rarer seeds. |
| 12 | **Poppet Protection (Death/Pain effigy)** | Living magic | M | high | `RitualActivation`+new rite, `SpawnItemRite` drop pattern, `AltarPowerManager` debit, `ChunkTaintData.addTaint`, item NBT, GameTest arena | **`Rite.perform` player-handle** (to capture binder UUID) | A poppet bound to you eats one lethal hit and shatters — cheating death dirties the chunk where it triggered. |
| 13 | **Cursed & Blessed Brands** | Brewing/Taint | M | med | `BrewRecipes`+`Brews` (2 new), **first custom `MobEffect`**, `CharmTickHandler`/`CharmDefs` suppress/boost hook, `ChunkTaintData.decayTick` (local cleanup), altar debit | live `BrewEffect`; first custom MobEffect | Curse = taint magnetism + charms disabled; Blessing = local taint *decay* + charm boost — a two-way player lever on taint. |
| 14 | **Grimoire Auto-Unlock Progression** | Altar prog. | M | med | `GrimoireItem`/Patchouli, `CauldronBlockEntity.collectBrew` / `RitualActivation` success / `AltarBlockEntity` formation hooks, en_us/ru_ru | guidebook (Patchouli, D1) shipped; custom `CriterionTrigger` | Gate guide entries behind real milestones (first brew/rite/altar) via advancement-locked Patchouli entries. |
| 15 | **Coven Resonance (players amplify a rite)** | Altar prog. | S | med | `RitualActivation` scan (already has pos+level), `RitualCircle` ring predicate, `ChunkTaintData` (split across home chunks), every Rite (opt-in `amplify`, default no-op) | **`Rite.perform` player-LIST handle** | Players in the circle amplify the rite and *split* the taint across their home chunks — group magic, gentler land. |
| 16 | **Scrying Mirror (remote viewing, text readout)** | Living magic | M | med | BlockEntity+NBT (altar pattern), `AltarScreen` client-only Screen via `DistExecutor`, altar debit, `ChunkTaintData` on viewed chunk, loot table | text-vs-live-render decision (text = no dep) | Gaze into a mirror to read a distant claimed location's biome/time/mobs for altar power; scrying taints the *viewed* chunk. |
| 17 | **Voodoo Poppet (affliction effigy)** | Living magic | L | high | cauldron (taglock reagent), altar per-use debit, `ChunkTaintData`, item NBT, `LivingHurtEvent` hooks, GameTest | taglock acquisition path; unloaded-target handling | Bind a poppet to a mob/player; hitting it strikes them at range, paid per-strike in altar power — black magic taints *your* land. |
| 18 | **Taint World Events (the corruption erupts)** | Taint | M | med | `ChunkTaintData.decayTick` loop (already iterates all chunks), `TempestRite.setWeatherParameters`, `WorldTaintAura` burst, `AltarPowerManager.allSources`, `HexereiNetwork` warning | best paired with Cleansing Loop so it's survivable | Cross a taint watermark → a one-shot "Tainted Tempest"/corrupted-witch event near the most-tainted altar; cooldown-gated. |
| 19 | **Coven Power (altars reinforce each other)** | Altar prog. | M | med | `AltarPowerManager.allSources`/range query, `AltarBlockEntity` scale fields, maxPower recompute timing, `ChunkTaintData` (×taint), `RelativePowerSource.isInRange` | **Altar Tiers** (needs the scale fields it amplifies) | 2+ altars in range form a cluster: +recharge/+power scaled by size, but taint multiplies harder — power that corrupts faster. |
| 20 | **The Craft (per-player mastery progression)** | Altar prog. | L | high | `RitualActivation` SUCCESS hook, `collectBrew` success hook, `RitualRecipe` gating field, `ChunkTaintData` (mastery-discounted taint), Grimoire page, actionbar i18n | **player capability / player-SavedData** (none exists yet) | A "craft mastery" score earned by casting/brewing gates heavier rites and softens their taint cost — skill mitigates corruption. |
| 21 | **Spirit World (Astral Shift via the Dreaming)** | Living magic | L | high | `RitualActivation`+new rite, `ChunkTaintData` (read+floor bump), `ClientTaintCache`+`TaintSyncS2CPacket` overlay, **custom MobEffect**, player SavedData, tick expiry | needs taint-consequence + custom MobEffect + player-handle | Sleep into a shadow "astral" state where taint is visible and harvestable as a high-tier reagent; the veil thins where you cross. |

---

## 4. NEXT 3–5 SLICES (recommended shortlist)

Optimized for **high impact × low risk × reuses-what's-built × unblocks-later-work**, favoring already-scoped backlog
items and natural extensions over the ambitious systemic bets. Order matters — the justification calls out the
dependency chains.

### Slice A — **Cleansing Loop: Rite of Purging** (#1, rite half only) — M, high
Build the `removeTaint`/`lowerFloor` primitives on `ChunkTaintData` and a `CleansingRite` (SMALL circle, garlic/snowbell
sacrifice, altar power, **zero taint added**), mirroring `VerdantRite` exactly. **The rite half has no unbuilt dependency**
(verified: `ChunkTaintData` has no removal method today, so this is pure addition), and it is the keystone that converts
taint from a one-way ratchet into a real economy. **Do this first** because almost every other taint idea (Consequence
Ladder counter-play, World Events survivability, Blessed-brand decay, Astral harvest) assumes the player *can* fight
corruption. Defer the *brew* half of #1 until the live `BrewEffect` path lands.

### Slice B — **Taint Consequence Ladder, Rung 1** (#2 / L1 "Tainted Ground") — S, high
Inside `WorldTaintAura.pulse`'s existing 200t loop, make standing on `tainted_ground`/`charred_stone` or sitting in a
MEDIUM+ chunk apply a brief Hunger/Weakness (refreshed each pulse via the `CharmTickHandler` trick). **S-sized, no new
persistence, no new dependency.** Pair it *immediately after* Slice A so taint both bites (B) and can be cleansed (A) in
the same beat — shipping the consequence without the counter-loop would be punitive; shipping the counter-loop without a
consequence would be pointless. Together A+B make the taint spine a closed, playable loop for the first time.

### Slice C — **Ritual-Crafted Charms** (#3, Charm Slice 2) — S, med
Pure wiring once Charms Slice 1 and Rituals-Expansion have both landed (the roadmap sequences exactly that, and ships
`SpawnItemRite` as the bridge). Add one `RitualRecipe` per charm — `SpawnItemRite(() -> new ItemStack(...CHARM))` with a
thematic sacrifice + power cost. **No new Rite class, no new item, no new persistence**; `RitualChalkItem` cycling already
wraps a longer list. Lowest-effort, highest-certainty progression unlock in the backlog — it turns the charm creative-tab
placeholders into earned content. Do it as soon as its two upstreams are in.

### Slice D — **Throwable Splash Brews** (#4) — M, high
A `SplashBrewEntity` (ThrowableItemProjectile) carrying the brew id in NBT; on impact, AABB-sweep and apply each
`MobEffectInstance` via the *same* resolution path drinking uses — so Withering Bile / Frailty become offensive splashes
at zero extra power (the brew was paid for at collection). **Depends only on the live `BrewEffect` path** (the
witch-brew-expansion slice). High impact: it's the first *offensive* use of brewing and reuses the entire brew catalog
for free. Sequence it after the brew-effect path lands; before **Brew Potency Modifiers (#6)** because modifiers multiply
whatever delivery exists, and after delivery is more legible to tune.

### Slice E — **Altar Tiers (Artefacts)** (#5) — M, high
Detect a small set (2–3 to start) of placeable artefact blocks in the existing `AltarBlockEntity.resolveDynamic` scan and
finally raise the **already-declared, already-persisted, already-applied** `powerScale/rechargeScale/rangeScale/
enhancementLevel` fields (verified pinned at 1/0 in DESIGN-NOTES). **No new persistence shape — the NBT and apply paths
exist; nothing ever raised them.** High impact and it's *unblocking infrastructure*: `enhancementLevel` gating is the
seam the heaviest CleansingRite (Slice A) and later MEDIUM/LARGE rites read via `IPowerSource.getEnhancementLevel()`, and
it is the prerequisite for **Coven Power (#19)** (which amplifies these very fields). The only cost is artefact art
(PIL-generated 16×16 or vanilla-parented).

**Dependency chains made explicit:**
- **A before B** (and before #13/#18/#21): a consequence with no cure is punitive; ship the cure-and-bite pair together.
- **Charms Slice 1 + Rituals-Exp before C** (roadmap-confirmed): `SpawnItemRite` and `CharmItem` must both exist.
- **live `BrewEffect` before D and #6**: both are no-ops until brews apply real effects; **D before #6** (deliver, then multiply).
- **E before #19 (Coven Power)**: the coven bonus writes into the scale fields E activates; without E there is nothing to amplify.
- **`Rite.perform` player-handle before #7/#12/#15/#21**: one signature change unblocks the entire Living-magic cluster — #7 (Familiar) is the cheapest, most-natural first consumer to justify it.

---

## 5. Big swings (later — keep on the radar)

These are the ambitious systemic bets. Each is high-impact but carries a real new dependency (player-attached persistence,
the `Rite.perform` refactor, a custom MobEffect, or a new BlockEntity) — defer until the cheap, high-certainty slices
above have hardened the spine.

- **The `Rite.perform` player-handle refactor** — not a feature itself but the *enabling* change for the entire
  **Living magic** theme (#7 Familiar, #12 Poppet, #15 Coven Resonance, #21 Astral). Cheapest justification to finally do
  it: **#15 Coven Resonance** (pure count/split math, fully unit-testable, no runtime) or **#7 Familiar** (natural,
  user-facing). Do the refactor once, behind the first of these.
- **#8 Multi-Step Timed Rituals (LARGE circle + ritual BlockEntity)** — introduces the deferred circle BlockEntity and
  the first non-instant rite; the gateway to staged/sacrificial rituals but a genuine L-sized subsystem.
- **#20 The Craft (per-player mastery)** — needs the project's first **player-attached persistence** (capability or
  player-keyed SavedData; current persistence is chunk/BE/item NBT only). Sits exactly between the two spine systems
  (earned by spending power, spends down taint cost) — worth building once the persistence shape exists for it and #21.
- **#21 Spirit World (Astral)** and **#13 Cursed/Blessed Brands** — both need the **first custom MobEffect** registration;
  #21 also needs the taint-consequence layer (A+B) and the player-handle. Strong thematic payoff, but stack them after the
  custom-MobEffect pattern is proven (do #13 first as the simpler custom-effect proving ground).
- **#17 Voodoo Poppet** and **#16 Scrying Mirror (live render)** — the most ambitious "black magic" surfaces; #17 needs a
  taglock acquisition path and unloaded-target handling, #16's *live* remote render is a real scope risk (ship the text
  readout #16 first, treat live render as a separate stretch).
- **#18 Taint World Events** and **#19 Coven Power** — the emergent end-state of the taint+altar spine (over-casting
  erupts; clustered altars corrupt faster). Best built *after* A (so events are survivable) and E (so coven has scale
  fields to amplify) respectively — the payoff of the whole system, not its foundation.
