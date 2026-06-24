# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository layout

This repo holds three loosely-related things under one root (`/home/h621l/minecraft`):

- **`hexerei/`** — the active development target: an **original Forge 1.20.1 witchcraft mod** (Java). This is where almost all coding work happens.
- **`mods/` + `data/` + `Dockerfile` + `docker-compose.yml`** — a self-contained Docker Forge 1.20.1 **server** that bakes in curated `.jar`s from `mods/`. See root `README.md`. World/config/logs persist in `data/` (gitignored runtime state). Port 25565, `ONLINE_MODE=FALSE`.
- **`RU-Mod-Guides/`** — a Russian Patchouli-guide translation pack (Ars Magica Legacy + Blood Magic). Lang-file-only; no Java.

`hexerei-work/` holds dev tooling: the portable JDK17, decompiled reference sources, extracted assets, and a test server. `for version rework/` and the `*.zip` archives are scratch/distribution artifacts.

When the task is "the mod," work in `hexerei/`. The Docker server and the RU pack are separate concerns.

## Building & testing the mod (`hexerei/`)

**Every Gradle command needs `JAVA_HOME` pointing at JDK 17.** A portable one is vendored:

```bash
cd hexerei
export JAVA_HOME=$(pwd)/../hexerei-work/tools/jdk17
```

Gradle is configured with `org.gradle.daemon=false`; pass `--no-daemon` explicitly anyway.

| Task | Command |
|------|---------|
| Build the jar | `./gradlew --no-daemon build` → `build/libs/hexerei-1.20.1-0.1.0.jar` |
| Pure unit tests (JUnit 5, no MC runtime) | `./gradlew --no-daemon test` |
| Single unit test | `./gradlew --no-daemon test --tests 'com.vel5id.hexerei.brewing.BrewRecipesTest'` |
| In-world GameTests (headless) | `./gradlew --no-daemon runGameTestServer` |
| Datagen | `./gradlew --no-daemon runData` (outputs to `src/generated/resources/`) |

First-ever build decompiles Minecraft and is slow (several minutes); subsequent builds are fast.

### Two-tier test strategy — important

Logic is split so the bulk can be tested **without a Minecraft runtime**:

- **`src/test/java/`** — fast JUnit 5 tests over **pure logic** classes (recipe matching, circle geometry, power math, crop drop/growth tables, brew color). These never boot Minecraft. When adding a feature, factor the decidable logic into a pure class and unit-test it here first (TDD).
- **`src/main/java/com/vel5id/hexerei/test/`** — Forge **GameTests** (`@GameTestHolder(MODID)`, `@PrefixGameTestTemplate(false)` so templates resolve to `hexerei:<name>`) that exercise real in-world block/entity behavior via `runGameTestServer`. These are *main* sources, not test sources, because Forge loads them at runtime.

In-world tests that depend on a large world scan (e.g. the altar's 29³ power scan) are marked `required = false` because the shared multi-arena GameTest world makes them flaky — the authoritative check for those is a dedicated-server smoke test. Don't "fix" such flakiness by tightening the GameTest; prefer the pure unit test for the logic and a smoke test for the integration.

## Architecture & conventions

The mod is built **slice by slice** (Altar → Herbs/Crops → Cauldron/Brewing → Ritual Circles → Taint/visuals). Each slice's deliberate design decisions and every **magic number** are documented in **`hexerei/DESIGN-NOTES.md`** — read it before changing balance values, and add to it (don't invent numbers ad hoc). Values still needing in-game confirmation are flagged `[UNVERIFIED]`.

**Registration** uses Forge `DeferredRegister` grouped in `registry/` (`HexereiBlocks`, `HexereiItems`, `HexereiBlockEntities`, `HexereiCreativeTabs`, `HexereiParticles`, `HexereiCrops`, `HexereiTags`). All are wired in `HexereiMod` constructor onto the mod event bus. `HexereiCrops.init()` runs *before* the registers fire because it creates per-crop `RegistryObject`s eagerly. Add new content by following the existing register-object pattern, not by inventing a new mechanism.

**Server/client split:**
- Server-authoritative state managers are **per-`ServerLevel`, transient, server-only** (`AltarPowerManager` rebuilt from block-entity load/unload; `ChunkTaintData` is persisted `SavedData`). Keep world state off the client.
- Client-only code lives under `client/` (`HexereiClient`, `AltarScreen`, particles, `ClientTaintCache`) and is reached via `DistExecutor` — never reference it from common code.
- Networking: a single `SimpleChannel` in `network/HexereiNetwork`, messages registered with an incrementing id (`CycleRiteC2SPacket`, `TaintSyncS2CPacket`). Add new packets there.

**Power model:** blocks contribute to altar power via the `AltarPowerTable` factor/limit table; consumers query through `IPowerSource`/`AltarPowerManager`. Power is consumed *before* the sacrifice/ingredient so a failed power check leaves inputs intact. Many table entries exist for blocks not yet implemented — guarded with `// FUTURE SLICE`.

**Assets reuse vanilla art** where possible (cauldron model parents `minecraft:block/cauldron`, brews tint the vanilla potion overlay) to avoid third-party assets. Blocks need a loot table (`data/hexerei/loot_tables/blocks/*`) or they drop nothing.

## Skills for this mod

Prefer the dedicated `mc-mod-*` skills over ad-hoc work — they encode this project's exact conventions:
- `mc-mod-ideate` — scope a new feature into a buildable design.
- `mc-mod-implement` — build a designed feature (registration, persistence, networking, assets, GameTests; TDD for pure logic).
- `mc-mod-debug` — crashes, magenta textures, registry errors, desync, failing GameTests.

## Commit conventions

Commits follow `type(hexerei): summary` (e.g. `feat(hexerei): …`, `fix(hexerei): …`), often referencing the slice or review task. Match this style.
