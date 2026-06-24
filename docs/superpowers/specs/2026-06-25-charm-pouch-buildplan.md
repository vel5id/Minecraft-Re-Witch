# Hexerei — Charm Pouch & Combat Charms — BUILD PLAN (Slice 1)

**Date:** 2026-06-25
**Implements:** [`2026-06-23-charm-pouch-design.md`](2026-06-23-charm-pouch-design.md) — Slice 1, verbatim balance.
**For:** `mc-mod-implement`, executed task-by-task with TDD. Nothing in this slice exists in `src` yet
(`grep -ri "charm\|talisman\|pouch" src` finds nothing).

This is a *sequencing* document. It does not change the design or any balance number. Every magic number
below is copied from the source spec's **Balance** section. The only new judgement calls are about **build
order** and the **Forge 1.20.1 API surface** — those are flagged explicitly.

---

## 0. Ground rules carried from the design

- Package root: `com.vel5id.hexerei`. Pure logic under `…/charm/`, item classes under `…/item/`, the
  menu/screen under `…/menu/` + `…/client/`, the registry under `…/registry/`, the tick handler under
  `…/charm/` (server, common-side), GameTests under `…/test/`, unit tests under `src/test/java/…/charm/`.
- NBT keys are namespaced `hexerei:…`. Read with `hasTag()` then `getOrCreateTag()`, **never bare `getTag()`**
  (except the read-only `@Nullable` accessor pattern `BrewItem.brewOf` uses, where a null tag is handled).
- Constants live exactly once, in `CharmCharge`: `MAX_CHARGE = 600`, `DRAIN = 1`, `RECHARGE = 5`,
  `RECHARGE_POWER_PER_UNIT = 0.2f`. Do **not** duplicate them in the tick handler or the item.
- The pouch never opens on the client by itself; the server opens the menu via `NetworkHooks.openScreen`.

---

## 1. NEW files to create (full path + one-line purpose)

### Pure logic (no Minecraft runtime — built and unit-tested first)
| # | Path | Purpose |
|---|---|---|
| 1 | `src/main/java/com/vel5id/hexerei/charm/CharmDef.java` | Record: `id, nameKey, effectId, amplifier, ActiveMode mode, int param` + nested `enum ActiveMode { ALWAYS, LOW_HEALTH, AURA_DEBUFF }`. Pure data, no MC imports. |
| 2 | `src/main/java/com/vel5id/hexerei/charm/CharmDefs.java` | Catalog: `WARD`, `BLOODLUST`, `HEXBANE` constants + `BY_ID` `LinkedHashMap` + `byId(String)`. Mirrors `Brews.java` shape exactly. |
| 3 | `src/main/java/com/vel5id/hexerei/charm/CharmCharge.java` | Final class, private ctor: the 4 constants + `playerCondition`, `nextCharge`, `isEffective`. The only place charge math lives. |

### Items
| # | Path | Purpose |
|---|---|---|
| 4 | `src/main/java/com/vel5id/hexerei/item/CharmItem.java` | `extends Item`, holds a final `CharmDef`; `defOf(ItemStack)` resolves def from item identity; `charge(stack)` / `setCharge(stack,int)` read/write NBT `hexerei:Charge`. |
| 5 | `src/main/java/com/vel5id/hexerei/item/CharmPouchItem.java` | `extends Item`; `use` opens the menu server-side; `contents(stack)` / NBT-backed `ItemStackHandler` helpers; `isBarVisible/getBarWidth/getBarColor` aggregate-charge bar. |

### Menu + screen
| # | Path | Purpose |
|---|---|---|
| 6 | `src/main/java/com/vel5id/hexerei/menu/CharmPouchMenu.java` | `extends AbstractContainerMenu`; 3 charm-only `SlotItemHandler`s over the held stack's `ItemStackHandler` + 36 player-inventory slots; `quickMoveStack`, `stillValid`, slot `isItemValid` rejects pouch/non-charm. Has the server ctor **and** the `IForgeMenuType.create` client ctor `(int id, Inventory inv, FriendlyByteBuf buf)`. |
| 7 | `src/main/java/com/vel5id/hexerei/client/CharmPouchScreen.java` | `@OnlyIn(Dist.CLIENT)` `AbstractContainerScreen<CharmPouchMenu>`; binds `textures/gui/charm_pouch.png`, draws bg + label. |

### Registry
| # | Path | Purpose |
|---|---|---|
| 8 | `src/main/java/com/vel5id/hexerei/registry/HexereiMenus.java` | `DeferredRegister<MenuType<?>>` on `ForgeRegistries.MENU_TYPES`; `CHARM_POUCH = register("charm_pouch", () -> IForgeMenuType.create(CharmPouchMenu::new))`. |

### Server tick
| # | Path | Purpose |
|---|---|---|
| 9 | `src/main/java/com/vel5id/hexerei/charm/CharmTickHandler.java` | Static `tick(ServerLevel)`; per-player charm drain/recharge/altar-debit/effect-apply. Called from `HexereiLevelEvents` at `gt % 20 == 0`. Holds **no** constants. |

### Assets (10–17)
| # | Path | Purpose |
|---|---|---|
| 10 | `src/main/resources/assets/hexerei/textures/item/charm_pouch.png` | 16×16, drawstring pouch, deep purple/leather. |
| 11 | `src/main/resources/assets/hexerei/textures/item/ward_charm.png` | 16×16, round silver ward sigil. |
| 12 | `src/main/resources/assets/hexerei/textures/item/bloodlust_charm.png` | 16×16, red bead/fang charm. |
| 13 | `src/main/resources/assets/hexerei/textures/item/hexbane_charm.png` | 16×16, black thorn/eye charm. |
| 14 | `src/main/resources/assets/hexerei/textures/gui/charm_pouch.png` | 176×166 container bg, one 3-slot row + player inventory. |
| 15 | `src/main/resources/assets/hexerei/models/item/charm_pouch.json` | `item/generated`, layer0 → `hexerei:item/charm_pouch`. |
| 16 | `src/main/resources/assets/hexerei/models/item/ward_charm.json` | `item/generated`, layer0 → `hexerei:item/ward_charm`. |
|  ↳ | `…/models/item/bloodlust_charm.json`, `…/models/item/hexbane_charm.json` | same `item/generated` pattern (2 more files). |

> **Texture warning (project convention):** a model pointing at a missing texture renders **magenta**. All
> four item PNGs and the GUI PNG must land before the first in-game/screenshot check. Hand-author or derive
> from vanilla art; do not ship placeholder-less models.

### Tests
| # | Path | Purpose |
|---|---|---|
| 18 | `src/test/java/com/vel5id/hexerei/charm/CharmChargeTest.java` | `nextCharge` / `playerCondition` / `isEffective` cases from the Test Plan. |
| 19 | `src/test/java/com/vel5id/hexerei/charm/CharmDefsTest.java` | every `effectId` is `minecraft:`-namespaced non-empty path; `byId` round-trips all three; amplifiers ≥ 0. |
| 20 | `src/test/java/com/vel5id/hexerei/charm/CharmPouchNbtTest.java` | serialize a 3-charm `ItemStackHandler` → NBT → back; charges + ids survive; `isItemValid` rejects a non-charm and a nested pouch. **See §6 caveat — may need to assert via a pure helper, not a live `ItemStackHandler`.** |
| 21 | `src/main/java/com/vel5id/hexerei/test/CharmPouchGameTests.java` | the 4 in-world cases from the Test Plan (Ward applies, dormancy at 0, altar recharge+debit, Bloodlust HP gate). |

---

## 2. EXISTING files to edit — exactly what to add

| File | Edit |
|---|---|
| `registry/HexereiItems.java` | Add 4 `RegistryObject<Item>`: `CHARM_POUCH = ITEMS.register("charm_pouch", () -> new CharmPouchItem(new Item.Properties().stacksTo(1)))`; `WARD_CHARM = …("ward_charm", () -> new CharmItem(CharmDefs.WARD, new Item.Properties().stacksTo(1)))`; same for `BLOODLUST_CHARM` (`CharmDefs.BLOODLUST`) and `HEXBANE_CHARM` (`CharmDefs.HEXBANE`). Charms `stacksTo(1)` so per-stack charge NBT is never merged across a stack. |
| `registry/HexereiCreativeTabs.java` | In the `displayItems` lambda add `output.accept(HexereiItems.CHARM_POUCH.get()); output.accept(HexereiItems.WARD_CHARM.get()); output.accept(HexereiItems.BLOODLUST_CHARM.get()); output.accept(HexereiItems.HEXBANE_CHARM.get());`. Charms enter the tab at full charge — see §5. |
| `HexereiMod.java` constructor | Add `HexereiMenus.MENUS.register(modBus);` next to the other `register(modBus)` calls (after `HexereiParticles`, before `HexereiNetwork.register()`). |
| `HexereiLevelEvents.java` `onLevelTick` | Inside the existing `ServerLevel sl` block add `if (gt % 20 == 0) { CharmTickHandler.tick(sl); }`. Reuse the existing `gt` variable; do not add a second tick subscriber. |
| `client/HexereiClient.java` | Add a `@SubscribeEvent public static void onClientSetup(FMLClientSetupEvent e)` (MOD bus, `Dist.CLIENT`) that calls `e.enqueueWork(() -> MenuScreens.register(HexereiMenus.CHARM_POUCH.get(), CharmPouchScreen::new))`. `MenuScreens.register` is **not** thread-safe → must be inside `enqueueWork`. |
| `assets/hexerei/lang/en_us.json` | Add the 6 keys from the design's Assets table (en_us column): `item.hexerei.charm_pouch`, `item.hexerei.ward_charm`, `item.hexerei.bloodlust_charm`, `item.hexerei.hexbane_charm`, `container.hexerei.charm_pouch`, `tooltip.hexerei.charm.dormant`. Insert before the closing `}` (the file is a flat object; keep the trailing-comma discipline of the existing file). |
| `assets/hexerei/lang/ru_ru.json` | Same 6 keys, ru_ru column values from the design table. |

No edits to `HexereiNetwork` (vanilla menu channel carries slot sync; see design §Client/Server). No new
`HexereiTags`, `HexereiBlocks`, `HexereiBlockEntities` entries — this slice is item-only.

---

## 3. Build order (the project's way: pure logic + unit tests FIRST)

Each step ends green before the next begins. Steps 1–3 need **no** Minecraft runtime, so they go through
`./gradlew --no-daemon test` only.

**Step 1 — `CharmDef` + `CharmDefs` + `CharmDefsTest` (TDD).**
Write `CharmDefsTest` asserting the three defs exist, `byId` round-trips, every `effectId` matches
`^minecraft:[a-z_]+$` with non-empty path, amplifiers ≥ 0. Then write `CharmDef`/`CharmDefs` to pass.
Values (verbatim): `WARD("ward", "item.hexerei.ward_charm", "minecraft:resistance", 0, ALWAYS, 0)`;
`BLOODLUST("bloodlust", "item.hexerei.bloodlust_charm", "minecraft:strength", 0, LOW_HEALTH, 6)`;
`HEXBANE("hexbane", "item.hexerei.hexbane_charm", "minecraft:weakness", 0, AURA_DEBUFF, 5)`.

**Step 2 — `CharmCharge` + `CharmChargeTest` (TDD).**
Write the test cases from the Test Plan first:
`nextCharge(5,true,false)==4`; `nextCharge(5,false,false)==5`; `nextCharge(598,true,true)==600`;
`nextCharge(0,true,false)==0`; recharge-beats-drain when both true (`nextCharge(100,true,true)==105`).
`playerCondition(WARD, anyHealth)==true`; `playerCondition(BLOODLUST, 6f)==true`,
`playerCondition(BLOODLUST, 6.1f)==false`; `isEffective(0,true)==false`, `isEffective(1,true)==true`,
`isEffective(50,false)==false`. Then implement `CharmCharge` exactly per the design's three method bodies.

**Step 3 — confirm `./gradlew --no-daemon test` green** (only the new pure tests run; nothing else touched).

**Step 4 — `CharmItem`.** Holds `CharmDef`; `charge`/`setCharge` over NBT `hexerei:Charge`;
`defOf(ItemStack) → ((CharmItem) stack.getItem()).def()` guarded by `stack.getItem() instanceof CharmItem`.
Optional: `appendHoverText` emits `effect.minecraft.*` line (reused vanilla key) + `tooltip.hexerei.charm.dormant`
when `charge==0`. No new constants.

**Step 5 — `CharmPouchItem`.** `use` (server branch) opens menu (§4). `contents(stack)` materializes up to 3
`ItemStack`s from the NBT-backed handler. Bar: `isBarVisible` = any contained charm; `getBarWidth` =
`round(13 * aggregate)` where `aggregate = sum(charge) / (MAX_CHARGE * slotsFilled)`; `getBarColor` a fixed
purple. Guard: contents helper must tolerate a never-opened pouch (no `hexerei:Items` tag) → empty list.

**Step 6 — `HexereiItems` + `HexereiCreativeTabs` edits (§2).** Build the jar
(`./gradlew --no-daemon build`) — items now register; verify no magenta in the creative tab once textures
(Step 10) land. (Order note: you may defer the visual check to after Step 10 but the registry must compile now.)

**Step 7 — Menu + registry: `CharmPouchMenu`, `HexereiMenus`, `HexereiMod` + `HexereiClient` wiring.**
This is the tricky piece — see §4 for the exact API. Build with `./gradlew --no-daemon build`.

**Step 8 — `CharmPouchScreen`.** Pure client render; bg blit + labels. Needs GUI texture (Step 10) to not
render magenta, but compiles independently.

**Step 9 — `CharmTickHandler` + `HexereiLevelEvents` hook.** Port the design's 5-step per-player loop:
find first pouch (main inv + offhand) → read contents → compute `altarAvailable` via
`AltarPowerManager.get(sl).query(level, player.blockPosition())` filtering
`r.source().getCurrentPower() >= RECHARGE_POWER_PER_UNIT * RECHARGE` → per charm:
`wantActive = playerCondition`; `after = nextCharge(before, wantActive, altarAvailable)`; on recharge
`debit = RECHARGE_POWER_PER_UNIT * (after - before)` via `r.source().consumePower(debit)`, **revert
`after = before` if the debit returns false** (power-first, mirrors `CauldronBlockEntity.collectBrew`);
write `after` back; if `isEffective(after, wantActive)` apply the effect:
`ALWAYS`/`LOW_HEALTH` → `player.addEffect(new MobEffectInstance(effect, 40, amplifier, true, false, true))`;
`AURA_DEBUFF` → same on each `Monster` within `param` blocks (`level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(param))`).
Resolve the `MobEffect` from the `effectId` string via `BuiltInRegistries.MOB_EFFECT.get(new ResourceLocation(effectId))`
(same lookup `BrewItem` already uses for effects). Persist changed charges back into the pouch stack NBT.

**Step 10 — Assets (textures, GUI, models, lang).** Land all 4 item PNGs + GUI PNG + 4 models + both lang
files. Then run the game / take a screenshot: creative tab shows non-magenta charms + pouch; right-click
opens the GUI; drag a charm in; charge bar visible.

**Step 11 — GameTests: `CharmPouchGameTests`.** Use the `FakeAltar` pattern already in `CauldronGameTests`
(a test-local `IPowerSource` injected via `AltarPowerManager.get(level).register(fake)`, unregistered on
success). The 4 cases from the Test Plan. Run `./gradlew --no-daemon runGameTestServer`.

**Step 12 — full verify:** `./gradlew --no-daemon test`, `runGameTestServer`, `build`, and an in-game screenshot
(`/build-verify`). Update `DESIGN-NOTES.md` with a "Charm Pouch slice" block listing the 8 balance numbers as
deliberate choices (the design's Balance section is the source text).

---

## 4. The tricky piece — `MenuType` via `IForgeMenuType.create` + NBT-backed `ItemStackHandler`

This is the one genuinely non-mechanical part. Concrete Forge 1.20.1 API:

**Registry (`HexereiMenus`):**
```java
public static final DeferredRegister<MenuType<?>> MENUS =
        DeferredRegister.create(ForgeRegistries.MENU_TYPES, HexereiMod.MODID);

public static final RegistryObject<MenuType<CharmPouchMenu>> CHARM_POUCH =
        MENUS.register("charm_pouch", () -> IForgeMenuType.create(CharmPouchMenu::new));
```
`IForgeMenuType.create` takes an `IContainerFactory<CharmPouchMenu>` whose SAM is
`(int windowId, Inventory inv, FriendlyByteBuf data) -> CharmPouchMenu`. That is the **client** ctor.

**Opening (server, in `CharmPouchItem.use`):**
```java
if (!level.isClientSide && player instanceof ServerPlayer sp) {
    boolean offhand = hand == InteractionHand.OFF_HAND;
    MenuProvider provider = new SimpleMenuProvider(
        (id, inv, p) -> new CharmPouchMenu(id, inv, offhand),               // server ctor
        Component.translatable("container.hexerei.charm_pouch"));
    NetworkHooks.openScreen(sp, provider, buf -> buf.writeBoolean(offhand)); // extra data
}
return InteractionResultHolder.success(player.getItemInHand(hand));
```

**Two constructors in `CharmPouchMenu`:**
```java
// client side — reads the extra-data boolean back out
public CharmPouchMenu(int id, Inventory inv, FriendlyByteBuf data) {
    this(id, inv, data.readBoolean());
}
// shared real ctor
public CharmPouchMenu(int id, Inventory inv, boolean offhand) {
    super(HexereiMenus.CHARM_POUCH.get(), id);
    ItemStack pouch = offhand ? inv.player.getOffhandItem() : inv.player.getMainHandItem();
    ItemStackHandler handler = readHandler(pouch);   // 3-slot, deserialized from pouch NBT
    // add 3 SlotItemHandler (charm-only) + 36 player slots
}
```

**NBT-backed handler that serializes into the held stack:** the 3-slot handler must write back into the
held pouch's NBT on every change. The standard backpack pattern is to subclass `ItemStackHandler` and
override `onContentsChanged`:
```java
ItemStackHandler handler = new ItemStackHandler(3) {
    @Override protected void onContentsChanged(int slot) {
        pouch.getOrCreateTag().put("hexerei:Items", this.serializeNBT());
    }
    @Override public boolean isItemValid(int slot, ItemStack s) {
        return s.getItem() instanceof CharmItem;          // rejects pouch + any non-charm
    }
};
if (pouch.hasTag() && pouch.getTag().contains("hexerei:Items")) {
    handler.deserializeNBT(pouch.getTag().getCompound("hexerei:Items"));
}
```
Wrap the 3 slots with Forge's `SlotItemHandler` (which already honours `isItemValid`). Because the slot's
backing stack is the **held** stack, capture the held `ItemStack` reference in the ctor — it is the live
stack, so writes persist. `stillValid` must re-check the player is still holding the pouch in that hand.

**Why this is the trap:** (a) `IForgeMenuType.create`'s factory is the *client* ctor only — the server uses
the `SimpleMenuProvider` lambda; mixing them up makes the screen open with an empty handler. (b) The handler
must serialize on `onContentsChanged`, not on menu close, or quick-move + immediate inventory action can drop
the write. (c) The slot must reject `CharmPouchItem` so a pouch cannot nest in itself (design guard).

---

## 5. Decisions an implementer should NOT silently make (carry these as given)

- **Charms enter the creative tab fully charged.** The design says "obtained from the creative tab (enough to
  build and GameTest the full loop)" but does not state the spawn charge. Spawning at `MAX_CHARGE` is required
  for the Ward GameTest ("fully-charged Ward Charm"). Implement `CharmItem` so a freshly-created stack with no
  `hexerei:Charge` tag reads as `MAX_CHARGE` (treat absent tag = full), so creative-tab and crafted charms are
  full without extra wiring. This is an inference, **flagged below as [UNVERIFIED]**.
- **Charms `stacksTo(1)`** (so per-stack charge NBT never merges). The design implies per-charm charge in NBT;
  stack size 1 is the safe reading. Flagged below.

---

## 6. [UNVERIFIED] / Forge-1.20.1 API confirmations needed

1. **`ForgeRegistries.MENU_TYPES` for `DeferredRegister`** — confirm this is the correct registry key in
   1.20.1 (vs. `Registries.MENU` via vanilla). The established mod-bus + `DeferredRegister.create(ForgeRegistries.…)`
   pattern (used for `ITEMS`, `BLOCKS`, particles) strongly implies `ForgeRegistries.MENU_TYPES` exists and is
   correct, but it is the one registry this codebase has never touched — **verify at compile time**. `[UNVERIFIED]`
2. **`NetworkHooks.openScreen(ServerPlayer, MenuProvider, Consumer<FriendlyByteBuf>)`** signature in 1.20.1 —
   confirm the 3-arg overload (with extra-data writer) exists; it is the standard 1.20.1 API but unused here so far. `[UNVERIFIED]`
3. **`IForgeMenuType.create` import path** — `net.minecraftforge.common.extensions.IForgeMenuType` in 1.20.1.
   Confirm at compile. `[UNVERIFIED]`
4. **Absent-tag-means-full-charge** (§5) — design does not specify creative spawn charge; chosen to make the
   "fully-charged Ward Charm" GameTest pass without item-stack post-processing in the creative tab. `[UNVERIFIED]`
   — revisit if the design author intended charms to spawn empty.
5. **`CharmPouchNbtTest` without a MC runtime** — `ItemStackHandler.serializeNBT/deserializeNBT` and
   `ItemStack` NBT touch Minecraft classes, which the pure `src/test/java` tier deliberately avoids (it never
   boots Minecraft). The Test Plan names this as a unit test. **Confirm whether `ItemStackHandler`/`ItemStack`
   are loadable in the JUnit tier**; if not, this assertion must move into `CharmPouchGameTests` (in-world) and
   the unit test should instead cover a *pure* extraction of the slot-validity predicate
   (`s -> s.getItem() instanceof CharmItem` cannot be pure → extract the def-resolution / charge round-trip
   logic into a pure helper that the unit test can hit). `[UNVERIFIED]` — resolve before writing test #20.
6. **`Monster` import** for `AURA_DEBUFF` — `net.minecraft.world.entity.monster.Monster` in 1.20.1; confirm the
   `getEntitiesOfClass(Monster.class, AABB)` reads hostiles as intended (the design says "hostile `Monster`").
   `[UNVERIFIED]` (low risk).

All **balance numbers** (`MAX_CHARGE 600`, `DRAIN 1`, `RECHARGE 5`, `RECHARGE_POWER_PER_UNIT 0.2`, HP gate 6.0,
Hexbane radius 5, 3 slots, amplifier 0 / tier I) are **unchanged** from the source design and are not subject to
implementer choice.

---

## 7. Reference points in the existing codebase (read these, don't reinvent)

- **Altar power query + power-first debit:** `blockentity/CauldronBlockEntity.java` `collectBrew()` (lines ~200–227)
  — `AltarPowerManager.get(server).query(level, pos)` then `r.source().consumePower(need)` in distance order.
- **`FakeAltar` test power source + register/unregister:** `test/CauldronGameTests.java` `cauldronCollectsWithPower`.
- **Pure catalog shape (defs + `BY_ID` + `byId`):** `brewing/Brews.java`.
- **Item-NBT identity pattern (`of`, `@Nullable …Of(stack)`, `getOrCreateTag`):** `item/BrewItem.java`.
- **Effect-id-string → `MobEffect` lookup:** `item/BrewItem.java` (`BuiltInRegistries.MOB_EFFECT.get(new ResourceLocation(...))`).
- **Record packet (not needed here, but the C2S shape):** `network/CycleRiteC2SPacket.java`.
- **Server tick hook gating:** `HexereiLevelEvents.onLevelTick` (`gt % N == 0`, `ServerLevel sl`).
- **Client MOD-bus setup subscriber:** `client/HexereiClient.java` (`@Mod.EventBusSubscriber(... Bus.MOD, Dist.CLIENT)`).
- **GameTest holder boilerplate:** `test/CauldronGameTests.java` (`@GameTestHolder(MODID)`, `@PrefixGameTestTemplate(false)`).
- **Lang file shape (flat JSON object, trailing keys):** `assets/hexerei/lang/en_us.json` tail.

---

## 8. Out of scope (unchanged from design — do NOT build)

Slice 2 ritual crafting (`SpawnItemRite` + `RitualRecipe` charm entries), Slice 3 reactive/thorns charms
(`LivingHurtEvent`), charm tiers/upgrades, pouch slot expansion, charm enchanting, set bonuses, Curios
integration, 3D pouch model / rendering contained charms. Charms come from the creative tab only this slice.
