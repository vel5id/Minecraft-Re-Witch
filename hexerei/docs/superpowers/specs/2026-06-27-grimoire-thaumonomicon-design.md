# Hexerei — Grimoire as Thaumonomicon (gated node-web guide, own engine, Patchouli dropped)

**Date:** 2026-06-27
**Extends:** the `read` verb of Модель §7 over existing State — `PlayerSoulData` (gains `knownEntries`),
`ChunkSoulData.disturbance[domain]`, `Bond`. Replaces the static Patchouli `grimoire` book (17 flat,
always-open entries) with a tabbed node-web that unlocks as the player acts on the world.
**Replaces / removes:** `vazkii.patchouli` dependency, `PatchouliAPI` call in `GrimoireItem`, the
`patchouli_books/` content tree, and the "disable Patchouli to run GameTests" workaround.
**Skill:** brainstorming → writing-plans → mc-mod-implement.

---

## Constitution gate (litmus — all pass)

| # | Question | Verdict |
|---|---|---|
| 1 | Выводится из Закона? | ✅ The book is the `read` verb over the one record — it reads `Bond`/State, writes nothing but a "known" mark via an Act. |
| 2 | Цена + общий ресурс? | ✅ Knowledge is not free: it is paid by **doing** (a real Act in the world) or **accumulating** State (debt / disturbance). No new currency — the literal Thaumcraft research-points/aspect-scan economy is **rejected** (it would be a parallel system). |
| 3 | Несколько применений? | ✅ One book = reference + progression spine + a tease of the unknown (silhouettes). |
| 4 | Предсказуемо? | ✅ Unlocking a node = doing the thing the node describes, or crossing a declared State threshold. No dice. |
| 5 | Питает петлю / углубляет понимание? | ✅ "deepens understanding" is an explicit Law clause; threshold-gated chapters (curses ← debt, storms ← disturbance) surface exactly when the loop pulls the player there. |
| 6 | Диегетично? | ✅ The witch records what she has come to know; knowledge survives death (`copyOnDeath`) — *you do not forget what you learned*. |
| 7 | Свободно от запрещённого? | ✅ Not free power; not a parallel currency; not cosmetics — it is a mechanic that reads State. |
| 8 | Не нарушает метафизику? | ✅ Reads the soul/chunk record through the existing accessors; the only write is `knownEntries` via the single `discover()` Act point. |
| 9 | Присутствия/состояние — из State? | ✅ A node's visibility = f(`knownEntries`, prereq edges); threshold unlocks = f(`totalDebt`, `disturbance`). |

**The one forbidden thing we do NOT build:** research points, aspect scanning, or any separate
"knowledge resource". Unlock conditions read only the State that already exists.

---

## Player loop

- **Open:** right-click the Grimoire item → opens `GrimoireScreen` (client-side; no server round-trip,
  all display + known-set data is client-cached).
- **Read:** left rail = the 5 category tabs (Altars / Herbs / Brewing / Rituals / Charms). Selecting a tab
  shows that category's **node-web**: entry nodes at authored `node:{x,y}`, connector lines along `prereq`
  edges. Known nodes render with their icon; locked nodes render as a **silhouette "????"** with a `teaser`
  line on hover. Clicking a known node opens its **paged entry** (prev/next).
- **Discover (the gate):** an entry becomes known when the player either
  - **(contact)** performs the Act the entry describes — first time placing an altar, harvesting a herb,
    brewing, performing a rite, etc. — or
  - **(state)** crosses a declared threshold of existing State — e.g. the curse chapter reveals itself once
    `totalDebt ≥ X`; a storm entry once a held-chunk `disturbance[domain] ≥ Y`.
  On unlock: a small "new entry" toast; the node flips from silhouette to known on next open.
- **Death:** `knownEntries` is carried by `copyOnDeath` — knowledge persists. Death does not un-teach.

---

## Architecture (4 units, bounded)

### 1. `GrimoireRegistry` (common, resource-reload listener)
Loads entry definitions from JSON on **both** sides (server needs structural + trigger data; client needs
everything to render). One entry:

```jsonc
{
  "id": "hexerei:altars/hungering_altar",
  "category": "hexerei:altars",
  "node": { "x": 120, "y": 40 },          // authored constellation position (px in the category canvas)
  "prereq": ["hexerei:altars/altar_basics"], // edges drawn as connector lines; gate-independent visual graph
  "icon": "hexerei:hungering_altar",
  "name": "...",                            // localized text lives in the per-language tree (see Content)
  "unlock": { "type": "contact", "trigger": "place_block:hexerei:hungering_altar" },
  "teaser": "...",                          // shown on hover over the locked silhouette
  "pages": [ /* page objects, see Page types */ ]
}
```

- **`unlock.type: "contact"`** → `trigger` is a stable token (`pickup_item:<id>`, `place_block:<id>`,
  `brew:<brewId>`, `rite:<riteId>`, …). The registry builds a `Map<trigger, entryId>` lookup table.
- **`unlock.type: "state"`** → `{ "read": "player.totalDebt" | "chunk.disturbance.<domain>", "gte": <n> }`.
- The structural part (id/category/node/prereq/unlock) is the only thing the server keeps; pages are kept
  too (cheap) so a single loader serves both sides. Parsing is **pure** → unit-tested.

### 2. Knowledge storage — `PlayerSoulData.knownEntries: Set<ResourceLocation>`
- The witch's known-set lives in the existing per-player soul attachment (already `copyOnDeath`, already
  NBT-serialized). Added field, serialized as a `ListTag` of strings alongside `marks`/`amulets`.
- **Written only** through `GrimoireDiscovery.discover(player, id)` — the single Act point (Грамматика:
  nobody writes State except through an Act). `addKnown(id)` returns `true` if newly added (drives the toast
  + sync delta).

### 3. `GrimoireDiscovery` (server) — the two trigger layers
- **Contact layer:** hook the *existing* gameplay events that already fire for these Acts
  (`ItemEntityPickupEvent` / block-place / brew-complete / rite-resolve). On each, build the trigger token,
  look it up in `GrimoireRegistry`'s table, and `discover()` the matched entry. No new gameplay systems —
  these are reads of Acts that already happen.
- **State-threshold layer:** a cheap server tick over **online players holding/owning the Grimoire** (or
  simply all online players, throttled every N ticks). For each `state`-type entry not yet known, evaluate
  its `read`/`gte` against `PlayerSoulData.totalDebt` or the player-chunk's `ChunkSoulData.disturbance` →
  `discover()` when crossed. Evaluation logic is **pure** (State snapshot → set of unlocks) → unit-tested.
- Every successful `discover()` → `GrimoireSyncS2CPacket` delta to that player + toast.

### 4. `GrimoireScreen` + `ClientGrimoireCache` (client)
- `ClientGrimoireCache` mirrors `ClientTaintCache`: holds the known-`Set<ResourceLocation>`, updated by the
  S2C payload handler. **Not** `@OnlyIn(CLIENT)` (common handler references it; no-ops on dedicated server).
- `GrimoireScreen`: left category rail; main canvas renders the selected category's node-web — nodes at
  `node:{x,y}`, prereq connector lines, locked → silhouette "????" + hover teaser, known → icon + click to
  open the paged entry view. Manual node positions (intentional constellation); auto-grid fallback when a
  node has no `node` block. Visibility resolver (known + prereq → `known | locked | hidden`) is **pure** →
  unit-tested.

### Networking
- One new payload: **`GrimoireSyncS2CPacket`** — full known-set on login/respawn/dimension-change, deltas on
  each `discover()`. Registered in `HexereiNetwork` via the existing `PayloadRegistrar`; sent with
  `PacketDistributor.sendToPlayer`. No C2S needed — the open is client-local.
- `GrimoireItem.use`: drop `PatchouliAPI.get().openBookGUI(...)`; on the client dist open `GrimoireScreen`
  (gate with `FMLEnvironment.dist == Dist.CLIENT` calling into the client class, the project's
  post-`DistExecutor` pattern).

---

## Content format
- Keep the repo's existing **parallel per-language trees** (`assets/hexerei/grimoire/en_us/…` +
  `…/ru_ru/…`) — our own schema replaces Patchouli's. Migrate all **17** current entries 1:1; hold **RU
  parity** (per project memory: the RU pack is a first-class concern).
- **Page types (v1):** `text`, `image`, `recipe` (crafting + altar + cauldron + ritual),
  **`multiblock`** (ghost/rotating preview of a structure — altars and ritual circles are multiblocks, so
  this is core, not optional), and **`entity`** (render a living creature by `EntityType` id in the page —
  **familiars and golems**, *not* the abstract spirit/`PresenceEntity`).
- **`entity` page detail:** renders a real `EntityType` via the vanilla GUI-entity render path
  (`InventoryScreen.renderEntityInInventory`-style: a non-added dummy instance, slow-rotated). It is
  **content-agnostic** — given any `EntityType` id it draws that mob. Today the only such creature is the
  **wolf familiar** summoned by `BoundBeastRite` (a vanilla `Wolf`), so v1's entity page showcases it;
  custom familiars and golems are **future content** the same page type will render once they exist (no book
  change needed then). This adds a modest renderer cost over the static page types (a ticking dummy entity +
  the vanilla entity-in-GUI transform) — accepted, since the user wants entity pages in v1.

---

## Phases (each ships a working artifact)
- **P1 — Renderer + de-Patchouli.** Own `GrimoireScreen` renders migrated content as tabbed node-webs with
  all v1 page types — `text`/`image`/`recipe`/`multiblock`/`entity` — **everything unlocked** (no gate yet).
  Drop the Patchouli dependency, content tree, and the GameTest workaround. De-risks the renderer (including
  the entity-in-GUI path); immediate visible result.
- **P2 — Contact gate.** `PlayerSoulData.knownEntries` + `GrimoireSyncS2CPacket` + `ClientGrimoireCache` +
  contact-trigger discovery + locked silhouettes + toast.
- **P3 — State gate.** Threshold-trigger layer + hover teasers.

---

## TDD surfaces (pure logic → `src/test/java`, no MC runtime)
- `GrimoireRegistry` parser: JSON → model; every `prereq` resolves to a real entry id; trigger table has no
  collisions.
- Node-visibility resolver: `(knownSet, prereq graph) → known | locked | hidden` per node.
- State-trigger evaluator: `(PlayerSoulData/ChunkSoulData snapshot, entry defs) → Set<unlocked ids>`.

Renderer, node layout, and the live discovery hooks are verified by a GameTest + a dedicated-server smoke
test (the project's rule for world-dependent behavior), not by tightening flaky GameTests.

---

## Out of scope (YAGNI)
- Research points / aspect scanning / any separate knowledge resource (Constitution-forbidden).
- One global cross-category web (rejected in favor of per-category tabs).
- Pan/zoom infinite canvas (per-category fixed canvas is enough at current entry counts).
- Authoring/editor tooling for node positions (hand-authored JSON is sufficient now).
