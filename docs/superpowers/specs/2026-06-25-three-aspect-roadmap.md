# Hexerei — Three-Aspect Implementation Roadmap (Charms · Rituals · Guidebook)

**Date:** 2026-06-25
**Synthesizes:**
- Charm Pouch build plan — [`2026-06-25-charm-pouch-buildplan.md`](2026-06-25-charm-pouch-buildplan.md) (design: [`2026-06-23-charm-pouch-design.md`](2026-06-23-charm-pouch-design.md))
- Rituals Expansion — [`2026-06-25-rituals-expansion-design.md`](2026-06-25-rituals-expansion-design.md)
- Guidebook ("Grimoire") — [`2026-06-25-guidebook-design.md`](2026-06-25-guidebook-design.md)

**Purpose:** sequence three independently-designed slices into one buildable plan, surface the cross-aspect
shared-file collisions, and name the one decision that blocks the whole thing. This document changes **no
design and no balance number** — it only orders the work and flags handoff gaps. Each per-aspect spec remains
the source of truth for its own mechanics.

---

## Premise

Three specs landed the same day. They are not independent in build terms:

- **Charms** (Slice 1) is mostly *new files* in a fresh `charm/` package — self-contained, ready spec, TDD-first.
- **Rituals Expansion** adds 4 rites, one of which — **`SpawnItemRite`** — is the *generic bridge* that the
  Charm-Pouch Slice 2 explicitly needs ("ritual crafting: sacrifice + power → a charm item"). `SpawnItemRite`
  ships here spawning **ritual chalk** (an already-registered item) to prove the mechanism end-to-end; wiring it
  to a *charm* supplier is a one-line `RitualRecipe` that depends on `CharmItem` existing.
- **Guidebook** documents *whatever the mod ships*. It is most valuable **last** (or incrementally, a category
  at a time), and it is the only aspect gated on a hard owner decision (the Patchouli runtime dependency).

So the dependency graph is: **Charms → (enables charm-spawning) Rituals → Guidebook documents all three.**
The roadmap below makes that graph explicit and de-conflicts the handful of shared files all three touch.

---

## Recommended Build Order (and why)

### Order: **1) Charms → 2) Rituals Expansion → 3) Guidebook (last, or incremental).**

| # | Aspect | Why here | Gated on |
|---|---|---|---|
| 1 | **Charm Pouch (Slice 1)** | Ready-to-hand build plan, TDD-first, almost entirely **new files** in a new `charm/` package. Touches the fewest *existing* files of the three and touches them additively (4 item registrations, 1 tab block, 1 constructor line, 1 tick-hook line, 1 client-setup method). Its pure logic (`CharmDef/CharmDefs/CharmCharge`) compiles and unit-tests with **no Minecraft runtime** — fastest path to a green first slice. Produces the `CharmItem`s that Rituals' charm-crafting bridge will later reference. | Three Forge-1.20.1 menu-API confirmations (see Decisions D2). None block *starting* — they surface at compile time of Step 7. |
| 2 | **Rituals Expansion** | Extends an existing, proven subsystem (`ritual/`) with **no new networking, BlockEntity, or SavedData**. Its `SpawnItemRite` is the bridge that lets *future* rituals manufacture charms — building it **after** charms means the charm-supplier recipe (`() -> new ItemStack(HexereiItems.WARD_CHARM.get())`) is a trivial add-on rather than a forward-reference to a non-existent item. This slice ships `SpawnItemRite` spawning **ritual chalk** to exercise the machinery without depending on charms; the charm-supplier `RitualRecipe` is then a clean follow-up once charms exist. | Decision D3 (TEMPEST/VERDANT sacrifice collision) and D4 (MEDIUM lang label) — both are recommended-default, low-risk; do **not** block starting. |
| 3 | **Guidebook (Grimoire)** | It documents what exists. Building it last means its `crafting` pages (`altar_placeholder`, `cauldron`) and its rite/brew prose reference **shipped, registered** ids — no risk of documenting vapor. The `charms` category is a single **"preview / not yet craftable"** entry regardless of order, so the guide does not *need* charms built first; but writing it last lets the preview entry be replaced with real charm entries in the same pass if desired. **Gated on the top decision (Patchouli dependency).** | **D1 (Patchouli vs custom Screen)** — a hard owner call that must be made **before this aspect starts**, plus D5/D6 (recipe-id and book-item confirmations, already verified below). |

### Incremental variant (recommended if the owner wants the guide sooner)

The guidebook is **category-decomposable**. Once D1 is resolved, the Altar / Herbs / Cauldron / Rituals
categories can be authored **immediately** (all that content already ships today), and the **Rituals category
gains its new rite entries** only after Aspect 2 lands, and the **Charms category** stays a one-page preview
until Aspect 1's charms are craftable (Slice 2). Practical cadence:

- After Aspect 1: nothing new for the guide yet (charms are creative-tab only; the preview entry already covers them).
- After Aspect 2: the `rituals` category can document **5 rites** instead of 1 — but only if D2 (lang label) and
  D1 are settled. Until then, keep the single TEMPEST entry the guidebook spec already grounds.
- Author Altar/Herbs/Cauldron categories any time after D1 — they are stable and need none of the other aspects.

**Why not guide-first:** the guide's value is "document the shipped mod." Authoring rite/charm entries before
those features exist risks documenting designs that shift during implementation (e.g. the VERDANT sacrifice
change in D3 would invalidate a pre-written Verdant page). Guide-last (or guide-incremental-trailing) keeps
docs truthful — matching the project's "every entry grounded in a real registry object" rule.

---

## Shared-File Collisions (do these sequentially, never in parallel)

Multiple aspects edit the same files. Editing them concurrently (e.g. two parallel subagents) **will clobber**.
The project's `superpowers:subagent-driven-development` flow is the right vehicle: dispatch the new-file work in
parallel **per aspect**, but funnel every shared-file edit through a **single serialized step per file**, in
build order (Charms edit → Rituals edit → Guidebook edit). Re-read each file immediately before editing
(collision-awareness convention) — file state changes between turns when slices land back-to-back.

| Shared file | Charms edit | Rituals edit | Guidebook edit | Sequencing rule |
|---|---|---|---|---|
| `registry/HexereiItems.java` | +4 `RegistryObject<Item>` (pouch + 3 charms) | none (rites are plain Java; reuse existing item ids) | +1 (`GRIMOIRE`) | Two writers (Charms, Guidebook). Land Charms' 4 first, then append `GRIMOIRE` — both are additive `ITEMS.register(...)` lines; keep alphabetic/grouped placement, no reordering. |
| `registry/HexereiCreativeTabs.java` | +4 `output.accept(...)` (charms, full-charge) | none | +1 `output.accept(GRIMOIRE)` (spec wants it **first** in the tab — a manual at the top) | Two writers. Guidebook wants `GRIMOIRE` as the **first** `accept`; Charms append theirs after existing items. Apply Guidebook's insert and Charms' appends in one reconciled edit when the guide lands, so the "grimoire first" intent isn't undone. |
| `assets/hexerei/lang/en_us.json` | +6 keys (pouch/charms/container/dormant) | +6 keys (4 rite names + 2 circle labels) | +2 keys (grimoire item + landing) | **Three writers** — highest-collision file. Flat JSON object; each aspect appends before the closing `}`. Serialize the three edits; after each, run a JSON-validity lint (the guidebook spec's step-1 lint) so a stray comma is caught immediately. |
| `assets/hexerei/lang/ru_ru.json` | +6 keys | +6 keys | +2 keys | Same as en_us. **Parity rule:** every en_us key added must get its ru_ru counterpart in the **same** slice (the mod's dual-lang invariant). Do not let a slice land EN-only. |
| `HexereiMod.java` (constructor) | +1 `HexereiMenus.MENUS.register(modBus);` (after `HexereiParticles`, before `HexereiNetwork.register()`) | none | none | Single writer (Charms). Confirmed insertion point exists: constructor currently registers BLOCKS→ITEMS→BLOCK_ENTITIES→TABS→PARTICLES then `HexereiNetwork.register()`. No collision. |
| `HexereiLevelEvents.java` (`onLevelTick`) | +1 `if (gt % 20 == 0) { CharmTickHandler.tick(sl); }` reusing the existing `gt` | none (rites fire from `RitualActivation`, not the tick loop) | none (optional first-join grant is a *separate* `PlayerLoggedInEvent` subscriber, not this file) | Single writer (Charms). Reuse the existing `gt` and `ServerLevel sl`; do **not** add a second tick subscriber. |
| `client/HexereiClient.java` | +1 `onClientSetup` calling `MenuScreens.register(...)` inside `enqueueWork` | none | none (Patchouli is server-initiated; Option B custom Screen would add a client opener here — only if D1 picks B) | Single writer for Aspect 1. If D1 → Option B, Guidebook becomes a second writer here; with Option A (recommended) it stays single-writer. |
| `ritual/RitualRecipes.java`, `RitualCircle.java`, `CircleSize.java`, `RitualChalkItem.java`, `CycleRiteC2SPacket.java` + ritual tests | none | all edits live here | none | Single aspect (Rituals) — no cross-aspect collision, but **internally** sequence per the rituals spec (pure geometry + `RitualRecipesTest` green before wiring rites into `ALL`). |
| `charm/*`, `item/CharmItem.java`, `menu/`, `client/CharmPouchScreen.java`, `registry/HexereiMenus.java`, `test/CharmPouchGameTests.java` | all new files (Charms) | none | none | Single aspect — fully isolated new package; safe to build in parallel with Rituals' new `Rite` classes since they share no file. |

**Parallelism that IS safe:** the *new-file* bodies of Aspect 1 (charm package) and Aspect 2 (rite classes +
pure geometry) share **zero files** and can be authored concurrently by separate subagents. Only the shared
table above must be serialized. The guidebook's *data tree* (`patchouli_books/grimoire/**`) is entirely new
files and collides with nothing except the lang/items/tabs rows above.

---

## Top Decision (make this FIRST) and the others

### D1 — TOP, BLOCKING: Patchouli dependency (Option A) vs custom guide Screen (Option B).

This is an **owner-level** call because Option A adds the mod's **first mandatory runtime dependency**
(`mandatory=true` in `mods.toml`). The guidebook spec recommends **A** (owner already ships Patchouli on the
same server, already fluent in its JSON, gets paging/recipe-pages/cross-links for free). **Aspect 3 cannot start
until this is decided** — A and B diverge at the very first file (dependency vs `GrimoireScreen` + `DistExecutor`
client opener). Sub-confirmations that only matter under Option A, and that **must resolve a real published file
before the build will compile**, are both currently `[UNVERIFIED]` placeholders:
  - the exact Patchouli 1.20.1-Forge **CurseMaven file id** (project `306770`) *or* the modmaven coordinate, and
  - the `mods.toml` `versionRange` lower bound (must align with the pinned build).

Decide A-vs-B first; if A, pin a real file id before handing Aspect 3 to implementation.

### Other decisions (do NOT block starting their aspect; recommended defaults given)

| ID | Aspect | Decision | Recommended default | Blocks? |
|---|---|---|---|---|
| **D2** | Charms | Forge-1.20.1 menu API surface: `ForgeRegistries.MENU_TYPES` for `DeferredRegister`; `IForgeMenuType.create` import path; 3-arg `NetworkHooks.openScreen(player, provider, buf-writer)`. | Spec's reading is the standard 1.20.1 API; **verify at compile time** of Step 7. | No — surfaces at compile, not at start. |
| **D2b** | Charms | Charms spawn **full charge** (absent `hexerei:Charge` = `MAX_CHARGE`) vs empty. | **Full charge** — required by the "fully-charged Ward Charm" GameTest; absent-tag-means-full needs no creative-tab post-processing. Confirm intent. | No. |
| **D2c** | Charms | `CharmPouchNbtTest` in the no-MC JUnit tier (`ItemStackHandler`/`ItemStack` touch MC classes). | If they don't load headless, move the NBT round-trip into the **GameTest** and unit-test only an extracted **pure** charge/def helper. Resolve before writing test #20. | No (only gates test #20). |
| **D3** | Rituals | TEMPEST and VERDANT both key on SMALL + `mandrake_root`, so `match` returns TEMPEST first. | **Switch VERDANT's sacrifice to `hexerei:artichoke`** to remove the collision; otherwise SMALL+mandrake always fires Tempest. | No — but decide before writing `RitualRecipesTest`'s match assertions and the VERDANT recipe line, or the test pins the collision as "intended." |
| **D4** | Rituals | MEDIUM circle lang label: pick `circle.small`/`circle.medium` by `CircleSize` (clean) vs. one hardcoded "Small" (cosmetic mislabel on the 20-glyph ring). | **Pick by `CircleSize`** (tiny mechanical change in `RitualChalkItem.appendHoverText` + `CycleRiteC2SPacket.handle`); ships the two new lang keys. | No (cosmetic). |
| **D4b** | Rituals | MEDIUM ring reads as a circle (20-position rounded outline) vs. fuller 24-position square shell. | `[UNVERIFIED]` until in-game visual check; ship 20 and confirm at `runClient`. | No (visual polish). |
| **D4c** | Rituals | Bound-Beast wolf is **un-owned** (no player handle in `Rite.perform`). | Ship un-owned + `setPersistenceRequired()`; defer owner-binding to a follow-up that changes the `Rite` signature. | No. |
| **D5** | Guidebook | Altar/cauldron crafting-page recipe ids. | **Verified below** — `altar_placeholder.json` and `cauldron.json` both exist under `data/hexerei/recipes/`. Both pages are valid as written. | No — resolved. |
| **D6** | Guidebook | Register own `GrimoireItem` vs rely on Patchouli's auto-item. | **Own `GrimoireItem`** — matches every other Hexerei item's creative-tab + recipe wiring. | No (only under Option A). |
| **D7** | Guidebook | Ship on-first-join book grant now vs keep off-by-default. | **Off by default** (recipe + creative tab only); first-join grant is a documented follow-up toggle. | No. |

---

## Spec-Readiness for `mc-mod-implement` (handoff gaps)

| Aspect | Concrete enough to hand directly? | Gap / precondition |
|---|---|---|
| **Charm Pouch (Slice 1)** | **Yes — build-ready.** The build plan is a numbered 12-step TDD sequence with full file paths, exact constants (single-sourced in `CharmCharge`), the tricky menu-API laid out verbatim, and named reference points in the existing code (`CauldronBlockEntity.collectBrew`, `CauldronGameTests` `FakeAltar`, `Brews`, `BrewItem`). | Only the D2 compile-time API confirmations and the D2c test-tier question — both resolvable *during* implementation, neither blocks starting. Hand directly to `mc-mod-implement`. |
| **Rituals Expansion** | **Yes — build-ready.** Pure geometry table (20-entry MEDIUM ring) is fully specified and unit-test-pinned; all 4 `perform()` bodies use concrete 1.20.1 APIs; `RitualRecipes.ALL` order, balance numbers, taint formula (`powerCost/4`), and the full unit+GameTest plan are given. Reuses existing `RitualActivation`/chalk/`ChunkTaintData` with **no new networking**. | Resolve **D3** (VERDANT sacrifice) before writing the recipe line + match test, and **D4** (lang label) before the chalk hover edit. Both have a recommended default; neither blocks starting the pure-geometry work. Hand directly once D3 is answered. |
| **Guidebook (Grimoire)** | **Conditionally — gated on D1.** Content tree is fully grounded (5 categories, every entry maps to a real registry object/brew/rite, recipe ids **verified to exist**). Under **Option A** it is build-ready *once a real Patchouli file id is pinned*. Under **Option B** the spec only sketches the custom `GrimoireScreen` (it's the rejected alternative) and would need a fuller paging/layout design before implementation. | **D1 is a hard precondition.** With D1→A + a pinned file id + D6 (own `GrimoireItem`), hand directly. With D1→B, route back through `mc-mod-ideate`/design for the Screen before `mc-mod-implement`. The `[UNVERIFIED]` Patchouli file id and `versionRange` are the only items that will hard-fail the build if left as placeholders. |

---

## Out of Scope (this roadmap)

- **Charm Slice 2/3** (ritual-crafted charms via `SpawnItemRite` charm-supplier recipe; reactive/thorns charms)
  — Slice 2 becomes a one-line `RitualRecipe` add *after* both Aspect 1 and Aspect 2 land; tracked as a follow-up.
- **Owner-aware Bound Beast** — deferred (needs a `Rite.perform` signature change).
- **First-join grant, advancement gating, auto-generated brew/rite recipe pages, multiblock altar preview** —
  all explicitly cut by the guidebook spec.
- **Any balance re-tuning** — every number here is carried verbatim from its source spec.
- This roadmap does **not** itself modify source, build files, or assets — it is a sequencing document only.
