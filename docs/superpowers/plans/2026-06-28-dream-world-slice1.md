# Dream World — Slice 1 (Sleep Brew → dreaming state) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Drinking a new "Dreaming Draught" brew reads the witch's soul state, charges essence, applies a dreaming MobEffect set, and — on a nightmare — brands a fear mark and disturbs the place (feeding the Article III loop). No dimension yet.

**Architecture:** Pure soul logic (`DreamResolver`, `DreamOnset`, `DreamNormalize`) stays MC-free and is delegated to the deepseek MCP behind JUnit gates. A thin server class `DreamEntry` orchestrates the in-world pipeline and is invoked from `BrewItem.finishUsingItem`. In-world behavior is proven by GameTests.

**Tech Stack:** Java 21, NeoForge 1.21.1, ModDevGradle, JUnit 5 (pure tests), NeoForge GameTest.

## Global Constraints
- Java 21 toolchain auto-downloaded; launch Gradle with `JAVA_HOME=$(pwd)/../hexerei-work/tools/jdk17`. Always pass `--no-daemon`.
- NeoForge 1.21.1 only. Do **not** add `Properties.setId(...)` (1.21.2+ API).
- Read soul attachments with the Supplier overload exactly as existing code does: `player.getData(HexereiAttachments.PLAYER_SOUL)` and `chunk.getData(HexereiAttachments.CHUNK_SOUL)` (no `.get()`). After mutating a `ChunkSoulData` in place, call `chunk.setUnsaved(true)`. `PlayerSoulData` is mutated in place (no `setData` needed — see `CurseRite.layCurse`).
- **Project rule:** pure decidable logic is delegated to the `deepseek` MCP (`deepseek_task`) behind an immutable JUnit gate; MC wiring + GameTests are written by Claude. Accept a delegation only on `status: "verified"` with `test_files_changed: []`.
- Balance numbers are `[UNVERIFIED]` until in-game; record them in `hexerei/DESIGN-NOTES.md`.
- Pure unit tests live in `hexerei/src/test/java/...`; GameTests live in `hexerei/src/main/java/com/vel5id/hexerei/test/` (`@GameTestHolder(MODID)`, `@PrefixGameTestTemplate(false)`).

**Already built & gate-verified (do not rebuild):** `soul/DreamReading`, `soul/DreamResolver`, `soul/DreamOnset`, `soul/DreamOutcome`.

## File Structure
- Create `soul/DreamNormalize.java` — pure: `totalDebt`/`marks` → normalized `debtN`/`marksN`. (deepseek)
- Create `soul/DreamEntry.java` — server pipeline: read state → resolve → charge essence → effects → nightmare writes. (Claude)
- Modify `brewing/Brews.java` — add `DREAMING_DRAUGHT` (empty static effects).
- Modify `brewing/BrewRecipes.java` — add `{mandrake_root, wormwood}` → `DREAMING_DRAUGHT`.
- Modify `item/BrewItem.java:84-91` — dispatch the dreaming draught to `DreamEntry`.
- Modify `assets/hexerei/lang/en_us.json` + `ru_ru.json` — brew name + dream action-bar keys.
- Create `test/DreamGameTests.java` — three in-world cases.
- Modify `hexerei/DESIGN-NOTES.md` — the slice's magic numbers.

---

### Task 1: `DreamNormalize` (pure helper, delegated to deepseek)

**Files:**
- Test (gate): `hexerei/src/test/java/com/vel5id/hexerei/soul/DreamNormalizeTest.java`
- Create (by deepseek): `hexerei/src/main/java/com/vel5id/hexerei/soul/DreamNormalize.java`

**Interfaces:**
- Produces: `DreamNormalize.debtN(float totalDebt) -> float`, `DreamNormalize.marksN(java.util.List<Bond> marks) -> float`, constants `DEBT_FULL=10f`, `MARKS_FULL=5f`. Used by Task 3 (`DreamEntry`).
- Consumes: `Bond.disposition().fear()`, `SoulMath.clamp01`.

- [ ] **Step 1: Write the failing gate test**

`hexerei/src/test/java/com/vel5id/hexerei/soul/DreamNormalizeTest.java`:
```java
package com.vel5id.hexerei.soul;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import static org.junit.jupiter.api.Assertions.*;

class DreamNormalizeTest {
    private static final float EPS = 1e-5f;

    private static Bond mark(float fear) {
        return new Bond(UUID.randomUUID(),
                ResourceLocation.fromNamespaceAndPath("hexerei", "test"),
                Correspondence.THRESHOLD, new Disposition(0f, 0f, fear, 0f),
                null, List.of(), List.of(), 0L, 0L);
    }

    @Test void debtN_normalizesAndClamps() {
        assertEquals(0.0f, DreamNormalize.debtN(0f), EPS);
        assertEquals(0.5f, DreamNormalize.debtN(5f), EPS);    // 5/10
        assertEquals(1.0f, DreamNormalize.debtN(10f), EPS);
        assertEquals(1.0f, DreamNormalize.debtN(20f), EPS);   // clamp high
        assertEquals(0.0f, DreamNormalize.debtN(-3f), EPS);   // clamp low
    }

    @Test void marksN_sumsCarriedFearClamped() {
        assertEquals(0.0f, DreamNormalize.marksN(List.of()), EPS);
        assertEquals(0.5f, DreamNormalize.marksN(List.of(mark(1.0f), mark(1.5f))), EPS); // 2.5/5
        assertEquals(1.0f, DreamNormalize.marksN(List.of(mark(3f), mark(3f))), EPS);     // 6/5 clamp
    }
}
```

- [ ] **Step 2: Commit the gate**

```bash
cd /home/h621l/minecraft
git add hexerei/src/test/java/com/vel5id/hexerei/soul/DreamNormalizeTest.java
git commit -m "test(hexerei): gate for DreamNormalize (deepseek delegation contract)"
```

- [ ] **Step 3: Delegate the implementation to deepseek**

Call the `deepseek_task` MCP tool (load its schema via ToolSearch first if needed) with:
- `task`: "Create `hexerei/src/main/java/com/vel5id/hexerei/soul/DreamNormalize.java`, a pure `final class` (private ctor) matching `SoulMath`/`DreamResolver` style. `public static final float DEBT_FULL = 10f; public static final float MARKS_FULL = 5f;`. `public static float debtN(float totalDebt)` = `SoulMath.clamp01(totalDebt / DEBT_FULL)`. `public static float marksN(java.util.List<Bond> marks)` = `SoulMath.clamp01(sum / MARKS_FULL)` where `sum` is `Σ mark.disposition().fear()` over the list. No Minecraft runtime beyond the existing pure `Bond` record. Only create this one file; do NOT modify any test."
- `base_ref`: "HEAD"
- `context_files`: `["hexerei/src/test/java/com/vel5id/hexerei/soul/DreamNormalizeTest.java", "hexerei/src/main/java/com/vel5id/hexerei/soul/SoulMath.java", "hexerei/src/main/java/com/vel5id/hexerei/soul/Bond.java", "hexerei/src/main/java/com/vel5id/hexerei/soul/Disposition.java"]`
- `verify_command`: `["./gradlew", "--no-daemon", "test", "--tests", "com.vel5id.hexerei.soul.DreamNormalizeTest"]`
- `verify_cwd`: `"hexerei"`
- `verify_env`: `{"JAVA_HOME": "/home/h621l/minecraft/hexerei-work/tools/jdk17"}`
- `verify_timeout`: `900`, `max_rounds`: `3`

Expected: `status: "verified"`, `test_files_changed: []`.

- [ ] **Step 4: Accept the diff into the working tree**

```bash
cp /tmp/ds-wt-*/hexerei/src/main/java/com/vel5id/hexerei/soul/DreamNormalize.java \
   hexerei/src/main/java/com/vel5id/hexerei/soul/DreamNormalize.java
```
(Use the exact `worktree_path` from the deepseek result.)

- [ ] **Step 5: Verify green in the main tree**

Run: `cd hexerei && JAVA_HOME=/home/h621l/minecraft/hexerei-work/tools/jdk17 ./gradlew --no-daemon test --tests 'com.vel5id.hexerei.soul.DreamNormalizeTest'`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Clean up the worktree + commit**

Call `deepseek_cleanup` with the `worktree_path`, then:
```bash
git add hexerei/src/main/java/com/vel5id/hexerei/soul/DreamNormalize.java
git commit -m "feat(hexerei): DreamNormalize soul->dream normalization (delegated to deepseek)"
```

---

### Task 2: Register the Dreaming Draught (brew + recipe + lang)

**Files:**
- Modify: `hexerei/src/main/java/com/vel5id/hexerei/brewing/Brews.java`
- Modify: `hexerei/src/main/java/com/vel5id/hexerei/brewing/BrewRecipes.java:12-18`
- Modify: `hexerei/src/main/resources/assets/hexerei/lang/en_us.json`, `.../ru_ru.json`
- Test: `hexerei/src/test/java/com/vel5id/hexerei/brewing/BrewsIndexTest.java`, `BrewRecipesTest.java` (run; update only if they assert an exact catalog count)

**Interfaces:**
- Produces: `Brews.DREAMING_DRAUGHT` (id `"dreaming_draught"`, empty effects list). Used by Task 3's dispatch check and Task 4.

- [ ] **Step 1: Add the brew constant** — in `Brews.java`, after `WITHERING_BILE` (line 39):
```java
    /** Entry to the dream: empty static effects — DreamEntry applies the dynamic calm/nightmare set on drink. */
    public static final Brew DREAMING_DRAUGHT = new Brew(
            "dreaming_draught", "brew.hexerei.dreaming_draught", 0x3A2A6E, 50,
            List.of());
```
and register it in `build()` (after the `WITHERING_BILE` put, line 50):
```java
        m.put(DREAMING_DRAUGHT.id(), DREAMING_DRAUGHT);
```

- [ ] **Step 2: Add the recipe** — in `BrewRecipes.java`, add a final entry to the `RECIPES` `List.of(...)` (after line 18, before the closing `)`), and add a comma to the previous entry:
```java
            new BrewRecipe(List.of("hexerei:belladonna_flower", "hexerei:icy_needle"), Brews.WITHERING_BILE),
            new BrewRecipe(List.of("hexerei:mandrake_root", "hexerei:wormwood"), Brews.DREAMING_DRAUGHT));
```

- [ ] **Step 3: Add lang keys** — in `en_us.json`:
```json
  "brew.hexerei.dreaming_draught": "Dreaming Draught",
  "dream.hexerei.entered": "You sink into a dream.",
  "dream.hexerei.nightmare": "You are dragged into a nightmare."
```
in `ru_ru.json`:
```json
  "brew.hexerei.dreaming_draught": "Сонное зелье",
  "dream.hexerei.entered": "Ты погружаешься в сон.",
  "dream.hexerei.nightmare": "Тебя затягивает в кошмар."
```
(Add a trailing comma to the preceding line in each file as needed for valid JSON.)

- [ ] **Step 4: Build + run the brew tests**

Run: `cd hexerei && JAVA_HOME=/home/h621l/minecraft/hexerei-work/tools/jdk17 ./gradlew --no-daemon test --tests 'com.vel5id.hexerei.brewing.*'`
Expected: PASS. If `BrewsIndexTest`/`BrewRecipesTest` assert an exact catalog count, update those expected counts (6 → 7) — that is the only allowed test change here.

- [ ] **Step 5: Commit**

```bash
git add hexerei/src/main/java/com/vel5id/hexerei/brewing/Brews.java \
        hexerei/src/main/java/com/vel5id/hexerei/brewing/BrewRecipes.java \
        hexerei/src/main/resources/assets/hexerei/lang/en_us.json \
        hexerei/src/main/resources/assets/hexerei/lang/ru_ru.json \
        hexerei/src/test/java/com/vel5id/hexerei/brewing/
git commit -m "feat(hexerei): register Dreaming Draught brew + {mandrake_root,wormwood} recipe + lang"
```

---

### Task 3: `DreamEntry` server pipeline + `BrewItem` hook + DESIGN-NOTES

**Files:**
- Create: `hexerei/src/main/java/com/vel5id/hexerei/soul/DreamEntry.java`
- Modify: `hexerei/src/main/java/com/vel5id/hexerei/item/BrewItem.java:84-91`
- Modify: `hexerei/DESIGN-NOTES.md`

**Interfaces:**
- Consumes: `DreamNormalize.debtN/marksN` (Task 1), `DreamResolver.read`, `DreamOnset.onDrink`, `DreamOutcome`, `ChunkSoulData.{disturbanceView,addDisturbance}`, `PlayerSoulData.{essence,totalDebt,marks,spendEssence,addMark}`, `Brews.DREAMING_DRAUGHT` (Task 2).
- Produces: `DreamEntry.onDrink(net.minecraft.server.level.ServerPlayer, net.minecraft.server.level.ServerLevel)`. Constants `SCRY` reuses `DreamOnset.SCRY_COST`; `DREAM_TICKS=600`, `DISTURB_SCALE=20f`. Used by `BrewItem` (this task) and Task 4 (GameTests call `DreamEntry.onDrink` directly).

- [ ] **Step 1: Create `DreamEntry`** (verified in-world by Task 4 — no pure unit test here, the logic is integration glue):

`hexerei/src/main/java/com/vel5id/hexerei/soul/DreamEntry.java`:
```java
package com.vel5id.hexerei.soul;

import com.vel5id.hexerei.HexereiMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side orchestration of the "drink the Dreaming Draught → enter a dream" verb (Slice 1).
 * Reads soul State (it is the {@code read} verb), charges essence, applies the dreaming MobEffect
 * set, and on a nightmare writes BOTH a personal fear mark and place disturbance — feeding the
 * Article III loop. Pure decisions come from {@link DreamResolver}/{@link DreamOnset}; this class
 * only applies their verdict to the world.
 */
public final class DreamEntry {
    private DreamEntry() {}

    /** Dreaming MobEffect duration (ticks). [UNVERIFIED] */
    public static final int DREAM_TICKS = 600;
    /** dreadPenalty (0..1) → THRESHOLD disturbance added to the chunk on a nightmare. [UNVERIFIED] */
    public static final float DISTURB_SCALE = 20f;
    /** spiritType id stamped on a nightmare's fear mark (not a registered entity — a record tag). */
    private static final ResourceLocation NIGHTMARE_SPIRIT =
            ResourceLocation.fromNamespaceAndPath(HexereiMod.MODID, "nightmare");

    public static void onDrink(ServerPlayer player, ServerLevel level) {
        PlayerSoulData psd = player.getData(HexereiAttachments.PLAYER_SOUL);
        LevelChunk chunk = level.getChunkAt(player.blockPosition());
        ChunkSoulData csd = chunk.getData(HexereiAttachments.CHUNK_SOUL);

        Map<Correspondence, Float> disturbance = csd.disturbanceView();
        float debtN = DreamNormalize.debtN(psd.totalDebt());
        float marksN = DreamNormalize.marksN(psd.marks());
        DreamReading reading = DreamResolver.read(debtN, marksN, disturbance);
        DreamOutcome outcome = DreamOnset.onDrink(psd.essence(), reading);

        if (!outcome.entered()) {
            // Not enough essence to cross — the draught fizzles.
            level.playSound(null, player.blockPosition(), SoundEvents.FIRE_EXTINGUISH,
                    SoundSource.PLAYERS, 0.5f, 0.7f);
            return;
        }

        psd.spendEssence(outcome.essenceSpent());
        applyDreamingEffects(player, outcome.nightmare());

        level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(),
                24, 0.3, 0.5, 0.3, 0.05);
        player.displayClientMessage(
                Component.translatable(outcome.nightmare() ? "dream.hexerei.nightmare" : "dream.hexerei.entered"),
                true);

        if (outcome.nightmare()) {
            long now = level.getGameTime();
            Bond fearMark = new Bond(UUID.randomUUID(), NIGHTMARE_SPIRIT, Correspondence.THRESHOLD,
                    new Disposition(0f, 0f, outcome.dreadPenalty(), 0f),
                    null, List.of(), List.of(), now, now);
            psd.addMark(fearMark);
            csd.addDisturbance(Correspondence.THRESHOLD, outcome.dreadPenalty() * DISTURB_SCALE);
            chunk.setUnsaved(true);
        }
    }

    private static void applyDreamingEffects(ServerPlayer p, boolean nightmare) {
        if (nightmare) {
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, DREAM_TICKS, 0));
            p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, DREAM_TICKS, 0));        // nausea
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DREAM_TICKS, 0)); // slowness
        } else {
            p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, DREAM_TICKS, 0));
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DREAM_TICKS, 0));
        }
    }
}
```

- [ ] **Step 2: Hook it into `BrewItem.finishUsingItem`** — inside the existing `if (brew != null && !level.isClientSide) { ... }` block, AFTER the `for (BrewEffect e : brew.effects())` loop (after line 90), add:
```java
            if (brew.id().equals(Brews.DREAMING_DRAUGHT.id())
                    && entity instanceof net.minecraft.server.level.ServerPlayer sp
                    && level instanceof net.minecraft.server.level.ServerLevel sl) {
                com.vel5id.hexerei.soul.DreamEntry.onDrink(sp, sl);
            }
```
(`DREAMING_DRAUGHT` has empty `effects()`, so the generic loop above is a no-op for it.)

- [ ] **Step 3: Record the numbers** — append to `hexerei/DESIGN-NOTES.md` a "Dream World — Slice 1" subsection:
```markdown
### Dream World — Slice 1 (Sleep Brew entry)
- `DreamOnset.SCRY_COST = 1.0f` essence to cross the threshold (~⅓ of one mandrake take). [UNVERIFIED]
- `DreamNormalize.DEBT_FULL = 10f`, `MARKS_FULL = 5f` — normalization saturation. [UNVERIFIED]
- `DreamEntry.DREAM_TICKS = 600` (30 s) dreaming MobEffect duration. [UNVERIFIED]
- `DreamEntry.DISTURB_SCALE = 20f` — a max nightmare adds 20 of 100 THRESHOLD disturbance. [UNVERIFIED]
```

- [ ] **Step 4: Build (compile check)**

Run: `cd hexerei && JAVA_HOME=/home/h621l/minecraft/hexerei-work/tools/jdk17 ./gradlew --no-daemon compileJava`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add hexerei/src/main/java/com/vel5id/hexerei/soul/DreamEntry.java \
        hexerei/src/main/java/com/vel5id/hexerei/item/BrewItem.java \
        hexerei/DESIGN-NOTES.md
git commit -m "feat(hexerei): DreamEntry pipeline + BrewItem hook for the Dreaming Draught"
```

---

### Task 4: GameTests for the three entry cases

**Files:**
- Create: `hexerei/src/main/java/com/vel5id/hexerei/test/DreamGameTests.java`
- Reference (boilerplate to mirror): `hexerei/src/main/java/com/vel5id/hexerei/test/CurseGameTests.java` (mock player + PLAYER_SOUL seeding), and the project memory note: use `makeMockPlayer()` (not `makeMockServerPlayerInLevel()`) for effect/inventory tests.

**Interfaces:**
- Consumes: `DreamEntry.onDrink(ServerPlayer, ServerLevel)`, `HexereiAttachments.PLAYER_SOUL/CHUNK_SOUL`, `PlayerSoulData.{addEssence,essence,addMark,marks}`, `ChunkSoulData.{addDisturbance,getDisturbance}`, `Correspondence.THRESHOLD`, `DreamOnset.SCRY_COST`, `MobEffects`.

- [ ] **Step 1: Write the GameTests.** Follow `CurseGameTests.java` for the `@GameTestHolder(HexereiMod.MODID)` + `@PrefixGameTestTemplate(false)` class annotations, the empty-structure template, and how it obtains a server player + `ServerLevel`. Three methods:

```java
package com.vel5id.hexerei.test;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.soul.*;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HexereiMod.MODID)
@PrefixGameTestTemplate(false)
public class DreamGameTests {

    // (a) too little essence -> no crossing, essence untouched, no dreaming effect.
    @GameTest(template = "empty")
    public void dream_poorEssence_fizzles(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer p = helper.makeMockServerPlayerInLevel(); // mirror CurseGameTests' player-acquisition
        PlayerSoulData psd = p.getData(HexereiAttachments.PLAYER_SOUL);
        psd.addEssence(DreamOnset.SCRY_COST - 0.5f);            // below the cost

        DreamEntry.onDrink(p, level);

        helper.assertTrue(psd.essence() == DreamOnset.SCRY_COST - 0.5f, "essence must be untouched on a fizzle");
        helper.assertFalse(p.hasEffect(MobEffects.NIGHT_VISION), "no dreaming effect on a fizzle");
        helper.succeed();
    }

    // (b) calm soul -> crosses: essence debited by SCRY_COST, Night Vision applied, no new mark.
    @GameTest(template = "empty")
    public void dream_calm_entersAndCharges(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer p = helper.makeMockServerPlayerInLevel();
        PlayerSoulData psd = p.getData(HexereiAttachments.PLAYER_SOUL);
        psd.addEssence(5f);                                    // affordable, no debt/marks -> calm
        int marksBefore = psd.marks().size();

        DreamEntry.onDrink(p, level);

        helper.assertTrue(Math.abs(psd.essence() - (5f - DreamOnset.SCRY_COST)) < 1e-4f, "essence debited by SCRY_COST");
        helper.assertTrue(p.hasEffect(MobEffects.NIGHT_VISION), "calm dream grants Night Vision");
        helper.assertTrue(psd.marks().size() == marksBefore, "calm dream lays no fear mark");
        helper.succeed();
    }

    // (c) burdened soul -> nightmare: a fear mark is added and THRESHOLD disturbance rises.
    @GameTest(template = "empty")
    public void dream_nightmare_marksAndDisturbs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer p = helper.makeMockServerPlayerInLevel();
        PlayerSoulData psd = p.getData(HexereiAttachments.PLAYER_SOUL);
        psd.addEssence(5f);
        psd.setTotalDebt(10f);                                 // debtN -> 1.0
        var chunk = level.getChunkAt(p.blockPosition());
        ChunkSoulData csd = chunk.getData(HexereiAttachments.CHUNK_SOUL);
        csd.addDisturbance(Correspondence.THRESHOLD, 100f);    // ambient -> 1.0 ; clarity 0, dread 1 -> nightmare
        int marksBefore = psd.marks().size();
        float disturbBefore = csd.getDisturbance(Correspondence.THRESHOLD);

        DreamEntry.onDrink(p, level);

        helper.assertTrue(psd.marks().size() == marksBefore + 1, "nightmare lays a fear mark");
        helper.assertTrue(csd.getDisturbance(Correspondence.THRESHOLD) > disturbBefore,
                "nightmare raises THRESHOLD disturbance");
        helper.assertTrue(p.hasEffect(MobEffects.CONFUSION), "nightmare applies Nausea");
        helper.succeed();
    }
}
```
NOTE: use the SAME player-acquisition call that `CurseGameTests` uses for a soul-bearing `ServerPlayer`; if that file uses a helper other than `makeMockServerPlayerInLevel()`, match it (the memory note warns `makeMockServerPlayerInLevel()` can NPE on null connection for effect/inventory paths — verify which `CurseGameTests` relies on and copy it).

- [ ] **Step 2: Run the GameTests**

Run: `cd hexerei && JAVA_HOME=/home/h621l/minecraft/hexerei-work/tools/jdk17 ./gradlew --no-daemon runGameTestServer --tests '*Dream*'` (or the project's standard `runGameTestServer` invocation).
Expected: the three `hexerei:dream_*` tests pass. The disturbance-100 seeding makes case (c) deterministic (clarity 0, dread 1 → nightmare), so it should not be flaky.

- [ ] **Step 3: Commit**

```bash
git add hexerei/src/main/java/com/vel5id/hexerei/test/DreamGameTests.java
git commit -m "test(hexerei): GameTests for Dreaming Draught entry (fizzle / calm / nightmare)"
```

---

## Out of scope (later slices)
The dream **dimension** (`hexerei:dream`), the brew-triggered **teleport**, **procedural island** layout/relief from `disturbance` (THRESHOLD center + 5 domain islands), and the full client **dream overlay**. Each is its own spec → plan → implement cycle; none is deepseek-delegable (dimension/teleport/render/procgen need in-world verification).
