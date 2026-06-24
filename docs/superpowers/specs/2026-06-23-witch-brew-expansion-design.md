# Hexerei — Witch's Brew Expansion

**Date:** 2026-06-23
**Extends:** Brewing system (`Brews`, `BrewRecipes`, `BrewItem`, `CauldronBlockEntity`)

## Premise (corrected from initial assumption)

The brewing loop is **already fully functional**: fill cauldron → heat → drop herb ingredients (absorbed by multiset match) → glass-bottle collects the brew (debits altar power) → drinking applies the brew's `MobEffect`s and returns a glass bottle. Effects are **not** stubbed.

The real gaps this feature fills:
1. Only **2 brews** exist, both pure debuffs — no beneficial witchcraft, no self-drink use case.
2. Two crop produce items — `icy_needle` (from snowbell) and `artichoke` — are **orphans**: grown but consumed by nothing. Dead content.
3. `BrewItem` has **no tooltip** — a player cannot see what a brew does before drinking it.

In this mod, **brews ARE the crafts**: a "recipe" is an entry in `BrewRecipes.RECIPES` (an ingredient multiset → `Brew`), brewed in the cauldron. This feature adds 4 such recipes. Pure-data extension — **zero new registry objects**.

## Player Loop
- **Trigger:** drop the recipe's two herb produce into a boiling, water-filled cauldron, then right-click with a glass bottle.
- **Cost:** the two ingredients + the brew's `power` debited from a nearby altar.
- **Effect:** a collectable `BrewItem` whose NBT names the brew; drinking applies its effects.
- **Duration:** timed (per-effect tick durations below); brew item itself is permanent until drunk.
- **Feedback:** existing cauldron color-blend (new ingredient tints) + vanilla DRINK animation/sound; new tooltip lists effects.

## New Brews (the "crafts")

Each is a distinct 2-ingredient multiset — verified non-colliding with the existing two (`{mandrake_root, belladonna_flower}`, `{wolfsbane, wormwood}`) and with each other.

| Brew id | nameKey | Multiset | Effects (id, amplifier, ticks) | Color | Theme |
|---|---|---|---|---|---|
| `witchs_sight` | `brew.hexerei.witchs_sight` | icy_needle + artichoke | night_vision 0 / 1800, water_breathing 0 / 1800 | `0x2E6E6E` | scrying / utility (beneficial) |
| `bloodwort_tonic` | `brew.hexerei.bloodwort_tonic` | mandrake_root + wolfsbane | damage_boost (strength) 0 / 1200, damage_resistance (resistance) 0 / 600 | `0x8B1A1A` | combat buff (beneficial) |
| `hags_swiftness` | `brew.hexerei.hags_swiftness` | artichoke + wormwood | movement_speed (speed) 1 / 1800, jump 0 / 1800 | `0x4A7A3A` | mobility (beneficial) |
| `withering_bile` | `brew.hexerei.withering_bile` | belladonna_flower + icy_needle | poison 1 / 200, wither 0 / 100 | `0x1A1020` | offensive (debuff) |

**Vanilla MobEffect resource locations** (exact, to avoid the `WITHER_BOSS_DEATH`-class typo trap):
`minecraft:night_vision`, `minecraft:water_breathing`, `minecraft:strength`, `minecraft:resistance`, `minecraft:speed`, `minecraft:jump_boost`, `minecraft:poison`, `minecraft:wither`.

> Note: `BuiltInRegistries.MOB_EFFECT` ids are `minecraft:strength`, `minecraft:resistance`, `minecraft:speed`, `minecraft:jump_boost` (registry names), NOT the field names (`damage_boost` etc.). Use the registry names above.

After this, **every** produce item is used in ≥1 brew (icy_needle and artichoke gain 2 each).

## Registry Objects
None. Pure data in `Brews` + `BrewRecipes` + lang + a tooltip method on the existing `BrewItem`.

## Mechanics
1. **`Brews.java`** — add 4 `public static final Brew` constants with the effect lists above; register each in `build()` so `BY_ID` resolves them.
2. **`BrewRecipes.java`** —
   - add 4 `new BrewRecipe(List.of(<idA>, <idB>), Brews.<NAME>)` to `RECIPES`.
   - add tints for the two new ingredients to `INGREDIENT_COLORS`: `hexerei:icy_needle → 0xB0E0E6` (pale ice), `hexerei:artichoke → 0x4A6E3A` (artichoke green). The other four already have tints.
   - `match`, `canAccept`, `isIngredient` are generic over `RECIPES` — no logic change needed.
3. **`BrewItem.java`** — add `appendHoverText`: for the brew of the stack, list each effect as a colored line, mirroring vanilla potion tooltips: `Component.translatable("effect."+namespace+"."+path).append(" "+roman(amplifier))` + duration `m:ss`, styled green for beneficial / red for harmful via `MobEffect.getCategory()`. Resolve the `MobEffect` from `BuiltInRegistries.MOB_EFFECT`; skip any that don't resolve (defensive).

## Persistence
None new. Brew identity already rides in `BrewItem` NBT (`BrewId`); cauldron ingredient list already persists via existing `saveAdditional`/`load`.

## Client/Server
- Brewing + effect application: server-authoritative (unchanged — `collectBrew` and `finishUsingItem` already guard `!level.isClientSide`).
- Cauldron liquid color: already synced via the BlockEntity update packet (`getUpdateTag`); new `INGREDIENT_COLORS` entries flow through the existing path.
- Tooltip: client-render only, reads the stack's own NBT — no packet needed.

## Assets
- **Textures:** none new (brews share one `brew` item texture, tinted by `BrewItem.color`; ingredients already have textures).
- **Models / blockstates:** none.
- **Lang keys** (add to BOTH `en_us.json` and `ru_ru.json`):

| key | en_us | ru_ru |
|---|---|---|
| `brew.hexerei.witchs_sight` | Witch's Sight | Ведьмин взор |
| `brew.hexerei.bloodwort_tonic` | Bloodwort Tonic | Кровавый тоник |
| `brew.hexerei.hags_swiftness` | Hag's Swiftness | Прыть ведьмы |
| `brew.hexerei.withering_bile` | Withering Bile | Иссушающая желчь |

(Effect-line text in the tooltip reuses vanilla `effect.minecraft.*` keys — already localized by Minecraft.)

## Balance
- **night_vision/water_breathing 1800t (90s):** long utility window for an exploration brew; matches vanilla extended Night Vision.
- **strength 0 / 1200t (60s) + resistance 0 / 600t (30s):** strength is the long combat window; resistance is the shorter "burst" layer so the tonic isn't a strict upgrade over a vanilla strength potion.
- **speed 1 / 1800t + jump 0 / 1800t:** speed II to feel distinctly "witch-fast"; long duration is a travel brew, not combat (no other combat layer).
- **poison 1 / 200t (10s) + wither 0 / 100t (5s):** short, lethal-leaning — an offensive throw-down-the-well brew; wither kept to amplifier 0 and 5s so it's dangerous but not instant-kill.
- **Ingredient tints** `0xB0E0E6` / `0x4A6E3A`: pale-ice and artichoke-green so the cauldron reads visually distinct while brewing the new recipes.
- **Power cost:** set per-brew `power` in the `Brew` constructor — `witchs_sight 40`, `bloodwort_tonic 60`, `hags_swiftness 40`, `withering_bile 30` (offensive brews cheap like Frailty=30; strong combat buff costliest). All within the altar's existing supply scale (Sleeping=50, Frailty=30).

## Test Plan
- **Unit (`BrewRecipesTest` / new `BrewExpansionTest`, no game runtime):**
  - `match` returns the correct `Brew` for each new exact multiset (order-independent: test both ingredient orderings).
  - No multiset collisions: assert all 6 recipes have pairwise-distinct ingredient multisets.
  - `canAccept` accepts the first ingredient of each new recipe on an empty cauldron, and rejects an item in no recipe.
  - Every `BrewEffect.effectId()` across all brews is a well-formed `minecraft:`-namespaced ResourceLocation drawn from the allowed vanilla set; amplifier ≥ 0, durationTicks > 0.
  - Every new ingredient id present in `RECIPES` has an entry in `INGREDIENT_COLORS` (guards the orphan-tint gap).
- **GameTest (`CauldronGameTests`, extend existing):**
  - Arena: cauldron over a lit heat source. Fill water (call `fillWater`), advance to boiling, spawn the two `witchs_sight` ingredients as item entities, tick until absorbed, call `collectBrew()` with power available, assert returned stack's `BrewItem.brewOf(...).id() == "witchs_sight"`.
  - Apply the brew to a mock player via `BrewItem.finishUsingItem` and assert `player.getEffect(MobEffects.NIGHT_VISION) != null`.

## Out of Scope
- **Splash/throwable brews** (delivery for the debuff brews) — separate feature; the offensive `withering_bile` is self-drink-only for now.
- New crops or produce items (the 8 crops already exist; this only wires the orphan two into recipes).
- Brew stacking/dilution mechanics, brew tiers, or >2-ingredient recipes.
- Custom brew item texture per brew (all share the tinted `brew` texture).
