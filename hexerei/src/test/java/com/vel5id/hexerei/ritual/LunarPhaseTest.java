package com.vel5id.hexerei.ritual;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LunarPhaseTest {

    @Test void fromIndexMapsEightPhasesInOrder() {
        assertEquals(LunarPhase.FULL, LunarPhase.fromIndex(0));
        assertEquals(LunarPhase.NEW, LunarPhase.fromIndex(4));
        assertEquals(LunarPhase.WAXING_GIBBOUS, LunarPhase.fromIndex(7));
    }

    @Test void fromIndexWrapsAround() {
        assertEquals(LunarPhase.WAXING_GIBBOUS, LunarPhase.fromIndex(-1));
        assertEquals(LunarPhase.FULL, LunarPhase.fromIndex(8));
        assertEquals(LunarPhase.FULL, LunarPhase.fromIndex(16));
        assertEquals(LunarPhase.NEW, LunarPhase.fromIndex(-4));
    }

    @Test void fullAndNewClassification() {
        assertTrue(LunarPhase.FULL.isFull());
        assertFalse(LunarPhase.FULL.isNew());
        assertTrue(LunarPhase.NEW.isNew());
        assertFalse(LunarPhase.LAST_QUARTER.isFull());
        assertFalse(LunarPhase.LAST_QUARTER.isNew());
    }

    @Test void mulTableIsTradeShaped() {
        assertTrue(LunarPhase.FULL.effectMul() > 1f && LunarPhase.FULL.taintMul() < 1f);
        assertTrue(LunarPhase.NEW.effectMul() < 1f && LunarPhase.NEW.taintMul() > 1f);
        assertEquals(1.0f, LunarPhase.LAST_QUARTER.effectMul(), 1e-6);
        assertEquals(1.0f, LunarPhase.FIRST_QUARTER.taintMul(), 1e-6);
    }

    @Test void effectKeyChosenByMul() {
        assertEquals("hexerei.moon.effect.strong", LunarPhase.FULL.effectKey());
        assertEquals("hexerei.moon.effect.weak", LunarPhase.NEW.effectKey());
        assertEquals("hexerei.moon.effect.neutral", LunarPhase.LAST_QUARTER.effectKey());
    }

    @Test void nameKeyLowercased() {
        assertEquals("hexerei.moon.full", LunarPhase.FULL.nameKey());
        assertEquals("hexerei.moon.waning_gibbous", LunarPhase.WANING_GIBBOUS.nameKey());
    }
}
