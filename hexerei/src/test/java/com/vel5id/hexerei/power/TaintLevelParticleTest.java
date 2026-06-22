package com.vel5id.hexerei.power;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies that TaintLevel.fromValue() thresholds produce the expected particle counts.
 * Client-side emission is visual and cannot be tested via GameTest; these cover
 * the pure logic helpers on TaintLevel used by the altar client tick.
 */
class TaintLevelParticleTest {

    @Test
    void none_emitsZeroParticles() {
        TaintLevel tl = TaintLevel.fromValue(0f);
        assertEquals(TaintLevel.NONE, tl);
        assertEquals(0, tl.wispCount());
        assertEquals(0, tl.ashCount());
    }

    @Test
    void low_emitsOneWisp() {
        TaintLevel tl = TaintLevel.fromValue(15f);
        assertEquals(TaintLevel.LOW, tl);
        assertEquals(1, tl.wispCount());
        assertEquals(0, tl.ashCount());
    }

    @Test
    void medium_emitsTwoWisps() {
        TaintLevel tl = TaintLevel.fromValue(40f);
        assertEquals(TaintLevel.MEDIUM, tl);
        assertEquals(2, tl.wispCount());
        assertEquals(0, tl.ashCount());
    }

    @Test
    void high_emitsThreeWispsAndOneAsh() {
        TaintLevel tl = TaintLevel.fromValue(70f);
        assertEquals(TaintLevel.HIGH, tl);
        assertEquals(3, tl.wispCount());
        assertEquals(1, tl.ashCount());
    }

    @Test
    void belowLowThreshold_isNone() {
        assertEquals(TaintLevel.NONE, TaintLevel.fromValue(14.9f));
        assertEquals(0, TaintLevel.fromValue(14.9f).wispCount());
    }

    @Test
    void belowMediumThreshold_isLow() {
        assertEquals(TaintLevel.LOW, TaintLevel.fromValue(39.9f));
        assertEquals(1, TaintLevel.fromValue(39.9f).wispCount());
    }

    @Test
    void belowHighThreshold_isMedium() {
        assertEquals(TaintLevel.MEDIUM, TaintLevel.fromValue(69.9f));
        assertEquals(2, TaintLevel.fromValue(69.9f).wispCount());
    }
}
