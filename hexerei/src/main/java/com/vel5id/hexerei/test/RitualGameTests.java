package com.vel5id.hexerei.test;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.power.AltarPowerManager;
import com.vel5id.hexerei.power.IPowerSource;
import com.vel5id.hexerei.registry.HexereiBlocks;
import com.vel5id.hexerei.registry.HexereiItems;
import com.vel5id.hexerei.item.RitualChalkItem;
import com.vel5id.hexerei.ritual.RitualActivation;
import com.vel5id.hexerei.ritual.RitualCircle;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HexereiMod.MODID)
@PrefixGameTestTemplate(false)
public class RitualGameTests {

    private static final int[][] RING = {
            {0, -2}, {1, -2}, {2, -1}, {2, 0}, {2, 1}, {1, 2},
            {0, 2}, {-1, 2}, {-2, 1}, {-2, 0}, {-2, -1}, {-1, -2}
    };

    private static void buildCircle(GameTestHelper h, BlockPos center, int glyphCount) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                h.setBlock(center.offset(dx, -1, dz), Blocks.STONE); // sturdy floor under glyphs
            }
        }
        h.setBlock(center, HexereiBlocks.RITUAL_SIGIL.get());
        int placed = 0;
        for (int[] o : RING) {
            if (placed >= glyphCount) {
                break;
            }
            h.setBlock(center.offset(o[0], 0, o[1]), HexereiBlocks.RUNE.get());
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

    static final class FakeAltar implements IPowerSource {
        private final Level level;
        private final BlockPos pos;
        private float power;

        FakeAltar(Level level, BlockPos pos, float power) {
            this.level = level;
            this.pos = pos;
            this.power = power;
        }

        @Override public Level getWorld() { return level; }
        @Override public BlockPos getLocation() { return pos; }
        @Override public boolean isLocationEqual(BlockPos p) { return pos.equals(p); }
        @Override public boolean consumePower(float r) {
            if (power >= r) { power -= r; return true; }
            return false;
        }
        @Override public float getCurrentPower() { return power; }
        @Override public float getRange() { return 16f; }
        @Override public int getEnhancementLevel() { return 0; }
        @Override public boolean isPowerInvalid() { return false; }
    }

    @GameTest(template = "empty", batch = "ritual", timeoutTicks = 100)
    public void chalkDrawsCircle(GameTestHelper h) {
        BlockPos center = new BlockPos(18, 2, 18);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                h.setBlock(center.offset(dx, -1, dz), Blocks.STONE); // floor, no glyphs
            }
        }
        h.setBlock(center, HexereiBlocks.RITUAL_SIGIL.get());
        h.runAfterDelay(3, () -> {
            BlockPos absCenter = h.absolutePos(center);
            int placed = RitualChalkItem.drawCircleAt(h.getLevel(), absCenter);
            if (placed != 12) {
                h.fail("chalk drew " + placed + " glyphs, expected 12");
            } else if (!RitualCircle.isSmallComplete(
                    p -> h.getLevel().getBlockState(p).is(HexereiBlocks.RUNE.get()), absCenter)) {
                h.fail("circle not complete after chalk draw");
            } else {
                h.succeed();
            }
        });
    }

    @GameTest(template = "empty", batch = "ritual", timeoutTicks = 100)
    public void ritualFiresTempest(GameTestHelper h) {
        BlockPos center = new BlockPos(18, 2, 18);
        buildCircle(h, center, 12);
        ItemEntity sac = dropSacrifice(h, center, HexereiItems.MANDRAKE_ROOT.get());
        FakeAltar fake = new FakeAltar(h.getLevel(), h.absolutePos(center), 1000f);
        AltarPowerManager.get(h.getLevel()).register(fake);
        h.runAfterDelay(3, () -> {
            h.getLevel().setWeatherParameters(20, 0, false, false); // clear, then fire
            RitualActivation.Result r = RitualActivation.tryPerform(h.getLevel(), h.absolutePos(center));
            AltarPowerManager.get(h.getLevel()).unregister(fake);
            if (r != RitualActivation.Result.SUCCESS) {
                h.fail("ritual did not succeed: " + r);
            } else if (!h.getLevel().getLevelData().isThundering()) {
                // raw weather flag (Level.isThundering() reads the interpolated level, which lags ~90 ticks)
                h.fail("tempest did not start a thunderstorm");
            } else if (sac.isAlive() && !sac.getItem().isEmpty()) {
                h.fail("sacrifice was not consumed");
            } else if (Math.abs(fake.power - 900f) > 0.01f) {
                h.fail("expected 100 power consumed (1000->900); got " + fake.power);
            } else {
                h.succeed();
            }
        });
    }

    @GameTest(template = "empty", batch = "ritual", timeoutTicks = 100)
    public void incompleteCircleFails(GameTestHelper h) {
        BlockPos center = new BlockPos(18, 2, 18);
        buildCircle(h, center, 11); // one glyph missing
        ItemEntity sac = dropSacrifice(h, center, HexereiItems.MANDRAKE_ROOT.get());
        FakeAltar fake = new FakeAltar(h.getLevel(), h.absolutePos(center), 1000f);
        AltarPowerManager.get(h.getLevel()).register(fake);
        h.runAfterDelay(3, () -> {
            RitualActivation.Result r = RitualActivation.tryPerform(h.getLevel(), h.absolutePos(center));
            AltarPowerManager.get(h.getLevel()).unregister(fake);
            if (r != RitualActivation.Result.NO_RECIPE) {
                h.fail("incomplete circle should not match: " + r);
            } else if (!sac.isAlive() || sac.getItem().isEmpty()) {
                h.fail("sacrifice should not be consumed on failure");
            } else if (Math.abs(fake.power - 1000f) > 0.01f) {
                h.fail("power should not be consumed on failure");
            } else {
                h.succeed();
            }
        });
    }

    @GameTest(template = "empty", batch = "ritual", timeoutTicks = 100)
    public void noPowerFails(GameTestHelper h) {
        BlockPos center = new BlockPos(18, 2, 18);
        buildCircle(h, center, 12);
        ItemEntity sac = dropSacrifice(h, center, HexereiItems.MANDRAKE_ROOT.get());
        // no power source registered
        h.runAfterDelay(3, () -> {
            RitualActivation.Result r = RitualActivation.tryPerform(h.getLevel(), h.absolutePos(center));
            if (r != RitualActivation.Result.NO_POWER) {
                h.fail("ritual without altar power should report NO_POWER: " + r);
            } else if (!sac.isAlive() || sac.getItem().isEmpty()) {
                h.fail("sacrifice should not be consumed when power is missing");
            } else {
                h.succeed();
            }
        });
    }
}
