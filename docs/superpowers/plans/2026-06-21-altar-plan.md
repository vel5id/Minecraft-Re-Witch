# Hexerei Altar — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the Hexerei Altar subsystem as a buildable, tested Forge 1.20.1 mod (`modid `hexerei`) — block, block-entity power mechanic, per-level power-query API, and read-only power GUI.

**Architecture:** Shape the Forge 1.20.1 MDK into the `hexerei` mod. Extract the two tricky algorithms (multiblock BFS formation, `min(count,limit)×factor` power math) into pure, JUnit-testable classes with no Minecraft-runtime dependency. Wire them into an `EntityBlock` + `BlockEntity` that scans for nature blocks, recharges power, syncs to client, and registers with a per-`ServerLevel` `AltarPowerManager`. Verify with pure unit tests + headless Forge GameTests + a dedicated-server boot smoke.

**Tech Stack:** Java 17, Forge 47.4.10 (MC 1.20.1), Gradle 8.8 (ForgeGradle), JUnit 5, Forge GameTest.

## Global Constraints

- Java toolchain: **17** (provided by portable JDK at `hexerei-work/tools/jdk17`; set `JAVA_HOME` for every gradle call).
- Forge: **47.4.10**, MC **1.20.1**. modid: **`hexerei`**. group: **`com.vel5id.hexerei`**. version: **`1.20.1-0.1.0-altar`**.
- All gradle commands: `JAVA_HOME=/home/h621l/minecraft/hexerei-work/tools/jdk17 ./gradlew --no-daemon <task>` run from `/home/h621l/minecraft/hexerei`.
- **Consistency rule:** every numeric value is from the Hexerei altar design notes. Never invent numbers.
- Power math: `power(source) = min(count, limit) × factor`; `maxPower = Σ over sources`. Scan cube = 29×29×29 (`SCAN_DISTANCE=14`). Recharge: every 20 ticks `power = (int)min(power + 10×rechargeScale, maxPower×powerScale)`. Power scan throttle: ≤ once / 100 ticks.
- Multiblock: 6 altar blocks, flat 2×3; BFS over horizontal N/S/E/W neighbours; every cell has 2–3 same-block neighbours; total ==6; core = first visited.
- NBT keys (fixed): `Core`(serialized as `CoreX/CoreY/CoreZ`), `Power`, `MaxPower`, `PowerScale`, `RechargeScale`, `RangeScale`, `EnhancementLevel`.
- Future-content references in the power/artefact tables are present but **guarded** behind `// FUTURE SLICE` (the referenced blocks/items don't exist yet).

---

### Task 1: Reshape MDK into the `hexerei` mod skeleton + JUnit harness

**Files:**
- Modify: `hexerei/build.gradle` (group, version, add JUnit + GameTest config)
- Modify: `hexerei/gradle.properties` (mod id/name/group/version props)
- Modify: `hexerei/src/main/resources/META-INF/mods.toml`
- Delete: `hexerei/src/main/java/com/example/examplemod/ExampleMod.java`, `Config.java`
- Create: `hexerei/src/main/java/com/hexerei/HexereiMod.java`
- Create: `hexerei/src/test/java/com/hexerei/SanityTest.java`
- Modify: `hexerei/src/main/resources/pack.mcmeta` (description)

**Interfaces:**
- Produces: `HexereiMod.MODID = "hexerei"`, `HexereiMod.LOGGER` (SLF4J).

- [ ] **Step 1: Write the failing test**

`src/test/java/com/hexerei/SanityTest.java`:
```java
package com.vel5id.hexerei;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SanityTest {
    @Test void modIdIsHexerei() {
        assertEquals("hexerei", HexereiMod.MODID);
    }
}
```

- [ ] **Step 2: Run it — expect compile failure** (HexereiMod missing)

Run: `JAVA_HOME=/home/h621l/minecraft/hexerei-work/tools/jdk17 ./gradlew --no-daemon test`
Expected: FAIL — `HexereiMod` not found / unresolved.

- [ ] **Step 3: Add JUnit to `build.gradle`** — inside the existing `dependencies { }` block add:
```gradle
    testImplementation platform('org.junit:junit-bom:5.10.2')
    testImplementation 'org.junit.jupiter:junit-jupiter'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
```
And after the `dependencies` block add:
```gradle
test {
    useJUnitPlatform()
}
```
Set near the top of `build.gradle` (replace the existing `version`/`group` lines):
```gradle
version = '1.20.1-0.1.0-altar'
group = 'com.vel5id.hexerei'
base { archivesName = 'hexerei' }
```

- [ ] **Step 4: Create the mod main class**

`src/main/java/com/hexerei/HexereiMod.java`:
```java
package com.vel5id.hexerei;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.eventbus.api.IEventBus;

@Mod(HexereiMod.MODID)
public final class HexereiMod {
    public static final String MODID = "hexerei";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HexereiMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        // Registries are wired in Task 5/6/7 (registers added here as they land).
        LOGGER.info("Hexerei (altar slice) loading");
    }
}
```

- [ ] **Step 5: Rewrite `mods.toml`** — replace `com.example.examplemod` / `examplemod` with hexerei. Minimal valid file:
```toml
modLoader="javafml"
loaderVersion="[47,)"
license="All Rights Reserved"
[[mods]]
modId="hexerei"
version="${file.jarVersion}"
displayName="Hexerei (Altar slice)"
description='''Hexerei witchcraft mod for 1.20.1. Altar core slice.'''
[[dependencies.hexerei]]
    modId="forge"
    mandatory=true
    versionRange="[47,)"
    ordering="NONE"
    side="BOTH"
[[dependencies.hexerei]]
    modId="minecraft"
    mandatory=true
    versionRange="[1.20.1,1.21)"
    ordering="NONE"
    side="BOTH"
```

- [ ] **Step 6: Delete example sources**
```bash
rm src/main/java/com/example/examplemod/ExampleMod.java src/main/java/com/example/examplemod/Config.java
rmdir -p src/main/java/com/example/examplemod 2>/dev/null || true
```

- [ ] **Step 7: Run tests + build — expect PASS / green**

Run: `JAVA_HOME=/home/h621l/minecraft/hexerei-work/tools/jdk17 ./gradlew --no-daemon test build`
Expected: `BUILD SUCCESSFUL`; `SanityTest` passes.

- [ ] **Step 8: Commit**
```bash
git add hexerei
git commit -m "feat(hexerei): reshape MDK into hexerei mod skeleton + junit harness"
```

---

### Task 2: Pure multiblock formation logic (`AltarFormation`) + unit tests

The BFS rule, runtime-free: operate on a `Set<BlockPos>` of altar positions, return the core (`BlockPos`) or `null`.  **Files:**
- Create: `hexerei/src/main/java/com/hexerei/block/AltarFormation.java`
- Test: `hexerei/src/test/java/com/hexerei/block/AltarFormationTest.java`

**Interfaces:**
- Produces: `static @Nullable BlockPos AltarFormation.findCore(Set<BlockPos> altarBlocks, BlockPos origin)` — returns core if `origin` is part of a valid complete (6-cell, 2×3) altar; else `null`. BFS over horizontal neighbours; each visited cell must have 2–3 same-set horizontal neighbours; total visited ==6; core = first visited (BFS order from `origin`).

- [ ] **Step 1: Write the failing tests**

`src/test/java/com/hexerei/block/AltarFormationTest.java`:
```java
package com.vel5id.hexerei.block;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class AltarFormationTest {
    private static Set<BlockPos> rect(int w, int d) {
        Set<BlockPos> s = new HashSet<>();
        for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) s.add(new BlockPos(x, 64, z));
        return s;
    }

    @Test void twoByThreeForms() {
        Set<BlockPos> s = rect(2, 3); // 6 cells
        BlockPos core = AltarFormation.findCore(s, new BlockPos(0, 64, 0));
        assertNotNull(core);
        assertTrue(s.contains(core));
    }

    @Test void threeByTwoForms() {
        Set<BlockPos> s = rect(3, 2);
        assertNotNull(AltarFormation.findCore(s, new BlockPos(0, 64, 0)));
    }

    @Test void twoByTwoDoesNotForm() { // 4 cells != 6
        assertNull(AltarFormation.findCore(rect(2, 2), new BlockPos(0, 64, 0)));
    }

    @Test void straightLineOfSixDoesNotForm() { // each end has 1 neighbour (<2)
        Set<BlockPos> s = new HashSet<>();
        for (int x = 0; x < 6; x++) s.add(new BlockPos(x, 64, 0));
        assertNull(AltarFormation.findCore(s, new BlockPos(0, 64, 0)));
    }

    @Test void threeByThreeDoesNotForm() { // 9 cells; centre has 4 neighbours (>3) and size!=6
        assertNull(AltarFormation.findCore(rect(3, 3), new BlockPos(0, 64, 0)));
    }

    @Test void coreIsDeterministicFirstVisited() {
        Set<BlockPos> s = rect(2, 3);
        assertEquals(new BlockPos(0, 64, 0), AltarFormation.findCore(s, new BlockPos(0, 64, 0)));
    }

    @Test void verticalStackIgnored() { // formation is horizontal only
        Set<BlockPos> s = new HashSet<>();
        for (int y = 0; y < 6; y++) s.add(new BlockPos(0, 64 + y, 0));
        assertNull(AltarFormation.findCore(s, new BlockPos(0, 64, 0)));
    }
}
```

- [ ] **Step 2: Run — expect FAIL** (AltarFormation missing)

Run: `JAVA_HOME=/home/h621l/minecraft/hexerei-work/tools/jdk17 ./gradlew --no-daemon test --tests '*AltarFormationTest'`
Expected: FAIL — class not found.

- [ ] **Step 3: Implement `AltarFormation`** (altar multiblock formation rule)

`src/main/java/com/hexerei/block/AltarFormation.java`:
```java
package com.vel5id.hexerei.block;

import net.minecraft.core.BlockPos;
import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Hexerei altar multiblock flood-fill validity rule. */
public final class AltarFormation {
    public static final int ELEMENTS_IN_COMPLETE_ALTAR = 6;
    private AltarFormation() {}

    private static final int[][] HORIZONTAL = {{0,0,-1},{0,0,1},{1,0,0},{-1,0,0}}; // N,S,E,W

    /** @return the core (first BFS-visited cell) if origin belongs to a valid complete altar, else null. */
    @Nullable
    public static BlockPos findCore(Set<BlockPos> altarBlocks, BlockPos origin) {
        if (!altarBlocks.contains(origin)) return null;
        List<BlockPos> visited = new ArrayList<>();
        Set<BlockPos> visitedSet = new HashSet<>();
        Deque<BlockPos> toVisit = new ArrayDeque<>();
        toVisit.add(origin);
        boolean valid = true;
        while (!toVisit.isEmpty()) {
            BlockPos coord = toVisit.poll();
            if (visitedSet.contains(coord)) continue;
            int neighbours = 0;
            for (int[] d : HORIZONTAL) {
                BlockPos n = coord.offset(d[0], d[1], d[2]);
                if (altarBlocks.contains(n)) {
                    neighbours++;
                    if (!visitedSet.contains(n) && !toVisit.contains(n)) toVisit.add(n);
                }
            }
            if (neighbours < 2 || neighbours > 3) valid = false;
            visited.add(coord);
            visitedSet.add(coord);
        }
        return (valid && visited.size() == ELEMENTS_IN_COMPLETE_ALTAR) ? visited.get(0) : null;
    }
}
```

- [ ] **Step 4: Run — expect PASS**

Run: `JAVA_HOME=/home/h621l/minecraft/hexerei-work/tools/jdk17 ./gradlew --no-daemon test --tests '*AltarFormationTest'`
Expected: PASS (7 tests).

> Note: `BlockPos` equality/hashCode is value-based (it extends `Vec3i`), so `Set<BlockPos>`/`contains` work without MC bootstrap. The `toVisit.contains` mirrors the original's dedupe; with the `visitedSet` guard it stays correct.

- [ ] **Step 5: Commit**
```bash
git add hexerei/src
git commit -m "feat(hexerei): pure altar multiblock formation BFS + tests"
```

---

### Task 3: Power table + `PowerSource` math (`AltarPowerTable`, `PowerSource`) + unit tests

 The table maps a `BlockState`→(factor,limit) at runtime; the *math* and *table values* are pure-testable. Tag/instanceof resolution is a runtime function but the (factor,limit) pairs are constants we assert.

**Files:**
- Create: `hexerei/src/main/java/com/hexerei/power/PowerSource.java`
- Create: `hexerei/src/main/java/com/hexerei/power/AltarPowerTable.java`
- Test: `hexerei/src/test/java/com/hexerei/power/PowerSourceTest.java`

**Interfaces:**
- Produces: `PowerSource(int factor, int limit)` with `void increment()`, `int getPower()` = `min(count,limit)×factor`, `int count()`.
- Produces: `AltarPowerTable.Entry(int factor, int limit)`; `AltarPowerTable.VANILLA` = `Map<String,Entry>` keyed by a stable id token (the modern block id) for the fixed vanilla entries; `AltarPowerTable.TAG_SAPLING/TAG_LOG/TAG_LEAVES/CATCHALL` Entry constants. (Runtime block→Entry resolution lives in the BlockEntity in Task 5; this class holds the fixed data + math so it is unit-testable.)

- [ ] **Step 1: Write failing tests**

`src/test/java/com/hexerei/power/PowerSourceTest.java`:
```java
package com.vel5id.hexerei.power;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PowerSourceTest {
    @Test void powerIsMinCountLimitTimesFactor() {
        PowerSource s = new PowerSource(4, 20);
        assertEquals(0, s.getPower());
        for (int i = 0; i < 5; i++) s.increment();
        assertEquals(20, s.getPower());          // min(5,20)*4
        for (int i = 0; i < 100; i++) s.increment();
        assertEquals(80, s.getPower());          // capped at limit 20 *4
    }

    @Test void dragonEggValue() {
        PowerSource s = new PowerSource(250, 1);
        s.increment(); s.increment();
        assertEquals(250, s.getPower());         // min(2,1)*250
    }

    @Test void tableHasExpectedVanillaValues() {
        assertEntry(AltarPowerTable.TAG_SAPLING, 4, 20);
        assertEntry(AltarPowerTable.TAG_LOG, 2, 50);
        assertEntry(AltarPowerTable.TAG_LEAVES, 3, 100);
        assertEntry(AltarPowerTable.CATCHALL, 2, 4);
        assertEntry(AltarPowerTable.VANILLA.get("minecraft:grass_block"), 2, 80);
        assertEntry(AltarPowerTable.VANILLA.get("minecraft:dragon_egg"), 250, 1);
        assertEntry(AltarPowerTable.VANILLA.get("minecraft:farmland"), 1, 100);
        assertEntry(AltarPowerTable.VANILLA.get("minecraft:water"), 1, 50);
    }

    private static void assertEntry(AltarPowerTable.Entry e, int factor, int limit) {
        assertNotNull(e);
        assertEquals(factor, e.factor());
        assertEquals(limit, e.limit());
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

Run: `JAVA_HOME=/home/h621l/minecraft/hexerei-work/tools/jdk17 ./gradlew --no-daemon test --tests '*PowerSourceTest'`
Expected: FAIL — classes missing.

- [ ] **Step 3: Implement `PowerSource`**

`src/main/java/com/hexerei/power/PowerSource.java`:
```java
package com.vel5id.hexerei.power;

/** Hexerei per-source altar power accumulator. */
public final class PowerSource {
    private final int factor;
    private final int limit;
    private int count;

    public PowerSource(int factor, int limit) { this.factor = factor; this.limit = limit; }
    public void increment() { count++; }
    public int count() { return count; }
    public int getPower() { return Math.min(count, limit) * factor; }
}
```

- [ ] **Step 4: Implement `AltarPowerTable`** — fixed values (future-content entries omitted here; added guarded in Task 5.)

`src/main/java/com/hexerei/power/AltarPowerTable.java`:
```java
package com.vel5id.hexerei.power;

import java.util.LinkedHashMap;
import java.util.Map;

/** Altar (factor,limit) power values per nature-block source. */
public final class AltarPowerTable {
    private AltarPowerTable() {}

    public record Entry(int factor, int limit) {}

    // OreDictionary -> block tags
    public static final Entry TAG_SAPLING = new Entry(4, 20);
    public static final Entry TAG_LOG     = new Entry(2, 50);
    public static final Entry TAG_LEAVES  = new Entry(3, 100);
    // instanceof FlowerBlock/CropBlock catch-all
    public static final Entry CATCHALL    = new Entry(2, 4);

    /** Fixed vanilla blocks, keyed by 1.20.1 registry id.  */
    public static final Map<String, Entry> VANILLA = build();
    private static Map<String, Entry> build() {
        Map<String, Entry> m = new LinkedHashMap<>();
        m.put("minecraft:grass_block",        new Entry(2, 80));
        m.put("minecraft:dirt",               new Entry(1, 80));
        m.put("minecraft:farmland",           new Entry(1, 100));
        m.put("minecraft:fern",               new Entry(3, 50));
        m.put("minecraft:short_grass",        new Entry(3, 50));   // tall-grass equivalent (renamed from grass in 1.20.1)
        m.put("minecraft:wheat",              new Entry(4, 20));
        m.put("minecraft:water",              new Entry(1, 50));
        m.put("minecraft:brown_mushroom",     new Entry(3, 20));
        m.put("minecraft:red_mushroom",       new Entry(3, 20));
        m.put("minecraft:cactus",             new Entry(3, 50));
        m.put("minecraft:sugar_cane",         new Entry(3, 50));
        m.put("minecraft:pumpkin",            new Entry(4, 20));
        m.put("minecraft:pumpkin_stem",       new Entry(3, 20));
        m.put("minecraft:brown_mushroom_block", new Entry(3, 20));
        m.put("minecraft:red_mushroom_block", new Entry(3, 20));
        m.put("minecraft:melon",              new Entry(4, 20));
        m.put("minecraft:melon_stem",         new Entry(3, 20));
        m.put("minecraft:vine",               new Entry(2, 50));
        m.put("minecraft:mycelium",           new Entry(1, 80));
        m.put("minecraft:dragon_egg",         new Entry(250, 1));
        m.put("minecraft:cocoa",              new Entry(3, 20));
        m.put("minecraft:carrots",            new Entry(4, 20));
        m.put("minecraft:potatoes",           new Entry(4, 20));
        return m;
    }

    /** Small-flower set, 4/30 each. See the small-flower fidelity note. */
    public static final Entry FLOWER = new Entry(4, 30); // small-flower set
}
```

- [ ] **Step 5: Run — expect PASS**

Run: `JAVA_HOME=/home/h621l/minecraft/hexerei-work/tools/jdk17 ./gradlew --no-daemon test --tests '*PowerSourceTest'`
Expected: PASS.

- [ ] **Step 6: Commit**
```bash
git add hexerei/src
git commit -m "feat(hexerei): altar power table + PowerSource math + tests"
```

---

### Task 4: Power-query API (`IPowerSource`, `RelativePowerSource`, `AltarPowerManager`)

Per-`ServerLevel` registry. Geometry/sort is the testable part; registration touches `BlockEntity` so it is exercised by GameTest in Task 8.

**Files:**
- Create: `hexerei/src/main/java/com/hexerei/power/IPowerSource.java`
- Create: `hexerei/src/main/java/com/hexerei/power/RelativePowerSource.java`
- Create: `hexerei/src/main/java/com/hexerei/power/AltarPowerManager.java`
- Test: `hexerei/src/test/java/com/hexerei/power/RelativePowerSourceTest.java`

**Interfaces:**
- Produces: `interface IPowerSource { Level getWorld(); BlockPos getLocation(); boolean isLocationEqual(BlockPos p); boolean consumePower(float required); float getCurrentPower(); float getRange(); int getEnhancementLevel(); boolean isPowerInvalid(); }`
- Produces: `RelativePowerSource(IPowerSource source, BlockPos from)` with `double distanceSq()`, `boolean isInWorld(Level)`, `boolean isInRange()`, `IPowerSource source()`.
- Produces: `AltarPowerManager.get(ServerLevel) → manager`; `void register(IPowerSource)`, `void unregister(IPowerSource)`, `List<RelativePowerSource> query(BlockPos from)` (distanceSq-sorted, in-range, self-healing sweep), `Optional<IPowerSource> closest(BlockPos from)`.

- [ ] **Step 1: Write failing test** (geometry only — uses a hand-rolled fake `IPowerSource` so no MC runtime/Level is needed; `Level` is referenced only by type, never dereferenced).

`src/test/java/com/hexerei/power/RelativePowerSourceTest.java`:
```java
package com.vel5id.hexerei.power;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RelativePowerSourceTest {
    /** Minimal fake; getRange() controls the range filter (radius arg is advisory). */
    static final class Fake implements IPowerSource {
        final BlockPos pos; final float range; final float power;
        Fake(BlockPos pos, float range, float power) { this.pos = pos; this.range = range; this.power = power; }
        public Level getWorld() { return null; }
        public BlockPos getLocation() { return pos; }
        public boolean isLocationEqual(BlockPos p) { return pos.equals(p); }
        public boolean consumePower(float r) { return r <= power; }
        public float getCurrentPower() { return power; }
        public float getRange() { return range; }
        public int getEnhancementLevel() { return 0; }
        public boolean isPowerInvalid() { return false; }
    }

    @Test void distanceAndRange() {
        Fake f = new Fake(new BlockPos(10, 64, 0), 16f, 100f);
        RelativePowerSource r = new RelativePowerSource(f, new BlockPos(0, 64, 0));
        assertEquals(100.0, r.distanceSq(), 0.001); // 10^2
        assertTrue(r.isInRange());                  // 100 <= 16^2=256
    }

    @Test void outOfRange() {
        Fake f = new Fake(new BlockPos(20, 64, 0), 16f, 100f);
        RelativePowerSource r = new RelativePowerSource(f, BlockPos.ZERO.above(64));
        assertFalse(r.isInRange());                 // 400 > 256
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

Run: `JAVA_HOME=/home/h621l/minecraft/hexerei-work/tools/jdk17 ./gradlew --no-daemon test --tests '*RelativePowerSourceTest'`
Expected: FAIL — classes missing.

- [ ] **Step 3: Implement `IPowerSource`**

`src/main/java/com/hexerei/power/IPowerSource.java`:
```java
package com.vel5id.hexerei.power;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Hexerei power-source contract (BlockPos-based). */
public interface IPowerSource {
    Level getWorld();
    BlockPos getLocation();
    boolean isLocationEqual(BlockPos location);
    /** Atomic all-or-nothing debit. Server-only; false on client / when no core. */
    boolean consumePower(float requiredPower);
    /** Current stored core power. Sentinels: -1 no core, -2 client. */
    float getCurrentPower();
    /** Reach in blocks = 16 * rangeScale. */
    float getRange();
    int getEnhancementLevel();
    boolean isPowerInvalid();
}
```

- [ ] **Step 4: Implement `RelativePowerSource`**

`src/main/java/com/hexerei/power/RelativePowerSource.java`:
```java
package com.vel5id.hexerei.power;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Transient query-time wrapper. Squared distances only (no sqrt). */
public final class RelativePowerSource {
    private final IPowerSource source;
    private final double distanceSq;
    private final double rangeSq;

    public RelativePowerSource(IPowerSource source, BlockPos from) {
        this.source = source;
        this.distanceSq = source.getLocation().distSqr(from);
        float range = source.getRange();
        this.rangeSq = (double) range * range;
    }
    public IPowerSource source() { return source; }
    public double distanceSq() { return distanceSq; }
    public boolean isInWorld(Level level) { return source.getWorld() == level; }
    public boolean isInRange() { return distanceSq <= rangeSq; }
}
```

- [ ] **Step 5: Implement `AltarPowerManager`** (per-ServerLevel, transient, self-healing)

`src/main/java/com/hexerei/power/AltarPowerManager.java`:
```java
package com.vel5id.hexerei.power;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/** Per-ServerLevel altar power-source registry. */
public final class AltarPowerManager {
    private static final Map<ServerLevel, AltarPowerManager> MANAGERS = new WeakHashMap<>();

    public static AltarPowerManager get(ServerLevel level) {
        return MANAGERS.computeIfAbsent(level, l -> new AltarPowerManager());
    }

    private final List<IPowerSource> sources = new ArrayList<>();

    public void register(IPowerSource src) {
        if (sources.contains(src)) return;
        sources.removeIf(s -> s == null || s.isPowerInvalid() || s.getLocation().equals(src.getLocation()));
        sources.add(src);
    }

    public void unregister(IPowerSource src) {
        sources.remove(src);
        sources.removeIf(s -> s == null || s.isPowerInvalid());
    }

    /** Distance-sorted, in-range sources. radius arg is advisory in the original; range governs. */
    public List<RelativePowerSource> query(Level level, BlockPos from) {
        List<RelativePowerSource> out = new ArrayList<>();
        for (Iterator<IPowerSource> it = sources.iterator(); it.hasNext();) {
            IPowerSource s = it.next();
            if (s == null || s.isPowerInvalid()) { it.remove(); continue; }
            RelativePowerSource r = new RelativePowerSource(s, from);
            if (r.isInWorld(level) && r.isInRange()) out.add(r);
        }
        out.sort(Comparator.comparingDouble(RelativePowerSource::distanceSq));
        return out;
    }

    public Optional<IPowerSource> closest(Level level, BlockPos from) {
        List<RelativePowerSource> all = query(level, from);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0).source());
    }
}
```

- [ ] **Step 6: Run — expect PASS**

Run: `JAVA_HOME=/home/h621l/minecraft/hexerei-work/tools/jdk17 ./gradlew --no-daemon test --tests '*RelativePowerSourceTest'`
Expected: PASS.

- [ ] **Step 7: Commit**
```bash
git add hexerei/src
git commit -m "feat(hexerei): power-query API (IPowerSource, RelativePowerSource, AltarPowerManager)"
```

---

### Task 5: `AltarBlock` + `AltarBlockEntity` + registries

The heart of the slice. Registers Block/BlockItem/BlockEntityType/CreativeTab; wires formation, power scan, recharge, NBT, sync, IPowerSource.

**Files:**
- Create: `hexerei/src/main/java/com/hexerei/registry/HexereiBlocks.java`
- Create: `hexerei/src/main/java/com/hexerei/registry/HexereiItems.java`
- Create: `hexerei/src/main/java/com/hexerei/registry/HexereiBlockEntities.java`
- Create: `hexerei/src/main/java/com/hexerei/registry/HexereiCreativeTabs.java`
- Create: `hexerei/src/main/java/com/hexerei/block/AltarBlock.java`
- Create: `hexerei/src/main/java/com/hexerei/blockentity/AltarBlockEntity.java`
- Modify: `hexerei/src/main/java/com/hexerei/HexereiMod.java` (wire registers)

**Interfaces:**
- Consumes: `AltarFormation.findCore`, `AltarPowerTable.*`, `PowerSource`, `IPowerSource`, `AltarPowerManager`.
- Produces: `HexereiBlocks.ALTAR` (`RegistryObject<Block>`), `HexereiBlockEntities.ALTAR` (`RegistryObject<BlockEntityType<AltarBlockEntity>>`), `AltarBlock.JOINED` (`BooleanProperty`), `AltarBlockEntity implements IPowerSource` with `float getCorePower()/getCoreMaxPower()/getCoreRechargeScale()` for the GUI (Task 7), and `static void serverTick(...)`.

- [ ] **Step 1: Registries**

`registry/HexereiCreativeTabs.java`:
```java
package com.vel5id.hexerei.registry;

import com.vel5id.hexerei.HexereiMod;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class HexereiCreativeTabs {
    private HexereiCreativeTabs() {}
    public static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(ForgeRegistries.CREATIVE_MODE_TABS, HexereiMod.MODID);

    public static final RegistryObject<CreativeModeTab> HEXEREI = TABS.register("hexerei",
        () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.hexerei"))
            .icon(() -> new ItemStack(HexereiBlocks.ALTAR.get()))
            .displayItems((params, output) -> output.accept(HexereiBlocks.ALTAR.get()))
            .build());
}
```

`registry/HexereiBlocks.java`:
```java
package com.vel5id.hexerei.registry;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.block.AltarBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.block.SoundType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class HexereiBlocks {
    private HexereiBlocks() {}
    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(ForgeRegistries.BLOCKS, HexereiMod.MODID);

    public static final RegistryObject<Block> ALTAR = BLOCKS.register("altar",
        () -> new AltarBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE)
            .strength(2.0F)              // hardness 2.0F
            .sound(SoundType.STONE)
            .requiresCorrectToolForDrops()));
}
```

`registry/HexereiItems.java`:
```java
package com.vel5id.hexerei.registry;

import com.vel5id.hexerei.HexereiMod;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class HexereiItems {
    private HexereiItems() {}
    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, HexereiMod.MODID);

    public static final RegistryObject<Item> ALTAR = ITEMS.register("altar",
        () -> new BlockItem(HexereiBlocks.ALTAR.get(), new Item.Properties()));
}
```

`registry/HexereiBlockEntities.java`:
```java
package com.vel5id.hexerei.registry;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.blockentity.AltarBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class HexereiBlockEntities {
    private HexereiBlockEntities() {}
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, HexereiMod.MODID);

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<AltarBlockEntity>> ALTAR =
        BLOCK_ENTITIES.register("altar",
            () -> BlockEntityType.Builder.of(AltarBlockEntity::new, HexereiBlocks.ALTAR.get()).build(null));
}
```

- [ ] **Step 2: `AltarBlock`** (EntityBlock, JOINED property, multiblock triggers, right-click)

`src/main/java/com/hexerei/block/AltarBlock.java`:
```java
package com.vel5id.hexerei.block;

import com.vel5id.hexerei.blockentity.AltarBlockEntity;
import com.vel5id.hexerei.registry.HexereiBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import javax.annotation.Nullable;

public class AltarBlock extends Block implements EntityBlock {
    public static final BooleanProperty JOINED = BlockStateProperties.CONDITIONAL; // "conditional" -> reuse? see note
    // NOTE: define our own to avoid semantic confusion:
    public static final BooleanProperty ALTAR_JOINED = BooleanProperty.create("joined");

    public AltarBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(ALTAR_JOINED, false));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(ALTAR_JOINED);
    }

    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AltarBlockEntity(pos, state);
    }

    @Nullable @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return type == HexereiBlockEntities.ALTAR.get()
            ? (lvl, pos, st, be) -> AltarBlockEntity.serverTick(lvl, pos, st, (AltarBlockEntity) be)
            : null;
    }

    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof AltarBlockEntity be) be.updateMultiblock(null);
    }

    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof AltarBlockEntity be) be.updateMultiblock(pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof AltarBlockEntity be) {
            be.revalidateAndUpdate();
            if (level.isClientSide) return InteractionResult.SUCCESS;
            com.vel5id.hexerei.network.HexereiNetwork.openAltarScreen((net.minecraft.server.level.ServerPlayer) player, be.corePos());
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }
}
```
> Remove the stray `JOINED = BlockStateProperties.CONDITIONAL` line during implementation; only `ALTAR_JOINED` is used. (Kept here so the reviewer notices the property name choice; do not ship the unused field.)

- [ ] **Step 3: `AltarBlockEntity`** — Hexerei implementation (formation, scan, recharge, NBT, sync, IPowerSource)

`src/main/java/com/hexerei/blockentity/AltarBlockEntity.java`:
```java
package com.vel5id.hexerei.blockentity;

import com.vel5id.hexerei.block.AltarBlock;
import com.vel5id.hexerei.block.AltarFormation;
import com.vel5id.hexerei.power.AltarPowerManager;
import com.vel5id.hexerei.power.AltarPowerTable;
import com.vel5id.hexerei.power.IPowerSource;
import com.vel5id.hexerei.power.PowerSource;
import com.vel5id.hexerei.registry.HexereiBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AltarBlockEntity extends BlockEntity implements IPowerSource {
    private static final int SCAN_DISTANCE = 14;       // -> 29^3 cube
    private static final long SCAN_THROTTLE_TICKS = 100;
    private static final float BASE_POWER_PER_UPDATE = 10.0F;

    @Nullable private BlockPos core;   // null = not part of a complete altar
    private float power;
    private float maxPower;
    private int powerScale = 1;
    private int rechargeScale = 1;
    private int rangeScale = 1;
    private int enhancementLevel = 0;
    private long ticks = 0;
    private long lastPowerUpdate = 0;

    public AltarBlockEntity(BlockPos pos, BlockState state) {
        super(HexereiBlockEntities.ALTAR.get(), pos, state);
    }

    private boolean isCore() { return core != null && core.equals(worldPosition); }
    public BlockPos corePos() { return core != null ? core : worldPosition; }

    // ---- ticking ----
    public static void serverTick(Level level, BlockPos pos, BlockState state, AltarBlockEntity be) {
        be.ticks++;
        if (!be.isCore()) return;
        float maxScaled = be.maxPower * be.powerScale;
        if (be.power < maxScaled) {
            if (be.ticks % 20L == 0L) {
                be.power = (int) Math.min(be.power + BASE_POWER_PER_UPDATE * be.rechargeScale, maxScaled);
                be.sync();
            }
        } else if (be.power > maxScaled && be.ticks % 20L == 0L) {
            be.power = maxScaled;
            be.sync();
        }
    }

    public void revalidateAndUpdate() {
        if (level == null || level.isClientSide) return;
        if (level.getBlockEntity(corePos()) instanceof AltarBlockEntity coreBe) {
            coreBe.updatePower(true);
        }
    }

    // ---- multiblock formation (AltarFormation) ----
    public void updateMultiblock(@Nullable BlockPos exclude) {
        if (level == null || level.isClientSide) return;
        Set<BlockPos> altars = new HashSet<>();
        // gather connected altar blocks via flood-fill on the world
        Set<BlockPos> seen = new HashSet<>();
        java.util.Deque<BlockPos> stack = new java.util.ArrayDeque<>();
        stack.add(worldPosition);
        while (!stack.isEmpty()) {
            BlockPos p = stack.poll();
            if (!seen.add(p)) continue;
            if (p.equals(exclude)) continue;
            if (!(level.getBlockState(p).getBlock() instanceof AltarBlock)) continue;
            altars.add(p);
            stack.add(p.north()); stack.add(p.south()); stack.add(p.east()); stack.add(p.west());
        }
        BlockPos newCore = AltarFormation.findCore(altars, worldPosition);
        for (BlockPos p : altars) {
            if (level.getBlockEntity(p) instanceof AltarBlockEntity be) be.setCore(newCore);
        }
        if (exclude != null && level.getBlockEntity(exclude) instanceof AltarBlockEntity be) be.setCore(null);
    }

    private void setCore(@Nullable BlockPos newCore) {
        this.core = newCore;
        if (level instanceof ServerLevel server) {
            if (isCore()) {
                updatePower(false);
                AltarPowerManager.get(server).register(this);
            } else if (newCore == null) {
                AltarPowerManager.get(server).unregister(this);
                power = 0; maxPower = 0; powerScale = 1; rechargeScale = 1; rangeScale = 1; enhancementLevel = 0;
            }
        }
        BlockState st = getBlockState();
        if (st.getBlock() instanceof AltarBlock && st.getValue(AltarBlock.ALTAR_JOINED) != (newCore != null)) {
            level.setBlock(worldPosition, st.setValue(AltarBlock.ALTAR_JOINED, newCore != null), 3);
        }
        setChanged();
        sync();
    }

    // ---- power scan ----
    private void updatePower(boolean throttle) {
        if (level == null || level.isClientSide) return;
        if (throttle && !(ticks - lastPowerUpdate <= 0 || ticks - lastPowerUpdate > SCAN_THROTTLE_TICKS)) return;
        lastPowerUpdate = ticks;
        Map<Block, PowerSource> table = new HashMap<>();
        // fixed vanilla entries
        for (Map.Entry<String, AltarPowerTable.Entry> e : AltarPowerTable.VANILLA.entrySet()) {
            Block b = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(e.getKey()));
            if (b != null) table.put(b, new PowerSource(e.getValue().factor(), e.getValue().limit()));
        }
        // small flowers (poppy/dandelion family) -> 4/30 each; tag #minecraft:small_flowers
        // (handled via instanceof FlowerBlock catch in the scan below at FLOWER value)
        BlockPos c = corePos();
        Set<Block> flowerCounted = new HashSet<>();
        for (int y = c.getY() - SCAN_DISTANCE; y <= c.getY() + SCAN_DISTANCE; y++) {
            for (int z = c.getZ() - SCAN_DISTANCE; z <= c.getZ() + SCAN_DISTANCE; z++) {
                for (int x = c.getX() - SCAN_DISTANCE; x <= c.getX() + SCAN_DISTANCE; x++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!level.isLoaded(p)) continue;
                    BlockState bs = level.getBlockState(p);
                    Block b = bs.getBlock();
                    PowerSource src = table.get(b);
                    if (src == null) src = resolveDynamic(bs, b, table);
                    if (src != null) src.increment();
                }
            }
        }
        float newMax = 0;
        for (PowerSource s : table.values()) newMax += s.getPower();
        if (newMax != maxPower) { maxPower = newMax; setChanged(); sync(); }
    }

    /** Tag/instanceof sources resolved lazily and memoised into the table. */
    @Nullable private PowerSource resolveDynamic(BlockState bs, Block b, Map<Block, PowerSource> table) {
        AltarPowerTable.Entry e = null;
        if (bs.is(BlockTags.SAPLINGS)) e = AltarPowerTable.TAG_SAPLING;
        else if (bs.is(BlockTags.LOGS)) e = AltarPowerTable.TAG_LOG;
        else if (bs.is(BlockTags.LEAVES)) e = AltarPowerTable.TAG_LEAVES;
        else if (bs.is(BlockTags.SMALL_FLOWERS)) e = AltarPowerTable.FLOWER;       // 4/30 (small-flower set)
        else if (b instanceof FlowerBlock || b instanceof CropBlock) e = AltarPowerTable.CATCHALL; // 2/4
        // FUTURE SLICE: future-content blocks (LEAVES 4/50, LOG 3/100, DEMON_HEART 40/2, INFINITY_EGG 1000/1, crops 4/20, ...)
        if (e == null) return null;
        PowerSource s = new PowerSource(e.factor(), e.limit());
        table.put(b, s);
        return s;
    }

    // ---- IPowerSource ----
    @Override public Level getWorld() { return level; }
    @Override public BlockPos getLocation() { return corePos(); }
    @Override public boolean isLocationEqual(BlockPos p) { return corePos().equals(p); }
    @Override public float getRange() { return 16f * rangeScale; }
    @Override public int getEnhancementLevel() { return enhancementLevel; }
    @Override public boolean isPowerInvalid() { return isRemoved(); }
    @Override public float getCurrentPower() {
        if (level == null) return -1f;
        if (level.isClientSide) return -2f;
        if (!isCore()) {
            if (level.getBlockEntity(corePos()) instanceof AltarBlockEntity coreBe && coreBe != this) return coreBe.getCurrentPower();
            return -1f;
        }
        return power;
    }
    @Override public boolean consumePower(float required) {
        if (level == null || level.isClientSide) return false;
        if (!isCore()) {
            return level.getBlockEntity(corePos()) instanceof AltarBlockEntity coreBe && coreBe != this && coreBe.consumePower(required);
        }
        if (power >= required) { power -= required; setChanged(); sync(); return true; }
        return false;
    }

    // GUI readouts (Task 7)
    public float getCorePower() { float p = getCurrentPower(); return p < 0 ? 0 : p; }
    public float getCoreMaxPower() {
        if (level != null && !level.isClientSide && !isCore()
            && level.getBlockEntity(corePos()) instanceof AltarBlockEntity coreBe && coreBe != this) return coreBe.maxPower * coreBe.powerScale;
        return maxPower * powerScale;
    }
    public int getCoreRechargeScale() { return rechargeScale; }

    // ---- lifecycle / registry ----
    @Override public void setRemoved() {
        if (level instanceof ServerLevel server) AltarPowerManager.get(server).unregister(this);
        super.setRemoved();
    }
    @Override public void onChunkUnloaded() {
        if (level instanceof ServerLevel server) AltarPowerManager.get(server).unregister(this);
        super.onChunkUnloaded();
    }
    @Override public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server && isCore()) AltarPowerManager.get(server).register(this);
    }

    // ---- NBT ----
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (core != null) { tag.putInt("CoreX", core.getX()); tag.putInt("CoreY", core.getY()); tag.putInt("CoreZ", core.getZ()); }
        tag.putFloat("Power", power);
        tag.putFloat("MaxPower", maxPower);
        tag.putInt("PowerScale", powerScale);
        tag.putInt("RechargeScale", rechargeScale);
        tag.putInt("RangeScale", rangeScale);
        tag.putInt("EnhancementLevel", enhancementLevel);
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        core = tag.contains("CoreX") ? new BlockPos(tag.getInt("CoreX"), tag.getInt("CoreY"), tag.getInt("CoreZ")) : null;
        power = tag.getFloat("Power");
        maxPower = tag.getFloat("MaxPower");
        powerScale = tag.contains("PowerScale") ? tag.getInt("PowerScale") : 1;
        rechargeScale = tag.contains("RechargeScale") ? tag.getInt("RechargeScale") : 1;
        rangeScale = tag.contains("RangeScale") ? tag.getInt("RangeScale") : 1;
        enhancementLevel = tag.getInt("EnhancementLevel");
    }

    // ---- sync (ClientboundBlockEntityDataPacket) ----
    private void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    @Nullable @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider provider) { CompoundTag t = new CompoundTag(); saveAdditional(t); return t; }
    @Override public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider provider) { load(tag); }
}
```
> Implementer notes: the artefact scan (`powerScale`/`rechargeScale` from skulls/torch) is **deferred**; `powerScale`/`rechargeScale` stay at the NBT-loaded/default value of 1 this slice (vanilla-only artefacts add little and pull in skull-block remapping; do it in a follow-up). This keeps the slice focused while preserving the fields + NBT for forward-compat. Document in DESIGN-NOTES.

- [ ] **Step 4: Wire registers in `HexereiMod`** — replace constructor body:
```java
    public HexereiMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        com.vel5id.hexerei.registry.HexereiBlocks.BLOCKS.register(modBus);
        com.vel5id.hexerei.registry.HexereiItems.ITEMS.register(modBus);
        com.vel5id.hexerei.registry.HexereiBlockEntities.BLOCK_ENTITIES.register(modBus);
        com.vel5id.hexerei.registry.HexereiCreativeTabs.TABS.register(modBus);
        com.vel5id.hexerei.network.HexereiNetwork.register(modBus);
        LOGGER.info("Hexerei (altar slice) loading");
    }
```
> `HexereiNetwork` is created in Task 7; if implementing strictly in order, stub `HexereiNetwork.register`/`openAltarScreen` as no-ops here and fill them in Task 7. (To keep build green per task, create the stub now — see Task 7 Step 1.)

- [ ] **Step 5: Build — expect green**

Run: `JAVA_HOME=/home/h621l/minecraft/hexerei-work/tools/jdk17 ./gradlew --no-daemon build`
Expected: `BUILD SUCCESSFUL` (compiles; unit tests still pass).

- [ ] **Step 6: Commit**
```bash
git add hexerei/src
git commit -m "feat(hexerei): AltarBlock + AltarBlockEntity + registries (power scan, recharge, sync, IPowerSource)"
```

---

### Task 6: Assets & data (blockstate, models, textures, lang, recipe)

Hand-authored JSON (no legacy models exist). Textures copied from the extracted originals.

**Files (all under `hexerei/src/main/resources`):**
- Create: `assets/hexerei/blockstates/altar.json`
- Create: `assets/hexerei/models/block/altar.json`
- Create: `assets/hexerei/models/block/altar_joined.json`
- Create: `assets/hexerei/models/item/altar.json`
- Create: `assets/hexerei/textures/block/altar.png` (+ altar_top, altar_joined, altar_joined_top)
- Create: `assets/hexerei/textures/gui/altar.png`
- Create: `assets/hexerei/lang/en_us.json`, `assets/hexerei/lang/ru_ru.json`
- Create: `data/hexerei/recipes/altar_placeholder.json`
- Create: `data/hexerei/recipes/altar_authentic.json.disabled` (reference; not loaded)

- [ ] **Step 1: Copy textures from extracted originals**
```bash
cd /home/h621l/minecraft/hexerei
mkdir -p src/main/resources/assets/hexerei/textures/block src/main/resources/assets/hexerei/textures/gui
SRC=/home/h621l/minecraft/hexerei-work/extracted-assets/assets/hexerei/textures
cp $SRC/blocks/altar.png            src/main/resources/assets/hexerei/textures/block/altar.png
cp $SRC/blocks/altar_top.png        src/main/resources/assets/hexerei/textures/block/altar_top.png
cp $SRC/blocks/altar_joined.png     src/main/resources/assets/hexerei/textures/block/altar_joined.png
cp $SRC/blocks/altar_joined_top.png src/main/resources/assets/hexerei/textures/block/altar_joined_top.png
cp $SRC/gui/altar.png               src/main/resources/assets/hexerei/textures/gui/altar.png
```
> Note: use the 1.20.1 `textures/block/` (singular) path.

- [ ] **Step 2: `blockstates/altar.json`** (variant on JOINED)
```json
{
  "variants": {
    "joined=false": { "model": "hexerei:block/altar" },
    "joined=true":  { "model": "hexerei:block/altar_joined" }
  }
}
```

- [ ] **Step 3: `models/block/altar.json`**
```json
{
  "parent": "minecraft:block/cube_bottom_top",
  "textures": {
    "side": "hexerei:block/altar",
    "bottom": "hexerei:block/altar_top",
    "top": "hexerei:block/altar_top"
  }
}
```

- [ ] **Step 4: `models/block/altar_joined.json`**
```json
{
  "parent": "minecraft:block/cube_bottom_top",
  "textures": {
    "side": "hexerei:block/altar_joined",
    "bottom": "hexerei:block/altar_top",
    "top": "hexerei:block/altar_joined_top"
  }
}
```

- [ ] **Step 5: `models/item/altar.json`**
```json
{ "parent": "hexerei:block/altar" }
```

- [ ] **Step 6: `lang/en_us.json`** ( lang table)
```json
{
  "block.hexerei.altar": "Altar",
  "itemGroup.hexerei": "Hexerei",
  "hexerei.book.altarpower": "Altar power",
  "hexerei.gui.altar.power": "Power: %s / %s",
  "hexerei.rite.missingpowersource": "No altar nearby.",
  "hexerei.rite.insufficientpower": "Altar has insufficient power."
}
```

- [ ] **Step 7: `lang/ru_ru.json`** (the user works in Russian; keep keys, translate values)
```json
{
  "block.hexerei.altar": "Алтарь",
  "itemGroup.hexerei": "Hexerei",
  "hexerei.book.altarpower": "Сила алтаря",
  "hexerei.gui.altar.power": "Сила: %s / %s",
  "hexerei.rite.missingpowersource": "Рядом нет алтаря.",
  "hexerei.rite.insufficientpower": "У алтаря недостаточно силы."
}
```

- [ ] **Step 8: `data/hexerei/recipes/altar_placeholder.json`** (vanilla-only; temporary — authentic recipe needs unported items)
```json
{
  "type": "minecraft:crafting_shaped",
  "pattern": [ "ppp", "sls", "sls" ],
  "key": {
    "p": { "item": "minecraft:potion" },
    "s": { "item": "minecraft:stone_bricks" },
    "l": { "tag": "minecraft:logs" }
  },
  "result": { "item": "hexerei:altar", "count": 3 }
}
```
> ⚠️ TEMPORARY/placeholder shape so the altar is craftable for testing. The **authentic** recipe (Breath of the Goddess / Exhale of the Horned One / the witch-wood log) is preserved in `altar_authentic.json.disabled` and re-enabled when those items are added.

- [ ] **Step 9: `data/hexerei/recipes/altar_authentic.json.disabled`** (reference, not loaded)
```json
{
  "type": "minecraft:crafting_shaped",
  "pattern": [ "abc", "xyx", "xyx" ],
  "key": {
    "a": { "item": "hexerei:breath_of_the_goddess" },
    "b": { "item": "minecraft:potion" },
    "c": { "item": "hexerei:exhale_of_the_horned_one" },
    "x": { "item": "minecraft:stone_bricks" },
    "y": { "item": "hexerei:witchlog" }
  },
  "result": { "item": "hexerei:altar", "count": 3 }
}
```

- [ ] **Step 10: Build — expect green (asset/data load validated at runtime in Task 8)**

Run: `JAVA_HOME=/home/h621l/minecraft/hexerei-work/tools/jdk17 ./gradlew --no-daemon build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 11: Commit**
```bash
git add hexerei/src/main/resources
git commit -m "feat(hexerei): altar assets, lang (en/ru), models, placeholder recipe"
```

---

### Task 7: Client power GUI (`AltarScreen`) + open-screen network packet

Container-less read-only Screen (read-only power panel), opened via a tiny S2C packet.

**Files:**
- Create: `hexerei/src/main/java/com/hexerei/network/HexereiNetwork.java`
- Create: `hexerei/src/main/java/com/hexerei/network/OpenAltarScreenPacket.java`
- Create: `hexerei/src/main/java/com/hexerei/client/AltarScreen.java`

**Interfaces:**
- Consumes: `AltarBlockEntity.getCorePower/getCoreMaxPower`.
- Produces: `HexereiNetwork.register(IEventBus)`, `HexereiNetwork.openAltarScreen(ServerPlayer, BlockPos)`.

- [ ] **Step 1: `HexereiNetwork`** (SimpleChannel; if you stubbed it in Task 5, replace the stub)

`src/main/java/com/hexerei/network/HexereiNetwork.java`:
```java
package com.vel5id.hexerei.network;

import com.vel5id.hexerei.HexereiMod;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class HexereiNetwork {
    private HexereiNetwork() {}
    private static final String VERSION = "1";
    private static SimpleChannel CHANNEL;

    public static void register(IEventBus modBus) {
        CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(HexereiMod.MODID, "main"))
            .networkProtocolVersion(() -> VERSION)
            .clientAcceptedVersions(VERSION::equals)
            .serverAcceptedVersions(VERSION::equals)
            .simpleChannel();
        int id = 0;
        CHANNEL.registerMessage(id++, OpenAltarScreenPacket.class,
            OpenAltarScreenPacket::encode, OpenAltarScreenPacket::decode, OpenAltarScreenPacket::handle);
    }

    public static void openAltarScreen(ServerPlayer player, BlockPos corePos) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenAltarScreenPacket(corePos));
    }
}
```

- [ ] **Step 2: `OpenAltarScreenPacket`**

`src/main/java/com/hexerei/network/OpenAltarScreenPacket.java`:
```java
package com.vel5id.hexerei.network;

import com.vel5id.hexerei.client.AltarScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record OpenAltarScreenPacket(BlockPos corePos) {
    public static void encode(OpenAltarScreenPacket m, FriendlyByteBuf b) { b.writeBlockPos(m.corePos); }
    public static OpenAltarScreenPacket decode(FriendlyByteBuf b) { return new OpenAltarScreenPacket(b.readBlockPos()); }
    public static void handle(OpenAltarScreenPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> AltarScreen.open(m.corePos())));
        ctx.get().setPacketHandled(true);
    }
}
```

- [ ] **Step 3: `AltarScreen`** (reads synced BE each frame; blits gui/altar.png 176×88)

`src/main/java/com/hexerei/client/AltarScreen.java`:
```java
package com.vel5id.hexerei.client;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.blockentity.AltarBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class AltarScreen extends Screen {
    private static final ResourceLocation TEX = new ResourceLocation(HexereiMod.MODID, "textures/gui/altar.png");
    private static final int W = 176, H = 88;
    private final BlockPos corePos;

    public AltarScreen(BlockPos corePos) { super(Component.translatable("hexerei.book.altarpower")); this.corePos = corePos; }
    public static void open(BlockPos corePos) { Minecraft.getInstance().setScreen(new AltarScreen(corePos)); }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        renderBackground(g);
        int left = (width - W) / 2, top = (height - H) / 2;
        g.blit(TEX, left, top, 0, 0, W, H);
        float power = 0, max = 0;
        if (minecraft != null && minecraft.level != null
            && minecraft.level.getBlockEntity(corePos) instanceof AltarBlockEntity be) {
            // client copy: getCurrentPower returns -2 on client; read fields via getUpdateTag-synced load
            power = clientPower(be); max = clientMax(be);
        }
        String label = Component.translatable("hexerei.gui.altar.power",
            (int) power, (int) max).getString();
        g.drawCenteredString(font, label, width / 2, top + H / 2 - 4, 0xFFFFFF);
        super.render(g, mouseX, mouseY, partial);
    }

    // The synced client BE has power/maxPower loaded via handleUpdateTag.
    private static float clientPower(AltarBlockEntity be) { return be.getCorePower(); }
    private static float clientMax(AltarBlockEntity be) { return be.getCoreMaxPower(); }
}
```
> Implementer note: `getCorePower()` returns 0 on the client because `getCurrentPower()` returns the `-2` client sentinel → clamped to 0. To show live values client-side, add client-readable getters on `AltarBlockEntity` that return the synced `power`/`maxPower*powerScale` fields directly regardless of side (the fields are populated by `handleUpdateTag`). Add:
> ```java
> public float clientPower() { return power; }
> public float clientMaxPower() { return maxPower * powerScale; }
> ```
> and call those from the screen. (Server-authoritative `getCurrentPower()` keeps the `-2` sentinel for the power-API contract; the GUI uses the plain synced fields.)

- [ ] **Step 4: Build — expect green**

Run: `JAVA_HOME=/home/h621l/minecraft/hexerei-work/tools/jdk17 ./gradlew --no-daemon build`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**
```bash
git add hexerei/src
git commit -m "feat(hexerei): read-only altar power GUI + open-screen packet"
```

---

### Task 8: Forge GameTests (formation + power) + run headless

**Files:**
- Create: `hexerei/src/main/java/com/hexerei/test/AltarGameTests.java`
- Modify: `hexerei/src/main/resources/META-INF/mods.toml` (no change needed) — GameTest discovery is via `@GameTestHolder` + `@Mod.EventBusSubscriber` or the `forge:registerGameTest` annotation scan; ensure `enableGameTest` run config exists (Task 8 Step 3).
- Modify: `hexerei/build.gradle` (add `runGameTestServer`-friendly config; the MDK's `gameTestServer` run already exists — verify).

**Interfaces:**
- Consumes: `HexereiBlocks.ALTAR`, `AltarBlock.ALTAR_JOINED`, `AltarBlockEntity`.

- [ ] **Step 1: Write the GameTests**

`src/main/java/com/hexerei/test/AltarGameTests.java`:
```java
package com.vel5id.hexerei.test;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.block.AltarBlock;
import com.vel5id.hexerei.blockentity.AltarBlockEntity;
import com.vel5id.hexerei.registry.HexereiBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;

@GameTestHolder(HexereiMod.MODID)
public class AltarGameTests {

    private static void place2x3(GameTestHelper h, BlockPos base) {
        for (int dx = 0; dx < 2; dx++) for (int dz = 0; dz < 3; dz++)
            h.setBlock(base.offset(dx, 0, dz), HexereiBlocks.ALTAR.get());
    }

    @GameTest(template = "hexerei:empty", timeoutTicks = 200)
    public void altarFormsAsJoined(GameTestHelper h) {
        BlockPos base = new BlockPos(1, 1, 1);
        place2x3(h, base);
        h.succeedWhen(() -> {
            for (int dx = 0; dx < 2; dx++) for (int dz = 0; dz < 3; dz++) {
                BlockPos p = base.offset(dx, 0, dz);
                h.assertBlockState(p, s -> s.getValue(AltarBlock.ALTAR_JOINED), () -> "altar not joined at " + p);
            }
        });
    }

    @GameTest(template = "hexerei:empty", timeoutTicks = 400)
    public void altarAccruesPowerFromNature(GameTestHelper h) {
        BlockPos base = new BlockPos(1, 1, 1);
        place2x3(h, base);
        // surround with grass blocks (2/80) to guarantee maxPower > 0
        for (int dx = -1; dx <= 3; dx++) for (int dz = -1; dz <= 4; dz++)
            h.setBlock(base.offset(dx, -1, dz), Blocks.GRASS_BLOCK);
        h.runAfterDelay(120, () -> {
            if (h.getBlockEntity(base) instanceof AltarBlockEntity be) {
                if (be.clientMaxPower() <= 0 && be.getCoreMaxPower() <= 0)
                    h.fail("altar maxPower did not rise from surrounding grass");
                else h.succeed();
            } else h.fail("no altar BE at core");
        });
    }

    @GameTest(template = "hexerei:empty", timeoutTicks = 200)
    public void twoByTwoDoesNotForm(GameTestHelper h) {
        BlockPos base = new BlockPos(1, 1, 1);
        for (int dx = 0; dx < 2; dx++) for (int dz = 0; dz < 2; dz++)
            h.setBlock(base.offset(dx, 0, dz), HexereiBlocks.ALTAR.get());
        h.runAfterDelay(40, () -> {
            BlockPos p = base;
            if (h.getBlockState(p).getValue(AltarBlock.ALTAR_JOINED)) h.fail("2x2 should not form an altar");
            else h.succeed();
        });
    }
}
```
> Needs an empty test template `hexerei:empty`. Create `src/main/resources/data/hexerei/structures/empty.snbt` OR `gametests` template. Simplest: a 6×3×7 empty structure. See Step 2.

- [ ] **Step 2: Create the empty GameTest template**

`src/main/resources/data/hexerei/structures/empty.snbt`:
```
{size:[8,4,8],data:[],palette:[{Name:"minecraft:air"}],entities:[]}
```

- [ ] **Step 3: Ensure a gametest run config / task** — the Forge MDK exposes `gameTestServer`. Verify in `build.gradle` `runs { }` there is a `gameTestServer` block; if absent add:
```gradle
        gameTestServer {
            workingDirectory project.file('run')
            property 'forge.logging.console.level', 'info'
            property 'forge.enabledGameTestNamespaces', 'hexerei'
            mods { hexerei { source sourceSets.main } }
        }
```

- [ ] **Step 4: Run GameTests headless — expect all PASS**

Run: `JAVA_HOME=/home/h621l/minecraft/hexerei-work/tools/jdk17 ./gradlew --no-daemon runGameTestServer`
Expected: log shows `GameTest ... passed` for `altarFormsAsJoined`, `altarAccruesPowerFromNature`, `twoByTwoDoesNotForm`; process exits 0. (Forge fails the run if any GameTest fails.)

- [ ] **Step 5: Commit**
```bash
git add hexerei/src
git commit -m "test(hexerei): GameTests for altar formation + power accrual"
```

---

### Task 9: Dedicated-server boot smoke + docs + final verification

**Files:**
- Create: `hexerei/README.md`
- Create: `hexerei/DESIGN-NOTES.md`

- [ ] **Step 1: Build the mod jar**

Run: `JAVA_HOME=/home/h621l/minecraft/hexerei-work/tools/jdk17 ./gradlew --no-daemon build`
Expected: `hexerei/build/libs/hexerei-1.20.1-0.1.0-altar.jar` exists.

- [ ] **Step 2: Boot a dedicated Forge 1.20.1 server with the mod** (headless smoke)

Use the repo's existing server setup or a throwaway Forge server dir. Drop the jar in `mods/`, accept EULA, boot with a short timeout, capture log. Verify:
- mod `hexerei` loads (`Hexerei (altar slice) loading` in log);
- no `ERROR`/exception/`Failed to load` lines mentioning hexerei;
- registries present: grep for missing-registry/`Unknown block` warnings — none for `hexerei:altar`.

```bash
# from a scratch server dir with forge-1.20.1-47.4.10 installed:
cp /home/h621l/minecraft/hexerei/build/libs/hexerei-*.jar mods/
echo "eula=true" > eula.txt
timeout 180 java -jar forge-1.20.1-47.4.10-*.jar nogui 2>&1 | tee server-smoke.log | tail -40
grep -iE "hexerei|error|exception" server-smoke.log | grep -vi "no error" | head
```
Expected: `Hexerei (altar slice) loading` present; no hexerei-related ERROR/exception; server reaches `Done (Xs)! For help, type "help"`.

- [ ] **Step 3: In-server functional check via console** (if RCON/stdin available) — `/setblock` six altars and read state:
```
/setblock 0 -60 0 hexerei:altar
... (2x3)
/data get block 0 -60 0
```
Expected: after placement, blockstate `joined=true` on the formed altar; `Power`/`MaxPower` present in BE data. (If driving stdin is impractical, the GameTests in Task 8 already prove this; note that in the report.)

- [ ] **Step 4: `README.md`** — build/run/test instructions:
```markdown
# Hexerei (Altar slice) — Forge 1.20.1

Hexerei witchcraft mod for Forge 1.20.1. This module is the **Altar core** slice.

## Build
    JAVA_HOME=../hexerei-work/tools/jdk17 ./gradlew --no-daemon build
Output: `build/libs/hexerei-1.20.1-0.1.0-altar.jar`

## Test
- Unit: `./gradlew test`
- In-game (headless): `./gradlew runGameTestServer`

## Install
Drop the jar into a Forge 1.20.1 (47.4.10) server/client `mods/` folder.

## Status
Implemented: Altar block, multiblock formation (2×3), power scan + recharge, power-query API, read-only power GUI.
Not yet: brews, rituals, cauldron and other power consumers, Hexerei plant/artefact blocks, particles. See DESIGN-NOTES.md.
```

- [ ] **Step 5: `DESIGN-NOTES.md`** — the design-decision ledger + future-slice map:
```markdown
# Design Notes — Altar slice

Ground truth: this design spec and the Hexerei altar design notes.

## Deliberate fidelity decisions
- Joined state: metadata 0/1 → blockstate `joined` boolean.
- Power registry: process-global static → per-ServerLevel transient manager (fixes cross-world leakage).
- Power store: float, recharge floored to int (preserved).
- Recipe: temporary vanilla-only placeholder; authentic recipe in `data/hexerei/recipes/altar_authentic.json.disabled`.

## Deferred to future slices (guarded in code with // FUTURE SLICE)
- Future-content power-table blocks (LEAVES 4/50, LOG 3/100, DEMON_HEART 40/2, INFINITY_EGG 1000/1, crops 4/20, mosses, etc.).
- Artefact bonuses (skulls/torch/candelabra/chalice/arthana/pentacle/infinity egg) → powerScale/rechargeScale/rangeScale stay 1 this slice. NB: wither-skull fall-through bug to be preserved when implemented.
- NaturePowerFX particles (cosmetic).
- INullSource / void bramble.

## Known [UNVERIFIED] to revisit
- Core-coord NBT key form (`CoreX/Y/Z` chosen).
- Authentic-recipe Hexerei item ids / the `minecraft:potion` ingredient variant.
```

- [ ] **Step 6: Commit + final verification report**
```bash
git add hexerei/README.md hexerei/DESIGN-NOTES.md
git commit -m "docs(hexerei): README + porting notes; altar slice complete"
```
Then run the full gate and capture evidence:
```bash
JAVA_HOME=/home/h621l/minecraft/hexerei-work/tools/jdk17 ./gradlew --no-daemon clean test build runGameTestServer
```
Expected: `BUILD SUCCESSFUL`; unit tests pass; GameTests pass. Report pass/fail with the log tails.

---

## Self-Review

**Spec coverage:** scaffold (T1) ✓; AltarBlock+JOINED (T5) ✓; AltarBlockEntity scan/recharge/NBT/sync/IPowerSource (T5) ✓; multiblock BFS (T2 pure + T5 world + T8 gametest) ✓; power table (T3 + T5) ✓; AltarPowerManager (T4) ✓; GUI (T7) ✓; assets/lang/recipe (T6) ✓; GameTest + server smoke (T8/T9) ✓; design-decision ledger / future-slice markers (T9 DESIGN-NOTES) ✓. Particles intentionally out of scope. Artefact bonuses deferred within scope and documented (T5/T9).

**Placeholder scan:** No "TBD/implement later". The two intentional `.disabled`/placeholder recipe + deferred-artefact items are explicitly scoped and documented, not hand-waving.

**Type consistency:** `AltarBlock.ALTAR_JOINED` used consistently (T5/T6 blockstate `joined`/T8). `AltarBlockEntity` getters: `getCorePower/getCoreMaxPower/getCoreRechargeScale` + client `clientPower/clientMaxPower` (T7 note adds `clientPower()/clientMaxPower()` — Task 8 references `clientMaxPower()`; ensure both client getters are added in T7 Step 3). `AltarPowerManager.query(Level,BlockPos)`/`closest(Level,BlockPos)` consistent with T4 impl. `IPowerSource` signature identical across T4 definition and T5 impl.
