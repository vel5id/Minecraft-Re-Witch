# Dream World Slice 3a — Sealed Dream Inventory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On a successful crossing the witch enters `hexerei:dream` empty-handed; her waking inventory is restored verbatim on **every** wake path (timer, death, logout), and anything mined/acquired in the dream is discarded — so nothing material crosses the threshold (the `seal` verb) and nothing is ever lost.

**Architecture:** Extend the existing per-player `DreamState` attachment with a serialized inventory snapshot + a `sealed` flag. A tiny stateless `DreamInventory` helper does the two MC-runtime operations: `sealInto` (snapshot the live inventory into `DreamState`, then clear it) and `restoreFrom` (load the snapshot back, then unseal). Wire `sealInto` into the entry path (`DreamEntry.onDrink`) and `restoreFrom` into **every** wake path (`DreamWorld.wake`, plus the login-recovery and stale-flag branches in `HexereiDreamEvents`) so a missed death never strands the player without items. Crash/logout-safe because the snapshot lives in `DreamState`, which serializes with player data and is `copyOnDeath`.

**Tech Stack:** NeoForge 1.21.1, Java 21, JUnit 5 (pure logic), NeoForge GameTest (in-world), `net.minecraft.world.entity.player.Inventory#save(ListTag)`/`#load(ListTag)`/`#clearContent()`.

## Global Constraints

- **Build/launch:** `cd hexerei && export JAVA_HOME=$(pwd)/../hexerei-work/tools/jdk17` then `./gradlew --no-daemon <task>`. The compile uses an auto-downloaded JDK 21 toolchain; do not repoint `JAVA_HOME` at a JRE.
- **Entirely Claude-side, no deepseek:** every unit here is MC-runtime-coupled (`Inventory`/`ItemStack`/registry/events). There is no pure logic core to delegate; deepseek delegation begins at 3b/3c (`DreamTerrain`/`DreamLayout`).
- **Enter empty-handed**, not with a disposable copy. **XP is NOT touched** (out of scope; inventory only).
- **`DreamInventory` methods take `Player`, not `ServerPlayer`** — widened for GameTest testability exactly as `DreamEntry.onDrink(Player,…)` was. Production callers pass a `ServerPlayer`, which is a `Player`.
- **GameTests are MAIN sources** under `src/main/java/com/vel5id/hexerei/test/`, `@GameTestHolder(HexereiMod.MODID)`, `@PrefixGameTestTemplate(false)`. Mock players come from `helper.makeMockPlayer(GameType.SURVIVAL)` (a `Player`, **not** a `ServerPlayer`) — never `makeMockServerPlayerInLevel()` (null-connection NPE). Patchouli must be disabled to run `runGameTestServer` (known project gotcha).
- **Attachment persistence:** `DreamState` is the live attached instance from `getData(...)`; mutating it in place persists on the next player save (no `setData` needed) — this matches the existing `st.begin(...)` call in `DreamEntry`. (Chunks need `setUnsaved(true)`; player attachments do not.)
- **Commit style:** `feat(hexerei): …` / `test(hexerei): …` / `docs(hexerei): …`. End commit messages with the two trailers used on this branch (`Co-Authored-By:` and `Claude-Session:`).
- **Ordering invariant:** in `DreamInventory.sealInto`, store the snapshot into `DreamState` **before** clearing the live inventory, so a crash between the two steps still has the snapshot in player data.

---

### Task 1: `DreamState` inventory-snapshot storage

**Files:**
- Modify: `hexerei/src/main/java/com/vel5id/hexerei/soul/DreamState.java`
- Test: `hexerei/src/test/java/com/vel5id/hexerei/soul/DreamStateRoundTripTest.java` (extend existing)

**Interfaces:**
- Consumes: nothing new.
- Produces (used by Task 2 + Task 3):
  - `boolean DreamState.sealed()`
  - `net.minecraft.nbt.ListTag DreamState.invSnapshot()` — the stored snapshot (empty `ListTag` when not sealed)
  - `void DreamState.seal(ListTag snapshot)` — store snapshot + set `sealed=true`
  - `void DreamState.unseal()` — set `sealed=false` and drop the snapshot (replace with empty `ListTag`)
  - The existing `clear()` is unchanged (it only flips `dreaming`); seal/unseal are an orthogonal axis.

- [ ] **Step 1: Write the failing test** — extend `DreamStateRoundTripTest` with a sealed round-trip. The synthetic snapshot uses plain NBT (no real `ItemStack`), so it runs in pure JUnit with no Minecraft bootstrap. Add these imports at the top of the file if absent: `import net.minecraft.nbt.CompoundTag;`, `import net.minecraft.nbt.ListTag;`. Append inside the class:

```java
    private static ListTag sampleSnapshot() {
        // Two synthetic inventory entries — shaped like Inventory#save output (a "Slot" byte),
        // but using plain NBT so no Minecraft registry/bootstrap is needed in a pure unit test.
        ListTag list = new ListTag();
        CompoundTag a = new CompoundTag();
        a.putByte("Slot", (byte) 0);
        a.putString("id", "minecraft:diamond");
        a.putByte("Count", (byte) 3);
        list.add(a);
        CompoundTag b = new CompoundTag();
        b.putByte("Slot", (byte) 100);
        b.putString("id", "minecraft:iron_helmet");
        b.putByte("Count", (byte) 1);
        list.add(b);
        return list;
    }

    @Test void sealedSnapshotRoundTripsExactly() {
        DreamState a = new DreamState();
        a.begin(NETHER, new BlockPos(1, 2, 3), 42L);
        a.seal(sampleSnapshot());
        DreamState b = roundTrip(a);
        assertTrue(b.sealed(), "sealed flag must survive the round-trip");
        assertEquals(sampleSnapshot(), b.invSnapshot(), "snapshot NBT must survive verbatim");
    }

    @Test void unsealDropsSnapshotAndFlag() {
        DreamState a = new DreamState();
        a.seal(sampleSnapshot());
        a.unseal();
        assertFalse(a.sealed());
        assertTrue(a.invSnapshot().isEmpty(), "unseal drops the snapshot");
        DreamState b = roundTrip(a);
        assertFalse(b.sealed(), "an unsealed state round-trips unsealed");
        assertTrue(b.invSnapshot().isEmpty());
    }

    @Test void freshStateIsUnsealedWithEmptySnapshot() {
        DreamState s = new DreamState();
        assertFalse(s.sealed());
        assertTrue(s.invSnapshot().isEmpty());
    }
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd hexerei && export JAVA_HOME=$(pwd)/../hexerei-work/tools/jdk17 && ./gradlew --no-daemon test --tests 'com.vel5id.hexerei.soul.DreamStateRoundTripTest'`
Expected: FAIL — compile error / `cannot find symbol: method seal(ListTag)` (and `sealed()`, `invSnapshot()`, `unseal()`).

- [ ] **Step 3: Implement the storage in `DreamState`.** Add the two fields, four accessors/mutators, and extend the NBT methods. The snapshot persists only when sealed (keeps NBT small). Exact edits:

Add imports near the top (after the existing `net.minecraft.nbt.CompoundTag` import):
```java
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
```

Add fields after `private boolean dreaming;`:
```java
    private boolean sealed;                  // true while the waking inventory is held in the dream
    private ListTag invSnapshot = new ListTag();   // the held inventory; empty unless sealed
```

Add accessors near the other getters:
```java
    public boolean sealed() { return sealed; }
    public ListTag invSnapshot() { return invSnapshot; }

    /** Hold the waking inventory snapshot for the duration of the dream. */
    public void seal(ListTag snapshot) { this.invSnapshot = snapshot; this.sealed = true; }

    /** Drop the held snapshot and unseal (called once it has been restored). */
    public void unseal() { this.sealed = false; this.invSnapshot = new ListTag(); }
```

In `serializeNBT`, before `return t;`, add:
```java
        t.putBoolean("sealed", sealed);
        if (sealed && !invSnapshot.isEmpty()) t.put("invSnapshot", invSnapshot);
```

In `deserializeNBT`, after the existing `dreaming = t.getBoolean("dreaming");` line, add:
```java
        sealed = t.getBoolean("sealed");
        invSnapshot = t.contains("invSnapshot") ? t.getList("invSnapshot", Tag.TAG_COMPOUND) : new ListTag();
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `cd hexerei && export JAVA_HOME=$(pwd)/../hexerei-work/tools/jdk17 && ./gradlew --no-daemon test --tests 'com.vel5id.hexerei.soul.DreamStateRoundTripTest'`
Expected: PASS (all `DreamStateRoundTripTest` cases, including the three new ones).

- [ ] **Step 5: Commit**

```bash
git add hexerei/src/main/java/com/vel5id/hexerei/soul/DreamState.java \
        hexerei/src/test/java/com/vel5id/hexerei/soul/DreamStateRoundTripTest.java
git commit -m "feat(hexerei): DreamState holds a sealed inventory snapshot (dream seal verb)

$(printf 'Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01WwAiUpxRVdDi8woK5pdQjT')"
```

---

### Task 2: `DreamInventory` seal/restore helper

**Files:**
- Create: `hexerei/src/main/java/com/vel5id/hexerei/soul/DreamInventory.java`
- Test (GameTest, main sources): `hexerei/src/main/java/com/vel5id/hexerei/test/DreamGameTests.java` (add methods)

**Interfaces:**
- Consumes: `DreamState.seal(ListTag)`, `DreamState.unseal()`, `DreamState.sealed()`, `DreamState.invSnapshot()` (Task 1).
- Produces (used by Task 3):
  - `static void DreamInventory.sealInto(Player player, DreamState st)` — `st.seal(snapshot)` **then** `inventory.clearContent()`.
  - `static void DreamInventory.restoreFrom(Player player, DreamState st)` — no-op when `!st.sealed()`; otherwise `inventory.load(st.invSnapshot())` (which clears all slots first, discarding dream loot) then `st.unseal()`.

- [ ] **Step 1: Write the failing GameTest.** Add to `DreamGameTests` (imports: `import com.vel5id.hexerei.soul.DreamInventory;`, `import net.minecraft.world.item.ItemStack;`, `import net.minecraft.world.item.Items;` — `DreamState`/`HexereiAttachments` are already covered by the `import com.vel5id.hexerei.soul.*;` wildcard):

```java
    /**
     * (3a) Sealing snapshots the inventory and empties it; restoring brings it back verbatim and
     * discards anything acquired in the dream. Uses a mock Player (no cross-dimension teleport).
     */
    @GameTest(template = "empty")
    public void dream_inventorySeal_roundTrips(GameTestHelper helper) {
        Player p = playerAt(helper, new BlockPos(2, 2, 2));
        p.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
        p.getInventory().setItem(100, new ItemStack(Items.IRON_HELMET, 1)); // an armor slot
        DreamState st = p.getData(HexereiAttachments.DREAM_STATE);

        DreamInventory.sealInto(p, st);
        helper.assertTrue(st.sealed(), "seal sets the sealed flag");
        helper.assertTrue(p.getInventory().isEmpty(), "seal empties the live inventory");

        // Simulate loot picked up inside the dream — it must NOT survive the wake.
        p.getInventory().setItem(5, new ItemStack(Items.DIRT, 64));

        DreamInventory.restoreFrom(p, st);
        helper.assertFalse(st.sealed(), "restore unseals");
        helper.assertTrue(p.getInventory().getItem(0).getItem() == Items.DIAMOND
                && p.getInventory().getItem(0).getCount() == 3, "diamonds restored verbatim");
        helper.assertTrue(p.getInventory().getItem(100).getItem() == Items.IRON_HELMET,
                "armor restored verbatim");
        helper.assertTrue(p.getInventory().getItem(5).isEmpty(), "dream-acquired loot discarded");
        helper.succeed();
    }
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd hexerei && export JAVA_HOME=$(pwd)/../hexerei-work/tools/jdk17 && ./gradlew --no-daemon compileJava`
Expected: FAIL — `cannot find symbol: class DreamInventory` (the class does not exist yet).

- [ ] **Step 3: Create `DreamInventory`.**

```java
package com.vel5id.hexerei.soul;

import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.player.Player;

/**
 * The dream's {@code seal} verb over a player's inventory: nothing material crosses the threshold.
 * On entry the waking inventory is snapshotted into {@link DreamState} and the live inventory is
 * emptied; on every wake path it is restored verbatim and anything acquired in the dream is dropped.
 *
 * <p>Stateless. Methods take {@link Player} (not {@code ServerPlayer}) so GameTests can exercise them
 * with a mock player, mirroring {@link DreamEntry#onDrink}. {@code Inventory#save/#load} use the
 * player's own {@code registryAccess()} internally, so no {@code HolderLookup.Provider} is needed.
 */
public final class DreamInventory {
    private DreamInventory() {}

    /** Snapshot the inventory into {@code st}, then empty it. Snapshot is stored BEFORE the clear. */
    public static void sealInto(Player player, DreamState st) {
        ListTag snapshot = player.getInventory().save(new ListTag());
        st.seal(snapshot);               // persisted in DreamState first…
        player.getInventory().clearContent();   // …then the live inventory is emptied
    }

    /** Restore the snapshot verbatim (Inventory#load clears all slots first) and unseal. No-op if unsealed. */
    public static void restoreFrom(Player player, DreamState st) {
        if (!st.sealed()) return;
        player.getInventory().load(st.invSnapshot());
        st.unseal();
    }
}
```

- [ ] **Step 4: Run the GameTest to verify it passes.** Confirm Patchouli is disabled for the gametest run (project gotcha), then:

Run: `cd hexerei && export JAVA_HOME=$(pwd)/../hexerei-work/tools/jdk17 && ./gradlew --no-daemon runGameTestServer --tests '*dream_inventorySeal_roundTrips*' 2>&1 | tail -40`
(If the test-filter flag is not honored by `runGameTestServer` in this project, run the full `./gradlew --no-daemon runGameTestServer` and confirm `dream_inventorySeal_roundTrips` appears as passed in the output.)
Expected: the `dream_inventorySeal_roundTrips` GameTest passes.

- [ ] **Step 5: Commit**

```bash
git add hexerei/src/main/java/com/vel5id/hexerei/soul/DreamInventory.java \
        hexerei/src/main/java/com/vel5id/hexerei/test/DreamGameTests.java
git commit -m "feat(hexerei): DreamInventory seal/restore helper + GameTest round-trip

$(printf 'Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01WwAiUpxRVdDi8woK5pdQjT')"
```

---

### Task 3: Wire seal into entry and restore into every wake path

**Files:**
- Modify: `hexerei/src/main/java/com/vel5id/hexerei/soul/DreamEntry.java` (the `instanceof ServerPlayer sp` teleport block)
- Modify: `hexerei/src/main/java/com/vel5id/hexerei/soul/DreamWorld.java` (`wake`)
- Modify: `hexerei/src/main/java/com/vel5id/hexerei/HexereiDreamEvents.java` (`onPlayerTick` stale-flag branch, `onLogin` recovery branch)
- Test (GameTest): `hexerei/src/main/java/com/vel5id/hexerei/test/DreamGameTests.java` (add a recovery/idempotency case)

**Interfaces:**
- Consumes: `DreamInventory.sealInto(Player,DreamState)`, `DreamInventory.restoreFrom(Player,DreamState)` (Task 2).
- Produces: no new API — behavioral wiring only.

- [ ] **Step 1: Write the failing GameTest** for the recovery contract: `restoreFrom` is safe/no-op when not sealed (the "missed death / stale flag, regardless of dimension" branch must never corrupt a normal inventory), and is idempotent after one restore. Add to `DreamGameTests`:

```java
    /**
     * (3a) restoreFrom is a safe no-op when the state is not sealed, and idempotent after a restore —
     * the property the login-recovery / stale-flag branches rely on so a missed death never doubles
     * or wipes a normal inventory.
     */
    @GameTest(template = "empty")
    public void dream_restore_isNoOpWhenUnsealed(GameTestHelper helper) {
        Player p = playerAt(helper, new BlockPos(2, 2, 2));
        p.getInventory().setItem(0, new ItemStack(Items.EMERALD, 7));
        DreamState st = p.getData(HexereiAttachments.DREAM_STATE);

        // Not sealed → restore must change nothing.
        DreamInventory.restoreFrom(p, st);
        helper.assertTrue(p.getInventory().getItem(0).getItem() == Items.EMERALD
                && p.getInventory().getItem(0).getCount() == 7, "unsealed restore leaves inventory intact");

        // Seal, restore once (brings items back, unseals), then a second restore is a no-op.
        DreamInventory.sealInto(p, st);
        DreamInventory.restoreFrom(p, st);
        helper.assertFalse(st.sealed(), "first restore unseals");
        DreamInventory.restoreFrom(p, st);  // idempotent — must not wipe the just-restored inventory
        helper.assertTrue(p.getInventory().getItem(0).getItem() == Items.EMERALD
                && p.getInventory().getItem(0).getCount() == 7, "second restore is a no-op");
        helper.succeed();
    }
```

- [ ] **Step 2: Run it to verify it fails (or passes trivially), then add the wiring.** First confirm it compiles/passes against the current helper (it exercises only Task-2 API, so it should already pass — its purpose is to lock the contract the wiring depends on). Run:

Run: `cd hexerei && export JAVA_HOME=$(pwd)/../hexerei-work/tools/jdk17 && ./gradlew --no-daemon runGameTestServer 2>&1 | grep -i dream_restore_isNoOpWhenUnsealed`
Expected: it appears and passes (contract holds before wiring).

- [ ] **Step 3: Wire `sealInto` into entry.** In `DreamEntry.onDrink`, inside `if (player instanceof net.minecraft.server.level.ServerPlayer sp) { … if (dream != null) { … } }`, insert the seal **after** `st.begin(...)` and **before** `sp.teleportTo(...)`:

```java
                st.begin(level.dimension(), sp.blockPosition(), level.getGameTime() + DREAM_TICKS);
                DreamInventory.sealInto(sp, st);   // enter empty-handed; snapshot held in DreamState
                sp.teleportTo(dream, DreamWorld.ANCHOR.getX() + 0.5, DreamWorld.ANCHOR.getY(),
                        DreamWorld.ANCHOR.getZ() + 0.5, sp.getYRot(), sp.getXRot());
```

- [ ] **Step 4: Wire `restoreFrom` into `DreamWorld.wake`.** Insert the restore immediately before `st.clear();`:

```java
        player.teleportTo(dest, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, player.getYRot(), player.getXRot());
        DreamInventory.restoreFrom(player, st);   // bring the waking inventory back; drop dream loot
        st.clear();
```

- [ ] **Step 5: Wire the two recovery branches in `HexereiDreamEvents`.** Both stranded-state branches must restore the inventory if a snapshot is held, **regardless of dimension** (a real death that slipped past the cancel, or a stale flag). In `onPlayerTick`, change the `else` branch:

```java
        } else {
            DreamInventory.restoreFrom(sp, st);   // sealed but not in the dream → return items, don't strand
            st.clear();   // dreaming flag but not in the dream (e.g. a real death past the cancel) → self-heal
        }
```

In `onLogin`, change the `else` branch:

```java
        } else {
            DreamInventory.restoreFrom(sp, st);   // a real death slipped past / stale flag → restore items first
            st.clear();
        }
```

(The in-dream `onLogin` branch and `onDeath` already call `DreamWorld.wake`, which now restores — no change there.)

- [ ] **Step 6: Build + run the dream GameTests to verify nothing regressed.**

Run: `cd hexerei && export JAVA_HOME=$(pwd)/../hexerei-work/tools/jdk17 && ./gradlew --no-daemon build 2>&1 | tail -20`
Expected: `BUILD SUCCESSFUL` (compiles all wiring; full unit suite green).

Run: `cd hexerei && export JAVA_HOME=$(pwd)/../hexerei-work/tools/jdk17 && ./gradlew --no-daemon runGameTestServer 2>&1 | grep -iE 'dream_|GameTest.*(passed|failed)' | tail -30`
Expected: all `dream_*` GameTests pass (the two new 3a cases plus the existing slice-1 cases). The pre-existing flaky taint test (`mediumtaint_flowersbecomwitchersrose`) is unrelated; ignore it if it is the only failure.

- [ ] **Step 7: Commit**

```bash
git add hexerei/src/main/java/com/vel5id/hexerei/soul/DreamEntry.java \
        hexerei/src/main/java/com/vel5id/hexerei/soul/DreamWorld.java \
        hexerei/src/main/java/com/vel5id/hexerei/HexereiDreamEvents.java \
        hexerei/src/main/java/com/vel5id/hexerei/test/DreamGameTests.java
git commit -m "feat(hexerei): seal inventory on dream entry, restore on every wake path

$(printf 'Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01WwAiUpxRVdDi8woK5pdQjT')"
```

---

### Task 4: Smoke procedure + automated gate

**Files:**
- Create: `hexerei/src/test/smoke/dream-slice3a-smoke.md`

**Interfaces:**
- Consumes: the full 3a behavior (Tasks 1–3).
- Produces: the authoritative manual gate for the cross-dimension inventory swap (not GameTestable end-to-end, per the altar-scan rationale).

- [ ] **Step 1: Run the full automated gate** and capture the result for the smoke doc.

Run: `cd hexerei && export JAVA_HOME=$(pwd)/../hexerei-work/tools/jdk17 && ./gradlew --no-daemon build 2>&1 | tail -5`
Expected: `BUILD SUCCESSFUL`. Record it.

- [ ] **Step 2: Write the smoke doc.**

```markdown
# Dream World — Slice 3a smoke test (sealed dream inventory)

The cross-dimension inventory swap (seal on entry → restore on wake) is **not** GameTested end-to-end —
cross-dimension teleport is unreliable in the shared multi-arena GameTest world (same rationale as the
altar power scan and Slice 2). The `DreamInventory` helper and its contract ARE covered by GameTests
(`dream_inventorySeal_roundTrips`, `dream_restore_isNoOpWhenUnsealed`); this procedure is the authoritative
gate for the wired entry/wake paths.

## A. Automated gate (CI-runnable, no client)
1. **Full build + unit suite** — `cd hexerei && JAVA_HOME=$(pwd)/../hexerei-work/tools/jdk17 ./gradlew --no-daemon build`
   → `BUILD SUCCESSFUL`; runs `DreamStateRoundTripTest` (incl. the sealed round-trip cases).
2. **In-world helper tests** — `./gradlew --no-daemon runGameTestServer` → `dream_inventorySeal_roundTrips`
   and `dream_restore_isNoOpWhenUnsealed` pass.

### Automated-gate result (2026-06-28)
- Build (`./gradlew build`): **PASS** — record `BUILD SUCCESSFUL`.
- GameTests `dream_inventorySeal_roundTrips`, `dream_restore_isNoOpWhenUnsealed`: **PASS**.

## B. Interactive player-loop checks (require a connected client — manual)
Op yourself on a dev/dedicated server with the jar installed, then:

1. **Enter empty-handed.** Fill your inventory (tools, armor, blocks). Brew + drink a Dreaming Draught with
   ≥ `SCRY_COST` essence. → You teleport to `hexerei:dream` with a **completely empty** inventory.
   `data get entity @s SelectedItem` is empty; `data get entity @s ...dream_state` shows `sealed:1b` and a
   non-empty `invSnapshot`.
2. **Timer wake restores.** Wait out `DREAM_TICKS` (30 s). → Back at the drink spot with your **exact** original
   inventory (every slot, armor, offhand); `dream_state` shows `sealed:0b`.
3. **Dream loot is discarded.** Re-enter, mine/pick up blocks in the dream, then wait for the timer. → On wake
   your original inventory is back and **none** of the dream-acquired items remain.
4. **Death wake restores.** Re-enter, `/kill @s` in `hexerei:dream`. → You wake (alive, ~2 hearts) with your
   original inventory intact — and **no items dropped** at the death spot (death was cancelled).
5. **Logout recovery restores.** Re-enter, disconnect, reconnect. → On login you are woken with your original
   inventory intact; `sealed:0b`. (Tests the login-recovery branch.)

### Interactive result (date / server build / tester)
- [ ] 1 empty entry  - [ ] 2 timer restore  - [ ] 3 loot discarded  - [ ] 4 death restore  - [ ] 5 logout restore
- Notes:
```

- [ ] **Step 3: Commit**

```bash
git add hexerei/src/test/smoke/dream-slice3a-smoke.md
git commit -m "docs(hexerei): Slice 3a sealed-inventory smoke procedure + automated gate result

$(printf 'Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>\nClaude-Session: https://claude.ai/code/session_01WwAiUpxRVdDi8woK5pdQjT')"
```

---

## Self-Review

**1. Spec coverage** (spec §"3a — Sealed dream inventory"):
- "enter empty-handed; restore verbatim on wake" → Task 2 (`sealInto`/`restoreFrom`) + Task 3 (wiring). ✓
- "Storage: extend DreamState with `ListTag invSnapshot` + `boolean sealed`" → Task 1. ✓
- "Entry: snapshot → persist FIRST → clear → teleport" → Task 3 Step 3 (sealInto stores then clears, inserted before teleport). ✓
- "Restore on every wake path; login-recovery restores regardless of dimension" → Task 3 Steps 4–5 (`wake`, `onPlayerTick` else, `onLogin` else). ✓
- "death-wake path (death cancelled, vanilla never drops) restores" → `onDeath` calls `wake` which now restores; smoke check 4 verifies no drop. ✓
- "XP not touched" → never referenced; Global Constraints state it. ✓
- "not deepseek-gateable; GameTest + smoke" → Tasks 2–4. ✓

**2. Placeholder scan:** No TBD/TODO; every code step shows complete code; commands have expected output. ✓

**3. Type consistency:** `seal(ListTag)`, `unseal()`, `sealed()`, `invSnapshot()` used identically in Tasks 1→2→3. `DreamInventory.sealInto(Player,DreamState)` / `restoreFrom(Player,DreamState)` consistent across Tasks 2–3. `Inventory#save(ListTag)`/`#load(ListTag)`/`#clearContent()` match the verified 1.21.1 signatures. ✓

---

## Execution Handoff

Plan complete. Per the project rule (3a has no pure logic core), execute **subagent-driven**, Claude-side — no deepseek delegation in this slice.
