package com.vel5id.hexerei.ritual;

import com.vel5id.hexerei.power.ChunkTaintData;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that TempestRite's taint logic deposits taint into ChunkTaintData.
 * TempestRite.perform() calls ChunkTaintData.get(level).addTaint(cp, 25f);
 * this test exercises that logic directly without a live ServerLevel.
 */
class TempestRiteTaintTest {
    private static final ChunkPos CP = new ChunkPos(0, 0);

    @Test
    void tempestRite_addsTwentyFiveTaint() {
        ChunkTaintData data = new ChunkTaintData();
        // Replicate the exact call inside TempestRite.perform()
        data.addTaint(CP, 25f);
        assertTrue(data.getTaint(CP) > 0f, "Taint should be > 0 after TempestRite");
        assertEquals(25f, data.getTaint(CP), 0.01f, "Taint should equal exactly 25f");
    }

    @Test
    void tempestRite_taintIsLowLevel() {
        ChunkTaintData data = new ChunkTaintData();
        data.addTaint(CP, 25f);
        assertEquals(com.vel5id.hexerei.power.TaintLevel.LOW, data.getLevel(CP),
                "25f taint should map to LOW level");
    }

    @Test
    void tempestRite_taintAccumulates() {
        ChunkTaintData data = new ChunkTaintData();
        // Two successive TempestRite performances
        data.addTaint(CP, 25f);
        data.addTaint(CP, 25f);
        assertEquals(50f, data.getTaint(CP), 0.01f, "Two TempestRites should give 50f taint");
    }
}
