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
