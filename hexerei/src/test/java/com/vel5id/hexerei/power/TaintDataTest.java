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
        d.addTaint(P, 10f); // delta=10, floor = 10 * 0.1 = 1.0
        for (int i = 0; i < 30; i++) d.decayTick();
        assertEquals(1.0f, d.getTaint(P), 0.01f);
    }

    @Test void permanentFloor_accumulates() {
        ChunkTaintData d = new ChunkTaintData();
        d.addTaint(P, 10f);  // delta=10, floor += 1.0
        d.addTaint(P, 20f);  // delta=20, floor += 2.0 → total floor = 3.0
        for (int i = 0; i < 100; i++) d.decayTick();
        assertEquals(3.0f, d.getTaint(P), 0.01f);
    }

    /**
     * Regression: floor must use the APPLIED delta, not the raw requested amount.
     * If taint is at 97 and addTaint(25) is called, only 3 units land (clamped to 100),
     * so floor should increase by 0.3 (3 × 0.1), not 2.5 (25 × 0.1).
     * After 10 such near-cap calls, floor must stay well below 10.
     */
    @Test void floorUsesAppliedDelta_notRawAmount() {
        ChunkTaintData d = new ChunkTaintData();
        // Fill to 97 using a single clean add
        d.addTaint(P, 97f); // delta=97, floor=9.7
        // Now spam addTaint(25) ten times — each call only applies 3 units on first call,
        // then 0 after taint is already at 100
        for (int i = 0; i < 10; i++) {
            d.addTaint(P, 25f);
        }
        // floor should be 9.7 + 0.3 (only first of the 10 calls applies any delta) = 10.0
        // It must NOT exceed 10.0 (which would happen if we used raw amount × 0.1 each time)
        float taintValue = d.getTaint(P);
        assertEquals(100f, taintValue, 0.01f);
        // decay all the way down to floor; floor ≤ 10.0 means taint eventually settles ≤ 10.0
        for (int i = 0; i < 300; i++) d.decayTick();
        assertTrue(d.getTaint(P) <= 10.0f + 0.01f,
                "Floor must not exceed 10.0 but was " + d.getTaint(P));
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
