# Hexerei — Charm Pouch & Combat Charms (Slice 1)

**Date:** 2026-06-23
**Extends:** new item subsystem; reuses Altar power (`AltarPowerManager`), the per-player server tick, and item-NBT conventions.

## Premise

The player carries a **Charm Pouch** — a container item with a small GUI (like a mini shulker box) that holds **combat charms**. While a charm sits in a pouch that is anywhere in the player's inventory, the charm grants a passive combat buff. Charms are **not** free: each holds a power **charge** that drains while its buff is active and **recharges only near an active altar**. A depleted charm goes **dormant** (no buff) until recharged. This ties the whole pouch to the altar economy — very witchy, and it fills the "altar power has few sinks" gap.

This is **Slice 1** of a 3-slice feature. Ritual crafting (Slice 2) and damage-event reactive charms like thorns (Slice 3) are explicitly out of scope here — see *Out of Scope*. In Slice 1 charms are obtained from the creative tab (enough to build and GameTest the full loop).

## Player Loop
- **Trigger:** right-click the Charm Pouch → opens its GUI; drag charms into its 3 slots. Buffs apply passively while the pouch is carried.
- **Cost:** each active charm drains its NBT charge (1/sec while active); recharging debits altar power near an active altar.
- **Effect:** the charm's combat buff is applied to the player every second (Ward → Resistance; Bloodlust → Strength when low HP; Hexbane → Weakness to nearby hostiles).
- **Duration:** continuous while carried **and** charged; dormant at 0 charge (spec rule: ongoing effects are conditional, not permanent).
- **Feedback:** buff icon in the HUD (no particle spam — ambient/hidden effect instances); pouch shows a charge bar (aggregate of contained charms) via the vanilla item durability-bar API.

## Registry Objects
| Kind | id | Notes |
|---|---|---|
| Item | `hexerei:charm_pouch` | `CharmPouchItem`; opens `CHARM_POUCH_MENU`; 3 charm-only slots in NBT; shows aggregate charge bar |
| Item | `hexerei:ward_charm` | `CharmItem(CharmDefs.WARD)` → Resistance I, always active |
| Item | `hexerei:bloodlust_charm` | `CharmItem(CharmDefs.BLOODLUST)` → Strength I, only while player HP ≤ 6.0 |
| Item | `hexerei:hexbane_charm` | `CharmItem(CharmDefs.HEXBANE)` → Weakness I to hostile mobs within 5 blocks |
| MenuType | `hexerei:charm_pouch` | `MenuType<CharmPouchMenu>` via `IForgeMenuType.create` (carries the held-stack hand in extra data) |

5 registry objects — at the YAGNI ceiling; no more get added in this slice.

## Mechanics

### Charm data (pure, unit-testable)
- `CharmDef` (record): `id`, `nameKey`, `effectId` (vanilla `minecraft:` RL string), `amplifier`, `ActiveMode mode`, `int param`.
  - `ActiveMode` enum: `ALWAYS`, `LOW_HEALTH` (active when `health ≤ param`), `AURA_DEBUFF` (applies `effectId` to hostile mobs within `param` blocks instead of to the player).
- `CharmDefs`: catalog `WARD = new CharmDef("ward", "...", "minecraft:resistance", 0, ALWAYS, 0)`, `BLOODLUST = (... "minecraft:strength", 0, LOW_HEALTH, 6)`, `HEXBANE = (... "minecraft:weakness", 0, AURA_DEBUFF, 5)`; `BY_ID` map + `byId`.
- `CharmItem extends Item` holds its `CharmDef`; static `defOf(ItemStack)` resolves the def from the item (not NBT — the item identity IS the def). Charge lives in NBT key `hexerei:Charge`.

### Charge logic (pure, unit-testable — `CharmCharge`)
- Constants: `MAX_CHARGE = 600`, `DRAIN = 1`, `RECHARGE = 5`, `RECHARGE_POWER_PER_UNIT = 0.2f`.
- `boolean playerCondition(CharmDef def, float playerHealth)` → for `ALWAYS`/`AURA_DEBUFF` true; for `LOW_HEALTH` `health ≤ def.param()`.
- `int nextCharge(int current, boolean wantActive, boolean altarAvailable)`:
  - if `altarAvailable` and `current < MAX_CHARGE`: `return min(MAX_CHARGE, current + RECHARGE)` (recharge takes priority; debit handled by caller).
  - else if `wantActive` and `current > 0`: `return max(0, current - DRAIN)`.
  - else `return current`.
- `boolean isEffective(int charge, boolean wantActive)` → `charge > 0 && wantActive` (the buff actually applies this tick).

### Per-player server tick (`CharmTickHandler`, gated to once/second)
Hooked into the existing world tick (`HexereiLevelEvents.onLevelTick`, server side, `gt % 20 == 0`) iterating `level.players()`:
1. Find the first `CharmPouchItem` stack in the player's inventory (main + offhand). If none, skip.
2. Read its contained charm stacks (`CharmPouchItem.contents(pouch)` → up to 3 `ItemStack`s from NBT).
3. Determine `altarAvailable`: query `AltarPowerManager.get(serverLevel)` for an in-range source with `getCurrentPower() ≥ RECHARGE_POWER_PER_UNIT * RECHARGE` at the player's position. (Reuse the same range check the cauldron uses.)
4. For each contained charm `c` with def `d`:
   - `wantActive = CharmCharge.playerCondition(d, player.getHealth())`.
   - `int before = charge(c); int after = CharmCharge.nextCharge(before, wantActive, altarAvailable)`.
   - If `after > before` (recharged): debit the altar `RECHARGE_POWER_PER_UNIT * (after - before)` via the source's `consumePower`; if the debit fails, revert `after = before`.
   - Write `after` to `c`'s NBT; if any contained charge changed, write the charm list back into the pouch stack NBT.
   - If `CharmCharge.isEffective(after, wantActive)`: apply the effect:
     - `ALWAYS` / `LOW_HEALTH` → `player.addEffect(new MobEffectInstance(effect, 40, amplifier, true, false, true))` (ambient, invisible particles, show icon).
     - `AURA_DEBUFF` → for each hostile `Monster` within `param` blocks of the player, `addEffect(... 40, amplifier, true, false, true)`.
5. Effect duration 40 ticks, refreshed every 20 → seamless while charged; lapses within 2s of going dormant.

### Pouch container
- `CharmPouchItem.use` → server: `NetworkHooks.openScreen(serverPlayer, menuProvider, buf -> buf.writeBoolean(offhand))`; the menu reads/writes the held pouch stack's NBT-backed `ItemStackHandler` (3 slots, `isItemValid` accepts only `CharmItem`). On any slot change the handler serializes back into the held ItemStack NBT (standard backpack pattern). Vanilla menu sync ships slot contents to the client automatically — **no custom packet**.
- Guard: do not let the pouch be placed inside itself or inside another pouch (slot `isItemValid` rejects `CharmPouchItem`).
- Charge bar: `CharmPouchItem.isBarVisible/getBarWidth/getBarColor` compute aggregate charge = `sum(charge)/ (MAX_CHARGE * slotsFilled)`.

## Persistence
- **Pouch contents** → pouch `ItemStack` NBT: `hexerei:Items` = list of `{Slot, id, Count, tag}` (standard `ItemStackHandler.serializeNBT`).
- **Per-charm charge** → that charm `ItemStack`'s own NBT `hexerei:Charge` (int), nested inside the pouch's item list. No `SavedData`, no BlockEntity — all item-resident, travels with the item.
- Read null-safely (`hasTag()` then `getOrCreateTag()`), never bare `getTag()`.

## Client/Server
- **Server-authoritative:** charge drain/recharge, altar debit, all `addEffect` calls (in `CharmTickHandler`, server tick only).
- **Client-visual:** `CharmPouchScreen` (`@OnlyIn(Dist.CLIENT)`), registered in `HexereiClient` via `MenuScreens.register(HexereiMenus.CHARM_POUCH.get(), CharmPouchScreen::new)` under the client event subscriber. The charge bar reads the carried stack's own NBT (already on the client) — no packet.
- **Sync:** container slot contents flow over the **vanilla menu channel** (opened via `IForgeMenuType.create` + `NetworkHooks.openScreen`). No entry in `HexereiNetwork` is needed for this slice.

## Assets
- **Textures (16×16 item):**
  - `item/charm_pouch.png` — a drawstring pouch, deep purple/leather.
  - `item/ward_charm.png` — round silver ward sigil.
  - `item/bloodlust_charm.png` — red bead/fang charm.
  - `item/hexbane_charm.png` — black thorn/eye charm.
- **GUI texture:** `textures/gui/charm_pouch.png` (176×166 vanilla container background with a 3-slot row + player inventory). Generate from the vanilla generic 9×3 layout cropped to one row, or hand-author.
- **Models:** 4 `models/item/*.json` (`item/generated`, layer0 → the textures above). Pouch model is plain (no custom mesh).
- **Lang keys (en_us + ru_ru):**

| key | en_us | ru_ru |
|---|---|---|
| `item.hexerei.charm_pouch` | Charm Pouch | Мешочек амулетов |
| `item.hexerei.ward_charm` | Ward Charm | Оберег |
| `item.hexerei.bloodlust_charm` | Bloodlust Charm | Кровавый амулет |
| `item.hexerei.hexbane_charm` | Hexbane Charm | Амулет-проклятие |
| `container.hexerei.charm_pouch` | Charm Pouch | Мешочек амулетов |
| `tooltip.hexerei.charm.dormant` | Dormant (no charge) | Спит (нет заряда) |

(Charm tooltips reuse vanilla `effect.minecraft.*` keys for the effect line, like the brew tooltip.)

## Balance
- **`MAX_CHARGE = 600`** (10 min of active use at DRAIN 1/sec) — long enough to feel reliable, short enough to require returning to an altar.
- **`DRAIN = 1`/sec** — one charge per active second; the LOW_HEALTH charm drains only when actually low, so it lasts far longer in practice.
- **`RECHARGE = 5`/sec near altar** — full refill in ~120 s, faster than drain so an altar visit tops up several charms.
- **`RECHARGE_POWER_PER_UNIT = 0.2`** → 1 altar power/sec per recharging charm; within altar supply (cauldron brews cost 30–60 total), so charms compete with brewing/rituals for the same power, creating a real economy.
- **Resistance I / Strength I / Weakness I (amplifier 0):** modest, non-stacking-with-itself tiers so 3 charms = breadth, not a power spike.
- **Bloodlust HP gate = 6.0 (3 hearts):** classic low-HP risk/reward; only rewards committed combat.
- **Hexbane radius = 5 blocks:** melee-range aura, not a free ranged debuff.
- **3 slots:** enough to mix a loadout, not enough to wear every buff at once.

## Test Plan
- **Unit (pure logic, no game runtime):**
  - `CharmChargeTest`: `nextCharge` drains when active & no altar (`5,true,false → 4`); holds when inactive (`5,false,false → 5`); recharges & caps near altar (`598,true,true → 600`, not 603); never below 0 (`0,true,false → 0`); recharge takes priority over drain when both true.
  - `CharmChargeTest`: `playerCondition` — ALWAYS true regardless of health; LOW_HEALTH true at `health == param`, false above; `isEffective` false at charge 0 even when wantActive.
  - `CharmDefsTest`: every `CharmDef.effectId` is `minecraft:`-namespaced with non-empty path; `byId` round-trips all three; amplifiers ≥ 0.
  - `CharmPouchNbtTest`: serialize a 3-charm `ItemStackHandler` to NBT and back; charges and ids survive; `isItemValid` rejects a non-charm and rejects a nested pouch.
- **GameTest (`CharmPouchGameTests`, arena):**
  - Give a fake player a pouch containing a fully-charged Ward Charm; tick 20; assert `player.getEffect(Resistance) != null`.
  - Set the contained Ward Charm charge to 0; tick 20; assert `player.getEffect(Resistance) == null` (dormancy).
  - Place a `FakeAltar` (the cauldron-test pattern) in range with power; set charge to 100; tick 40; assert charge increased and altar power decreased.
  - Bloodlust: full player health → no Strength; set health ≤ 6 → Strength applied within 1s.

## Out of Scope (later slices / explicit cuts)
- **Slice 2 — Ritual crafting:** a `SpawnItemRite` that drops a charm ItemEntity at the circle center + `RitualRecipe` entries (sacrifice item + power → charm). Until then charms come from the creative tab.
- **Slice 3 — Reactive charms:** thorns-style retaliation needs `LivingHurtEvent`/`LivingAttackEvent` hooks — deferred to keep Slice 1 free of damage-event wiring.
- Charm tiers / amplifier upgrades, pouch slot expansion beyond 3, charm enchanting, set bonuses.
- Curios API integration (the pouch replaces the need for a dedicated slot; no external dependency added).
- Rendering the pouch as a 3D model or showing contained charms on it.
