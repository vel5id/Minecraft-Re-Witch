# Hexerei — Sealed Amulets (unify the flat charm system into Bond)

**Date:** 2026-06-26
**Extends:** `soul/` vector core (the "запечатать" verb of Модель §7); replaces the `item/Charm*` flat system.
**Skill:** mc-mod-ideate → hands off to mc-mod-implement.

---

## Constitution gate (litmus verdict — all 9 pass)

| # | Question | Verdict |
|---|---|---|
| 1 | Выводится из Закона? | ✅ "Запечатать" — один из четырёх глаголов Модели §7. |
| 2 | Цена + связь с общим ресурсом? | ✅ Носка → `debt += drawRate` (поле `PlayerSoulData`), печаль grinding `seal.integrity` → разрушение. |
| 3 | Несколько применений? | ✅ Один механизм = N амулетов (по доменам); домен берётся из реагента-жертвы. |
| 4 | Предсказуемо? | ✅ Эффект = f(домен); lifespan = f(долг). Без костей. |
| 5 | Питает петлю / углубляет? | ✅ Разрушение = новая добыча → новый ритуал запечатывания ⟳. |
| 6 | Диегетично? | ✅ Дух запечатан в предмет; природа духа = его домен. |
| 7 | Свободно от запрещённого? | ✅ Убирает параллельную систему (плоские чармы); не бесплатная сила (конечный срок); не косметика. |
| 8 | Сон? | N/A (не этот срез). |
| 9 | Присутствия — поведение из состояния? | Частично: `Mark` + `totalDebt` записываются для будущего PresenceEntity; глубокий отклик мира — out of scope. |

**Article III loop:** preserved — power source (essence spent in the sealing rite) and danger source (`debt`/`disturbance` written at seal + wear) are the same act's fields.

---

## Player Loop

- **Trigger (creation):** a **sealing rite** at a ritual circle. Drop a domain-defining reagent as the sacrifice + pay altar essence → the rite spawns a sealed `hexerei:amulet` at the circle centre.
- **Trigger (use):** place the amulet in a carried **Charm Pouch** (existing container). The tick reconciles the pouch ↔ `PlayerSoulData.amulets`.
- **Cost:** while in the pouch, the sealed spirit **draws debt** every tick (`bond.disposition.debt += DRAW_RATE`) and its **resentment grinds the seal** (`seal.integrity -= grind`). No altar recharge — the cost is the witch's own soul-debt, not nature-power (Article III pure).
- **Effect:** a buff **derived from the sealed bond's `Correspondence` domain** (see mapping below), applied once per second.
- **Duration:** permanent **until the seal's `integrity` reaches 0** → the amulet **shatters** (item removed, a `disturbance` spike + the `Mark` persists). Re-sealing costs a new rite + reagent + essence.
- **Feedback:** sealing = amethyst-chime + end-rod burst (SpawnItemRite precedent); wearing = ambient/hidden-particle/visible-icon effect instance (CharmTickHandler precedent); shatter = large smoke + sound.

---

## Registry Objects

| Kind | id | Notes |
|---|---|---|
| Item | `hexerei:amulet` | **New.** The `SealedBond` carrier. One item (mirrors the `brew` one-item-tinted-by-content pattern). Carries a `Bond` in NBT. Tinted/overlaid by domain. |
| RitualRecipe | `hexerei:seal_amulet_forest` | SMALL ring, sacrifice `hexerei:celandine`, cost 80, `new SealAmuletRite(FOREST)`. |
| RitualRecipe | `hexerei:seal_amulet_death` | SMALL ring, sacrifice `hexerei:crowseye_berry`, cost 80, `new SealAmuletRite(DEATH)`. |
| RitualRecipe | `hexerei:seal_amulet_threshold` | SMALL ring, sacrifice `hexerei:garlic`, cost 80, `new SealAmuletRite(THRESHOLD)`. |
| Rite | `SealAmuletRite` | **New class.** `Rite` impl; one class, domain in ctor (SpawnItemRite-style data-driven instances). |
| **Retire** | `ward_charm`, `bloodlust_charm`, `hexbane_charm` | The 3 flat `CharmItem`s + `CharmDef`/`CharmDefs`/`CharmCharge` are removed; their behaviours migrate into the one `amulet` (domain-driven). |
| Keep | `charm_pouch`, `bone_charm` | Pouch = the wear container (unchanged). `bone_charm` is an **altar artefact** (`ArtefactItem`), not a worn charm — untouched. |

**Sacrifice disambiguation:** `celandine` / `crowseye_berry` / `garlic` are chosen because **no existing rite uses them** (verify against `RitualRecipes.ALL`: tempest=mandrake, verdant=artichoke, manifest_chalk=wormwood, bound_beast=wolfsbane, waning_moon=belladonna, eclipse=wolfsbane, hungering=obsidian_skull). Distinct sacrifice ids on a SMALL ring → `RitualRecipes.match` is unambiguous.

---

## Mechanics (server-side)

### 1. `SealAmuletRite implements Rite` (`ritual/SealAmuletRite.java`, new)
```
ctor(domain: Correspondence)
perform(ServerLevel level, BlockPos center):
    Bond bond = new Bond(
        bondId        = UUID.randomUUID(),
        spiritType    = ResourceLocation("hexerei", domain.key() + "_warden"),  // diegetic flavour id
        domain        = this.domain,
        disposition   = Disposition.EMPTY,            // fresh spirit: zeroed
        seal          = new SealRef(1.0f),             // full hold
        marks         = List.of(new Mark(MarkScope.DOMAIN, this.domain, SEAL_MARK_SEVERITY)),
        ledger        = List.of(),                     // (no Act handle here yet; Slice D builds the Act upstream)
        bornTick      = level.getGameTime(),
        lastResolvedTick = level.getGameTime());
    ItemStack stack = new ItemStack(HexereiItems.AMULET.get());
    AmuletItem.writeBond(stack, bond);                 // NBT (see persistence)
    spawn ItemEntity at center (SpawnItemRite geometry) with zero velocity
    sound: AMETHYST_BLOCK_CHIME; particles: END_ROD burst
    Rites.addRitualTaint(level, new ChunkPos(center), this.domain, SEAL_DISTURBANCE)  // feeds the loop
```
`Rite.perform(level, center)` has **no player handle** (existing contract), so the amulet drops un-owned at the circle — same deferred-owner caveat as `BoundBeastRite` (`BoundBeastRite.java:14`). The witch who seals it is whoever picks it up and pouches it. Owner-binding-at-creation is a follow-up (requires the planned player-handle addition to `Rite`).

### 2. `AmuletItem extends Item` (`item/AmuletItem.java`, new) — the SealedBond carrier
- `static Bond readBond(ItemStack)` / `writeBond(ItemStack, Bond)` — round-trip the `Bond` via `Bond.CODEC` ↔ NBT (`NbtOps`) under key `hexerei:sealed_bond`. (Forge 1.20.1 has **no `DataComponentType`** — that's 1.20.5+/NeoForge. NBT is the codebase's existing item-data path, same as `CharmItem`'s charge.)
- `static boolean isSealed(ItemStack)` — NBT present.
- `effectFor(domain)` delegates to the pure `SealedAmulet` class (below) for the testable mapping.
- Tooltip: name + the bound spirit's domain + current `debt`/`integrity` (read from NBT) so the player sees the cost accruing.

### 3. `SealedAmulet` (`item/SealedAmulet.java`, new) — **pure, unit-tested first (TDD)**
All the decidable logic, no MC runtime:
```
// domain → vanilla effect, amplifier, ActiveMode (the spirit's "nature")
record AmuletEffect(String effectId, int amplifier, ActiveMode mode, int param)
static AmuletEffect effectFor(Correspondence domain):
    FOREST    -> ("minecraft:resistance", 0, ALWAYS,    0)   // wood-warden's endurance   [was WARD]
    DEATH     -> ("minecraft:weakness",   0, AURA_DEBUFF, 5) // malice projected outward   [was HEXBANE]
    THRESHOLD -> ("minecraft:strength",   0, LOW_HEALTH, 6)  // stirs near death            [was BLOODLUST]
    // other domains -> none yet (no amulet recipe targets them this slice)

// per-second debt accrual on a worn amulet's bond
static float debtDelta(float drawRate) -> drawRate   // bond.debt += drawRate each second tick

// resentment grows from unpaid debt (loyalty offsets)
static float resentmentDelta(float debt, float loyalty, float rate)
    -> max(0, rate * max(0, debt - loyalty))

// the seal grinds down by resentment
static float integrityDelta(float resentment, float grindRate)
    -> -grindRate * resentment

static boolean shouldBreak(float integrity) -> integrity <= 0f
```
Constants (all `[UNVERIFIED]`, first-pass; tunable; tuned so an actively-worn amulet lasts ~1–2h):
`DRAW_RATE = 0.0003f`/sec-tick · `RESENT_RATE = 0.0002f` · `GRIND_RATE = 0.02f` · `SEAL_MARK_SEVERITY = 0.5f` · `SEAL_DISTURBANCE = 30f`.

### 4. `AmuletTickHandler` (`item/AmuletTickHandler.java`, new — supersedes `CharmTickHandler`)
Driven from `HexereiLevelEvents.onLevelTick` on the existing 20-tick boundary (replaces the `CharmTickHandler.tick` call), server-only:
```
for each ServerPlayer p:
    pouch = findPouch(p)                      // reuse CharmPouchItem.readHandler
    worn = []                                 // bondIds currently in pouch amulets
    for each slot in pouch handler:
        stack = slot; if !AmuletItem.isSealed(stack): continue
        bond = AmuletItem.readBond(stack)
        worn.add(bond.bondId)
        // --- cost ---
        disp = bond.disposition
        disp = disp.plus(new Disposition(debtDelta(DRAW_RATE), resentmentDelta(...), 0, 0))
        seal = bond.seal.integrity + integrityDelta(disp.resentment, GRIND_RATE)
        if shouldBreak(seal):
            remove the stack from the slot                     // amulet shatters
            Disturbance.add(level, p.chunk, bond.domain, BREAK_DISTURBANCE)
            // the Mark persists in the (now-broken) record conceptually; v1 simply drops the item
            play break SFX/particles
            continue
        bond = bond.withDisposition(disp).withSeal(new SealRef(seal))
        AmuletItem.writeBond(stack, bond)
        // --- effect ---
        AmuletEffect fx = SealedAmulet.effectFor(bond.domain)
        apply MobEffectInstance (duration 40, ambient, hidden particles, visible icon) —
            to the player (ALWAYS/LOW_HEALTH if condition met) or to Monsters in `param` radius (AURA_DEBUFF)
    // --- reconcile PlayerSoulData.amulets with the pouch ---
    psd = PlayerSoulData for p
    for id in psd.amulets() not in worn: psd.removeAmulet(id)
    for id in worn not in psd.amulets(): psd.wearAmulet(id)
    psd.setTotalDebt(Σ debt of worn bonds)     // Модель §3: totalDebt = Σ over worn
```
Effect application mirrors `CharmTickHandler.applyEffect`/`instance` exactly (the `EFFECT_DURATION=40`, `ambient,true / particles,false / icon,true` pattern).

### 5. `RitualRecipes` (edit) — add the 3 recipes
Append to `ALL` after `HUNGERING`: `SEAL_AMULET_FOREST`, `SEAL_AMULET_DEATH`, `SEAL_AMULET_THRESHOLD`. `RitualChalkItem`'s scroll order picks them up automatically (they appear in the chalk cycle).

### 6. `HexereiItems` / `HexereiCreativeTabs` (edit)
- Register `AMULET = ITEMS.register("amulet", () -> new AmuletItem(new Item.Properties().stacksTo(1)))`.
- Remove `WARD_CHARM`/`BLOODLUST_CHARM`/`HEXBANE_CHARM` registry objects + their creative-tab lines; add `AMULET` to the tab.
- Remove `item/CharmItem.java`, `item/CharmDef.java`, `item/CharmDefs.java`, `item/CharmCharge.java`, `item/CharmTickHandler.java` (their logic folds into `AmuletItem`/`SealedAmulet`/`AmuletTickHandler`). **Keep** `item/ActiveMode.java` (reused by `SealedAmulet.effectFor`) and `item/CharmPouchItem.java` (the container).

---

## Persistence

- **The `Bond` lives in the amulet ItemStack's NBT** (`hexerei:sealed_bond`, via `Bond.CODEC` + `NbtOps`) — the amulet *carries its bond with it* (Модель §3 `SealedBond`). Round-trips in a plain unit test (the `Bond` codec is already NBT-tested).
- **`PlayerSoulData.amulets`** (`List<UUID>`) already persists (capability, copied on respawn — Модель §3 "debt survives death"). The tick reconciles it; wearing survives death.
- **No new capability, no new `SavedData`.** The amulet's evolving `disposition`/`seal` are written back to the item NBT each tick — the item *is* the store.

## Client/Server

- **Server-authoritative:** all debt/integrity/effect logic in `AmuletTickHandler` (server tick), exactly like the current `CharmTickHandler`.
- **Client:** the amulet item reads its NBT for the tooltip/domain tint (client has the stack). The pouch GUI (`CharmPouchScreen`) is unchanged — it just now holds `amulet` items.
- **Networking:** **no new packet.** The pouch handler sync is already server-authoritative; the amulet's effect is applied server-side. (When a future slice wants a visible "worn amulet glow", add a sync packet then — out of scope here.)

---

## Assets

- **Item texture:** `items/amulet.png` (16×16) — one base pendant; tinted per domain via an overlay (`items/amulet_forest.png` / `_death.png` / `_threshold.png` colour wash, the brew-tint pattern) OR three model variants keyed off an NBT-as-model. Simplest v1: **one texture, one model**, domain shown only in the tooltip + effect (defer per-domain art to a polish pass). Generate via the existing `WARRANTLY/sprite_pipeline.py`.
- **Item model:** `items/amulet.json` parent `minecraft:item/generated`.
- **Lang (en_us + ru_ru BOTH — project ships bilingual):**
  - `item.hexerei.amulet` / `item.hexerei.amulet` (Sealed Amulet / Запечатанный амулет)
  - `ritual.hexerei.seal_amulet_forest` / `_death` / `_threshold` (Rite of the Forest Ward / Обряд лесного стража, etc.)
  - tooltip lines for domain/debt/integrity (en + ru).

---

## Balance (every magic number, first-pass `[UNVERIFIED]`)

| Number | Value | Justification |
|---|---|---|
| Sealing recipe `powerCost` | 80 | Between VERDANT(60) and TEMPEST(100); a deliberate sacrifice, not trivial. |
| `DRAW_RATE` (debt/sec worn) | 0.0003 | ~1.08 debt/hour → resentment begins grinding meaningfully within an hour of continuous wear. |
| `RESENT_RATE` | 0.0002 | resentment follows debt slowly; loyalty (future APPEASE rite) offsets. |
| `GRIND_RATE` | 0.02 | at resentment ~1, integrity 1→0 in ~50 sec — but resentment reaches 1 only after long unpaid debt, so real lifespan ≈ 1–2h. |
| `SEAL_MARK_SEVERITY` | 0.5 | raises the resentment floor (Грамматика decay) — the sealing itself is a small grievance. |
| `SEAL_DISTURBANCE` | 30 | the act of sealing a spirit disturbs its domain (Article III), mirroring `HungeringRite.AWAKENING_SCAR=30`. |
| `BREAK_DISTURBANCE` | 50 | a broken seal releases the spirit violently into the place — bigger than the sealing. |
| Effect amplifiers | all 0 (level I) | matches the retired charms; no runaway. |
| Stacking | allowed (multiple pouch slots) | emergent self-limit: 3 amulets = 3× debt = 3× faster decay on all. `[UNVERIFIED]` balance. |

**Invariant:** `DRAW_RATE`/`GRIND_RATE` must make a worn amulet **finite-lifespan** — permanent free power is Constitution-forbidden (Art. III). The pure `SealedAmulet` unit test asserts `shouldBreak` is reachable from `integrity=1.0` under sustained `DRAW_RATE`.

---

## Test Plan

### Pure unit tests (`src/test/java`, no MC runtime — TDD first)
- `SealedAmuletTest`:
  - `effectFor` maps the 3 domains to the 3 expected `(effectId, amp, mode, param)`; other domains → none.
  - `debtDelta`/`resentmentDelta`/`integrityDelta` arithmetic at representative values.
  - **Lifespan invariant:** starting `integrity=1.0`, `debt=0`, iterating `debtDelta`+`resentmentDelta`+`integrityDelta` for N steps **reaches `shouldBreak`** within a bounded N (no infinite-life regression).
  - `shouldBreak` boundary at exactly 0.
- `AmuletItemTest` (NBT round-trip, like the existing `Bond` codec test): `writeBond`→`readBond` is identity for a sample sealed bond.

### GameTests (`src/main/java/.../test/`, in-world)
- `SealingRiteGameTest`: place a SMALL circle + ritual sigil, drop `celandine` as sacrifice, power an altar to ≥80, activate → assert a `hexerei:amulet` `ItemEntity` spawns at the centre **and** `AmuletItem.readBond` of its stack has `domain==FOREST` + `seal.integrity==1.0` + one FOREST mark.
- `AmuletWearGameTest` (mock player via the `processPlayer` hook, like `CharmPouchGameTests`): pouch a sealed amulet, drive `AmuletTickHandler.processPlayer` for >1 second → assert the wearer has the Resistance effect (FOREST) **and** the bond's NBT `debt` increased.

(In-world altar-power scans are flaky in the shared GameTest arena — per the two-tier strategy, the sealing rite's *logic* is the unit test; the GameTest is the integration smoke. Mark large-scan tests `required=false` if needed.)

---

## Out of Scope (explicit cuts — follow-up slices)

- **PresenceEntity / "the world turns on you"** — the deep Article-III payoff of accrued `debt`/`Mark` (presences seeing `hasGrievance`) needs the unbuilt `PresenceEntity`. This slice only records `totalDebt` + `Mark` for it; the bite here is the finite amulet lifespan.
- **Owner-binding at creation** — the rite drops an un-owned amulet (no player handle in `Rite.perform`); binding happens on pouch. Follows `BoundBeastRite`'s deferred-owner pattern.
- **APPEASE / FEED rites** (loyalty to offset debt) and **dream rendering** of worn-bond debt — later verbs over the same record.
- **Per-domain amulet art** (v1: one texture + tooltip).
- **Breaking releasing a hostile presence** — v1 shatter = item loss + disturbance only.
- **`bone_charm`/`wax_poppet`/`obsidian_skull` altar artefacts** — untouched (different system: altar-slot multipliers).
