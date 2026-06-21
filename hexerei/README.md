# Hexerei (Altar slice) — Forge 1.20.1

**Hexerei** is an original witchcraft mod for Minecraft 1.20.1 (Forge): altars, herb-growing,
brewing, and rituals. This module is the **Altar core** slice — the foundation the rest of the
mod builds on.

Design notes and per-slice decisions live in [`DESIGN-NOTES.md`](./DESIGN-NOTES.md).

## Requirements
- Minecraft **1.20.1**, Forge **47.4.10**
- JDK **17** (a portable one lives at `../hexerei-work/tools/jdk17`)

All Gradle commands need `JAVA_HOME` pointed at JDK 17:

```bash
export JAVA_HOME=$(pwd)/../hexerei-work/tools/jdk17
```

## Build
```bash
./gradlew --no-daemon build
```
Output: `build/libs/hexerei-1.20.1-0.1.0.jar`

## Test
- Pure unit tests (formation BFS, power math, range geometry):
  ```bash
  ./gradlew --no-daemon test
  ```
- In-world behaviour, headless (formation + power accrual + negative case):
  ```bash
  ./gradlew --no-daemon runGameTestServer
  ```

## Install
Drop `build/libs/hexerei-*.jar` into a Forge 1.20.1 (47.4.10) server or client `mods/` folder.

## What works

**Altar slice**
- **Altar block** with a `joined` blockstate property.
- **Multiblock formation**: a flat **2×3** of altar blocks joins into one altar with a single core.
- **Power**: the core scans a 29×29×29 cube for nature blocks and builds `maxPower` from the
  power factor/limit table; passively recharges current power every second.
- **Power-query API** (`AltarPowerManager` / `IPowerSource`) for future consumers.
- **Read-only power GUI** on right-click. Temporary placeholder recipe; full recipe preserved disabled.

**Herbs (crops) slice**
- **8 crops** (Belladonna, Mandrake, Water Artichoke, Snowbell, Wormwood, Minedrake, Wolfsbane, Garlic)
  with per-crop growth stages, light-gated growth, bonemeal, and soil rules (farmland only, no stacking).
- **Seeds & produce** (14 items) — seeds plant the crop; harvest drops produce + seeds (Snowbell → snowball
  +20% Icy Needle; Mindrake/Garlic seed==produce). Mature/immature drop split is implemented.
- **Altar synergy**: crops feed altar power (4/20 each).

**Witch's Cauldron (brewing core) slice**
- **Cauldron** block + block-entity: fill with a water bucket, heat from a block below (fire/lava/magma/
  campfire), boil after ~5 s, then drop herb ingredients in (item-entities are absorbed, tinting the liquid).
- **Altar-powered**: the forming brew draws on the nearest altar (`getCurrentPower`/`consumePower`) — no
  altar power, no brew.
- **Brews**: right-click a ready cauldron with a glass bottle to collect a drinkable **Brew** (applies its
  effects on drink). Starter recipes: **Sleeping Draught** (mandrake root + belladonna flower) and
  **Brew of Frailty** (wolfsbane + wormwood).

**Ritual Circles slice**
- **Ritual Circle** (center marker) + **Ritual Glyph** (flat chalk) blocks. Ring the center with a small
  circle of 12 glyphs (radius 2), drop a **sacrifice** item, have a powered **altar** in range, then
  right-click the center to perform a **rite**.
- **Ritual Chalk**: right-click a Ritual Circle with chalk to **auto-draw** the whole glyph circle
  (or draw single glyphs on other blocks); durability-based, with tooltips explaining the ritual.
- **Rite of the Tempest**: small circle + 1 mandrake root + altar power → summons a thunderstorm
  (consumes the sacrifice and the altar power).

## Not yet (future slices)
Throwable/splash brews + dispersal; brew modifiers (potency/duration); more brews & rites; chalk item +
glyph colours; medium/large circles; timed multi-step rites; coven scaling; Mandrake/Minedrake live
entities; Treefyd; Mutandis seed acquisition; decorative wood/artefact blocks; altar artefact bonuses;
particles; Wolf Altar. See [`DESIGN-NOTES.md`](./DESIGN-NOTES.md).
