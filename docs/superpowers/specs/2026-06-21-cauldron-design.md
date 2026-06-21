# Hexerei — Witch's Cauldron (Brewing Core) Slice: Design Spec

**Date:** 2026-06-21
**Status:** Approved (direction), proceeding
**Mod:** Hexerei — original Forge 1.20.1 witchcraft mod (`com.vel5id.hexerei`)
**Builds on:** Altar slice (power) + Herbs slice (ingredients).

## 1. Goal

Add the **Witch's Cauldron** — the core brewing loop — as Hexerei's third slice: fill a cauldron with water, heat it, drop herb ingredients in, draw on a nearby altar's power, and collect a drinkable **Brew**. This is our own design inspired by classic witch-cauldron mechanics; balance values are our choices.

## 2. The brewing loop (player-facing)

1. Place a **Cauldron** and right-click it with a **water bucket** → filled (water).
2. Put a **heat source directly below** (fire, lava, campfire(lit), magma block) → after ~5 s it begins **boiling**.
3. While boiling, **drop ingredient items** in (toss them onto the cauldron) → each accepted ingredient joins the brew and tints the liquid; only recognised ingredients are accepted.
4. The forming brew needs **altar power** within range — the cauldron queries the nearest altar (`AltarPowerManager`). If the altar has enough power, the cauldron is **powered**.
5. When the accumulated ingredients match a **brew recipe** AND the cauldron is boiling AND powered → the brew is **ready**.
6. Right-click the ready cauldron with a **glass bottle** → receive a **Brew** item (drinkable, applies the recipe's effects); this **consumes** the required altar power and resets the cauldron to plain water.

## 3. State machine (CauldronBlockEntity)

| State | Condition | Notes |
|---|---|---|
| EMPTY | no water | accepts water bucket |
| FILLED | water, not hot | heat source below starts the heat timer |
| HEATING | heat below, `heatTicks < BOIL_TICKS(=100)` | transient |
| BOILING | `heatTicks >= BOIL_TICKS`, water present | accepts ingredients |
| BREWING | boiling + ≥1 ingredient | tracks ingredient list + color + required power |
| READY | ingredient set matches a recipe + powered | right-click bottle to collect |

Losing the heat source resets heat; removing water resets ingredients. Server-authoritative; synced to client via `ClientboundBlockEntityDataPacket` (water level, boiling, color, ready).

## 4. Components

```
block/cauldron/
  CauldronBlock          EntityBlock; right-click (fill / collect); shape; heat-source check helper
  CauldronBlockEntity    state machine, water level, heat ticks, ingredient list, color, power gating, sync, NBT
brewing/
  BrewRecipe             record: ingredient multiset (List<Item>) -> result Brew (effects + color + power cost)
  BrewRecipes            registry of recipes; matcher `match(List<Item>) -> Optional<BrewRecipe>` (PURE, unit-testable)
  BrewColor              pure color blend of ingredient colors (unit-testable)
  Brew / BrewItem        drinkable item carrying a brew id in NBT; on use applies MobEffects
registry/
  HexereiBlocks   + CAULDRON
  HexereiItems    + BREW (BrewItem)
  HexereiBlockEntities + CAULDRON
power/  (reuse AltarPowerManager.closest(level,pos) -> consumePower)
```

**Ingredient intake (concrete):** `CauldronBlock.entityInside` (or BE tick scanning `level.getEntitiesOfClass(ItemEntity, box)`) — when boiling, for each `ItemEntity` overlapping the cauldron whose item is a known brew ingredient and accepted by the current recipe-prefix, consume one from the stack, add the item id to the ingredient list, reblend color, recompute required power & readiness, and play a splash. Unknown items are ignored (left floating).

**Brew item:** `BrewItem extends Item`, `UseAnim.DRINK`, finishes in ~32 ticks; NBT `BrewId` (recipe id). On `finishUsingItem`, apply the recipe's `MobEffectInstance`s to the drinker, return a glass bottle. Display name from the recipe.

## 5. Altar-power integration (reuse existing API)

- The cauldron computes `requiredPower` = sum of its ingredients' power costs (per-ingredient constant, our values).
- Each tick (throttled): `powered = AltarPowerManager.get(serverLevel).closest(level, pos).map(s -> s.getCurrentPower() >= requiredPower).orElse(requiredPower == 0)`.
- On collect: `closest(...).filter(s -> s.consumePower(requiredPower)).isPresent()` — only yield the brew if power was actually consumed (or cost is 0).
- Range: altars use their own `getRange()` (16). A cauldron must be within an altar's range.

## 6. Starter content (this slice)

Brews are **drinkable**, applying vanilla `MobEffect`s (our thematic mapping of the herbs):

| Brew | Ingredients (multiset) | Effect on drink | power |
|---|---|---|---|
| **Sleeping Draught** | mandrake_root + belladonna_flower | Slowness II (20s) + Blindness (10s) | 50 |
| **Brew of Frailty** | wolfsbane + wormwood | Weakness II (30s) + Mining Fatigue (15s) | 30 |

(Two recipes prove matching/ordering. More brews + throwable/splash dispersal = future slices.)

## 7. 1.20.1 implementation notes
- Water level as `IntegerProperty LEVEL 0..3` on the block (like vanilla cauldron) or tracked in BE; we track in BE + a `boolean`/`int` blockstate for render. Keep it simple: BE holds water amount; blockstate has `lit`/`level` for model variants (optional this slice — can use one model + BE-driven particles).
- Heat-source check: block below in a tag (custom `hexerei:cauldron_heat_sources` = fire, lava, campfire[lit], magma_block, soul_fire).
- Ingredient list persisted in NBT as a list of item ids; color as int; `requiredPower`, `boiling`, `heatTicks`, water amount.
- Sync via `getUpdatePacket`/`getUpdateTag` (same pattern as the altar BE).
- Recipe matching: multiset equality against registered recipes (order-independent for the slice); `BrewRecipes.match` is pure.

## 8. Testing
1. **Unit** (`BrewRecipesTest`, `BrewColorTest`): multiset matching (exact set → recipe; wrong/partial → none; order-independent); color blend.
2. **GameTest** (`hexerei:cauldron_*`): build a powered 2×3 altar; place cauldron + water + fire below; tick to boiling; spawn ingredient `ItemEntity`s; tick; assert cauldron READY + correct brew id; simulate bottle collect → assert a `Brew` item with the right NBT and altar power consumed; negative: unpowered cauldron does not become ready.
3. **Build** green; **dedicated-server smoke**: place cauldron, `/setblock` heat + water, `/summon` item, read BE NBT, no errors.

## 9. Out of scope (future)
Rituals/circles/coven, throwable splash brews + dispersal (gas/liquid/triggered), brew modifiers (strength/duration potency), the full brew catalog, cauldron rendering polish, misfortune/backfire. Only the **core loop + 2 drinkable brews** here.
