# Taint & Atmosphere System — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a world-taint data layer that makes the altar visually evolve across 4 glow stages, mutates nearby blocks over time, makes rituals visually dramatic, and lets the chalk cycle through rites via Shift+Scroll.

**Architecture:** `ChunkTaintData` (SavedData) is the single source of truth for taint per chunk. `AltarBlockEntity` reads it every 40 ticks to update its `TAINT_LEVEL` blockstate property (0–3), which drives 4 texture variants. A `LevelTickEvent` handler pulses world block mutations and taint decay. Ritual visuals live inside `Rite.perform()`. Chalk rite-cycling is a client `MouseScrollingEvent` → C2S packet → item NBT update.

**Tech Stack:** Forge 1.20.1 (47.4.10), Java 17, JUnit 5, Gradle 8.8. Run tests with `JAVA_HOME=../hexerei-work/tools/jdk17 ./gradlew test`. Run client with `JAVA_HOME=../hexerei-work/tools/jdk17 ./gradlew runClient`. All paths relative to `hexerei/src/main/java/com/vel5id/hexerei/`.

## Global Constraints

- Package root: `com.vel5id.hexerei`
- Mod ID: `hexerei`
- No new dependencies beyond existing Forge + MC
- All `DeferredRegister` instances registered in `HexereiMod` constructor on `modBus`
- Forge-bus event handlers registered via `MinecraftForge.EVENT_BUS.register()`
- Client-only code guarded with `DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)` or `@OnlyIn(Dist.CLIENT)`
- Tests: pure-Java JUnit 5, no MC game instance. MC data classes (`BlockPos`, `CompoundTag`, `ChunkPos`) are on the test classpath and usable directly.

---

## File Map

```
New Java (main):
  power/TaintLevel.java
  power/ChunkTaintData.java
  network/HexereiNetwork.java
  network/CycleRiteC2SPacket.java
  network/TaintSyncS2CPacket.java
  client/ClientTaintCache.java
  ritual/CircleSize.java
  ritual/WorldTaintAura.java
  registry/HexereiParticles.java
  client/particle/WispParticle.java
  client/particle/AshParticle.java

New Java (test):
  power/TaintDataTest.java
  ritual/CircleSizeTest.java

Modified Java (main):
  HexereiMod.java                         — register network, particles, event handlers
  block/AltarBlock.java                   — add TAINT_LEVEL IntegerProperty
  blockentity/AltarBlockEntity.java       — server tick: update blockstate; client tick: particles
  ritual/RitualRecipe.java                — add id, circleSize fields; drop requiresSmallCircle
  ritual/RitualRecipes.java               — add ALL list; update match() signature
  ritual/RitualActivation.java            — update match() call
  ritual/TempestRite.java                 — add particles, sound, charred_stone, addTaint
  block/ritual/RitualCircleBlock.java     — burst effect before tryPerform
  item/RitualChalkItem.java               — read rite NBT, draw by circleSize ring, tooltip
  client/HexereiClient.java               — MouseScrollingEvent + particle provider registration

Modified Java (test):
  ritual/RitualRecipesTest.java           — update match() call to new signature

New assets:
  textures/block/altar_low.png            (copy from /tmp/hexerei_textures/)
  textures/block/altar_medium.png         (copy from /tmp/hexerei_textures/)
  textures/block/altar_high.png           (copy from /tmp/hexerei_textures/)
  textures/item/ritual_chalk.png          (copy from /tmp/hexerei_textures/)
  textures/particle/wisp_low.png          (already done)
  textures/particle/wisp_medium.png       (already done)
  textures/particle/wisp_high.png         (already done)
  particles/wisp_low.json
  particles/wisp_medium.json
  particles/wisp_high.json
  particles/ash.json
  models/block/altar_taint_1.json
  models/block/altar_taint_2.json
  models/block/altar_taint_3.json

Modified assets:
  blockstates/altar.json                  — 8 variants (joined × taint_level)
  lang/en_us.json                         — chalk rite/circle tooltip keys
  lang/ru_ru.json                         — same in Russian
```

---

## Task 1: TaintLevel + ChunkTaintData

**Files:**
- Create: `src/main/java/com/vel5id/hexerei/power/TaintLevel.java`
- Create: `src/main/java/com/vel5id/hexerei/power/ChunkTaintData.java`
- Create: `src/test/java/com/vel5id/hexerei/power/TaintDataTest.java`

**Interfaces:**
- Produces: `TaintLevel.fromValue(float)`, `ChunkTaintData.get(ServerLevel)`, `addTaint(ChunkPos, float)`, `getTaint(ChunkPos)`, `getLevel(ChunkPos)`, `decayTick()`

- [ ] **Step 1: Write the failing tests**

```java
// src/test/java/com/vel5id/hexerei/power/TaintDataTest.java
package com.vel5id.hexerei.power;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TaintDataTest {
    private static final ChunkPos P = new ChunkPos(0, 0);

    @Test void freshChunk_isNone() {
        assertEquals(TaintLevel.NONE, new ChunkTaintData().getLevel(P));
    }

    @Test void addTaint_increases() {
        ChunkTaintData d = new ChunkTaintData();
        d.addTaint(P, 25f);
        assertEquals(25f, d.getTaint(P), 0.01f);
        assertEquals(TaintLevel.LOW, d.getLevel(P));
    }

    @Test void addTaint_capsAt100() {
        ChunkTaintData d = new ChunkTaintData();
        d.addTaint(P, 80f);
        d.addTaint(P, 80f);
        assertEquals(100f, d.getTaint(P), 0.01f);
    }

    @Test void decayTick_reducesBy05() {
        ChunkTaintData d = new ChunkTaintData();
        d.addTaint(P, 25f);
        d.decayTick();
        assertEquals(24.5f, d.getTaint(P), 0.01f);
    }

    @Test void decayTick_doesNotGoBelowFloor() {
        ChunkTaintData d = new ChunkTaintData();
        d.addTaint(P, 10f); // floor = 10 * 0.1 = 1.0
        for (int i = 0; i < 30; i++) d.decayTick();
        assertEquals(1.0f, d.getTaint(P), 0.01f);
    }

    @Test void permanentFloor_accumulates() {
        ChunkTaintData d = new ChunkTaintData();
        d.addTaint(P, 10f);  // floor += 1.0
        d.addTaint(P, 20f);  // floor += 2.0 → total floor = 3.0
        for (int i = 0; i < 100; i++) d.decayTick();
        assertEquals(3.0f, d.getTaint(P), 0.01f);
    }

    @Test void taintLevelThresholds() {
        assertEquals(TaintLevel.NONE,   TaintLevel.fromValue(0f));
        assertEquals(TaintLevel.NONE,   TaintLevel.fromValue(14.9f));
        assertEquals(TaintLevel.LOW,    TaintLevel.fromValue(15f));
        assertEquals(TaintLevel.LOW,    TaintLevel.fromValue(39.9f));
        assertEquals(TaintLevel.MEDIUM, TaintLevel.fromValue(40f));
        assertEquals(TaintLevel.HIGH,   TaintLevel.fromValue(70f));
        assertEquals(TaintLevel.HIGH,   TaintLevel.fromValue(100f));
    }

    @Test void roundTrip_preservesTaintAndFloor() {
        ChunkTaintData original = new ChunkTaintData();
        original.addTaint(P, 40f);
        CompoundTag tag = original.save(new CompoundTag());
        ChunkTaintData loaded = ChunkTaintData.load(tag);
        assertEquals(40f, loaded.getTaint(P), 0.01f);
        // floor = 40 * 0.1 = 4.0; decay 80 times (36/0.5 = 72 steps) → should floor at 4.0
        for (int i = 0; i < 80; i++) loaded.decayTick();
        assertEquals(4.0f, loaded.getTaint(P), 0.01f);
    }
}
```

- [ ] **Step 2: Run — expect compile failure**

```bash
cd hexerei && JAVA_HOME=../hexerei-work/tools/jdk17 ./gradlew test 2>&1 | grep -E "error:|FAILED|cannot find"
```

Expected: `error: cannot find symbol — TaintLevel, ChunkTaintData`

- [ ] **Step 3: Implement TaintLevel**

```java
// src/main/java/com/vel5id/hexerei/power/TaintLevel.java
package com.vel5id.hexerei.power;

public enum TaintLevel {
    NONE, LOW, MEDIUM, HIGH;

    public static TaintLevel fromValue(float v) {
        if (v >= 70f) return HIGH;
        if (v >= 40f) return MEDIUM;
        if (v >= 15f) return LOW;
        return NONE;
    }
}
```

- [ ] **Step 4: Implement ChunkTaintData**

```java
// src/main/java/com/vel5id/hexerei/power/ChunkTaintData.java
package com.vel5id.hexerei.power;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

public class ChunkTaintData extends SavedData {
    private static final String KEY = "hexerei_taint";
    private final Map<Long, Float> taint = new HashMap<>();
    private final Map<Long, Float> floor = new HashMap<>();

    public static ChunkTaintData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ChunkTaintData::load, ChunkTaintData::new, KEY);
    }

    public void addTaint(ChunkPos pos, float amount) {
        long k = pos.toLong();
        float cur = taint.getOrDefault(k, 0f);
        float next = Math.min(100f, cur + amount);
        taint.put(k, next);
        floor.put(k, Math.min(100f, floor.getOrDefault(k, 0f) + amount * 0.1f));
        setDirty();
    }

    public float getTaint(ChunkPos pos) {
        return taint.getOrDefault(pos.toLong(), 0f);
    }

    public TaintLevel getLevel(ChunkPos pos) {
        return TaintLevel.fromValue(getTaint(pos));
    }

    /** Call every 1200 server ticks (60s). Decays taint by 0.5, never below permanent floor. */
    public void decayTick() {
        boolean changed = false;
        for (Long k : new java.util.HashSet<>(taint.keySet())) {
            float cur = taint.get(k);
            float flr = floor.getOrDefault(k, 0f);
            float next = Math.max(flr, cur - 0.5f);
            if (next != cur) { taint.put(k, next); changed = true; }
        }
        if (changed) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, Float> e : taint.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLong("p", e.getKey());
            t.putFloat("t", e.getValue());
            t.putFloat("f", floor.getOrDefault(e.getKey(), 0f));
            list.add(t);
        }
        tag.put("entries", list);
        return tag;
    }

    public static ChunkTaintData load(CompoundTag tag) {
        ChunkTaintData d = new ChunkTaintData();
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            long k = t.getLong("p");
            d.taint.put(k, t.getFloat("t"));
            d.floor.put(k, t.getFloat("f"));
        }
        return d;
    }
}
```

- [ ] **Step 5: Run tests — expect all pass**

```bash
cd hexerei && JAVA_HOME=../hexerei-work/tools/jdk17 ./gradlew test 2>&1 | tail -8
```

Expected: `BUILD SUCCESSFUL` with all TaintDataTest tests passing.

- [ ] **Step 6: Commit**

```bash
cd hexerei && git add src/main/java/com/vel5id/hexerei/power/TaintLevel.java \
  src/main/java/com/vel5id/hexerei/power/ChunkTaintData.java \
  src/test/java/com/vel5id/hexerei/power/TaintDataTest.java
git commit -m "feat(hexerei): ChunkTaintData + TaintLevel — world taint persistence layer"
```

---

## Task 2: CircleSize + RitualRecipe/RitualRecipes refactor

**Files:**
- Create: `src/main/java/com/vel5id/hexerei/ritual/CircleSize.java`
- Modify: `src/main/java/com/vel5id/hexerei/ritual/RitualRecipe.java`
- Modify: `src/main/java/com/vel5id/hexerei/ritual/RitualRecipes.java`
- Modify: `src/main/java/com/vel5id/hexerei/ritual/RitualActivation.java`
- Modify: `src/test/java/com/vel5id/hexerei/ritual/RitualRecipesTest.java`
- Create: `src/test/java/com/vel5id/hexerei/ritual/CircleSizeTest.java`

**Interfaces:**
- Consumes: `RitualCircle.smallRing()`, `RitualCircle.isSmallComplete()`
- Produces: `CircleSize.SMALL`, `CircleSize.isComplete(Predicate<BlockPos>, BlockPos)`, `CircleSize.ringPositions(BlockPos)`, `RitualRecipe.id()`, `RitualRecipe.circleSize()`, `RitualRecipes.ALL`, `RitualRecipes.match(Predicate<BlockPos>, BlockPos, String)`

- [ ] **Step 1: Write failing tests**

```java
// src/test/java/com/vel5id/hexerei/ritual/CircleSizeTest.java
package com.vel5id.hexerei.ritual;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CircleSizeTest {
    @Test void small_has12Positions() {
        assertEquals(12, CircleSize.SMALL.ringPositions(new BlockPos(0, 0, 0)).size());
    }

    @Test void small_isComplete_whenAllGlyphs() {
        BlockPos center = new BlockPos(0, 0, 0);
        Set<BlockPos> ring = new HashSet<>(CircleSize.SMALL.ringPositions(center));
        assertTrue(CircleSize.SMALL.isComplete(ring::contains, center));
    }

    @Test void small_notComplete_whenOneMissing() {
        BlockPos center = new BlockPos(0, 0, 0);
        Set<BlockPos> ring = new HashSet<>(CircleSize.SMALL.ringPositions(center));
        ring.remove(ring.iterator().next());
        assertFalse(CircleSize.SMALL.isComplete(ring::contains, center));
    }
}
```

Update `RitualRecipesTest` to use the new `match()` signature:

```java
// src/test/java/com/vel5id/hexerei/ritual/RitualRecipesTest.java
package com.vel5id.hexerei.ritual;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class RitualRecipesTest {
    private static java.util.function.Predicate<BlockPos> fullSmallRing(BlockPos center) {
        Set<BlockPos> ring = new HashSet<>(RitualCircle.smallRing(center));
        return ring::contains;
    }

    @Test void matchesTempestWithCircleAndSacrifice() {
        BlockPos center = new BlockPos(0, 0, 0);
        assertEquals(RitualRecipes.TEMPEST,
                RitualRecipes.match(fullSmallRing(center), center, "hexerei:mandrake_root").orElse(null));
    }

    @Test void incompleteCircleDoesNotMatch() {
        BlockPos center = new BlockPos(0, 0, 0);
        assertTrue(RitualRecipes.match(p -> false, center, "hexerei:mandrake_root").isEmpty());
    }

    @Test void wrongSacrificeDoesNotMatch() {
        BlockPos center = new BlockPos(0, 0, 0);
        assertTrue(RitualRecipes.match(fullSmallRing(center), center, "minecraft:diamond").isEmpty());
    }

    @Test void tempestHasExpectedProperties() {
        assertEquals(100, RitualRecipes.TEMPEST.powerCost());
        assertEquals(CircleSize.SMALL, RitualRecipes.TEMPEST.circleSize());
        assertEquals("hexerei:tempest", RitualRecipes.TEMPEST.id());
    }

    @Test void all_containsTempest() {
        assertTrue(RitualRecipes.ALL.contains(RitualRecipes.TEMPEST));
    }
}
```

- [ ] **Step 2: Run — expect compile failures**

```bash
cd hexerei && JAVA_HOME=../hexerei-work/tools/jdk17 ./gradlew test 2>&1 | grep "error:" | head -10
```

- [ ] **Step 3: Implement CircleSize**

```java
// src/main/java/com/vel5id/hexerei/ritual/CircleSize.java
package com.vel5id.hexerei.ritual;

import net.minecraft.core.BlockPos;
import java.util.List;
import java.util.function.Predicate;

public enum CircleSize {
    SMALL {
        @Override public List<BlockPos> ringPositions(BlockPos center) {
            return RitualCircle.smallRing(center);
        }
        @Override public boolean isComplete(Predicate<BlockPos> isGlyph, BlockPos center) {
            return RitualCircle.isSmallComplete(isGlyph, center);
        }
    };

    public abstract List<BlockPos> ringPositions(BlockPos center);
    public abstract boolean isComplete(Predicate<BlockPos> isGlyph, BlockPos center);
}
```

- [ ] **Step 4: Update RitualRecipe** (replace `requiresSmallCircle` with `id` + `circleSize`)

```java
// src/main/java/com/vel5id/hexerei/ritual/RitualRecipe.java
package com.vel5id.hexerei.ritual;

/** A ritual: an id + required circle size + sacrifice item + altar-power cost + a rite. */
public record RitualRecipe(String id, CircleSize circleSize, String sacrificeId, int powerCost, Rite rite, String nameKey) {}
```

- [ ] **Step 5: Update RitualRecipes**

```java
// src/main/java/com/vel5id/hexerei/ritual/RitualRecipes.java
package com.vel5id.hexerei.ritual;

import net.minecraft.core.BlockPos;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public final class RitualRecipes {
    private RitualRecipes() {}

    public static final RitualRecipe TEMPEST = new RitualRecipe(
            "hexerei:tempest", CircleSize.SMALL, "hexerei:mandrake_root", 100,
            new TempestRite(12000), "ritual.hexerei.tempest");

    public static final List<RitualRecipe> ALL = List.of(TEMPEST);

    public static Optional<RitualRecipe> match(Predicate<BlockPos> isGlyph, BlockPos center, String sacrificeId) {
        for (RitualRecipe r : ALL) {
            if (r.circleSize().isComplete(isGlyph, center) && r.sacrificeId().equals(sacrificeId)) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }
}
```

- [ ] **Step 6: Update RitualActivation** (change `match()` call)

In `RitualActivation.java`, replace:
```java
boolean circleComplete = RitualCircle.isSmallComplete(
        p -> level.getBlockState(p).is(HexereiBlocks.RITUAL_GLYPH.get()), center);
// ...
Optional<RitualRecipe> match = RitualRecipes.match(circleComplete, id);
```
With:
```java
java.util.function.Predicate<BlockPos> isGlyph =
        p -> level.getBlockState(p).is(HexereiBlocks.RITUAL_GLYPH.get());
// ...
Optional<RitualRecipe> match = RitualRecipes.match(isGlyph, center, id);
```

Also remove the `circleComplete` local variable entirely — the old `boolean circleComplete = ...` line is deleted.

- [ ] **Step 7: Run all tests — expect all pass**

```bash
cd hexerei && JAVA_HOME=../hexerei-work/tools/jdk17 ./gradlew test 2>&1 | tail -8
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Commit**

```bash
cd hexerei && git add src/main/java/com/vel5id/hexerei/ritual/CircleSize.java \
  src/main/java/com/vel5id/hexerei/ritual/RitualRecipe.java \
  src/main/java/com/vel5id/hexerei/ritual/RitualRecipes.java \
  src/main/java/com/vel5id/hexerei/ritual/RitualActivation.java \
  src/test/java/com/vel5id/hexerei/ritual/CircleSizeTest.java \
  src/test/java/com/vel5id/hexerei/ritual/RitualRecipesTest.java
git commit -m "refactor(hexerei): CircleSize enum + RitualRecipe.id/circleSize + RitualRecipes.ALL"
```

---

## Task 3: Network layer

**Files:**
- Create: `src/main/java/com/vel5id/hexerei/network/HexereiNetwork.java`
- Create: `src/main/java/com/vel5id/hexerei/network/CycleRiteC2SPacket.java`
- Create: `src/main/java/com/vel5id/hexerei/network/TaintSyncS2CPacket.java`
- Create: `src/main/java/com/vel5id/hexerei/client/ClientTaintCache.java`
- Modify: `src/main/java/com/vel5id/hexerei/HexereiMod.java`

**Interfaces:**
- Consumes: `RitualRecipes.ALL`, `HexereiItems.RITUAL_CHALK`, `ChunkTaintData` (server side in packet handlers)
- Produces: `HexereiNetwork.CHANNEL`, `HexereiNetwork.sendTaintSync(ServerLevel, ChunkPos)`, `ClientTaintCache.get(long)`, `ClientTaintCache.getLevel(ChunkPos)`

- [ ] **Step 1: Create HexereiNetwork**

```java
// src/main/java/com/vel5id/hexerei/network/HexereiNetwork.java
package com.vel5id.hexerei.network;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.power.ChunkTaintData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class HexereiNetwork {
    private HexereiNetwork() {}

    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(HexereiMod.MODID, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private static int nextId = 0;

    public static void register() {
        CHANNEL.registerMessage(nextId++, CycleRiteC2SPacket.class,
                CycleRiteC2SPacket::encode, CycleRiteC2SPacket::decode, CycleRiteC2SPacket::handle);
        CHANNEL.registerMessage(nextId++, TaintSyncS2CPacket.class,
                TaintSyncS2CPacket::encode, TaintSyncS2CPacket::decode, TaintSyncS2CPacket::handle);
    }

    /** Send current taint for a chunk to all players watching it. */
    public static void sendTaintSync(ServerLevel level, ChunkPos pos) {
        float value = ChunkTaintData.get(level).getTaint(pos);
        CHANNEL.send(
                PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunk(pos.x, pos.z)),
                new TaintSyncS2CPacket(pos.toLong(), value));
    }
}
```

- [ ] **Step 2: Create CycleRiteC2SPacket**

```java
// src/main/java/com/vel5id/hexerei/network/CycleRiteC2SPacket.java
package com.vel5id.hexerei.network;

import com.vel5id.hexerei.registry.HexereiItems;
import com.vel5id.hexerei.ritual.RitualRecipe;
import com.vel5id.hexerei.ritual.RitualRecipes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public record CycleRiteC2SPacket(int delta) {
    public static void encode(CycleRiteC2SPacket p, FriendlyByteBuf buf) { buf.writeInt(p.delta()); }
    public static CycleRiteC2SPacket decode(FriendlyByteBuf buf) { return new CycleRiteC2SPacket(buf.readInt()); }

    public static void handle(CycleRiteC2SPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            ItemStack stack = player.getMainHandItem();
            if (!stack.is(HexereiItems.RITUAL_CHALK.get())) {
                stack = player.getOffhandItem();
                if (!stack.is(HexereiItems.RITUAL_CHALK.get())) return;
            }
            List<RitualRecipe> all = RitualRecipes.ALL;
            if (all.isEmpty()) return;
            String current = stack.hasTag() ? stack.getOrCreateTag().getString("hexerei:rite") : "";
            int idx = 0;
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).id().equals(current)) { idx = i; break; }
            }
            idx = Math.floorMod(idx + pkt.delta(), all.size());
            RitualRecipe next = all.get(idx);
            stack.getOrCreateTag().putString("hexerei:rite", next.id());
            player.displayClientMessage(
                    Component.translatable(next.nameKey())
                            .append(Component.literal(" · " + next.circleSize().ringPositions(player.blockPosition()).size() + " glyphs")),
                    true);
        });
        ctx.get().setPacketHandled(true);
    }
}
```

- [ ] **Step 3: Create TaintSyncS2CPacket**

```java
// src/main/java/com/vel5id/hexerei/network/TaintSyncS2CPacket.java
package com.vel5id.hexerei.network;

import com.vel5id.hexerei.client.ClientTaintCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record TaintSyncS2CPacket(long chunkPos, float taint) {
    public static void encode(TaintSyncS2CPacket p, FriendlyByteBuf buf) {
        buf.writeLong(p.chunkPos()); buf.writeFloat(p.taint());
    }
    public static TaintSyncS2CPacket decode(FriendlyByteBuf buf) {
        return new TaintSyncS2CPacket(buf.readLong(), buf.readFloat());
    }
    public static void handle(TaintSyncS2CPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> ClientTaintCache.set(pkt.chunkPos(), pkt.taint())));
        ctx.get().setPacketHandled(true);
    }
}
```

- [ ] **Step 4: Create ClientTaintCache**

```java
// src/main/java/com/vel5id/hexerei/client/ClientTaintCache.java
package com.vel5id.hexerei.client;

import com.vel5id.hexerei.power.TaintLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.api.distmarker.Dist;

import java.util.HashMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class ClientTaintCache {
    private ClientTaintCache() {}
    private static final Map<Long, Float> CACHE = new HashMap<>();

    public static void set(long chunkPos, float value) { CACHE.put(chunkPos, value); }
    public static float get(long chunkPos) { return CACHE.getOrDefault(chunkPos, 0f); }
    public static TaintLevel getLevel(ChunkPos pos) { return TaintLevel.fromValue(get(pos.toLong())); }
    public static void clear() { CACHE.clear(); }
}
```

- [ ] **Step 5: Register network in HexereiMod**

In `HexereiMod.java`, add `HexereiNetwork.register()` call in the constructor:

```java
// At the end of the HexereiMod() constructor, after existing register calls:
HexereiNetwork.register();
```

Add the import: `import com.vel5id.hexerei.network.HexereiNetwork;`

- [ ] **Step 6: Build — expect success**

```bash
cd hexerei && JAVA_HOME=../hexerei-work/tools/jdk17 ./gradlew build 2>&1 | tail -6
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
cd hexerei && git add src/main/java/com/vel5id/hexerei/network/ \
  src/main/java/com/vel5id/hexerei/client/ClientTaintCache.java \
  src/main/java/com/vel5id/hexerei/HexereiMod.java
git commit -m "feat(hexerei): network layer — CycleRiteC2SPacket, TaintSyncS2CPacket, ClientTaintCache"
```

---

## Task 4: Altar TAINT_LEVEL blockstate + taint update tick

**Files:**
- Modify: `src/main/java/com/vel5id/hexerei/block/AltarBlock.java`
- Modify: `src/main/java/com/vel5id/hexerei/blockentity/AltarBlockEntity.java`
- Modify: `src/main/resources/assets/hexerei/blockstates/altar.json`
- Create: `src/main/resources/assets/hexerei/models/block/altar_taint_1.json`
- Create: `src/main/resources/assets/hexerei/models/block/altar_taint_2.json`
- Create: `src/main/resources/assets/hexerei/models/block/altar_taint_3.json`
- Copy textures: `altar_low.png`, `altar_medium.png`, `altar_high.png`

**Interfaces:**
- Consumes: `ChunkTaintData.get()`, `HexereiNetwork.sendTaintSync()`, `TaintLevel`
- Produces: `AltarBlock.TAINT_LEVEL` (IntegerProperty 0–3), altar blockstate with 8 variants

- [ ] **Step 1: Copy altar taint textures**

```bash
cp /tmp/hexerei_textures/altar_low.png    hexerei/src/main/resources/assets/hexerei/textures/block/
cp /tmp/hexerei_textures/altar_medium.png hexerei/src/main/resources/assets/hexerei/textures/block/
cp /tmp/hexerei_textures/altar_high.png   hexerei/src/main/resources/assets/hexerei/textures/block/
```

- [ ] **Step 2: Create 3 taint model files**

```json
// src/main/resources/assets/hexerei/models/block/altar_taint_1.json
{
  "parent": "minecraft:block/cube_bottom_top",
  "textures": {
    "side":   "hexerei:block/altar",
    "bottom": "hexerei:block/altar_top",
    "top":    "hexerei:block/altar_low"
  }
}
```

```json
// src/main/resources/assets/hexerei/models/block/altar_taint_2.json
{
  "parent": "minecraft:block/cube_bottom_top",
  "textures": {
    "side":   "hexerei:block/altar",
    "bottom": "hexerei:block/altar_top",
    "top":    "hexerei:block/altar_medium"
  }
}
```

```json
// src/main/resources/assets/hexerei/models/block/altar_taint_3.json
{
  "parent": "minecraft:block/cube_bottom_top",
  "textures": {
    "side":   "hexerei:block/altar",
    "bottom": "hexerei:block/altar_top",
    "top":    "hexerei:block/altar_high"
  }
}
```

- [ ] **Step 3: Update blockstate JSON** (8 variants: 2 joined states × 4 taint levels; joined+taint>0 shows taint model)

```json
// src/main/resources/assets/hexerei/blockstates/altar.json
{
  "variants": {
    "joined=false,taint_level=0": { "model": "hexerei:block/altar" },
    "joined=false,taint_level=1": { "model": "hexerei:block/altar_taint_1" },
    "joined=false,taint_level=2": { "model": "hexerei:block/altar_taint_2" },
    "joined=false,taint_level=3": { "model": "hexerei:block/altar_taint_3" },
    "joined=true,taint_level=0":  { "model": "hexerei:block/altar_joined" },
    "joined=true,taint_level=1":  { "model": "hexerei:block/altar_taint_1" },
    "joined=true,taint_level=2":  { "model": "hexerei:block/altar_taint_2" },
    "joined=true,taint_level=3":  { "model": "hexerei:block/altar_taint_3" }
  }
}
```

- [ ] **Step 4: Add TAINT_LEVEL to AltarBlock**

In `AltarBlock.java`, add after the existing `ALTAR_JOINED` field:

```java
public static final IntegerProperty TAINT_LEVEL = IntegerProperty.create("taint_level", 0, 3);
```

Add import: `import net.minecraft.world.level.block.state.properties.IntegerProperty;`

Update `createBlockStateDefinition`:
```java
@Override
protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(ALTAR_JOINED, TAINT_LEVEL);
}
```

Update `registerDefaultState` in constructor:
```java
registerDefaultState(stateDefinition.any().setValue(ALTAR_JOINED, false).setValue(TAINT_LEVEL, 0));
```

Update `getTicker` to also return a client ticker:
```java
@Nullable
@Override
public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
    if (type != HexereiBlockEntities.ALTAR.get()) return null;
    if (level.isClientSide) {
        return (lvl, pos, st, be) -> ((AltarBlockEntity) be).clientTick(lvl, pos, st);
    }
    return (lvl, pos, st, be) -> AltarBlockEntity.serverTick(lvl, pos, st, (AltarBlockEntity) be);
}
```

In `setCore()` inside `AltarBlockEntity`, the line that sets ALTAR_JOINED must also preserve TAINT_LEVEL:
```java
// Find this block in AltarBlockEntity.setCore():
if (st.getBlock() instanceof AltarBlock && st.getValue(AltarBlock.ALTAR_JOINED) != joined) {
    level.setBlock(worldPosition, st.setValue(AltarBlock.ALTAR_JOINED, joined), 3);
}
```
This is fine — `setValue(ALTAR_JOINED, joined)` only changes that property, TAINT_LEVEL stays.

- [ ] **Step 5: Add taint tick to AltarBlockEntity**

At the end of `serverTick()`, after the existing recharge logic, add:

```java
// Sync taint level to blockstate every 40 ticks (core only)
if (be.ticks % 40 == 0 && be.isCore() && level instanceof net.minecraft.server.level.ServerLevel sl) {
    net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(pos);
    com.vel5id.hexerei.power.TaintLevel tl = com.vel5id.hexerei.power.ChunkTaintData.get(sl).getLevel(cp);
    int lvl = tl.ordinal();
    BlockState cur = level.getBlockState(pos);
    if (cur.getBlock() instanceof com.vel5id.hexerei.block.AltarBlock && cur.getValue(com.vel5id.hexerei.block.AltarBlock.TAINT_LEVEL) != lvl) {
        level.setBlock(pos, cur.setValue(com.vel5id.hexerei.block.AltarBlock.TAINT_LEVEL, lvl), 3);
        com.vel5id.hexerei.network.HexereiNetwork.sendTaintSync(sl, cp);
    }
}
```

Add `clientTick` method at the end of `AltarBlockEntity`:

```java
public void clientTick(Level level, BlockPos pos, BlockState state) {
    if (!level.isClientSide) return;
    net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
            net.minecraftforge.api.distmarker.Dist.CLIENT,
            () -> () -> doClientParticleTick(level, pos));
}

@net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
private void doClientParticleTick(Level level, BlockPos pos) {
    com.vel5id.hexerei.power.TaintLevel tl =
            com.vel5id.hexerei.client.ClientTaintCache.getLevel(new net.minecraft.world.level.ChunkPos(pos));
    if (tl == com.vel5id.hexerei.power.TaintLevel.NONE) return;
    // Particle emission is wired in Task 5 after HexereiParticles is registered.
    // This method is the hook; leave body empty until then.
}
```

- [ ] **Step 6: Register LevelTickEvent for decay and chunk-watch sync**

Create a new event handler class:

```java
// src/main/java/com/vel5id/hexerei/HexereiLevelEvents.java
package com.vel5id.hexerei;

import com.vel5id.hexerei.network.HexereiNetwork;
import com.vel5id.hexerei.power.ChunkTaintData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public final class HexereiLevelEvents {
    private HexereiLevelEvents() {}

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel sl)) return;
        long gt = sl.getGameTime();
        if (gt % 1200 == 0) {           // every 60s: decay taint
            ChunkTaintData.get(sl).decayTick();
        }
    }

    @SubscribeEvent
    public static void onChunkWatch(ChunkWatchEvent.Watch event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        ChunkPos pos = event.getPos();
        float taint = ChunkTaintData.get(sl).getTaint(pos);
        if (taint > 0f) {
            // Send current taint to the player who just loaded this chunk
            HexereiNetwork.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(event::getPlayer),
                    new com.vel5id.hexerei.network.TaintSyncS2CPacket(pos.toLong(), taint));
        }
    }
}
```

Register in `HexereiMod` constructor:
```java
net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(HexereiLevelEvents.class);
```

Add import: `import net.minecraftforge.common.MinecraftForge;`

- [ ] **Step 7: Build — expect success**

```bash
cd hexerei && JAVA_HOME=../hexerei-work/tools/jdk17 ./gradlew build 2>&1 | tail -6
```

- [ ] **Step 8: Visual verify in runClient**

```bash
cd hexerei && JAVA_HOME=../hexerei-work/tools/jdk17 ./gradlew runClient
```

In creative: place an altar, open console (`/`), run:
```
/data merge block ~ ~ ~ {hexerei_taint: ... }
```
Easier: temporarily add `ChunkTaintData.get(level).addTaint(chunkPos, 50f)` to `AltarBlockEntity.onLoad()` for testing, then revert.

Verify: altar top texture changes at taint levels 1/2/3.

- [ ] **Step 9: Commit**

```bash
cd hexerei && git add \
  src/main/java/com/vel5id/hexerei/block/AltarBlock.java \
  src/main/java/com/vel5id/hexerei/blockentity/AltarBlockEntity.java \
  src/main/java/com/vel5id/hexerei/HexereiLevelEvents.java \
  src/main/java/com/vel5id/hexerei/HexereiMod.java \
  src/main/resources/assets/hexerei/blockstates/altar.json \
  src/main/resources/assets/hexerei/models/block/altar_taint_1.json \
  src/main/resources/assets/hexerei/models/block/altar_taint_2.json \
  src/main/resources/assets/hexerei/models/block/altar_taint_3.json \
  src/main/resources/assets/hexerei/textures/block/altar_low.png \
  src/main/resources/assets/hexerei/textures/block/altar_medium.png \
  src/main/resources/assets/hexerei/textures/block/altar_high.png
git commit -m "feat(hexerei): altar TAINT_LEVEL blockstate — 4 visual stages, decay, chunk sync"
```

---

## Task 5: Particle system + altar client emission

**Files:**
- Create: `src/main/java/com/vel5id/hexerei/registry/HexereiParticles.java`
- Create: `src/main/java/com/vel5id/hexerei/client/particle/WispParticle.java`
- Create: `src/main/java/com/vel5id/hexerei/client/particle/AshParticle.java`
- Create: `src/main/resources/assets/hexerei/particles/wisp_low.json` (and wisp_medium, wisp_high, ash)
- Modify: `src/main/java/com/vel5id/hexerei/client/HexereiClient.java`
- Modify: `src/main/java/com/vel5id/hexerei/blockentity/AltarBlockEntity.java` (fill `doClientParticleTick`)
- Modify: `src/main/java/com/vel5id/hexerei/HexereiMod.java`

**No unit test** — particle rendering is visual. Verified by running client.

- [ ] **Step 1: Register particle types**

```java
// src/main/java/com/vel5id/hexerei/registry/HexereiParticles.java
package com.vel5id.hexerei.registry;

import com.vel5id.hexerei.HexereiMod;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class HexereiParticles {
    private HexereiParticles() {}

    public static final DeferredRegister<net.minecraft.core.particles.ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, HexereiMod.MODID);

    public static final RegistryObject<SimpleParticleType> WISP_LOW    = PARTICLES.register("wisp_low",    () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> WISP_MEDIUM = PARTICLES.register("wisp_medium", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> WISP_HIGH   = PARTICLES.register("wisp_high",   () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> ASH         = PARTICLES.register("ash",         () -> new SimpleParticleType(false));
}
```

Register in `HexereiMod` constructor:
```java
HexereiParticles.PARTICLES.register(modBus);
```
Add import: `import com.vel5id.hexerei.registry.HexereiParticles;`

- [ ] **Step 2: Create particle JSON files**

```json
// src/main/resources/assets/hexerei/particles/wisp_low.json
{ "textures": ["hexerei:wisp_low"] }
```
```json
// src/main/resources/assets/hexerei/particles/wisp_medium.json
{ "textures": ["hexerei:wisp_medium"] }
```
```json
// src/main/resources/assets/hexerei/particles/wisp_high.json
{ "textures": ["hexerei:wisp_high"] }
```
```json
// src/main/resources/assets/hexerei/particles/ash.json
{ "textures": ["hexerei:wisp_high"] }
```

- [ ] **Step 3: Create WispParticle**

```java
// src/main/java/com/vel5id/hexerei/client/particle/WispParticle.java
package com.vel5id.hexerei.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WispParticle extends TextureSheetParticle {
    WispParticle(ClientLevel level, double x, double y, double z,
                 double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz);
        pickSprite(sprites);
        lifetime = 30 + random.nextInt(20);
        hasPhysics = false;
        friction = 0.94f;
        gravity = -0.015f;
        quadSize = 0.12f + random.nextFloat() * 0.08f;
        alpha = 0.75f;
    }

    @Override
    public void tick() {
        super.tick();
        xd *= 0.96f;
        zd *= 0.96f;
        if (age > lifetime - 8) alpha = Math.max(0f, alpha - 0.09f);
    }

    @Override
    public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    @OnlyIn(Dist.CLIENT)
    public static class LowProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public LowProvider(SpriteSet s) { this.sprites = s; }
        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel level,
                double x, double y, double z, double vx, double vy, double vz) {
            WispParticle p = new WispParticle(level, x, y, z, vx, vy, vz, sprites);
            p.rCol = 0x6E / 255f; p.gCol = 0x14 / 255f; p.bCol = 0xBE / 255f;
            return p;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class MediumProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public MediumProvider(SpriteSet s) { this.sprites = s; }
        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel level,
                double x, double y, double z, double vx, double vy, double vz) {
            WispParticle p = new WispParticle(level, x, y, z, vx, vy, vz, sprites);
            p.rCol = 0x46 / 255f; p.gCol = 0f; p.bCol = 0x9B / 255f;
            return p;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static class HighProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public HighProvider(SpriteSet s) { this.sprites = s; }
        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel level,
                double x, double y, double z, double vx, double vy, double vz) {
            WispParticle p = new WispParticle(level, x, y, z, vx, vy, vz, sprites);
            p.rCol = 0xA0 / 255f; p.gCol = 0f; p.bCol = 0x4A / 255f;
            return p;
        }
    }
}
```

- [ ] **Step 4: Create AshParticle**

```java
// src/main/java/com/vel5id/hexerei/client/particle/AshParticle.java
package com.vel5id.hexerei.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class AshParticle extends TextureSheetParticle {
    AshParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0);
        pickSprite(sprites);
        lifetime = 20 + random.nextInt(15);
        hasPhysics = false;
        gravity = 0.03f;
        quadSize = 0.06f + random.nextFloat() * 0.04f;
        alpha = 0.5f;
        rCol = 0.15f; gCol = 0.1f; bCol = 0.1f;
        xd = (random.nextDouble() - 0.5) * 0.02;
        zd = (random.nextDouble() - 0.5) * 0.02;
    }

    @Override
    public void tick() {
        super.tick();
        if (age > lifetime - 6) alpha = Math.max(0f, alpha - 0.08f);
    }

    @Override
    public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    @OnlyIn(Dist.CLIENT)
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;
        public Provider(SpriteSet s) { this.sprites = s; }
        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel level,
                double x, double y, double z, double vx, double vy, double vz) {
            return new AshParticle(level, x, y, z, sprites);
        }
    }
}
```

- [ ] **Step 5: Register particle providers in HexereiClient**

Add to `HexereiClient.java`:

```java
@SubscribeEvent
public static void registerParticles(RegisterParticleProvidersEvent event) {
    event.registerSpriteSet(HexereiParticles.WISP_LOW.get(),    WispParticle.LowProvider::new);
    event.registerSpriteSet(HexereiParticles.WISP_MEDIUM.get(), WispParticle.MediumProvider::new);
    event.registerSpriteSet(HexereiParticles.WISP_HIGH.get(),   WispParticle.HighProvider::new);
    event.registerSpriteSet(HexereiParticles.ASH.get(),         AshParticle.Provider::new);
}
```

Add imports:
```java
import com.vel5id.hexerei.client.particle.AshParticle;
import com.vel5id.hexerei.client.particle.WispParticle;
import com.vel5id.hexerei.registry.HexereiParticles;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
```

- [ ] **Step 6: Fill doClientParticleTick in AltarBlockEntity**

Replace the empty `doClientParticleTick` body with:

```java
@net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
private void doClientParticleTick(Level level, BlockPos pos) {
    com.vel5id.hexerei.power.TaintLevel tl =
            com.vel5id.hexerei.client.ClientTaintCache.getLevel(new net.minecraft.world.level.ChunkPos(pos));
    if (tl == com.vel5id.hexerei.power.TaintLevel.NONE) return;
    long gt = level.getGameTime();
    int period = (tl == com.vel5id.hexerei.power.TaintLevel.LOW) ? 6 : (tl == com.vel5id.hexerei.power.TaintLevel.MEDIUM) ? 4 : 2;
    if (gt % period != 0) return;

    net.minecraft.core.particles.SimpleParticleType type = switch (tl) {
        case LOW    -> com.vel5id.hexerei.registry.HexereiParticles.WISP_LOW.get();
        case MEDIUM -> com.vel5id.hexerei.registry.HexereiParticles.WISP_MEDIUM.get();
        case HIGH   -> com.vel5id.hexerei.registry.HexereiParticles.WISP_HIGH.get();
        default     -> null;
    };
    if (type != null) {
        double px = pos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 0.8;
        double py = pos.getY() + 1.1;
        double pz = pos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 0.8;
        double vy = 0.02 + level.random.nextDouble() * 0.02;
        level.addParticle(type, px, py, pz, 0, vy, 0);
    }
    if (tl == com.vel5id.hexerei.power.TaintLevel.HIGH && gt % 5 == 0) {
        double px = pos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 1.4;
        double py = pos.getY() + 1.6;
        double pz = pos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 1.4;
        level.addParticle(com.vel5id.hexerei.registry.HexereiParticles.ASH.get(), px, py, pz, 0, 0, 0);
    }
}
```

- [ ] **Step 7: Build + visual verify**

```bash
cd hexerei && JAVA_HOME=../hexerei-work/tools/jdk17 ./gradlew build 2>&1 | tail -6
```

Then `runClient`. Place altar, add taint (use `/data` or temporarily call `addTaint` in code), verify wisps appear and change color with taint level.

- [ ] **Step 8: Commit**

```bash
cd hexerei && git add \
  src/main/java/com/vel5id/hexerei/registry/HexereiParticles.java \
  src/main/java/com/vel5id/hexerei/client/particle/ \
  src/main/java/com/vel5id/hexerei/client/HexereiClient.java \
  src/main/java/com/vel5id/hexerei/blockentity/AltarBlockEntity.java \
  src/main/java/com/vel5id/hexerei/HexereiMod.java \
  src/main/resources/assets/hexerei/particles/
git commit -m "feat(hexerei): WispParticle + AshParticle — altar emits colored wisps by taint level"
```

---

## Task 6: WorldTaintAura

**Files:**
- Create: `src/main/java/com/vel5id/hexerei/ritual/WorldTaintAura.java`
- Modify: `src/main/java/com/vel5id/hexerei/HexereiLevelEvents.java`

**Interfaces:**
- Consumes: `AltarPowerManager.get(level).getSources()` (or iterate registered altars), `ChunkTaintData.getLevel()`, `HexereiBlocks.TAINTED_GROUND`, `HexereiBlocks.CHARRED_STONE`

- [ ] **Step 1: Check how to iterate registered altars**

`AltarPowerManager` has a list of `IPowerSource`. Read its `query()` or internal list. In `AltarPowerManager.java`, the registered sources are available — the `WorldTaintAura` will call `AltarPowerManager.get(level)` and iterate sources via the existing `query(level, pos)` or a new `allSources()` getter.

Add `allSources()` to `AltarPowerManager`:

```java
// In AltarPowerManager.java, add:
public java.util.List<IPowerSource> allSources() {
    return java.util.Collections.unmodifiableList(sources);
}
```

(Read `AltarPowerManager.java` first to confirm the field name `sources`.)

- [ ] **Step 2: Create WorldTaintAura**

```java
// src/main/java/com/vel5id/hexerei/ritual/WorldTaintAura.java
package com.vel5id.hexerei.ritual;

import com.vel5id.hexerei.power.AltarPowerManager;
import com.vel5id.hexerei.power.ChunkTaintData;
import com.vel5id.hexerei.power.IPowerSource;
import com.vel5id.hexerei.power.TaintLevel;
import com.vel5id.hexerei.registry.HexereiBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

public final class WorldTaintAura {
    private WorldTaintAura() {}

    private static final int RADIUS = 5;

    /** Call every 200 server ticks (10s). Mutates blocks near tainted altars. */
    public static void pulse(ServerLevel level) {
        ChunkTaintData taintData = ChunkTaintData.get(level);
        Random rng = level.getRandom();

        for (IPowerSource src : AltarPowerManager.get(level).allSources()) {
            BlockPos center = src.getLocation();
            TaintLevel tl = taintData.getLevel(new net.minecraft.world.level.ChunkPos(center));
            if (tl == TaintLevel.NONE) continue;

            int grassCount = 0, flowerCount = 0, stoneCount = 0;

            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos p = center.offset(dx, dy, dz);
                        if (!level.isLoaded(p)) continue;
                        BlockState bs = level.getBlockState(p);

                        if (grassCount < 6 && bs.is(Blocks.GRASS_BLOCK) && rng.nextFloat() < 0.15f) {
                            level.setBlock(p, HexereiBlocks.TAINTED_GROUND.get().defaultBlockState(), 3);
                            grassCount++;
                        } else if (flowerCount < 2 && tl.ordinal() >= TaintLevel.MEDIUM.ordinal()
                                && (bs.is(Blocks.DANDELION) || bs.is(Blocks.POPPY)) && rng.nextFloat() < 0.20f) {
                            level.setBlock(p, Blocks.WITHER_ROSE.defaultBlockState(), 3);
                            flowerCount++;
                        } else if (stoneCount < 2 && tl == TaintLevel.HIGH
                                && (bs.is(Blocks.STONE) || bs.is(Blocks.COBBLESTONE)) && rng.nextFloat() < 0.05f) {
                            level.setBlock(p, HexereiBlocks.CHARRED_STONE.get().defaultBlockState(), 3);
                            stoneCount++;
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Wire pulse into HexereiLevelEvents**

In `HexereiLevelEvents.onLevelTick`, add:

```java
if (gt % 200 == 0) {           // every 10s: aura pulse
    WorldTaintAura.pulse(sl);
}
```

Add import: `import com.vel5id.hexerei.ritual.WorldTaintAura;`

- [ ] **Step 4: Build + verify**

```bash
cd hexerei && JAVA_HOME=../hexerei-work/tools/jdk17 ./gradlew build 2>&1 | tail -6
```

Visual verify: in runClient, add taint to MEDIUM via data commands, wait ~10 seconds, observe nearby grass → tainted_ground and flowers → wither roses.

- [ ] **Step 5: Commit**

```bash
cd hexerei && git add \
  src/main/java/com/vel5id/hexerei/ritual/WorldTaintAura.java \
  src/main/java/com/vel5id/hexerei/HexereiLevelEvents.java \
  src/main/java/com/vel5id/hexerei/power/AltarPowerManager.java
git commit -m "feat(hexerei): WorldTaintAura — tainted ground + wither roses + charred stone near altar"
```

---

## Task 7: Ritual visual signatures

**Files:**
- Modify: `src/main/java/com/vel5id/hexerei/block/ritual/RitualCircleBlock.java`
- Modify: `src/main/java/com/vel5id/hexerei/ritual/TempestRite.java`

- [ ] **Step 1: Add burst effect to RitualCircleBlock.use()**

Replace the body of `use()` with:

```java
@Override
public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
    if (level.isClientSide) return InteractionResult.SUCCESS;
    ServerLevel sl = (ServerLevel) level;

    // Burst particles along glyph ring before attempt
    for (BlockPos ring : com.vel5id.hexerei.ritual.RitualCircle.smallRing(pos)) {
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                ring.getX() + 0.5, ring.getY() + 0.1, ring.getZ() + 0.5,
                3, 0.1, 0.1, 0.1, 0.05);
    }
    level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE,
            net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 1.8f);

    RitualActivation.Result result = RitualActivation.tryPerform(sl, pos);
    if (result == RitualActivation.Result.SUCCESS) {
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 1.2F);
    } else {
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.4F, 0.8F);
    }
    return InteractionResult.CONSUME;
}
```

- [ ] **Step 2: Add visuals + taint to TempestRite.perform()**

```java
@Override
public void perform(ServerLevel level, BlockPos center) {
    level.setWeatherParameters(0, durationTicks, true, true);

    // Sound
    level.playSound(null, center, net.minecraft.sounds.SoundEvents.WITHER_BOSS_DEATH,
            net.minecraft.sounds.SoundSource.BLOCKS, 0.6f, 0.5f);

    // Particle burst from center
    level.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH,
            center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5,
            60, 1.5, 0.5, 1.5, 0.1);

    // Lightning-style particles at random glyph ring positions
    java.util.List<net.minecraft.core.BlockPos> ring = com.vel5id.hexerei.ritual.RitualCircle.smallRing(center);
    java.util.Collections.shuffle(ring);
    for (int i = 0; i < Math.min(8, ring.size()); i++) {
        net.minecraft.core.BlockPos rp = ring.get(i);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLASH,
                rp.getX() + 0.5, rp.getY() + 0.1, rp.getZ() + 0.5, 1, 0, 0, 0, 0);
    }

    // Charred stone aftermath inside circle
    java.util.Random rng = level.getRandom();
    int charred = 0;
    for (int dx = -2; dx <= 2 && charred < 5; dx++) {
        for (int dz = -2; dz <= 2 && charred < 5; dz++) {
            net.minecraft.core.BlockPos p = center.offset(dx, -1, dz);
            net.minecraft.world.level.block.state.BlockState bs = level.getBlockState(p);
            if ((bs.is(net.minecraft.world.level.block.Blocks.STONE)
                    || bs.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)
                    || bs.is(net.minecraft.world.level.block.Blocks.DIRT))
                    && rng.nextFloat() < 0.5f) {
                level.setBlock(p, com.vel5id.hexerei.registry.HexereiBlocks.CHARRED_STONE.get().defaultBlockState(), 3);
                charred++;
            }
        }
    }

    // Add world taint
    net.minecraft.world.level.ChunkPos cp = new net.minecraft.world.level.ChunkPos(center);
    com.vel5id.hexerei.power.ChunkTaintData.get(level).addTaint(cp, 25f);
    com.vel5id.hexerei.network.HexereiNetwork.sendTaintSync(level, cp);
}
```

- [ ] **Step 3: Run all tests**

```bash
cd hexerei && JAVA_HOME=../hexerei-work/tools/jdk17 ./gradlew test 2>&1 | tail -8
```

Expected: `BUILD SUCCESSFUL` — all 14+ tests still pass.

- [ ] **Step 4: Visual verify in runClient**

Perform a ritual: place altar + build circle + drop mandrake root + right-click center. Verify: burst particles on click, then lightning flash + witch particles + wither sound + charred stone appears, storm starts.

- [ ] **Step 5: Commit**

```bash
cd hexerei && git add \
  src/main/java/com/vel5id/hexerei/block/ritual/RitualCircleBlock.java \
  src/main/java/com/vel5id/hexerei/ritual/TempestRite.java
git commit -m "feat(hexerei): ritual visuals — burst on activation, TempestRite particles + charred stone + addTaint"
```

---

## Task 8: Ritual Chalk — rite selection via Shift+Scroll

**Files:**
- Modify: `src/main/java/com/vel5id/hexerei/item/RitualChalkItem.java`
- Modify: `src/main/java/com/vel5id/hexerei/client/HexereiClient.java`
- Copy: `ritual_chalk.png` texture
- Modify: `src/main/resources/assets/hexerei/lang/en_us.json`
- Modify: `src/main/resources/assets/hexerei/lang/ru_ru.json`

- [ ] **Step 1: Copy chalk texture**

```bash
cp /tmp/hexerei_textures/ritual_chalk.png hexerei/src/main/resources/assets/hexerei/textures/item/
```

- [ ] **Step 2: Update RitualChalkItem**

Replace the entire `useOn()` ritual-circle branch and add a `selectedRite()` helper:

```java
// Add helper at the bottom of the class:
private static com.vel5id.hexerei.ritual.RitualRecipe selectedRite(ItemStack stack) {
    if (stack.hasTag()) {
        String id = stack.getTag().getString("hexerei:rite");
        for (com.vel5id.hexerei.ritual.RitualRecipe r : com.vel5id.hexerei.ritual.RitualRecipes.ALL) {
            if (r.id().equals(id)) return r;
        }
    }
    return com.vel5id.hexerei.ritual.RitualRecipes.ALL.isEmpty()
            ? null : com.vel5id.hexerei.ritual.RitualRecipes.ALL.get(0);
}
```

Replace the ritual-circle branch in `useOn()`:

```java
if (level.getBlockState(clicked).is(HexereiBlocks.RITUAL_CIRCLE.get())) {
    if (!level.isClientSide) {
        ItemStack stack = ctx.getItemInHand();
        com.vel5id.hexerei.ritual.RitualRecipe rite = selectedRite(stack);
        java.util.List<BlockPos> ring = (rite != null)
                ? rite.circleSize().ringPositions(clicked)
                : RitualCircle.smallRing(clicked);
        for (BlockPos ringPos : ring) {
            if (canPlaceGlyph(level, ringPos)) {
                level.setBlock(ringPos, HexereiBlocks.RITUAL_GLYPH.get().defaultBlockState(), 3);
                damage(ctx, player, hand);
                level.playSound(null, ringPos, SoundEvents.SAND_PLACE, SoundSource.BLOCKS, 0.7F, 1.3F);
                break;
            }
        }
    }
    return InteractionResult.sidedSuccess(level.isClientSide);
}
```

Update `appendHoverText()`:

```java
@Override
public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
    com.vel5id.hexerei.ritual.RitualRecipe rite = selectedRite(stack);
    if (rite != null) {
        tooltip.add(Component.translatable("item.hexerei.ritual_chalk.rite",
                Component.translatable(rite.nameKey())).withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("item.hexerei.ritual_chalk.circle",
                rite.circleSize().ringPositions(BlockPos.ZERO).size()).withStyle(ChatFormatting.DARK_GRAY));
    }
    tooltip.add(Component.translatable("item.hexerei.ritual_chalk.tip2").withStyle(ChatFormatting.GRAY));
}
```

- [ ] **Step 3: Add MouseScrollingEvent to HexereiClient**

Add a new `@Mod.EventBusSubscriber` class for Forge-bus client events:

```java
// Add inside HexereiClient.java as a nested class, or as a separate class.
// Easier: add a separate static inner subscription using FORGE bus.

@Mod.EventBusSubscriber(modid = HexereiMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public static final class ForgeClientEvents {
    private ForgeClientEvents() {}

    @SubscribeEvent
    public static void onScroll(net.minecraftforge.client.event.InputEvent.MouseScrollingEvent event) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || !mc.player.isShiftKeyDown()) return;
        net.minecraft.world.item.ItemStack main = mc.player.getMainHandItem();
        net.minecraft.world.item.ItemStack off  = mc.player.getOffhandItem();
        boolean holdingChalk = main.is(HexereiItems.RITUAL_CHALK.get())
                            || off.is(HexereiItems.RITUAL_CHALK.get());
        if (!holdingChalk) return;
        event.setCanceled(true);
        int delta = event.getScrollDelta() > 0 ? 1 : -1;
        com.vel5id.hexerei.network.HexereiNetwork.CHANNEL.sendToServer(
                new com.vel5id.hexerei.network.CycleRiteC2SPacket(delta));
    }
}
```

Add to `HexereiClient.java` imports:
```java
import com.vel5id.hexerei.registry.HexereiItems;
import net.minecraftforge.eventbus.api.SubscribeEvent;
```

- [ ] **Step 4: Update lang files**

In `en_us.json`, add:
```json
"item.hexerei.ritual_chalk.rite":    "Rite: %s",
"item.hexerei.ritual_chalk.circle":  "Small circle · %d glyphs",
"item.hexerei.ritual_chalk.tip2":    "[Shift+Scroll] Change rite"
```

In `ru_ru.json`, add:
```json
"item.hexerei.ritual_chalk.rite":    "Обряд: %s",
"item.hexerei.ritual_chalk.circle":  "Малый круг · %d глифов",
"item.hexerei.ritual_chalk.tip2":    "[Shift+Скролл] Сменить обряд"
```

Remove old `item.hexerei.ritual_chalk.tip1` key from both lang files if present (it's replaced by the rite tooltip).

- [ ] **Step 5: Run all tests**

```bash
cd hexerei && JAVA_HOME=../hexerei-work/tools/jdk17 ./gradlew test 2>&1 | tail -8
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Visual verify in runClient**

1. Hold Ritual Chalk in hand.
2. Hover — tooltip shows "Rite: Rite of the Tempest · Small circle · 12 glyphs".
3. Hold Shift + scroll — action bar updates with rite name. (Only one rite exists currently, so cycling does nothing visible yet — that's correct.)
4. Right-click the center block — one glyph appears in ring order.
5. Texture: chalk item should show cream stick with purple tip.

- [ ] **Step 7: Commit**

```bash
cd hexerei && git add \
  src/main/java/com/vel5id/hexerei/item/RitualChalkItem.java \
  src/main/java/com/vel5id/hexerei/client/HexereiClient.java \
  src/main/resources/assets/hexerei/textures/item/ritual_chalk.png \
  src/main/resources/assets/hexerei/lang/
git commit -m "feat(hexerei): chalk rite selection — Shift+Scroll cycles rites, tooltip, NBT, chalk texture"
```

---

## Self-Review Checklist

- [x] **Spec §1 ChunkTaintData:** Task 1 — `addTaint`, `decayTick`, `permanentFloor`, `save`/`load`
- [x] **Spec §2 TaintLevel:** Task 1 — `fromValue()` with thresholds
- [x] **Spec §3 Altar visual:** Task 4 — `TAINT_LEVEL` property, 8-variant blockstate, 3 taint models, server tick, client tick skeleton
- [x] **Spec §3 Client particles:** Task 5 — WispParticle (3 colors), AshParticle, `doClientParticleTick`
- [x] **Spec §4 WorldTaintAura:** Task 6 — `pulse()` at 200t, grass/flower/stone mutations
- [x] **Spec §5 Burst on activation:** Task 7 — PORTAL particles + enchant sound in `RitualCircleBlock.use()`
- [x] **Spec §5 TempestRite visuals:** Task 7 — WITCH burst + FLASH ring + WITHER_BOSS_DEATH sound + charred_stone + `addTaint(25)`
- [x] **Spec §6 Chalk NBT + Shift+Scroll:** Task 8 — `CycleRiteC2SPacket`, `MouseScrollingEvent`, `selectedRite()`
- [x] **Spec §6 CircleSize:** Task 2 — enum with `ringPositions` + `isComplete`
- [x] **Spec §6.1 CircleSize added to RitualRecipes.ALL:** Task 2 — `ALL` is `List.of(TEMPEST)`, ordered
- [x] **Spec §7 New blocks:** Already done (tainted_ground, charred_stone registered + loot tables)
- [x] **Spec §8 Packets:** Task 3 — `CycleRiteC2SPacket` + `TaintSyncS2CPacket` + `HexereiNetwork`
- [x] **Spec §9 Chalk texture:** Task 8 — copy from `/tmp/hexerei_textures/ritual_chalk.png`
- [x] **Spec §9 Altar textures:** Task 4 — copy altar_low/medium/high PNGs
- [x] **Decay LevelTickEvent:** Task 4, `HexereiLevelEvents` — `gt % 1200 == 0`
- [x] **Chunk-watch sync:** Task 4, `HexereiLevelEvents.onChunkWatch`
- [x] **Taint sync after addTaint:** Task 7 (TempestRite) — `HexereiNetwork.sendTaintSync()`; Task 4 (altar tick) — same
- [x] **RitualRecipesTest updated:** Task 2 — new `match(Predicate, BlockPos, String)` signature
- [x] **No `requiresSmallCircle` references remain:** Replaced by `CircleSize` in Task 2
