# Hexerei — Altar Core Slice: Design Spec

**Date:** 2026-06-21
**Status:** Approved (design), pending spec review
**Mod:** Hexerei — an original Minecraft Forge 1.20.1 witchcraft mod (`com.vel5id.hexerei`)

## 1. Goal & Context

Build the **Altar** subsystem of Hexerei on **Minecraft Forge 1.20.1** (Forge 47.4.10) as the first vertical slice of an incremental, long-running build. The Altar is Hexerei's central power source; getting it right establishes the foundation (registration, block-entities, networking, a power-query API) that every later slice (brews, rituals, cauldron…) builds on.

We implement the *behaviour* cleanly against modern APIs, with the design captured here as the single source of truth.

**Consistency rule (non-negotiable):** every numeric value (scan radius, per-block power factor/limit, recharge rate, hardness, recipe) is fixed by this design and the Hexerei altar design notes, never improvised. Where an edge case is ambiguous, we make a *deliberate, documented* decision.

## 2. Scope

### In scope (this slice)
1. **Mod scaffold** — `modid = hexerei`, group `com.vel5id.hexerei`, a `CreativeModeTab`, `DeferredRegister`s for Block/Item/BlockEntityType/CreativeTab. (No `MenuType` — the altar GUI is a Container-less read-only `Screen`, matching the original.)
2. **`AltarBlock`** (`EntityBlock`) — `BooleanProperty JOINED` (replaces metadata 0/1), hardness 2.0, `Material.rock` equivalent (`SoundType.STONE`, `pushReaction`/`mapColor` reasonable), `RenderShape.MODEL`.
3. **`AltarBlockEntity`** — Hexerei altar block-entity:
   - multiblock BFS detection (exact rule: horizontal N/S/E/W flood-fill, every cell 2–3 same-block neighbours, total **==6**, core = first visited);
   - 29×29×29 power scan (`SCAN_DISTANCE=14`), throttled to ≤ once / 100 ticks;
   - power table (vanilla blocks mapped to 1.20.1 ids + tag-based sapling/log/leaf sources + `CropBlock`/`FlowerBlock` catch-all), `min(count,limit)×factor`;
   - recharge every 20 ticks: `power = (int)min(power + 10×rechargeScale, maxPower×powerScale)`;
   - artefact scan (structure included; **vanilla-available artefacts only**: player/skeleton/wither skull blocks, torch; Hexerei artefacts null-tolerant stubs);
   - NBT keys `Core, Power, MaxPower, PowerScale, RechargeScale, RangeScale, EnhancementLevel`;
   - sync via `ClientboundBlockEntityDataPacket` + `getUpdateTag`/`handleUpdateTag`;
   - implements `IPowerSource`.
4. **`AltarPowerManager`** — per-`ServerLevel`, server-only, transient registry rebuilt from BE load/unload (NOT `SavedData`). `get(level, pos)` → distanceSq-sorted in-range altars; self-healing sweep evicting stale BEs. Foundation API for future consumers.
5. **`AltarScreen`** — read-only client `Screen` (no `Menu`/slots, a read-only panel), opened on right-click, blits `gui/altar.png` (176×88) and shows power / maxPower / recharge from synced BE state.
6. **Data & assets** — blockstate JSON (joined/standalone variants), block + item models (cube: `altar` sides, `altar_top` top; joined uses `*_joined*`), extracted textures, `en_US`/`ru_ru` lang, recipe JSON.
7. **Verification** — `gradlew build` green + a **Forge GameTest** that places a 2×3 altar, ticks, and asserts `JOINED=true` on all 6 and `power` accrues; run headless via `runGameTestServer`. Plus a dedicated-server boot smoke test (mod loads, no errors).

### Out of scope (future slices, explicitly)
Brews, distilling, rituals/rites and all power **consumers** (cauldron, brazier, spinning wheel, crystal ball); Hexerei plant/artefact blocks (LEAVES, LOG, crops, EMBER_MOSS, CANDELABRA, CHALICE, DEMON_HEART, INFINITY_EGG, the placed-item artefacts ARTHANA/MYSTIC_BRANCH/koboldite pentacle); Wolf Altar; `NaturePowerFX` particles (stub/omit — cosmetic); `INullSource`/void bramble.

> The power table and artefact scan **reference** future-content blocks. Those entries are present in the code but **inert/guarded** until the referenced block exists; clearly marked `// FUTURE SLICE`. This keeps the structure complete without pulling in not-yet-built content.

## 3. Architecture

```
hexerei (modid)
├─ HexereiMod.java                    @Mod entry; wires DeferredRegisters, events
├─ registry/
│   ├─ HexereiBlocks                 DeferredRegister<Block>   (ALTAR)
│   ├─ HexereiItems                  DeferredRegister<Item>    (ALTAR BlockItem)
│   ├─ HexereiBlockEntities          DeferredRegister<BlockEntityType> (ALTAR)
│   └─ HexereiCreativeTabs           DeferredRegister<CreativeModeTab> (HEXEREI)
├─ block/
│   └─ AltarBlock                     EntityBlock, JOINED property, multiblock triggers
├─ blockentity/
│   └─ AltarBlockEntity               power scan + recharge + artefacts + NBT + sync + IPowerSource
├─ power/
│   ├─ IPowerSource                   power-source contract (BlockPos-based)
│   ├─ AltarPowerManager              per-ServerLevel registry + closest-altar query
│   └─ RelativePowerSource            transient distance wrapper
├─ client/
│   ├─ AltarScreen                    read-only power GUI
│   └─ ClientSetup                    screen open hook, render layer
└─ resources/
    ├─ META-INF/mods.toml
    ├─ assets/hexerei/{blockstates,models,textures,lang,...}
    └─ data/hexerei/recipes + tags
```

**Data flow:** place altar block → `AltarBlock` neighbor/place hooks run `updateMultiblock` BFS → BE sets `JOINED` + designates core → core BE server-ticks: throttled power scan computes `maxPower`, recharge raises `power`, registers in `AltarPowerManager` → BE marks dirty → `ClientboundBlockEntityDataPacket` syncs power to client → right-click opens `AltarScreen` reading synced power. Future consumers call `AltarPowerManager.get(level, pos)` then `consumePower(cost)`.

## 4. Key design decisions

| Topic | Naive approach | Decision | Why |
|---|---|---|---|
| Joined state | metadata 0/1 | `BooleanProperty JOINED` | blockstate properties replace legacy metadata |
| Power registry | dual process-global statics by FML side | **per-`ServerLevel` transient manager**, server-only | fixes cross-world/side leakage; altar power is inherently server-side |
| `Coord` | custom int-triple | `BlockPos` (NBT compat: serialize core as `CoreX/CoreY/CoreZ` ints to match likely legacy form; **verify**) | native, less code |
| Tile→BE | reflection-based tile lookup | `EntityBlock` + `BlockEntityTicker` | modern API |
| Sync | `S35PacketUpdateTileEntity` | `ClientboundBlockEntityDataPacket` + `getUpdateTag` | modern API |
| GUI | `openGui`+`IGuiHandler`, Container-less `GuiScreen` | client `Screen` via `setScreen` (open triggered by a tiny S→C packet on right-click) | legacy GUI-handler API not used |
| oredict sources | `treeSapling/logWood/treeLeaves` | block tags `minecraft:saplings/logs/leaves`, same factor/limit | no ore-dictionary dependency |
| catch-all nature | `instanceof BlockFlower\|\|BlockCrops`, cached | `instanceof FlowerBlock\|\|CropBlock`, cached (rebuild on tag/registry ready) | direct analog |
| Wither-skull bonus | **bug:** missing `break` → wither gets player's +3/+3 | **preserve the bug** behavior, documented in code | documented quirk; artefacts are out-of-scope polish anyway |
| `get(world,pos,radius)` radius arg | unused (dead) | keep signature, **radius is advisory**; real filter = altar's own `getRange()` | keep the simple advisory-radius semantics |
| Power store | float, recharge floored to int | keep float store + int rounding | preserves balance and display |
| Recipe | shaped, needs not-yet-implemented Hexerei items | ship **temporary placeholder recipe** (vanilla-only) + creative-tab access; authentic recipe documented & added when ingredients land | authentic recipe can't load without its items |
| Particles | `NaturePowerFX` | omit this slice | cosmetic, zero gameplay impact |

## 5. Power table (authoritative)

`per-source power = min(count, limit) × factor`, summed = `maxPower`. Scanned in a 29³ cube.

- **Tags:** `minecraft:saplings` 4/20, `minecraft:logs` 2/50, `minecraft:leaves` 3/100.
- **Vanilla blocks** (handling flower/grass/pumpkin splits): grass_block 2/80, dirt 1/80, farmland 1/100, (tall_grass+fern) 3/50, (each small flower / dandelion+poppy family) 4/30, wheat 4/20, water 1/50, brown_mushroom 3/20, red_mushroom 3/20, cactus 3/50, sugar_cane 3/50, pumpkin 4/20, pumpkin_stem 3/20, brown_mushroom_block 3/20, red_mushroom_block 3/20, melon 4/20, melon_stem 3/20, vine 2/50, mycelium 1/80, **dragon_egg 250/1**, cocoa 3/20, carrots 4/20, potatoes 4/20.
- **Catch-all:** any other `FlowerBlock`/`CropBlock` not already mapped → 2/4.
- **Future-content blocks** (`// FUTURE SLICE`, guarded): DEMON_HEART 40/2, CROP_* 4/20, EMBER_MOSS 4/20, LEAVES 4/50, LOG 3/100, SPANISH_MOSS 3/20, GLINT_WEED 2/20, CRITTER_SNARE 2/10, BLOOD_ROSE 2/10, GRASSPER 2/10, WISPY_COTTON 3/20, **INFINITY_EGG 1000/1**.

> A classic small-flower pair was 2 block types (4/30 each, so the *type* caps at 30). In 1.20.1 they are ~14 distinct flower blocks. **Decision:** map the modern small-flower set to the same 4/30 per *flower block id* to keep the per-type-cap intent; documented as a known fidelity nuance (a field of mixed modern flowers yields slightly more than the original two-type cap).

## 6. Testing strategy

1. **`gradlew build`** — compiles + data-gen + runs `runData` if used; must be green.
2. **GameTest** (`hexerei:altar_forms_and_powers`) — Forge `@GameTestHolder(HexereiMod.MODID)` class with `@GameTest` methods doing programmatic placement (no `.nbt` structure needed for empty-template tests): place a 2×3 of `hexerei:altar`, surround with e.g. grass/flowers, run N ticks, assert (a) all 6 `JOINED=true`, (b) exactly one core, (c) `maxPower > 0` matching the placed nature blocks, (d) `power` increases over ticks toward cap; and a negative test that a 2×2 / straight line does **not** form. Run headless: `gradlew runGameTestServer`.
3. **Dedicated-server boot smoke** — build the mod jar, drop into a 1.20.1 Forge server (or the repo's Docker server), boot, confirm: mod loads, ALTAR + BE + recipe register, no `ERROR`/exception in logs, `/setblock` an altar works. Matches the repo's server-first deployment.
4. **Client visual (optional, if desktop available)** — right-click a formed altar, confirm the GUI shows non-zero power that ticks up.

**Verification bar for "done":** items 1–3 green with captured evidence (build log tail, GameTest PASS line, server log grep). Item 4 noted if no display.

## 7. Risks ( , with mitigations)

1. **Power-registry world/side scoping (HIGH)** → per-`ServerLevel` manager, server-only, BE-driven; preserve self-healing sweep.
2. **Multiblock BFS exactness (HIGH)** → encode the rule exactly; GameTest asserts the 2×3 forms and a 2×2 / line does NOT.
3. **Tag mapping (HIGH)** → use vanilla tags; GameTest checks at least sapling/log/leaf + grass contribute expected amounts.
4. **Vanilla block split/rename (HIGH)** → explicit per-block remap table in code with an explicit per-block comment.
5. **Recipe ingredient/item-id unknowns (LOW, [UNVERIFIED])** → deferred with placeholder recipe; revisit in the items slice.
6. **NBT core-coord key form (LOW, [UNVERIFIED])** → pick `CoreX/Y/Z`; no live saves to preserve yet, so low risk; document.
7. **29³ scan perf (LOW)** → keep >100-tick throttle; iterate via `level.getBlockState` without forcing chunk loads (skip unloaded sections).

## 8. Deliverables

- `hexerei/` — buildable Forge 1.20.1 mod producing `hexerei-1.20.1-0.1.0-altar.jar`.
- Passing `gradlew build` + GameTest + server boot smoke, with evidence.
- This spec + the analysis report committed under `docs/superpowers/`.
- A short `hexerei/README.md` (build/run/test) and `DESIGN-NOTES.md` (the design-decision ledger + future-slice TODO map).
