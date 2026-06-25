# Phase 2–3 — Economy + Ritual migration onto the vector core

Derived from the WARRANTLY conformance audit. Phase 1 (the `soul/` vector core: `Act`,
`Bond`, `Correspondence`, `Integration`, `Decay`, `Resolution`, `PlayerSoulData`,
`ChunkSoulData`) is built, tested (204 tests), and committed. This spec converts the live
mod onto it.

## Litmus verdict (WARRANTLY gate)
PASS. Replacing free `AltarPower` with `essence` sourced only from release-Acts, and making
ritual success a function of `Act`/`State`, *is* the Article III loop made real. **Hard
constraint:** every effect must carry a cost (more power ⇒ more debt/disturbance). No effect
may grant free power (Art. II.2). All `State` writes go through `Integration` (Грамматика §2).

## Slices (ordered; each ends green — pure-logic JUnit first, then wiring + GameTest)

**A. Reagent → Act assembly (pure, ADDITIVE, no breakage).**
- `ReagentDescriptor` record: `{domain: Correspondence, reciprocity, binding, defilement, magnitude}`.
- `ReagentRegistry` keyed by item id (seed from current ritual sacrifices + herbs).
- `ActAssembler.assemble(List<ReagentDescriptor>) -> Act` (wraps `Act.sum`).
- JUnit: mixing reagents yields the expected summed `Act`. Touches nothing live.

**B. Essence economy (replace AltarPower source).**
- `PlayerSoulData.essence` already exists. Add essence *sourcing*: harvesting/felling/sacrificing
  a tagged block emits a release-Act (reciprocity<0) → `essence += k·mag·w`, and in the SAME op
  `disturbance[domain] += …` + `debt`. Delete `AltarBlockEntity` passive proximity scan + timer
  recharge. Reframe `IPowerSource.consumePower` as essence access over `PlayerSoulData`, keeping
  atomic-debit-before-input. JUnit on the sourcing math; GameTest on harvest→essence.

**C. Per-domain disturbance (replace scalar taint).**
- Migrate `ChunkTaintData` users (RitualDestruction, BloodMoonPulse, WorldTaintAura,
  TaintPunishment, `Rites.addRitualTaint`) onto `ChunkSoulData.disturbance[domain]` (already built,
  tested). Widen `TaintSyncS2CPacket` + `ClientTaintCache` to per-domain. Keep the asymmetric floor.

**D. Ritual activation reads Act + resolves via state.**
- `RitualActivation`: build the `Act` from the circle's reagents (Slice A) + rune domains (later),
  resolve with `Resolution.success/classify` (Phase 1), pay in essence, integrate debt+disturbance.
  Replace `SUCCESS/NO_RECIPE/NO_POWER` so a sub-threshold cast STILL resolves (consumes inputs,
  writes the loop), never a free no-op. `Rite.perform` gains a caster handle.
- Replace `maybeIgniteBloodMoon` dice with a `disturbance/totalDebt` threshold crossing.

**E. Runes → domain, Altar → ritual modulation (the user-facing payoff).**
- Rune symbol (18 glyphs, 3 per domain) contributes its `Correspondence` to the ritual `Act`.
- Altar-placed artefacts modulate the resolved `Act` AT A COST (amplify magnitude ⇒ more debt).

## Notes
- Build/test per `hexerei/CLAUDE.md` (JAVA_HOME=jdk17, `./gradlew --no-daemon test|build`).
- Each slice is independently committable. Checkpoint with the user after each.
- `LunarPhase` already conforms — repoint its mul onto `Act.magnitude` opportunistically in D.
