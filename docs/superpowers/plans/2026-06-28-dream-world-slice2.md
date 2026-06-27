# Dream World — Slice 2 (hexerei:dream dimension + teleport) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A successful Dreaming Draught crossing teleports the witch into a custom `hexerei:dream` void dimension (a barren central THRESHOLD platform) and returns her to the overworld on a timer or on death-in-dream.

**Architecture:** The dimension is declared by datapack JSON (void flat generator) and its `ResourceKey<Level>` is referenced in code. A per-player `DreamState` attachment holds the return target + wake tick. `DreamEntry` (slice 1) is extended to save-return → place the platform → teleport on a crossing. A game-bus event class wakes the player (timer tick, death cancel, login recovery). Only the pure return-target resolver is delegated to deepseek.

**Tech Stack:** Java 21, NeoForge 1.21.1, ModDevGradle, datapack worldgen JSON, JUnit 5.

## Global Constraints
- Java 21 toolchain auto-downloads; launch Gradle with `JAVA_HOME=$(pwd)/../hexerei-work/tools/jdk17`, always `--no-daemon`.
- NeoForge 1.21.1 only. No `Properties.setId(...)`.
- Attachments: register in `HexereiAttachments` via `ATTACHMENT_TYPES.register("name", () -> AttachmentType.serializable(Ctor::new)....build())`; read with the Supplier overload `holder.getData(HexereiAttachments.X)` (no `.get()`); `PlayerSoulData`/attachments mutate in place.
- Game-bus events: a `final class` of `@SubscribeEvent public static void` methods, registered with `NeoForge.EVENT_BUS.register(TheClass.class)` in the `HexereiMod` constructor (mirror `HexereiLevelEvents`).
- **Project rule:** pure decidable logic → delegate to `deepseek` MCP (`deepseek_task`) behind an immutable JUnit gate (accept only `status:"verified"`, `test_files_changed:[]`); dimension/datapack/teleport/events → Claude. Cross-dimension behavior is NOT GameTested (unreliable in the shared multi-arena world) — verified by a dedicated-server smoke test.
- Balance numbers `[UNVERIFIED]`, recorded in `hexerei/DESIGN-NOTES.md`: anchor `(0,64,0)`, platform 5×5 at y=63, `DEATH_WAKE_HEALTH=4f`, dream duration reuses `DreamEntry.DREAM_TICKS=600`.

**Already built (slice 1):** `soul/DreamEntry.onDrink(Player, ServerLevel)` (the `outcome.entered()` branch is the extension point), `DreamOnset`/`DreamOutcome`/`DreamResolver`/`DreamNormalize`, `HexereiAttachments` (PLAYER_SOUL/CHUNK_SOUL).

## File Structure
- Create `soul/DreamReturn.java` — pure return-target resolver. (deepseek)
- Create `data/hexerei/dimension_type/dream.json`, `data/hexerei/dimension/dream.json` — the void dimension. (Claude)
- Create `soul/DreamWorld.java` — the `ResourceKey<Level> DREAM`, the spawn anchor, platform placement, and `wake(ServerPlayer)`. (Claude)
- Create `soul/DreamState.java` — per-player attachment; register it in `HexereiAttachments`. (Claude)
- Modify `soul/DreamEntry.java` — teleport-on-crossing. (Claude)
- Create `HexereiDreamEvents.java` + register in `HexereiMod` — wake handlers. (Claude)
- Test `soul/DreamReturnTest.java` (deepseek gate); `DESIGN-NOTES.md`; a smoke-test doc.

---

### Task 1: `DreamReturn` (pure resolver, delegated to deepseek)

**Files:**
- Test (gate): `hexerei/src/test/java/com/vel5id/hexerei/soul/DreamReturnTest.java`
- Create (by deepseek): `hexerei/src/main/java/com/vel5id/hexerei/soul/DreamReturn.java`

**Interfaces:**
- Produces: `record DreamReturn(ResourceKey<Level> dim, BlockPos pos)` and `static DreamReturn resolveTarget(ResourceKey<Level> savedDim, BlockPos savedPos, ResourceKey<Level> fallbackDim, BlockPos fallbackPos)`. Used by `DreamWorld.wake` (Task 3/5).

- [ ] **Step 1: Write the failing gate test**

`hexerei/src/test/java/com/vel5id/hexerei/soul/DreamReturnTest.java`:
```java
package com.vel5id.hexerei.soul;

import org.junit.jupiter.api.Test;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import static org.junit.jupiter.api.Assertions.*;

class DreamReturnTest {
    private static ResourceKey<Level> key(String path) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("minecraft", path));
    }
    private static final ResourceKey<Level> OVERWORLD = key("overworld");
    private static final ResourceKey<Level> NETHER = key("the_nether");
    private static final BlockPos SPAWN = new BlockPos(0, 64, 0);

    @Test void savedTargetIsReturnedWhenPresent() {
        BlockPos saved = new BlockPos(10, 70, -5);
        DreamReturn r = DreamReturn.resolveTarget(NETHER, saved, OVERWORLD, SPAWN);
        assertEquals(NETHER, r.dim());
        assertEquals(saved, r.pos());
    }

    @Test void nullSavedDimFallsBack() {
        DreamReturn r = DreamReturn.resolveTarget(null, new BlockPos(1, 2, 3), OVERWORLD, SPAWN);
        assertEquals(OVERWORLD, r.dim());
        assertEquals(SPAWN, r.pos());
    }

    @Test void nullSavedPosFallsBack() {
        DreamReturn r = DreamReturn.resolveTarget(NETHER, null, OVERWORLD, SPAWN);
        assertEquals(OVERWORLD, r.dim());
        assertEquals(SPAWN, r.pos());
    }
}
```

- [ ] **Step 2: Commit the gate**
```bash
cd /home/h621l/minecraft
git add hexerei/src/test/java/com/vel5id/hexerei/soul/DreamReturnTest.java
git commit -m "test(hexerei): gate for DreamReturn (deepseek delegation contract)"
```

- [ ] **Step 3: Delegate to deepseek** — call `deepseek_task` with:
  - `task`: "Create `hexerei/src/main/java/com/vel5id/hexerei/soul/DreamReturn.java`: a pure record `public record DreamReturn(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim, net.minecraft.core.BlockPos pos)` with a static factory `public static DreamReturn resolveTarget(ResourceKey<Level> savedDim, BlockPos savedPos, ResourceKey<Level> fallbackDim, BlockPos fallbackPos)`: return `new DreamReturn(savedDim, savedPos)` when BOTH savedDim and savedPos are non-null, otherwise `new DreamReturn(fallbackDim, fallbackPos)`. No Minecraft runtime needed (these are plain value types). Only create this one file; do not modify any test."
  - `base_ref`:"HEAD", `context_files`:`["hexerei/src/test/java/com/vel5id/hexerei/soul/DreamReturnTest.java"]`
  - `verify_command`:`["./gradlew","--no-daemon","test","--tests","com.vel5id.hexerei.soul.DreamReturnTest"]`, `verify_cwd`:"hexerei", `verify_env`:`{"JAVA_HOME":"/home/h621l/minecraft/hexerei-work/tools/jdk17"}`, `verify_timeout`:900, `max_rounds`:3
  Expected: `status:"verified"`, `test_files_changed:[]`.

- [ ] **Step 4: Accept** — `cp /tmp/ds-wt-*/hexerei/src/main/java/com/vel5id/hexerei/soul/DreamReturn.java hexerei/src/main/java/com/vel5id/hexerei/soul/DreamReturn.java` (use the result's worktree_path).

- [ ] **Step 5: Verify** — `cd hexerei && JAVA_HOME=/home/h621l/minecraft/hexerei-work/tools/jdk17 ./gradlew --no-daemon test --tests 'com.vel5id.hexerei.soul.DreamReturnTest'` → `BUILD SUCCESSFUL`.

- [ ] **Step 6: Cleanup + commit** — call `deepseek_cleanup` with the worktree_path, then:
```bash
git add hexerei/src/main/java/com/vel5id/hexerei/soul/DreamReturn.java
git commit -m "feat(hexerei): DreamReturn return-target resolver (delegated to deepseek)"
```

---

### Task 2: The `hexerei:dream` void dimension (datapack)

**Files:**
- Create: `hexerei/src/main/resources/data/hexerei/dimension_type/dream.json`
- Create: `hexerei/src/main/resources/data/hexerei/dimension/dream.json`

**Interfaces:**
- Produces: a server level registered at `hexerei:dream` (NeoForge auto-creates it from the datapack). Consumed by Task 3's `DreamWorld.DREAM` key and the smoke test.

- [ ] **Step 1: Dimension type** — `dimension_type/dream.json` (a dark, fixed-night void):
```json
{
  "ultrawarm": false,
  "natural": false,
  "piglin_safe": false,
  "respawn_anchor_works": false,
  "bed_works": false,
  "has_raids": false,
  "has_skylight": false,
  "has_ceiling": false,
  "coordinate_scale": 1.0,
  "ambient_light": 0.1,
  "fixed_time": 18000,
  "monster_spawn_light_level": 0,
  "monster_spawn_block_light_limit": 0,
  "min_y": 0,
  "height": 256,
  "logical_height": 256,
  "infiniburn": "#minecraft:infiniburn_overworld",
  "effects": "minecraft:the_end"
}
```

- [ ] **Step 2: Dimension** — `dimension/dream.json` (a flat generator with NO layers = void):
```json
{
  "type": "hexerei:dream",
  "generator": {
    "type": "minecraft:flat",
    "settings": {
      "biome": "minecraft:the_void",
      "lakes": false,
      "features": false,
      "layers": [],
      "structure_overrides": []
    }
  }
}
```

- [ ] **Step 3: Validate the datapack loads** — boot the server far enough to register dimensions:
```
cd /home/h621l/minecraft/hexerei && JAVA_HOME=/home/h621l/minecraft/hexerei-work/tools/jdk17 ./gradlew --no-daemon runData
```
`runData` parses worldgen JSON; a malformed dimension/dimension_type fails here with a clear codec error. Expected: `BUILD SUCCESSFUL`. (The authoritative load check is the Task 6 smoke test, which actually creates the level.)

- [ ] **Step 4: Commit**
```bash
git add hexerei/src/main/resources/data/hexerei/dimension_type/dream.json \
        hexerei/src/main/resources/data/hexerei/dimension/dream.json
git commit -m "feat(hexerei): hexerei:dream void dimension (datapack)"
```

---

### Task 3: `DreamState` attachment + `DreamWorld` holder

**Files:**
- Create: `hexerei/src/main/java/com/vel5id/hexerei/soul/DreamState.java`
- Create: `hexerei/src/main/java/com/vel5id/hexerei/soul/DreamWorld.java`
- Modify: `hexerei/src/main/java/com/vel5id/hexerei/soul/HexereiAttachments.java`

**Interfaces:**
- Consumes: `DreamReturn.resolveTarget` (Task 1), the `hexerei:dream` datapack (Task 2).
- Produces: `HexereiAttachments.DREAM_STATE` (Supplier); `DreamState` with getters/setters `returnDim/returnPos/wakeTick/dreaming` + NBT; `DreamWorld.DREAM` (`ResourceKey<Level>`), `DreamWorld.ANCHOR` (`BlockPos`), `DreamWorld.preparePlatform(ServerLevel)`, `DreamWorld.wake(ServerPlayer)`. Used by Tasks 4 & 5.

- [ ] **Step 1: `DreamState`** (mirror `PlayerSoulData`'s INBTSerializable shape):
```java
package com.vel5id.hexerei.soul;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** Per-player dream session state: where to return, when to wake, and whether currently dreaming. */
public class DreamState implements INBTSerializable<CompoundTag> {
    private ResourceKey<Level> returnDim;   // null until a crossing
    private BlockPos returnPos;             // null until a crossing
    private long wakeTick;
    private boolean dreaming;

    public ResourceKey<Level> returnDim() { return returnDim; }
    public BlockPos returnPos() { return returnPos; }
    public long wakeTick() { return wakeTick; }
    public boolean dreaming() { return dreaming; }

    public void begin(ResourceKey<Level> dim, BlockPos pos, long wakeAt) {
        this.returnDim = dim; this.returnPos = pos; this.wakeTick = wakeAt; this.dreaming = true;
    }
    public void clear() { this.dreaming = false; }

    @Override public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag t = new CompoundTag();
        t.putBoolean("dreaming", dreaming);
        t.putLong("wakeTick", wakeTick);
        if (returnDim != null) t.putString("returnDim", returnDim.location().toString());
        if (returnPos != null) { t.putInt("rx", returnPos.getX()); t.putInt("ry", returnPos.getY()); t.putInt("rz", returnPos.getZ()); }
        return t;
    }
    @Override public void deserializeNBT(HolderLookup.Provider provider, CompoundTag t) {
        dreaming = t.getBoolean("dreaming");
        wakeTick = t.getLong("wakeTick");
        returnDim = t.contains("returnDim")
                ? ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(t.getString("returnDim"))) : null;
        returnPos = t.contains("rx") ? new BlockPos(t.getInt("rx"), t.getInt("ry"), t.getInt("rz")) : null;
    }
}
```

- [ ] **Step 2: Register the attachment** — in `HexereiAttachments.java`, after the `CHUNK_SOUL` block:
```java
    public static final java.util.function.Supplier<AttachmentType<DreamState>> DREAM_STATE =
            ATTACHMENT_TYPES.register("dream_state", () ->
                    AttachmentType.serializable(DreamState::new).copyOnDeath().build());
```

- [ ] **Step 3: `DreamWorld`** (the key, anchor, platform, and wake):
```java
package com.vel5id.hexerei.soul;

import com.vel5id.hexerei.HexereiMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/** The hexerei:dream level key + the barren central platform + the wake/return teleport. */
public final class DreamWorld {
    private DreamWorld() {}

    public static final ResourceKey<Level> DREAM =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(HexereiMod.MODID, "dream"));
    public static final BlockPos ANCHOR = new BlockPos(0, 64, 0);   // spawn stands on the y=63 platform [UNVERIFIED]
    public static final float DEATH_WAKE_HEALTH = 4f;               // 2 hearts on a death-wake [UNVERIFIED]

    /** Ensure a 5x5 barren stone platform exists under the anchor (idempotent). */
    public static void preparePlatform(ServerLevel dream) {
        int y = ANCHOR.getY() - 1;
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos p = new BlockPos(ANCHOR.getX() + x, y, ANCHOR.getZ() + z);
                if (!dream.getBlockState(p).is(Blocks.STONE)) {
                    dream.setBlock(p, Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }
    }

    /** Wake the player: teleport to the saved return target (or overworld spawn) and clear the flag. */
    public static void wake(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        DreamState st = player.getData(HexereiAttachments.DREAM_STATE);
        ServerLevel over = server.overworld();
        DreamReturn target = DreamReturn.resolveTarget(
                st.returnDim(), st.returnPos(), over.dimension(), over.getSharedSpawnPos());
        ServerLevel dest = server.getLevel(target.dim());
        if (dest == null) dest = over;
        BlockPos p = target.pos();
        player.teleportTo(dest, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, player.getYRot(), player.getXRot());
        st.clear();
    }
}
```

- [ ] **Step 4: Build (compile)** — `cd hexerei && JAVA_HOME=/home/h621l/minecraft/hexerei-work/tools/jdk17 ./gradlew --no-daemon compileJava` → `BUILD SUCCESSFUL`. (Confirm `getSharedSpawnPos()`/`teleportTo` overloads exist; if a name differs in 1.21.1, use the compiling equivalent — `ServerLevel.getSharedSpawnPos()` returns the world spawn `BlockPos`; `ServerPlayer.teleportTo(ServerLevel,double,double,double,float,float)` is the cross-dim teleport.)

- [ ] **Step 5: Commit**
```bash
git add hexerei/src/main/java/com/vel5id/hexerei/soul/DreamState.java \
        hexerei/src/main/java/com/vel5id/hexerei/soul/DreamWorld.java \
        hexerei/src/main/java/com/vel5id/hexerei/soul/HexereiAttachments.java
git commit -m "feat(hexerei): DreamState attachment + DreamWorld key/platform/wake"
```

---

### Task 4: Teleport on a crossing (extend `DreamEntry`)

**Files:**
- Modify: `hexerei/src/main/java/com/vel5id/hexerei/soul/DreamEntry.java`
- Modify: `hexerei/DESIGN-NOTES.md`

**Interfaces:**
- Consumes: `DreamWorld.DREAM/ANCHOR/preparePlatform`, `HexereiAttachments.DREAM_STATE`, `DreamState.begin`, `DreamEntry.DREAM_TICKS` (slice 1).

- [ ] **Step 1: Add the teleport branch** — in `DreamEntry.onDrink`, after the existing effects/feedback are applied on a crossing (after the `applyDreamingEffects(...)` + omen block, still inside the `entered` path, BEFORE the `if (outcome.nightmare())` block is fine), add:
```java
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            net.minecraft.server.level.ServerLevel dream = sp.getServer().getLevel(DreamWorld.DREAM);
            if (dream != null) {
                DreamWorld.preparePlatform(dream);
                DreamState st = sp.getData(HexereiAttachments.DREAM_STATE);
                st.begin(level.dimension(), sp.blockPosition(), level.getGameTime() + DREAM_TICKS);
                sp.teleportTo(dream, DreamWorld.ANCHOR.getX() + 0.5, DreamWorld.ANCHOR.getY(),
                        DreamWorld.ANCHOR.getZ() + 0.5, sp.getYRot(), sp.getXRot());
            }
        }
```
(The mock-`Player` test path and the fizzle path do not teleport. `level` is the overworld `ServerLevel` the draught was drunk in — it is the return dimension/position.)

- [ ] **Step 2: Record numbers** — append to the `## Dream World — Slice 1` section's tail in `DESIGN-NOTES.md` a Slice 2 block:
```markdown

### Dream World — Slice 2 (dimension + teleport)
- `DreamWorld.ANCHOR = (0,64,0)`, 5×5 stone platform at y=63 — barren central THRESHOLD island. [UNVERIFIED]
- `DreamWorld.DEATH_WAKE_HEALTH = 4f` (2 hearts) on a death-wake. [UNVERIFIED]
- Dream duration reuses `DreamEntry.DREAM_TICKS = 600` (wakeTick = entry gameTime + 600). [UNVERIFIED]
```

- [ ] **Step 3: Build** — `compileJava` → `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**
```bash
git add hexerei/src/main/java/com/vel5id/hexerei/soul/DreamEntry.java hexerei/DESIGN-NOTES.md
git commit -m "feat(hexerei): teleport into hexerei:dream on a Dreaming Draught crossing"
```

---

### Task 5: Wake handlers (`HexereiDreamEvents`)

**Files:**
- Create: `hexerei/src/main/java/com/vel5id/hexerei/HexereiDreamEvents.java`
- Modify: `hexerei/src/main/java/com/vel5id/hexerei/HexereiMod.java` (constructor, after the `HexereiLevelEvents` register line)

**Interfaces:**
- Consumes: `DreamWorld.DREAM/wake/DEATH_WAKE_HEALTH`, `HexereiAttachments.DREAM_STATE`, `DreamState`.

- [ ] **Step 1: Create the handler** (mirror `HexereiLevelEvents` style):
```java
package com.vel5id.hexerei;

import com.vel5id.hexerei.soul.DreamState;
import com.vel5id.hexerei.soul.DreamWorld;
import com.vel5id.hexerei.soul.HexereiAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** The dream wake state machine: timer expiry, death-cancel, and login recovery. */
public final class HexereiDreamEvents {
    private HexereiDreamEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        DreamState st = sp.getData(HexereiAttachments.DREAM_STATE);
        if (!st.dreaming()) return;
        if (sp.level().dimension().equals(DreamWorld.DREAM) && sp.level().getGameTime() >= st.wakeTick()) {
            DreamWorld.wake(sp);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        DreamState st = sp.getData(HexereiAttachments.DREAM_STATE);
        if (st.dreaming() && sp.level().dimension().equals(DreamWorld.DREAM)) {
            event.setCanceled(true);                 // a dream death wakes, it does not kill
            sp.setHealth(DreamWorld.DEATH_WAKE_HEALTH);
            DreamWorld.wake(sp);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedIn event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        DreamState st = sp.getData(HexereiAttachments.DREAM_STATE);
        if (!st.dreaming()) return;
        if (sp.level().dimension().equals(DreamWorld.DREAM)) {
            DreamWorld.wake(sp);                     // logged out in the dream → wake on return
        } else {
            st.clear();                              // a real death slipped past / stale flag → just clear
        }
    }
}
```

- [ ] **Step 2: Register it** — in `HexereiMod` constructor, right after `NeoForge.EVENT_BUS.register(HexereiLevelEvents.class);`:
```java
        NeoForge.EVENT_BUS.register(HexereiDreamEvents.class);            // dream wake state machine
```

- [ ] **Step 3: Build** — `compileJava` → `BUILD SUCCESSFUL`. (If `PlayerTickEvent.Post` import path differs, it is `net.neoforged.neoforge.event.tick.PlayerTickEvent.Post` in NeoForge 1.21.1; `LivingDeathEvent` is cancelable.)

- [ ] **Step 4: Commit**
```bash
git add hexerei/src/main/java/com/vel5id/hexerei/HexereiDreamEvents.java \
        hexerei/src/main/java/com/vel5id/hexerei/HexereiMod.java
git commit -m "feat(hexerei): dream wake handlers (timer / death-cancel / login recovery)"
```

---

### Task 6: Dedicated-server smoke test

**Files:**
- Create: `hexerei/src/test/smoke/dream-slice2-smoke.md` (the procedure; cross-dim teleport is not GameTested).

**Interfaces:** exercises the full Task 1–5 stack end to end.

- [ ] **Step 1: Build the mod jar** — `cd hexerei && JAVA_HOME=/home/h621l/minecraft/hexerei-work/tools/jdk17 ./gradlew --no-daemon build` → jar at `build/libs/hexerei-1.21.1-0.1.0.jar`. Confirms the datapack + all code load together.

- [ ] **Step 2: Run a dev server** — `JAVA_HOME=/home/h621l/minecraft/hexerei-work/tools/jdk17 ./gradlew --no-daemon runServer` and wait for "Done". This is where a malformed dimension JSON or registration error surfaces.

- [ ] **Step 3: In the server console / as an op, verify the dimension exists and the loop works.** Document these exact checks in `dream-slice2-smoke.md` and run them:
  1. `/execute in hexerei:dream run tp @s 0 64 0` succeeds (the level exists) — or give yourself a Dreaming Draught (`/give @s hexerei:brew{...}` is awkward; instead craft via the cauldron, or temporarily add a creative-tab brew item) and drink it.
  2. After drinking with ≥1 essence, you are in `hexerei:dream` standing on the stone platform; `data get entity @s` shows the `hexerei:dream_state` attachment with `dreaming:1b` and a `returnDim/returnPos`.
  3. Wait `DREAM_TICKS` (30 s) → you are teleported back to the drink position; `dreaming:0b`.
  4. Re-enter, then `/kill @s` while in the dream → you do NOT die; you wake at the return position with ~2 hearts.
  5. Re-enter, disconnect, reconnect → you are woken (not stuck in the dream).

- [ ] **Step 4: Record the result** in `dream-slice2-smoke.md` (pass/fail per check, server version, date) and commit:
```bash
git add hexerei/src/test/smoke/dream-slice2-smoke.md
git commit -m "test(hexerei): dedicated-server smoke procedure + result for dream Slice 2"
```

---

## Out of scope (Slice 3+)
Procedural island terrain from `disturbance`; the 5 outer domain islands; client dream sky/render. The void datapack + `DreamWorld.preparePlatform` are the seams slice 3 replaces with a disturbance-driven island generator.
