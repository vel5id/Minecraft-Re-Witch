package com.vel5id.hexerei.test;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.power.AltarPowerManager;
import com.vel5id.hexerei.power.IPowerSource;
import com.vel5id.hexerei.registry.HexereiBlocks;
import com.vel5id.hexerei.registry.HexereiItems;
import com.vel5id.hexerei.ritual.TempestRite;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Integration test: verifies TempestRite.perform() deposits taint via ChunkTaintData.
 * Tests call perform() directly so that any removal of the addTaint call will be caught.
 */
@GameTestHolder(HexereiMod.MODID)
@PrefixGameTestTemplate(false)
public class TempestRiteTaintGameTest {

    private static final class FakeAltar implements IPowerSource {
        private final Level level;
        private final BlockPos pos;
        private float power;

        FakeAltar(Level level, BlockPos pos, float power) {
            this.level = level;
            this.pos = pos;
            this.power = power;
        }

        @Override public Level getWorld()                        { return level; }
        @Override public BlockPos getLocation()                  { return pos; }
        @Override public boolean isLocationEqual(BlockPos p)     { return pos.equals(p); }
        @Override public boolean consumePower(float r) {
            if (power >= r) { power -= r; return true; }
            return false;
        }
        @Override public float getCurrentPower()                 { return power; }
        @Override public float getRange()                        { return 32f; }
        @Override public int getEnhancementLevel()               { return 0; }
        @Override public boolean isPowerInvalid()                { return false; }
    }

    /**
     * Calls TempestRite.perform() and asserts that ChunkTaintData records taint > 0
     * for the chunk containing the ritual center.
     */
    @GameTest(template = "empty", batch = "tempest_taint", timeoutTicks = 100)
    public void perform_addsTaint(GameTestHelper h) {
        ServerLevel sl = h.getLevel();
        BlockPos centre = new BlockPos(18, 2, 18);
        BlockPos absCentre = h.absolutePos(centre);

        // Stone floor so charred-block logic has something to work with
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                h.setBlock(centre.offset(dx, -1, dz), Blocks.STONE);
            }
        }

        // Register power source so RitualActivation can deduct power if needed
        FakeAltar altar = new FakeAltar(sl, absCentre, 1000f);
        AltarPowerManager.get(sl).register(altar);

        h.runAfterDelay(3, () -> {
            // Ensure no prior taint
            ChunkPos cp = new ChunkPos(absCentre);
            float before = com.vel5id.hexerei.soul.Disturbance.total(sl, cp);

            new TempestRite(1200).perform(sl, absCentre);

            float after = com.vel5id.hexerei.soul.Disturbance.total(sl, cp);
            AltarPowerManager.get(sl).unregister(altar);

            if (after <= before) {
                h.fail("TempestRite.perform() did not increase chunk taint (before=" + before + " after=" + after + ")");
            } else {
                h.succeed();
            }
        });
    }

    /**
     * Calling TempestRite.perform() twice accumulates taint.
     */
    @GameTest(template = "empty", batch = "tempest_taint", timeoutTicks = 100)
    public void perform_taintAccumulates(GameTestHelper h) {
        ServerLevel sl = h.getLevel();
        BlockPos centre = new BlockPos(18, 2, 18);
        BlockPos absCentre = h.absolutePos(centre);

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                h.setBlock(centre.offset(dx, -1, dz), Blocks.STONE);
            }
        }

        h.runAfterDelay(3, () -> {
            ChunkPos cp = new ChunkPos(absCentre);
            TempestRite rite = new TempestRite(1200);

            rite.perform(sl, absCentre);
            float after1 = com.vel5id.hexerei.soul.Disturbance.total(sl, cp);

            rite.perform(sl, absCentre);
            float after2 = com.vel5id.hexerei.soul.Disturbance.total(sl, cp);

            if (after2 <= after1) {
                h.fail("Second TempestRite.perform() did not further increase taint (after1=" + after1 + " after2=" + after2 + ")");
            } else {
                h.succeed();
            }
        });
    }
}
