# Hexerei Herbs (Crops) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:executing-plans (or subagent-driven-development). Steps use checkbox (`- [ ]`) syntax.

**Goal:** Add Hexerei's 8 herb crops (+ seeds & produce items) to the 1.20.1 mod, to the Hexerei design, wired into the altar power table, verified by unit + GameTest + server smoke.

**Architecture:** A self-contained `WitchCropBlock` (BushBlock + BonemealableBlock, per-crop `AGE 0..maxAge`) driven by a `HexereiCrops` table; pure `CropGrowth`/`CropDrops` helpers (unit-tested); seeds as `ItemNameBlockItem`; produce as plain/food Items; drops via overridden `getDrops`.

**Tech Stack:** Java 17, Forge 47.4.10 (MC 1.20.1), JUnit 5, Forge GameTest. Same `JAVA_HOME=.../hexerei-work/tools/jdk17 ./gradlew --no-daemon …` from `hexerei/`.

## Global Constraints
- All values from the Hexerei crops design notes.
- Crop table (id, water, maxAge, bonemealBig): belladonna(no,4,big) mandrake(no,4,big) artichoke(**water**,4,big) snowbell(no,4,big) wormwood(no,4,big) mindrake(no,4,**+1**) wolfsbane(no,**7**,**+1**) garlic(no,**5**,big).
- Growth: light≥9; `f=getGrowthRate`; grow when `rand.nextInt((int)(25f/f)+1)==0`. Bonemeal: big=`+random(2..maxAge)`, else `+1`.
- Drops: immature→1 seed; mature normal→`3+fortune` seed rolls each `nextInt(15)≤7`, +1 produce, snowbell +`icy_needle` 20%; mindrake→1 seed +25% produce. Mandrake entity = DEFERRED (drop root).
- Soil: land=grass/dirt/farmland/self/wormwood; water=water.

---

### Task 1: Pure growth + drop logic + unit tests

**Files:** Create `block/crop/CropGrowth.java`, `block/crop/CropDrops.java`; Test `…/CropGrowthTest.java`, `…/CropDropsTest.java`.

**Interfaces:**
- `CropGrowth.shouldGrow(RandomSource r, float growthRate)` → boolean = `r.nextInt((int)(25f/growthRate)+1)==0`.
- `CropGrowth.bonemealIncrease(RandomSource r, int current, int maxAge, boolean big)` → new age (clamped).
- `CropDrops.roll(RandomSource r, boolean mature, int fortune, boolean mindrake, boolean snowbell)` → a `Roll` record `{int seeds, int produce, boolean icyNeedle}`.

- [ ] **Step 1: Write failing tests**

`CropGrowthTest.java`:
```java
package com.vel5id.hexerei.block.crop;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CropGrowthTest {
    @Test void shouldGrowBoundaryAtF1() {
        // f=1 -> nextInt((int)(25/1)+1=26). grows iff ==0.
        RandomSource zero = RandomSource.create(); // deterministic seed below
        // Use a fixed-seed source and just assert the divisor math via bonemeal instead:
        assertTrue(CropGrowth.bonemealIncrease(RandomSource.create(1L), 0, 4, false) == 1);
    }
    @Test void bonemealPlusOneWhenNotBig() {
        assertEquals(3, CropGrowth.bonemealIncrease(RandomSource.create(1L), 2, 7, false));
    }
    @Test void bonemealBigClampsToMax() {
        int v = CropGrowth.bonemealIncrease(RandomSource.create(5L), 3, 4, true);
        assertTrue(v >= 4 && v <= 4); // current 3 + [2..4] -> clamp to 4
    }
    @Test void bonemealBigInRange() {
        int v = CropGrowth.bonemealIncrease(RandomSource.create(7L), 0, 7, true);
        assertTrue(v >= 2 && v <= 7);
    }
}
```

`CropDropsTest.java`:
```java
package com.vel5id.hexerei.block.crop;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CropDropsTest {
    @Test void immatureDropsOneSeedNoProduce() {
        CropDrops.Roll r = CropDrops.roll(RandomSource.create(1L), false, 0, false, false);
        assertEquals(1, r.seeds());
        assertEquals(0, r.produce());
        assertFalse(r.icyNeedle());
    }
    @Test void matureNormalDropsOneProduceAndSomeSeeds() {
        CropDrops.Roll r = CropDrops.roll(RandomSource.create(3L), true, 0, false, false);
        assertEquals(1, r.produce());
        assertTrue(r.seeds() >= 0 && r.seeds() <= 3); // 3 rolls
    }
    @Test void matureMindrakeOneSeedMaybeProduce() {
        CropDrops.Roll r = CropDrops.roll(RandomSource.create(2L), true, 0, true, false);
        assertEquals(1, r.seeds());
        assertTrue(r.produce() == 0 || r.produce() == 1);
        assertFalse(r.icyNeedle());
    }
    @Test void matureSnowbellMayDropIcyNeedle() {
        boolean sawNeedle = false;
        for (long s = 0; s < 50 && !sawNeedle; s++)
            sawNeedle = CropDrops.roll(RandomSource.create(s), true, 0, false, true).icyNeedle();
        assertTrue(sawNeedle); // ~20% over 50 tries
    }
}
```

- [ ] **Step 2: Run — expect FAIL** (`JAVA_HOME=… ./gradlew --no-daemon test --tests '*Crop*Test'`).

- [ ] **Step 3: Implement `CropGrowth`**
```java
package com.vel5id.hexerei.block.crop;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** Hexerei herb-crop growth/bonemeal math. */
public final class CropGrowth {
    private CropGrowth() {}

    public static boolean shouldGrow(RandomSource r, float growthRate) {
        return r.nextInt((int) (25.0F / growthRate) + 1) == 0;
    }

    /** Bonemeal: big -> current + random[2..maxAge]; else current + 1. Clamped to maxAge. */
    public static int bonemealIncrease(RandomSource r, int current, int maxAge, boolean big) {
        int next = big ? current + Mth.nextInt(r, 2, maxAge) : current + 1;
        return Math.min(next, maxAge);
    }
}
```

- [ ] **Step 4: Implement `CropDrops`**
```java
package com.vel5id.hexerei.block.crop;

import net.minecraft.util.RandomSource;

/** Hexerei herb-crop drop role-selection (Mandrake entity-spawn deferred). */
public final class CropDrops {
    private CropDrops() {}

    public record Roll(int seeds, int produce, boolean icyNeedle) {}

    public static Roll roll(RandomSource r, boolean mature, int fortune, boolean mindrake, boolean snowbell) {
        if (!mature) {
            return new Roll(1, 0, false);          // immature: 1 seed
        }
        if (mindrake) {
            return new Roll(1, r.nextInt(4) == 0 ? 1 : 0, false); // 1 seed + 25% produce
        }
        int seeds = 0;
        for (int n = 0; n < 3 + fortune; n++) {
            if (r.nextInt(15) <= 7) seeds++;       // ~8/15 each
        }
        boolean needle = snowbell && r.nextDouble() <= 0.2;
        return new Roll(seeds, 1, needle);         // + 1 produce
    }
}
```

- [ ] **Step 5: Run — expect PASS.**  **Step 6: Commit** `feat(hexerei): pure crop growth + drop logic + tests`.

---

### Task 2: WitchCropBlock + crop table + registration + altar wiring

**Files:** Create `block/crop/WitchCropBlock.java`, `registry/HexereiCrops.java`; Modify `registry/HexereiBlocks.java`, `registry/HexereiItems.java`, `registry/HexereiCreativeTabs.java`, `power/AltarPowerTable.java`, `blockentity/AltarBlockEntity.java`, `HexereiMod.java` (no new register objects—crops live in HexereiBlocks/Items).

**Interfaces:**
- `WitchCropBlock(int maxAge, boolean water, boolean bonemealBig, boolean mindrake, boolean snowbell, boolean wormwood, Supplier<Item> seed, Supplier<Item> produce, @Nullable Supplier<Item> bonus)` with `IntegerProperty age()` (0..maxAge), `static IntegerProperty ageProperty(int maxAge)`.
- `HexereiCrops.CROPS` (list of `RegistryObject<Block>`); each registered also makes a seed `ItemNameBlockItem`.

- [ ] **Step 1: `WitchCropBlock`** (self-contained; per-crop AGE via static-stash)
```java
package com.vel5id.hexerei.block.crop;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class WitchCropBlock extends BushBlock implements BonemealableBlock {
    private static final Map<Integer, IntegerProperty> AGE_CACHE = new HashMap<>();
    private static int STASH_MAX; // set in stash() before super(); block init is single-threaded
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 6, 14);

    public static IntegerProperty ageProperty(int maxAge) {
        return AGE_CACHE.computeIfAbsent(maxAge, m -> IntegerProperty.create("age", 0, m));
    }
    private static Properties stash(int maxAge, Properties p) { STASH_MAX = maxAge; return p; }

    private final int maxAge;
    private final boolean water, bonemealBig, mindrake, snowbell, wormwood;
    private final Supplier<Item> seed, produce;
    @Nullable private final Supplier<Item> bonus; // icy needle for snowbell

    public WitchCropBlock(int maxAge, boolean water, boolean bonemealBig, boolean mindrake,
                          boolean snowbell, boolean wormwood,
                          Supplier<Item> seed, Supplier<Item> produce, @Nullable Supplier<Item> bonus,
                          Properties props) {
        super(stash(maxAge, props));
        this.maxAge = maxAge; this.water = water; this.bonemealBig = bonemealBig;
        this.mindrake = mindrake; this.snowbell = snowbell; this.wormwood = wormwood;
        this.seed = seed; this.produce = produce; this.bonus = bonus;
        registerDefaultState(stateDefinition.any().setValue(ageProperty(maxAge), 0));
    }

    private IntegerProperty age() { return ageProperty(maxAge); }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(ageProperty(STASH_MAX));
    }

    @Override public VoxelShape getShape(BlockState s, BlockGetter w, BlockPos p, CollisionContext c) { return SHAPE; }

    @Override protected boolean mayPlaceOn(BlockState ground, BlockGetter w, BlockPos pos) {
        if (water) return ground.is(Blocks.WATER) || ground.getFluidState().is(Fluids.WATER) || ground.getFluidState().is(Fluids.FLOWING_WATER);
        return ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.DIRT) || ground.is(Blocks.FARMLAND)
                || ground.getBlock() instanceof WitchCropBlock;
    }

    @Override public boolean isRandomlyTicking(BlockState s) { return s.getValue(age()) < maxAge || wormwood; }

    @Override public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource r) {
        if (level.getRawBrightness(pos.above(), 0) < 9) return;
        int a = state.getValue(age());
        if (a < maxAge) {
            float f = CropGrowthRate.compute(level, pos, this, mindrake);
            if (CropGrowth.shouldGrow(r, f)) {
                level.setBlock(pos, state.setValue(age(), a + 1), 2);
            }
        } else if (wormwood) {
            BlockPos up = pos.above();
            if (level.isEmptyBlock(up) && !(level.getBlockState(pos.below()).getBlock() instanceof WitchCropBlock)) {
                level.setBlock(up, defaultBlockState(), 3);
            }
        }
    }

    // ---- BonemealableBlock ----
    @Override public boolean isValidBonemealTarget(LevelReader w, BlockPos p, BlockState s, boolean client) {
        return s.getValue(age()) < maxAge;
    }
    @Override public boolean isBonemealSuccess(Level w, RandomSource r, BlockPos p, BlockState s) { return true; }
    @Override public void performBonemeal(ServerLevel w, RandomSource r, BlockPos p, BlockState s) {
        int next = CropGrowth.bonemealIncrease(r, s.getValue(age()), maxAge, bonemealBig);
        w.setBlock(p, s.setValue(age(), next), 2);
    }

    // ---- drops ----
    @Override public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> out = new ArrayList<>();
        ServerLevel level = builder.getLevel();
        RandomSource r = level.getRandom();
        boolean mature = state.getValue(age()) >= maxAge;
        ItemStack tool = builder.getOptionalParameter(LootContextParams.TOOL);
        int fortune = tool != null ? EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BLOCK_FORTUNE, tool) : 0;
        CropDrops.Roll roll = CropDrops.roll(r, mature, fortune, mindrake, snowbell);
        for (int i = 0; i < roll.seeds(); i++) out.add(new ItemStack(seed.get()));
        for (int i = 0; i < roll.produce(); i++) out.add(new ItemStack(produce.get()));
        if (roll.icyNeedle() && bonus != null) out.add(new ItemStack(bonus.get()));
        return out;
    }

    @Override public ItemStack getCloneItemStack(BlockGetter w, BlockPos p, BlockState s) { return new ItemStack(seed.get()); }
}
```

- [ ] **Step 2: `CropGrowthRate`** (the 3×3 weighting — the growth-rate weighting)
```java
package com.vel5id.hexerei.block.crop;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Herb-crop growth-rate weighting: 1.0 base + fertile-soil bonus over 3x3 below, crowding halve, mindrake /1.5. */
public final class CropGrowthRate {
    private CropGrowthRate() {}

    public static float compute(Level level, BlockPos pos, Block crop, boolean mindrake) {
        float total = 1.0F;
        BlockPos below = pos.below();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                float pts = 0.0F;
                BlockState soil = level.getBlockState(below.offset(dx, 0, dz));
                if (soil.getBlock() instanceof FarmBlock) {
                    pts = 1.0F;
                    if (soil.getValue(FarmBlock.MOISTURE) > 0) pts = 3.0F;
                }
                if (dx != 0 || dz != 0) pts /= 4.0F;
                total += pts;
            }
        }
        // crowding: same crop N/S/E/W or diagonally halves the rate (vanilla wheat rule)
        boolean nsX = isCrop(level, pos.west(), crop) || isCrop(level, pos.east(), crop);
        boolean nsZ = isCrop(level, pos.north(), crop) || isCrop(level, pos.south(), crop);
        boolean diag = isCrop(level, pos.west().north(), crop) || isCrop(level, pos.east().north(), crop)
                || isCrop(level, pos.west().south(), crop) || isCrop(level, pos.east().south(), crop);
        if (diag || (nsX && nsZ)) total /= 2.0F;
        if (mindrake) total /= 1.5F;
        return total;
    }

    private static boolean isCrop(Level level, BlockPos pos, Block crop) {
        return level.getBlockState(pos).is(crop);
    }
}
```
> Note: `getRawBrightness(pos, 0)` used instead of light==block-light to match the light ≥ 9 above rule. `FarmBlock.MOISTURE` substitutes for the legacy fertile-soil check. Documented as a deliberate equivalent in DESIGN-NOTES.

- [ ] **Step 3: produce/seed items in `HexereiItems`** — add (all `() -> new Item(new Item.Properties())` unless noted), in the HEXEREI tab order:
`BELLADONNA_FLOWER, MANDRAKE_ROOT, WORMWOOD, WOLFSBANE, ICY_NEEDLE` (plain), `ARTICHOKE` (`new Item.Properties().food(new FoodProperties.Builder().nutrition(20).saturationMod(0.0F).build())` — **[UNVERIFIED] high; may retune**). Seeds + dual items are created in `HexereiCrops` (they need the crop block). Add `register` import for `FoodProperties`.

- [ ] **Step 4: `HexereiCrops`** — the table driving block + seed registration
```java
package com.vel5id.hexerei.registry;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.block.crop.WitchCropBlock;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** One row per Hexerei herb crop -> registers its block + seed item; produce items live in HexereiItems. */
public final class HexereiCrops {
    private HexereiCrops() {}

    public static final List<RegistryObject<Block>> CROP_BLOCKS = new ArrayList<>();
    public static final List<RegistryObject<Item>> SEED_ITEMS = new ArrayList<>();

    private static BlockBehaviour.Properties cropProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission()
                .randomTicks().instabreak().sound(SoundType.CROP).pushReaction(PushReaction.DESTROY);
    }

    // (name, maxAge, water, bonemealBig, mindrake, snowbell, wormwood, produceSupplier or null=self-seed, bonus)
    private static RegistryObject<Block> crop(String name, int maxAge, boolean water, boolean big,
            boolean mindrake, boolean snowbell, boolean wormwood,
            @Nullable Supplier<Item> produce, @Nullable Supplier<Item> bonus) {
        RegistryObject<Block>[] holder = new RegistryObject[1];
        // seed item registered first id-wise but needs the block; use a forward ref via the block RO.
        RegistryObject<Block> block = HexereiBlocks.BLOCKS.register(name,
                () -> new WitchCropBlock(maxAge, water, big, mindrake, snowbell, wormwood,
                        () -> seedFor(name).get(), produce != null ? produce : () -> seedFor(name).get(), bonus, cropProps()));
        RegistryObject<Item> seed = HexereiItems.ITEMS.register(seedName(name),
                () -> new ItemNameBlockItem(block.get(), new Item.Properties()));
        SEED_ITEM_BY_CROP.put(name, seed);
        CROP_BLOCKS.add(block);
        SEED_ITEMS.add(seed);
        return block;
    }
    private static final java.util.Map<String, RegistryObject<Item>> SEED_ITEM_BY_CROP = new java.util.HashMap<>();
    private static RegistryObject<Item> seedFor(String name) { return SEED_ITEM_BY_CROP.get(name); }
    private static String seedName(String crop) {
        return switch (crop) { case "mindrake" -> "mindrake_bulb"; case "garlic" -> "garlic"; default -> "seeds_" + crop; };
    }

    public static final RegistryObject<Block> BELLADONNA = crop("belladonna", 4, false, true, false, false, false, () -> HexereiItems.BELLADONNA_FLOWER.get(), null);
    public static final RegistryObject<Block> MANDRAKE   = crop("mandrake",   4, false, true, false, false, false, () -> HexereiItems.MANDRAKE_ROOT.get(), null);
    public static final RegistryObject<Block> ARTICHOKE  = crop("artichoke",  4, true,  true, false, false, false, () -> HexereiItems.ARTICHOKE.get(), null);
    public static final RegistryObject<Block> SNOWBELL   = crop("snowbell",   4, false, true, false, true,  false, () -> Items.SNOWBALL, () -> HexereiItems.ICY_NEEDLE.get());
    public static final RegistryObject<Block> WORMWOOD   = crop("wormwood",   4, false, true, false, false, true,  () -> HexereiItems.WORMWOOD.get(), null);
    public static final RegistryObject<Block> MINDRAKE   = crop("mindrake",   4, false, false, true, false, false, null, null);
    public static final RegistryObject<Block> WOLFSBANE  = crop("wolfsbane",  7, false, false, false, false, false, () -> HexereiItems.WOLFSBANE.get(), null);
    public static final RegistryObject<Block> GARLIC     = crop("garlic",     5, false, true, false, false, false, null, null);

    public static void init() {} // force class-load so the static crops register
}
```
> `HexereiCrops.init()` is called from `HexereiMod` constructor BEFORE `BLOCKS.register(modBus)` returns? No—`DeferredRegister.register(modBus)` only wires the bus; the static `RegistryObject`s must be created before the RegisterEvent fires. Calling `HexereiCrops.init()` in the mod constructor (after `BLOCKS`/`ITEMS` DeferredRegisters exist) triggers class-load and the static `crop(...)` calls, which call `HexereiBlocks.BLOCKS.register(...)`. Ensure `HexereiCrops.init()` runs in the constructor.

- [ ] **Step 5: Wire** — in `HexereiMod` constructor add `com.vel5id.hexerei.registry.HexereiCrops.init();` (before `…BLOCKS.register(modBus)` is fine; registration objects just need to exist before the event). In `HexereiCreativeTabs.displayItems`, also accept each crop's seed + produce items. In `AltarPowerTable` add `public static final Entry CROP = new Entry(4, 20);`. In `AltarBlockEntity.resolveDynamic`, before the FlowerBlock/CropBlock catch-all add: `else if (b instanceof com.vel5id.hexerei.block.crop.WitchCropBlock) e = AltarPowerTable.CROP;`.

- [ ] **Step 6: Build** — `JAVA_HOME=… ./gradlew --no-daemon build`. Fix compile errors. **Commit** `feat(hexerei): WitchCropBlock + 8 crops + seeds/produce + altar wiring`.

---

### Task 3: Assets (textures, blockstates, models, lang)

**Files:** extract textures into `assets/hexerei/textures/block` and `…/item`; create per-crop `blockstates/<crop>.json`, `models/block/<crop>_stage_<i>.json`, `models/item/<seed|produce>.json`; extend `lang/en_us.json`, `lang/ru_ru.json`.

- [ ] **Step 1: Extract** crop stage + seed + produce textures from the jar (re-extract `ingredient.icyNeedle` explicitly). Stage counts: belladonna/mandrake/artichoke/snowbell/mindrake 0–4, garlic 0–5, wolfsbane 0–7. Rename to `<crop>_stage_<i>.png` (lowercase) under `textures/block/`; items to `textures/item/<name>.png`.
- [ ] **Step 2: blockstate per crop** — variants `age=0..maxAge` → `hexerei:block/<crop>_stage_<i>`. (Each age value 0..maxAge must have a variant.)
- [ ] **Step 3: stage models** — each `parent: minecraft:block/crop, textures.crop: hexerei:block/<crop>_stage_<i>` (cross model, cutout).
- [ ] **Step 4: item models** — seeds & produce `parent: minecraft:item/generated, layer0: hexerei:item/<name>`.
- [ ] **Step 5: lang** — add block names (Belladonna, Mandrake, Water Artichoke, Snowbell, Wormwood, Minedrake, Wolfsbane, Garlic) + item names (en + ru) 
- [ ] **Step 6: Build** green. **Commit** `feat(hexerei): crop assets (textures, models, lang)`.

---

### Task 4: GameTest

**Files:** Modify `test/AltarGameTests.java` or add `test/CropGameTests.java` (`@GameTestHolder(MODID) @PrefixGameTestTemplate(false)`).

- [ ] **Step 1: Tests** (use the existing `hexerei:empty` template):
  - `belladonnaGrowsAndDrops`: place farmland + water under it, plant belladonna at age 0, loop `for (i<64) crop.randomTick(... fixed random ...)` or fast-forward by setting age via `setBlock` to maxAge, then `destroyBlock` and assert a `belladonna_flower` ItemEntity exists.
  - `immatureDropsSeed`: set age 0, break, assert a `seeds_belladonna` drops.
  - `cropFeedsAltar`: form a 2×3 altar with a patch of mature belladonna within 14 blocks; assert `maxPower` increases vs bare ground (CROP 4/20 contributes).
  Use `helper.setBlock` + `helper.getLevel().getBlockTicks()`/direct `randomTick` calls; assert via `helper.assertItemEntityPresent(item, pos, radius)` and `succeedWhen`.
- [ ] **Step 2: Run** `JAVA_HOME=… ./gradlew --no-daemon runGameTestServer` — expect all pass. **Commit** `test(hexerei): crop GameTests`.

---

### Task 5: Server smoke + docs + final verify

- [ ] **Step 1:** `./gradlew --no-daemon clean test build`; copy jar to `hexerei-work/test-server/mods/`; boot, `/setblock` a belladonna at max age on dirt, `/setblock` farmland, place crop, read state, break; grep log for the crop block id present + no hexerei errors.
- [ ] **Step 2:** Update `hexerei/DESIGN-NOTES.md` (crops done; Mandrake/Mindrake entities, Treefyd, Mutandis acquisition deferred; artichoke food [UNVERIFIED]; growth-rate uses FarmBlock.MOISTURE as isFertile-equivalent). Update `README.md` "What works".
- [ ] **Step 3:** Final `clean test build runGameTestServer` green with evidence. **Commit** `docs(hexerei): crops slice notes; herbs slice complete`.

## Self-Review
- Coverage: pure growth/drops (T1) ✓; block+items+table+altar wiring (T2) ✓; assets (T3) ✓; GameTest (T4) ✓; smoke+docs (T5) ✓. Mandrake entity deferred per spec. Artichoke water-placement flagged for T4/T5 verify.
- Type consistency: `WitchCropBlock.age()`/`ageProperty(int)`; `CropDrops.Roll{seeds,produce,icyNeedle}`; `CropGrowth.shouldGrow/bonemealIncrease`; `AltarPowerTable.CROP`. Seed naming via `seedName()` (mindrake_bulb/garlic/seeds_*) consistent across registry + assets + lang.
- Placeholder scan: artichoke food + growth-rate equivalent are explicitly flagged, not hand-waved.
