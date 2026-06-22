package com.vel5id.hexerei.power;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that TaintLevel.fromValue() thresholds produce the expected levels.
 * Client-side emission counts live in AltarBlockEntity.doClientParticleTick()
 * and cannot be unit-tested here.
 */
class TaintLevelParticleTest {

    @Test
    void none_belowLowThreshold() {
        assertEquals(TaintLevel.NONE, TaintLevel.fromValue(0f));
        assertEquals(TaintLevel.NONE, TaintLevel.fromValue(14.9f));
    }

    @Test
    void low_atAndAboveThreshold() {
        assertEquals(TaintLevel.LOW, TaintLevel.fromValue(15f));
        assertEquals(TaintLevel.LOW, TaintLevel.fromValue(39.9f));
    }

    @Test
    void medium_atAndAboveThreshold() {
        assertEquals(TaintLevel.MEDIUM, TaintLevel.fromValue(40f));
        assertEquals(TaintLevel.MEDIUM, TaintLevel.fromValue(69.9f));
    }

    @Test
    void high_atAndAboveThreshold() {
        assertEquals(TaintLevel.HIGH, TaintLevel.fromValue(70f));
        assertEquals(TaintLevel.HIGH, TaintLevel.fromValue(100f));
    }
}
