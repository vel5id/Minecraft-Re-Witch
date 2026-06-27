package com.vel5id.hexerei.soul;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gate for the Sleep-Brew entry core ({@link DreamOnset} + {@link DreamOutcome}) — the pure math of
 * "drink the sleep brew → cross the threshold": pay {@code SCRY_COST} essence, or fail to cross; a
 * nightmare reading carries a dread-scaled waking penalty (the caller turns it into a fear Act).
 * The immutable delegation contract; the implementation is written to satisfy it.
 */
class DreamOnsetTest {

    private static final float EPS = 1e-5f;

    private static final DreamReading CALM = new DreamReading(1.0f, 0.0f, false, null);
    private static final DreamReading CURDLED = new DreamReading(0.2f, 0.62f, true, Correspondence.DEATH);
    private static final DreamReading HIGH_DREAD_CLEAR = new DreamReading(0.65f, 0.51f, false, Correspondence.DEATH);

    @Test void tooLittleEssence_doesNotCross_andSpendsNothing() {
        DreamOutcome o = DreamOnset.onDrink(DreamOnset.SCRY_COST - 0.5f, CALM);
        assertFalse(o.entered());
        assertEquals(0.0f, o.essenceSpent(), EPS);
        assertFalse(o.nightmare());
        assertEquals(0.0f, o.dreadPenalty(), EPS);
    }

    @Test void exactlyEnoughEssence_crossesCalmly_noPenalty() {
        DreamOutcome o = DreamOnset.onDrink(DreamOnset.SCRY_COST, CALM);
        assertTrue(o.entered());
        assertEquals(DreamOnset.SCRY_COST, o.essenceSpent(), EPS);
        assertFalse(o.nightmare());
        assertEquals(0.0f, o.dreadPenalty(), EPS);
    }

    @Test void nightmareReading_crossesAndCarriesDreadPenalty() {
        DreamOutcome o = DreamOnset.onDrink(5.0f, CURDLED);
        assertTrue(o.entered());
        assertEquals(DreamOnset.SCRY_COST, o.essenceSpent(), EPS);
        assertTrue(o.nightmare());
        assertEquals(0.62f, o.dreadPenalty(), EPS); // == reading.dread()
    }

    @Test void highDreadButNotNightmare_crossesWithNoPenalty() {
        DreamOutcome o = DreamOnset.onDrink(5.0f, HIGH_DREAD_CLEAR);
        assertTrue(o.entered());
        assertEquals(DreamOnset.SCRY_COST, o.essenceSpent(), EPS);
        assertFalse(o.nightmare());          // penalty is gated on nightmare, not raw dread
        assertEquals(0.0f, o.dreadPenalty(), EPS);
    }
}
