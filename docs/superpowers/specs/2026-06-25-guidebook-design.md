# Hexerei — In-Game Guide Book ("Grimoire of the Hedge-Witch")

**Date:** 2026-06-25
**Extends:** new item + data-pack subsystem; documents existing slices (Altar, Herbs, Cauldron/Brewing, Ritual Circles) and forward-references the Charm Pouch slice. Adds the mod's first **runtime dependency decision** (Patchouli).

## Premise

The player crafts (or is granted on first join) a single book item, the **Grimoire**, that opens an in-game guide teaching the whole mod: how the **Altar** forms and powers magic, how to grow the **herb crops**, how to brew in the **Witch's Cauldron**, how to lay a **Ritual Circle** and run a rite, and — as a teaser entry — the upcoming **Charm Pouch**. The guide is content, not mechanics: it changes no game balance and adds no power sink. Its job is discoverability, because nothing in the mod is craft-table-obvious (the altar forms from `onPlace`, the cauldron needs a tagged heat source, brews match on item-id multisets, rites need a glyph ring + sacrifice + altar power).

The owner already maintains a Patchouli translation pack (`RU-Mod-Guides/`) for Ars Magica + Blood Magic and runs real Patchouli books on the server (`data/patchouli_books/`), so the Patchouli JSON format (`book.json` → categories → entries → typed pages) is a known, proven quantity for this project. That makes Patchouli the strong default; this spec recommends it and lays out the lightweight-custom alternative honestly.

**This spec is content-complete only against what the mod ships today.** Every entry below is grounded in a real registry object, brew, or rite (inventoried from `HexereiBlocks`, `HexereiItems`, `HexereiCrops`, `Brews`, `BrewRecipes`, `RitualRecipes`). The Charm Pouch entry is explicitly marked "preview / not yet craftable" because that slice (`2026-06-23-charm-pouch-design.md`) is designed but not built.

---

## Decision: Patchouli dependency (A) vs custom guide Screen (B)

### Recommendation: **(A) Add Patchouli as a real Forge 1.20.1 dependency.**

Rationale: the owner is already fluent in Patchouli JSON, already ships Patchouli books on the same server, and Patchouli gives — for free — the page types this mod needs (`text`, `crafting`, `spotlight`, `image`, `entity`, `relations`, plus its `$(l:...)` cross-links and `$(item)`/`$(thing)` formatting). Re-implementing even a fraction of that in a custom Screen is throwaway work. The mod's own convention ("reuse existing art/tooling where possible, don't invent a new mechanism") points the same way.

### Option A — Patchouli (recommended)

**`gradle.properties`** — add a pinned version (so the coordinate isn't buried in `build.gradle`):

```properties
# Patchouli (in-game documentation) — pin a 1.20.1 build. [UNVERIFIED: confirm exact build id on CurseForge/Modrinth]
patchouli_version=1.20.1-84-FORGE
```

**`build.gradle` `repositories {}`** — CurseMaven is the canonical way to pull a CurseForge mod into ForgeGradle:

```gradle
repositories {
    maven {
        name = "CurseMaven"
        url = "https://cursemaven.com"
        content { includeGroup "curse.maven" }
    }
}
```

**`build.gradle` `dependencies {}`** — Patchouli ships an API artifact (compile against the API, run the full jar). CurseMaven coordinate form is `curse.maven:<slug>-<projectId>:<fileId>`; Patchouli's CurseForge project id is **`306770`**:

```gradle
dependencies {
    // Patchouli — in-game guide book engine. [UNVERIFIED: fileId must be a real 1.20.1-Forge build file id from CurseForge]
    implementation fg.deobf("curse.maven:patchouli-306770:<FILE_ID>")
}
```

> If a Maven-coordinate build is preferred over CurseMaven, Patchouli is also on the **VazkiiMods / blamejared modmaven** repo as `vazkii.patchouli:Patchouli:1.20.1-<build>-FORGE`. Either repo is acceptable; CurseMaven matches how the owner already sources server mods. **Both the repo and the exact build id are `[UNVERIFIED]` and MUST be confirmed against a live CurseForge/Modrinth 1.20.1-Forge file before the dependency will resolve** — do not guess a `<FILE_ID>`.

**`META-INF/mods.toml`** — add a dependency block so the load order is right and the book registers after Patchouli. The existing file is the MDK template with `[[dependencies.${mod_id}]]` for `forge`; append a second block:

```toml
[[dependencies.hexerei]]
    modId="patchouli"
    mandatory=true
    versionRange="[1.20.1-84,)"   # [UNVERIFIED: align lower bound with the pinned build]
    ordering="AFTER"
    side="BOTH"
```

`mandatory=true` is correct because the whole feature is the book; if Patchouli is absent the mod has no guide and should say so loudly at load rather than silently degrade. (If a "soft" guide is wanted later, flip to `mandatory=false` + `ordering="AFTER"` and null-guard the book registration — out of scope here.)

**Cost of A:** one extra runtime dependency (~small jar, already on the owner's server), and the dev `runClient`/`runGameTestServer` runtime classpath must include Patchouli (the `fg.deobf` dependency handles this automatically).

### Option B — Custom guide-book item + `Screen`

A `GrimoireItem` whose `use()` opens a client-only `GrimoireScreen` (`@OnlyIn(Dist.CLIENT)`, reached via `DistExecutor`, registered in `HexereiClient` — exactly the pattern the read-only `AltarScreen` already uses, so **no menu/packet is needed**). Content would be a hand-rolled list of pages (text + an item render + an optional recipe widget) backed by lang keys.

| | **A — Patchouli** | **B — Custom Screen** |
|---|---|---|
| Dev cost | low (JSON only; engine exists) | high (paging, layout, rich text, recipe rendering all hand-built) |
| Runtime deps | +1 (Patchouli) | 0 |
| Cross-links / `$(l:)` / formatting | built-in | must build |
| Recipe pages auto-pulling from the recipe registry | built-in | must build |
| Localization | per-lang entry folders (proven in `RU-Mod-Guides/`) | flat lang keys (simpler, but no structure) |
| Owner familiarity | high (already ships Patchouli books) | n/a |
| Risk if dep unavailable | book gone unless `mandatory=false` | none |

**Honest tradeoff:** B's only real win is "zero dependencies." Given the owner's existing Patchouli investment and that the server already loads it, that win is small and the engineering cost is large. **Choose A.** The rest of this spec is written for A; a B-port would reuse the same content tree as a Java page list and is noted where it diverges.

---

## Registry Objects

| Kind | id | Notes |
|---|---|---|
| Item | `hexerei:grimoire` | The book item. With Patchouli, register as `new ItemModBook()`-style stub **or** simply a `patchouli:book` reference — Patchouli auto-creates a book item when `book.json` sets `"creative_tab"` / is given an item. Simplest path: register a plain `Item` whose `use()` calls `PatchouliAPI.get().openBookGUI((ServerPlayer) player, BOOK_ID)`. `BOOK_ID = new ResourceLocation("hexerei","grimoire")`. |
| Data | `hexerei:grimoire` (book) | `data/hexerei/patchouli_books/grimoire/book.json` — book root (name, landing text, model, version, macros). |

Registry footprint is intentionally tiny: **1 item** + the book data tree. Patchouli supplies the GUI, paging, and rendering — no `MenuType`, no packet, no BlockEntity, no `SavedData`.

> **Patchouli book-item nuance:** Patchouli can render the book item itself from the book JSON (`"model": "patchouli:book_brown"` or a custom model). The cleanest registration that stays inside Hexerei conventions is a `GrimoireItem extends Item` that opens the book in `use()` via the Patchouli API guarded by `DistExecutor`/`ServerPlayer` check, and a `models/item/grimoire.json` that parents a Patchouli book model or uses our own 16×16 texture. Confirm at implementation time whether to rely on Patchouli's auto-item or our own item — **both work; spec recommends our own `GrimoireItem`** so the creative-tab + recipe wiring matches every other Hexerei item.

---

## Content Tree (grounded in shipped content)

Layout note: Patchouli reads localized content from `…/patchouli_books/grimoire/<lang>/{categories,entries}/…`. We author the **`en_us`** tree as source of truth and the **`ru_ru`** tree as the translation (the owner's established workflow — see `RU-Mod-Guides/` which is *only* the localized override tree). `book.json` itself is **not** localized (lives at the book root).

### Categories (5) — `…/grimoire/en_us/categories/*.json`

| file | name | icon | sortnum |
|---|---|---|---|
| `altars.json` | The Altar | `hexerei:altar` | 0 |
| `herbs.json` | The Garden | `hexerei:mandrake_root` | 1 |
| `brewing.json` | The Cauldron | `hexerei:cauldron` | 2 |
| `rituals.json` | Rites & Circles | `hexerei:ritual_circle` | 3 |
| `charms.json` | Charms (Preview) | `minecraft:leather` | 4 |

Category JSON shape (matches the Blood Magic reference exactly):
```json
{ "name": "The Altar", "description": "...intro with $(l:hexerei:altars/forming)links$(/l)...", "icon": "hexerei:altar", "sortnum": 0 }
```

### Entries & pages

Page types used (all stock Patchouli): `patchouli:text`, `patchouli:crafting` (vanilla recipe id), `patchouli:spotlight` (highlight an item with prose), `patchouli:image`. No custom page types — keeps the build pure-JSON.

#### Category: **The Altar** (`altars`)
- **`landing.json`** — *"The Hedge-Witch's Altar"* — entry flagged `"priority": true` so it sorts first. Pages:
  1. `text` — what the altar is; that **placing the altar block forms the multiblock automatically** (`onPlace`), no GUI ritual needed. (Grounded: `AltarBlock`, DESIGN-NOTES "Formation trigger".)
  2. `spotlight` — `hexerei:altar` — right-click opens the **read-only power GUI**; re-right-click to **rescan** power after changing nearby nature blocks (grounded: DESIGN-NOTES "Power scan cadence").
  3. `crafting` — `"recipe": "hexerei:altar_placeholder"` (the real shipped recipe id; the authentic recipe is disabled — `altar_authentic.json.disabled`). [UNVERIFIED: confirm the placeholder recipe id matches the data file name.]
- **`power.json`** — *"Drawing Power"* — pages:
  1. `text` — power comes from **nature blocks around the altar**; crops contribute **4/20**, flowers **4/30** (grounded: DESIGN-NOTES "Altar synergy" + "Flower power"). Power is spent by brews and rites; a failed power check leaves inputs intact.
  2. `text` — power **recharges over time** (floored to int every 20 ticks) and is rescanned only on placement / right-click.

#### Category: **The Garden** (`herbs`) — one entry per shipped crop (8) + an intro
- **`growing.json`** — *"Tilling the Witch-Garden"* (priority):
  1. `text` — crops plant **only on farmland** like vanilla, don't stack; need **light ≥ 9**; bonemeal works (grounded: DESIGN-NOTES "Herbs slice").
  2. `text` — two exceptions: **Water Artichoke** plants on water; **Wormwood** is a tall self-stacking crop.
- **`belladonna.json`** — `spotlight` `hexerei:seeds_belladonna` + `hexerei:belladonna_flower`; text: mature drops 1 flower + seeds.
- **`mandrake.json`** — `spotlight` `hexerei:seeds_mandrake` + `hexerei:mandrake_root`; note the screaming-mandrake entity is a **future slice** — this version just drops the root (grounded: DESIGN-NOTES "Deferred").
- **`artichoke.json`** — `spotlight` `hexerei:seeds_artichoke` + `hexerei:artichoke`; edible (high nutrition, flagged `[UNVERIFIED]` in code — describe as "very filling").
- **`snowbell.json`** — `spotlight` `hexerei:seeds_snowbell`; drops `minecraft:snowball` + 20% chance `hexerei:icy_needle`.
- **`wormwood.json`** — `spotlight` `hexerei:seeds_wormwood` + `hexerei:wormwood`; tall crop.
- **`wolfsbane.json`** — `spotlight` `hexerei:seeds_wolfsbane` + `hexerei:wolfsbane`; longest grow (age 7).
- **`mindrake.json`** — `spotlight` `hexerei:mindrake_bulb`; bulb is both seed and produce.
- **`garlic.json`** — `spotlight` `hexerei:garlic`; both seed and produce.

> All eight crop blocks (`belladonna, mandrake, artichoke, snowbell, wormwood, mindrake, wolfsbane, garlic`) and their seed items are confirmed registered in `HexereiCrops`.

#### Category: **The Cauldron** (`brewing`)
- **`cauldron.json`** — *"The Witch's Cauldron"* (priority):
  1. `text` — fill with a **water bucket**; heat from a block in the **`hexerei:cauldron_heat_sources` tag** below (fire/soul fire/lava/magma/lit campfire); **boils after 100 ticks**; needs a **nearby altar** with power (grounded: DESIGN-NOTES "Witch's Cauldron").
  2. `crafting` — `"recipe": "hexerei:cauldron"`. [UNVERIFIED: confirm a cauldron crafting recipe data file exists; if not, omit this page or add the recipe.]
  3. `text` — throw herb **ItemEntities** into the boiling water; only ingredients that progress a recipe are absorbed; right-click a **glass bottle** to collect the brew, or an **empty bucket** to rinse a wrong mix back to a water bucket.
- **`brews.json`** — *"Recipes of the Cauldron"* — one **page per shipped brew** (6), each a `spotlight` on `hexerei:brew` with prose listing its **exact ingredients, power cost, and effects** (all taken verbatim from `BrewRecipes` + `Brews`):

  | brew | ingredients (from `BrewRecipes`) | power | effects (from `Brews`) |
  |---|---|---|---|
  | Sleeping Draught | mandrake root + belladonna flower | 50 | Slowness II 20s, Blindness I 10s |
  | Brew of Frailty | wolfsbane + wormwood | 30 | Weakness II 30s, Mining Fatigue I 15s |
  | Witch's Sight | icy needle + artichoke | 40 | Night Vision 90s, Water Breathing 90s |
  | Bloodwort Tonic | mandrake root + wolfsbane | 60 | Strength I 60s, Resistance I 30s |
  | Hag's Swiftness | artichoke + wormwood | 40 | Speed II 90s, Jump Boost 90s |
  | Withering Bile | belladonna flower + icy needle | 30 | Poison II 10s, Wither I 5s |

  > Durations converted from the tick counts in `Brews.java` (e.g. `400t = 20s`). Because the brew item's identity is NBT-driven, these pages use prose, not auto-recipe widgets (Patchouli has no built-in cauldron recipe type).

#### Category: **Rites & Circles** (`rituals`)
- **`circles.json`** — *"Laying a Circle"* (priority):
  1. `text` — a **Ritual Circle** center block ringed by **Ritual Glyph** chalk; right-click the center to perform a rite. A **small circle** is **12 glyphs in a radius-2 ring** on the center's Y-layer (grounded: DESIGN-NOTES "Circle geometry"). Glyphs need a sturdy face below.
  2. `spotlight` — `hexerei:ritual_chalk` — **Shift+Scroll cycles the selected rite**; the chalk tooltip shows the current rite and required glyph count (grounded: `RitualChalkItem`, lang keys `ritual_chalk.rite/.circle/.tip2`).
  3. `text` — activation order: circle check → nearest **sacrifice ItemEntity** → recipe match → **power debited first** → sacrifice consumed → rite runs. Outcomes: success / no recipe / no power.
- **`tempest.json`** — *"Rite of the Tempest"* — `spotlight` `hexerei:mandrake_root` + text: **small circle + 1 mandrake root + 100 altar power → thunderstorm** (grounded verbatim: `RitualRecipes.TEMPEST`). Mention the visual: an activation burst, charred stone, and added taint (grounded: recent ritual-visuals commit + `tainted_ground`/`charred_stone` blocks).

> Only **one** rite (`TEMPEST`) is registered today (`RitualRecipes.ALL = List.of(TEMPEST)`). The category has exactly one rite entry — no invented rites.

#### Category: **Charms (Preview)** (`charms`)
- **`charm_pouch.json`** — *"The Charm Pouch (coming soon)"* — single `text` page summarizing the designed-but-unbuilt charm system (pouch holds combat charms; each holds a power **charge** that drains while active and recharges **near an altar**; depleted charms go dormant). Page text opens with `$(o)Not yet craftable — preview of a planned feature.$()`. Grounded in `2026-06-23-charm-pouch-design.md`; **flagged so it can't be mistaken for shipped content.** Remove/replace this entry when the Charm Pouch slice lands.

**Entry JSON shape** (every entry follows this, mirroring the Blood Magic reference):
```json
{
  "name": "The Hedge-Witch's Altar",
  "icon": "hexerei:altar",
  "category": "hexerei:altars",
  "priority": true,
  "pages": [
    { "type": "patchouli:text", "text": "..." },
    { "type": "patchouli:spotlight", "item": "hexerei:altar", "text": "..." },
    { "type": "patchouli:crafting", "recipe": "hexerei:altar_placeholder" }
  ]
}
```

---

## Persistence

**None.** The guide is static data + a stateless item. No per-world `SavedData`, no BlockEntity NBT, no per-item NBT. (Patchouli tracks per-player "read" / unlock progress in its own player data; this spec uses no `flag`/advancement gating, so every entry is visible from the start — `"advancement"` keys are omitted on purpose to avoid coupling to an advancement tree that doesn't exist yet.)

**On-first-join grant (optional):** if enabled, set `book.json` `"show_progress": false` and either (a) Patchouli's `"i18n": false` + a vanilla advancement-reward recipe, or (b) a `PlayerEvent.PlayerLoggedInEvent` handler (server-side, in a Forge event subscriber alongside `HexereiLevelEvents`) that grants one `hexerei:grimoire` if a per-player persistent flag (`player.getPersistentData()` under a `hexerei:` key, read null-safely) isn't already set. **Recommendation: ship the recipe + creative tab first; make first-join grant a follow-up toggle** to avoid surprising existing players. The first-join flag, if added, is the *only* piece of persistence in the feature and follows the project's null-safe NBT convention.

---

## Client/Server

- **Rendering & paging:** entirely Patchouli, client-side. Hexerei adds no Screen, no `MenuScreens.register`, no client packet.
- **Opening the book:** `GrimoireItem.use()` on the server calls `PatchouliAPI.get().openBookGUI(serverPlayer, BOOK_ID)` (Patchouli sends its own open-book packet to that client). No entry in `HexereiNetwork` is needed.
- **Dist safety:** the Patchouli API call is server-initiated (takes a `ServerPlayer`), so no `@OnlyIn(Dist.CLIENT)` client class is required in Hexerei. If a B-port (custom Screen) is chosen instead, the open call must go through `DistExecutor` to a `@OnlyIn(Dist.CLIENT)` opener in `HexereiClient`, matching `AltarScreen`.
- **Creative tab:** add `output.accept(HexereiItems.GRIMOIRE.get());` to `HexereiCreativeTabs.HEXEREI.displayItems` (first entry, so it reads like a manual at the top of the tab).

---

## Assets / Data Layout

```
src/main/resources/
  data/hexerei/patchouli_books/grimoire/
    book.json
    en_us/
      categories/{altars,herbs,brewing,rituals,charms}.json
      entries/
        altars/{landing,power}.json
        herbs/{growing,belladonna,mandrake,artichoke,snowbell,wormwood,mindrake,wolfsbane,garlic}.json
        brewing/{cauldron,brews}.json
        rituals/{circles,tempest}.json
        charms/charm_pouch.json
    ru_ru/                      # same tree, translated (owner's established workflow)
      categories/…  entries/…
  data/hexerei/recipes/grimoire.json            # craft the book (see Balance)
  assets/hexerei/
    models/item/grimoire.json                   # item/generated → layer0: item/grimoire (or parent a Patchouli book model)
    textures/item/grimoire.png                  # 16×16 — a witch's grimoire; deep-purple/leather cover. Hand-author or recolor vanilla written_book.
    lang/{en_us,ru_ru}.json                     # + grimoire item name (book content text lives in the entry JSON, not lang)
```

`book.json` (root, not localized) — minimal shape:
```json
{
  "name": "item.hexerei.grimoire",
  "landing_text": "hexerei.book.grimoire.landing",
  "model": "hexerei:grimoire",
  "show_progress": false,
  "creative_tab": "hexerei:hexerei",
  "version": 1,
  "use_resource_pack": true
}
```
> `"use_resource_pack": true` lets the book live under `data/.../patchouli_books/` and be reloaded without a Patchouli reload command during dev — confirm against the installed Patchouli build. `"name"`/`"landing_text"` may be either literal strings or lang keys; using keys keeps EN/RU consistent with the rest of the mod.

**Magenta-texture guard:** the only new texture is `item/grimoire.png`; if `models/item/grimoire.json` points at a missing texture it renders magenta. Verify the texture exists and the model `layer0` path matches before runClient (per project convention). All page `icon`/`item`/`recipe` references point at **already-registered** Hexerei or vanilla ids (inventoried above), so no page can reference a missing item.

---

## Lang implications (en_us + ru_ru)

Only the **item name** and a couple of book-shell keys go in the lang files; **entry/page prose lives in the per-lang entry JSON**, not lang keys (Patchouli convention, and how `RU-Mod-Guides/` is structured).

| key | en_us | ru_ru |
|---|---|---|
| `item.hexerei.grimoire` | Grimoire | Гримуар |
| `hexerei.book.grimoire.landing` | The collected workings of the hedge-witch — altars, the garden, the cauldron, and the rites. | Собрание трудов ведьмы — алтари, сад, котёл и обряды. |

(`itemGroup.hexerei` already exists. The 6 brew names, 8 crop names, all ingredient names, and the rite name already have EN+RU keys — the book's `$(item)`/`$(l:)` references resolve through those existing keys, so **no new lang keys are needed for any referenced content**, only the two book-shell keys above.)

The **`ru_ru` entry tree** is a full translation of the `en_us` entry tree (same filenames, translated `name`/`description`/page `text`). This is the same author-EN-then-translate workflow the owner already runs; the RU entries are required for parity with the existing dual-lang mod (every current key has an RU counterpart).

---

## Balance / justified choices

The book changes no combat or economy numbers; the only "balance" knob is **how the player obtains it**.

- **Recipe = `minecraft:book` + 1 `hexerei:mandrake_root`** (shapeless), yielding 1 `hexerei:grimoire`. Justification: book = the vanilla "documentation" base; mandrake root is the cheapest, most iconic Hexerei ingredient and is itself documented in the guide, so the recipe is thematically self-referential and obtainable as soon as the player has *any* Hexerei crop — i.e. exactly when a guide becomes useful. Cost is deliberately trivial (one guide per playthrough; gating it would defeat its purpose). [Single deliberate choice; flagged for in-game feel check, not `[UNVERIFIED]` in the data-correctness sense.]
- **No advancement/flag gating** — every entry visible immediately. A new player needs the *whole* picture up front; progressive unlocks suit a deep tech tree (Blood Magic), not a 4-mechanic mod. Re-evaluate if the mod grows past ~5 categories.
- **`sortnum` 0–4** follows player progression order (altar → garden → cauldron → rituals → charms-preview), matching the slice build order in DESIGN-NOTES so the book reads as a tutorial.
- **First-join grant: off by default** — avoids force-injecting an item into existing inventories; recipe + creative tab cover discovery. Toggle is a documented follow-up.

---

## Test / Verification Plan

Book content is **not unit-testable** (no pure logic; it's data + a Patchouli render). Verification is therefore a **`runClient` visual check**, plus cheap automated guards on the JSON.

1. **JSON validity (CI-cheap, no MC):** a `./gradlew build` resource-validation pass, or a tiny shell/jq lint over `data/hexerei/patchouli_books/grimoire/**.json`, confirms every file parses and every `"category"`, `"icon"`, `"item"`, and `"recipe"` value is a syntactically valid `namespace:path`. This catches typos before runtime. *(Not a JUnit test — there is no pure logic to assert; documented as a lint step.)*
2. **Reference-integrity check:** grep every `item`/`icon`/`recipe` id in the entry JSONs against the known registry inventory in this spec (all are `hexerei:` ids that exist in `HexereiBlocks`/`HexereiItems`/`HexereiCrops` or `minecraft:` vanilla, or recipe ids that have a data file). A referenced id that isn't registered renders an empty/missing slot in-game — catch it here.
3. **`runClient` smoke (authoritative):**
   - `JAVA_HOME=$(pwd)/../hexerei-work/tools/jdk17 ./gradlew --no-daemon runClient` (Patchouli on the runtime classpath via the `fg.deobf` dep).
   - In a creative world: confirm `hexerei:grimoire` appears in the Hexerei tab; open the book; confirm **5 categories** render with non-magenta icons.
   - Open one entry per category; confirm **no missing-entry placeholder**, **no magenta item icons**, the **altar crafting page renders the real recipe grid**, and `$(l:...)` cross-links navigate.
   - Confirm the grimoire **item icon is not magenta** (the model→texture path check).
4. **Dependency-resolution check:** `./gradlew --no-daemon build` must succeed *after* the Patchouli coordinate resolves — a failed resolve (bad `<FILE_ID>`) fails the build loudly, which is the intended signal that the `[UNVERIFIED]` coordinate still needs a real file id.
5. **RU parity:** switch language to Русский in-game; reopen the book; confirm RU category/entry names render (proves the `ru_ru/` tree is picked up, mirroring how the owner verifies `RU-Mod-Guides/`).

There is **no GameTest** for this feature — Patchouli rendering can't be asserted headlessly, and per project convention in-world-only behavior that can't be unit-tested is verified by a runtime smoke check, not a flaky `@GameTest`.

---

## Out of Scope (explicit cuts)

- **Auto-generated brew/ritual recipe pages** — Patchouli has no built-in "cauldron" or "ritual circle" recipe page type; brews/rites are documented as prose `spotlight` pages. A custom Patchouli page type (a `IComponentProcessor` + `templates/`) to render cauldron recipes from `BrewRecipes` is a possible later polish, not this slice.
- **Multiblock preview page for the altar** — the Blood Magic reference uses `"type": "multiblock"` with a registered `multiblock_id`. Hexerei's altar forms via `onPlace` and has **no Patchouli multiblock definition** registered; adding one is out of scope (would need a `IMultiblock` registration). The altar entry uses `spotlight` + text instead.
- **Advancement / progression gating** of entries — deferred (see Balance).
- **First-join auto-grant** — designed above as an optional follow-up toggle, not shipped in this slice.
- **Charm Pouch real content** — the `charms` category ships **one preview entry only**; full charm entries land with the Charm Pouch slice (`2026-06-23-charm-pouch-design.md`).
- **Option B (custom Screen)** implementation — documented as the rejected alternative; only built if the Patchouli dependency is later disallowed.
- **Entity pages** (`patchouli:entity`) for the screaming mandrake — that entity is itself a future slice; no entry until it exists.
