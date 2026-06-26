package com.vel5id.hexerei.test;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.item.AmuletItem;
import com.vel5id.hexerei.power.AltarPowerManager;
import com.vel5id.hexerei.registry.HexereiBlocks;
import com.vel5id.hexerei.registry.HexereiItems;
import com.vel5id.hexerei.ritual.RitualActivation;
import com.vel5id.hexerei.ritual.RitualCircle;
import com.vel5id.hexerei.ritual.VerdantRite;
import com.vel5id.hexerei.soul.Bond;
import com.vel5id.hexerei.soul.Correspondence;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(HexereiMod.MODID)
@PrefixGameTestTemplate(false)
public class RitualExpansionGameTests {

    // ---- helpers (reuse RitualGameTests.FakeAltar from the same package) ----

    private static void buildSmall(GameTestHelper h, BlockPos center, int glyphCount) {
        floor(h, center, 2);
        h.setBlock(center, HexereiBlocks.RITUAL_SIGIL.get());
        place(h, RitualCircle.smallRing(center), glyphCount);
    }

    private static void buildMedium(GameTestHelper h, BlockPos center, int glyphCount) {
        floor(h, center, 3);
        h.setBlock(center, HexereiBlocks.RITUAL_SIGIL.get());
        place(h, RitualCircle.mediumRing(center), glyphCount);
    }

    private static void floor(GameTestHelper h, BlockPos center, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                h.setBlock(center.offset(dx, -1, dz), Blocks.STONE);
            }
        }
    }

    private static void place(GameTestHelper h, List<BlockPos> ring, int glyphCount) {
        int placed = 0;
        for (BlockPos p : ring) {
            if (placed >= glyphCount) {
                break;
            }
            h.setBlock(p, HexereiBlocks.RUNE.get());
            placed++;
        }
    }

    private static ItemEntity dropSacrifice(GameTestHelper h, BlockPos center, Item item) {
        BlockPos abs = h.absolutePos(center);
        ItemEntity ie = new ItemEntity(h.getLevel(), abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 0.5,
                new ItemStack(item));
        ie.setDeltaMovement(Vec3.ZERO);
        h.getLevel().addFreshEntity(ie);
        return ie;
    }

    private static RitualGameTests.FakeAltar poweredAltar(GameTestHelper h, BlockPos center, float power) {
        RitualGameTests.FakeAltar fake = new RitualGameTests.FakeAltar(h.getLevel(), h.absolutePos(center), power);
        AltarPowerManager.get(h.getLevel()).register(fake);
        return fake;
    }

    // ---- tests ----

    /**
     * Verdant's effect: bonemeals a nearby crop and taints the chunk. Calls perform() directly to isolate the
     * rite's deterministic effect from sacrifice-item physics and the shared per-level AltarPowerManager (the
     * full sacrifice->power->consume activation path is covered by the Tempest/Manifest/Bound-Beast tests).
     */
    @GameTest(template = "empty", batch = "ritual", timeoutTicks = 100)
    public void verdantGrowsCrops(GameTestHelper h) {
        BlockPos center = new BlockPos(18, 2, 18);
        floor(h, center, 3); // stone (non-bonemealable) under the whole sweep
        h.setBlock(center.offset(1, -1, 1), Blocks.FARMLAND);
        h.setBlock(center.offset(1, 0, 1), Blocks.WHEAT); // age 0 — the only bonemealable block in the sweep
        h.runAfterDelay(2, () -> {
            BlockPos abs = h.absolutePos(center);
            new VerdantRite().perform(h.getLevel(), abs);
            BlockState wheat = h.getLevel().getBlockState(h.absolutePos(center.offset(1, 0, 1)));
            int age = wheat.hasProperty(CropBlock.AGE) ? wheat.getValue(CropBlock.AGE) : -1;
            if (age <= 0) {
                h.fail("verdant did not grow the wheat (age=" + age + ")");
            } else if (com.vel5id.hexerei.soul.Disturbance.total(h.getLevel(), new ChunkPos(abs)) <= 0f) {
                h.fail("verdant did not taint the chunk");
            } else {
                h.succeed();
            }
        });
    }

    /** Manifest drops 8 ritual chalk at the circle, consumes the sacrifice, and debits 40 power. */
    @GameTest(template = "empty", batch = "ritual", timeoutTicks = 100)
    public void manifestSpawnsChalk(GameTestHelper h) {
        BlockPos center = new BlockPos(18, 2, 18);
        buildSmall(h, center, 12);
        dropSacrifice(h, center, HexereiItems.WORMWOOD.get());
        RitualGameTests.FakeAltar fake = poweredAltar(h, center, 1000f);
        h.runAfterDelay(3, () -> {
            RitualActivation.Result r = RitualActivation.tryPerform(h.getLevel(), h.absolutePos(center));
            AltarPowerManager.get(h.getLevel()).unregister(fake);
            AABB box = new AABB(h.absolutePos(center)).inflate(2);
            List<ItemEntity> chalk = h.getLevel().getEntitiesOfClass(ItemEntity.class, box,
                    e -> e.isAlive() && e.getItem().is(HexereiItems.RITUAL_CHALK.get()));
            if (r != RitualActivation.Result.SUCCESS) {
                h.fail("manifest did not succeed: " + r);
            } else if (chalk.isEmpty()) {
                h.fail("manifest did not spawn ritual chalk");
            } else if (chalk.get(0).getItem().getCount() != 8) {
                h.fail("expected a stack of 8 chalk; got " + chalk.get(0).getItem().getCount());
            } else if (Math.abs(fake.getCurrentPower() - 960f) > 0.01f) {
                h.fail("expected 40 power consumed (1000->960); got " + fake.getCurrentPower());
            } else {
                h.succeed();
            }
        });
    }

    /** Bound Beast summons a persistent wolf and debits 120 power. */
    @GameTest(template = "empty", batch = "ritual", timeoutTicks = 100)
    public void boundBeastSummonsWolf(GameTestHelper h) {
        BlockPos center = new BlockPos(18, 2, 18);
        buildSmall(h, center, 12);
        dropSacrifice(h, center, HexereiItems.WOLFSBANE.get());
        RitualGameTests.FakeAltar fake = poweredAltar(h, center, 1000f);
        h.runAfterDelay(3, () -> {
            RitualActivation.Result r = RitualActivation.tryPerform(h.getLevel(), h.absolutePos(center));
            AltarPowerManager.get(h.getLevel()).unregister(fake);
            List<Wolf> wolves = h.getLevel().getEntitiesOfClass(Wolf.class,
                    new AABB(h.absolutePos(center)).inflate(3));
            if (r != RitualActivation.Result.SUCCESS) {
                h.fail("bound beast did not succeed: " + r);
            } else if (wolves.isEmpty()) {
                h.fail("bound beast did not summon a wolf");
            } else if (!wolves.get(0).isPersistenceRequired()) {
                h.fail("summoned wolf must be spawn-persistent");
            } else if (Math.abs(fake.getCurrentPower() - 880f) > 0.01f) {
                h.fail("expected 120 power consumed (1000->880); got " + fake.getCurrentPower());
            } else {
                h.succeed();
            }
        });
    }

    /** Waning Moon (MEDIUM circle) sets time to midnight and weakens a nearby hostile. required=false: large footprint. */
    @GameTest(template = "empty", batch = "ritual", timeoutTicks = 100, required = false)
    public void waningMoonCursesAndSetsNight(GameTestHelper h) {
        BlockPos center = new BlockPos(18, 3, 18);
        buildMedium(h, center, 20);
        dropSacrifice(h, center, HexereiItems.BELLADONNA_FLOWER.get());
        RitualGameTests.FakeAltar fake = poweredAltar(h, center, 1000f);
        Zombie zombie = h.spawn(EntityType.ZOMBIE, center.offset(2, 0, 0)); // within radius 10
        h.runAfterDelay(3, () -> {
            h.getLevel().setDayTime(6000L); // noon, before the rite drags it to midnight
            RitualActivation.Result r = RitualActivation.tryPerform(h.getLevel(), h.absolutePos(center));
            AltarPowerManager.get(h.getLevel()).unregister(fake);
            long tod = h.getLevel().getDayTime() % 24000L;
            if (r != RitualActivation.Result.SUCCESS) {
                h.fail("waning moon did not succeed: " + r);
            } else if (tod != 18000L) {
                h.fail("expected midnight (18000); got " + tod);
            } else if (zombie.getEffect(MobEffects.WEAKNESS) == null) {
                h.fail("waning moon did not weaken the nearby zombie");
            } else {
                h.succeed();
            }
        });
    }

    /** An incomplete MEDIUM circle does not match — sacrifice and power untouched. required=false: large footprint. */
    @GameTest(template = "empty", batch = "ritual", timeoutTicks = 100, required = false)
    public void incompleteMediumCircleFails(GameTestHelper h) {
        BlockPos center = new BlockPos(18, 3, 18);
        buildMedium(h, center, 19); // one glyph short
        ItemEntity sac = dropSacrifice(h, center, HexereiItems.BELLADONNA_FLOWER.get());
        RitualGameTests.FakeAltar fake = poweredAltar(h, center, 1000f);
        h.runAfterDelay(3, () -> {
            RitualActivation.Result r = RitualActivation.tryPerform(h.getLevel(), h.absolutePos(center));
            AltarPowerManager.get(h.getLevel()).unregister(fake);
            if (r != RitualActivation.Result.NO_RECIPE) {
                h.fail("incomplete medium circle should not match: " + r);
            } else if (!sac.isAlive() || sac.getItem().isEmpty()) {
                h.fail("sacrifice should survive a failed match");
            } else if (Math.abs(fake.getCurrentPower() - 1000f) > 0.01f) {
                h.fail("power should be untouched on a failed match");
            } else {
                h.succeed();
            }
        });
    }

    /** The sealing rite (celandine sacrifice) spawns a forest-domain sealed amulet and debits 80 power. */
    @GameTest(template = "empty", batch = "ritual", timeoutTicks = 100)
    public void sealAmuletSpawnsForestAmulet(GameTestHelper h) {
        BlockPos center = new BlockPos(18, 2, 18);
        buildSmall(h, center, 12);
        dropSacrifice(h, center, HexereiItems.CELANDINE.get());
        RitualGameTests.FakeAltar fake = poweredAltar(h, center, 1000f);
        h.runAfterDelay(3, () -> {
            RitualActivation.Result r = RitualActivation.tryPerform(h.getLevel(), h.absolutePos(center));
            AltarPowerManager.get(h.getLevel()).unregister(fake);
            AABB box = new AABB(h.absolutePos(center)).inflate(2);
            List<ItemEntity> amulets = h.getLevel().getEntitiesOfClass(ItemEntity.class, box,
                    e -> e.isAlive() && e.getItem().is(HexereiItems.AMULET.get()));
            if (r != RitualActivation.Result.SUCCESS) {
                h.fail("sealing rite did not succeed: " + r);
            } else if (amulets.isEmpty()) {
                h.fail("sealing rite did not spawn an amulet");
            } else {
                Bond bond = AmuletItem.readBond(amulets.get(0).getItem());
                if (bond == null) {
                    h.fail("spawned amulet carries no bond");
                } else if (bond.domain() != Correspondence.FOREST) {
                    h.fail("expected FOREST domain; got " + bond.domain());
                } else if (bond.seal() == null || bond.seal().integrity() < 0.999f) {
                    h.fail("spawned amulet should have a full seal");
                } else if (Math.abs(fake.getCurrentPower() - 920f) > 0.01f) {
                    h.fail("expected 80 power consumed (1000->920); got " + fake.getCurrentPower());
                } else {
                    h.succeed();
                }
            }
        });
    }
}
