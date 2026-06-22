package com.vel5id.hexerei.test;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.power.AltarPowerManager;
import com.vel5id.hexerei.power.ChunkTaintData;
import com.vel5id.hexerei.power.IPowerSource;
import com.vel5id.hexerei.registry.HexereiBlocks;
import com.vel5id.hexerei.ritual.WorldTaintAura;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import javax.annotation.Nullable;

/**
 * GameTests for {@link WorldTaintAura#pulse(ServerLevel)}.
 *
 * <p>Strategy: place a grass block and a stone block near the arena centre, register
 * a lightweight {@link IPowerSource} stub at that same position, inject the desired
 * taint level into {@link ChunkTaintData}, then call {@code pulse()} directly
 * (bypassing the 200-tick timer) and assert block states.</p>
 */
@GameTestHolder(HexereiMod.MODID)
@PrefixGameTestTemplate(false)
public class WorldTaintAuraGameTests {

    // ---- minimal IPowerSource stub ------------------------------------------------

    /** A no-op power source backed by a fixed BlockPos. */
    private record FixedPowerSource(Level world, BlockPos pos) implements IPowerSource {
        @Override public Level getWorld()                        { return world; }
        @Override public BlockPos getLocation()                  { return pos; }
        @Override public boolean isLocationEqual(BlockPos p)     { return pos.equals(p); }
        @Override public boolean consumePower(float r)           { return false; }
        @Override public float getCurrentPower()                 { return 100f; }
        @Override public float getRange()                        { return 32f; }
        @Override public int getEnhancementLevel()               { return 0; }
        @Override public boolean isPowerInvalid()                { return false; }
    }

    // ---- helpers ------------------------------------------------------------------

    /**
     * Scans the 11×3×11 cube (radius 5, Y ±1) around {@code centre} (absolute)
     * for any block matching {@code target}.
     */
    private static boolean anyBlockInRadius(GameTestHelper h, BlockPos centre,
                                             net.minecraft.world.level.block.Block target) {
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    // h.getBlockState uses relative coords
                    BlockPos rel = centre.offset(dx, dy, dz);
                    if (h.getBlockState(rel).is(target)) return true;
                }
            }
        }
        return false;
    }

    // ---- tests --------------------------------------------------------------------

    /**
     * LOW taint: pulse must mutate at least one GRASS_BLOCK → tainted_ground.
     * STONE in the same area must NOT be mutated to charred_stone (HIGH only).
     *
     * <p>To make the grass mutation deterministic despite the 15 % probability we
     * place 7 grass blocks (one more than the max-6 cap) and run the pulse 8 times.
     * P(zero mutations after 8 × 7 independent 15 % chances) ≈ 0.85^56 &lt; 10⁻⁴,
     * so the test is overwhelmingly likely to pass.</p>
     */
    @GameTest(template = "empty", batch = "taint_aura", timeoutTicks = 100)
    public void lowTaint_grassMutates_stoneDoesNot(GameTestHelper h) {
        ServerLevel sl = h.getLevel();

        // Centre of the arena at a safe Y
        BlockPos centre = new BlockPos(18, 2, 18);

        // Place 7 grass blocks around centre (dy=0, within radius 5)
        for (int i = 0; i < 7; i++) {
            h.setBlock(centre.offset(i + 1, 0, 0), Blocks.GRASS_BLOCK);
        }
        // Place 3 stone blocks
        for (int i = 0; i < 3; i++) {
            h.setBlock(centre.offset(0, 0, i + 1), Blocks.STONE);
        }

        // Register stub power source and inject LOW taint
        BlockPos absCenter = h.absolutePos(centre);
        AltarPowerManager.get(sl).register(new FixedPowerSource(sl, absCenter));
        ChunkTaintData.get(sl).addTaint(new ChunkPos(absCenter), 20f); // → LOW (15–39)

        // Run pulse many times to overcome probabilistic mutations
        for (int pulse = 0; pulse < 8; pulse++) {
            WorldTaintAura.pulse(sl);
        }

        // Assert tainted_ground appeared somewhere near centre (relative coords)
        boolean foundTainted = anyBlockInRadius(h, centre, HexereiBlocks.TAINTED_GROUND.get());
        // Assert charred_stone did NOT appear (requires HIGH)
        boolean foundCharred = anyBlockInRadius(h, centre, HexereiBlocks.CHARRED_STONE.get());

        if (!foundTainted) {
            h.fail("LOW taint: expected at least one tainted_ground near altar after 8 pulses, found none");
        } else if (foundCharred) {
            h.fail("LOW taint: charred_stone must not appear (requires HIGH taint)");
        } else {
            h.succeed();
        }
    }

    /**
     * HIGH taint: pulse must mutate STONE → charred_stone in addition to grass.
     *
     * <p>5 % probability per stone; we place 5 stone blocks and run 20 pulses.
     * P(zero after 100 independent 5 % chances) = 0.95^100 ≈ 0.006.</p>
     */
    @GameTest(template = "empty", batch = "taint_aura", timeoutTicks = 100)
    public void highTaint_charredStoneMutates(GameTestHelper h) {
        ServerLevel sl = h.getLevel();

        BlockPos centre = new BlockPos(18, 2, 18);

        // Place 5 stone and 5 grass blocks
        for (int i = 0; i < 5; i++) {
            h.setBlock(centre.offset(i + 1, 0, 0), Blocks.STONE);
            h.setBlock(centre.offset(-(i + 1), 0, 0), Blocks.GRASS_BLOCK);
        }

        BlockPos absCenter = h.absolutePos(centre);
        AltarPowerManager.get(sl).register(new FixedPowerSource(sl, absCenter));
        ChunkTaintData.get(sl).addTaint(new ChunkPos(absCenter), 75f); // → HIGH (≥70)

        for (int pulse = 0; pulse < 20; pulse++) {
            WorldTaintAura.pulse(sl);
        }

        boolean foundCharred = anyBlockInRadius(h, centre, HexereiBlocks.CHARRED_STONE.get());
        boolean foundTainted = anyBlockInRadius(h, centre, HexereiBlocks.TAINTED_GROUND.get());

        if (!foundCharred) {
            h.fail("HIGH taint: expected charred_stone after 20 pulses, found none");
        } else if (!foundTainted) {
            h.fail("HIGH taint: expected tainted_ground after 20 pulses, found none");
        } else {
            h.succeed();
        }
    }

    /**
     * NONE taint: pulse must NOT mutate any block regardless of block type.
     */
    @GameTest(template = "empty", batch = "taint_aura", timeoutTicks = 100)
    public void noneTaint_noMutations(GameTestHelper h) {
        ServerLevel sl = h.getLevel();

        BlockPos centre = new BlockPos(18, 2, 18);

        // Place targets
        for (int i = 0; i < 5; i++) {
            h.setBlock(centre.offset(i + 1, 0, 0), Blocks.GRASS_BLOCK);
            h.setBlock(centre.offset(-(i + 1), 0, 0), Blocks.STONE);
        }

        BlockPos absCenter = h.absolutePos(centre);
        AltarPowerManager.get(sl).register(new FixedPowerSource(sl, absCenter));
        // Deliberately add NO taint — level stays NONE

        for (int pulse = 0; pulse < 10; pulse++) {
            WorldTaintAura.pulse(sl);
        }

        boolean foundTainted = anyBlockInRadius(h, centre, HexereiBlocks.TAINTED_GROUND.get());
        boolean foundCharred = anyBlockInRadius(h, centre, HexereiBlocks.CHARRED_STONE.get());

        if (foundTainted || foundCharred) {
            h.fail("NONE taint: unexpected mutations (tainted=" + foundTainted + " charred=" + foundCharred + ")");
        } else {
            h.succeed();
        }
    }
}
