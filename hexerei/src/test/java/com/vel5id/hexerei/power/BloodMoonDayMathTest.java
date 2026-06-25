package com.vel5id.hexerei.power;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BloodMoonDayMathTest {

    @Test void nightStartFromDaytimeGoesToTonight() {
        assertEquals(13000L, BloodMoonData.nightStart(1000L));
        assertEquals(13000L, BloodMoonData.nightStart(13000L)); // already at nightfall
    }

    @Test void nightStartPastNightRollsToNextNight() {
        assertEquals(37000L, BloodMoonData.nightStart(14000L)); // never moves backward
        assertEquals(37000L, BloodMoonData.nightStart(20000L));
    }

    @Test void dayOf() {
        assertEquals(0L, BloodMoonData.dayOf(0L));
        assertEquals(0L, BloodMoonData.dayOf(23999L));
        assertEquals(1L, BloodMoonData.dayOf(24000L));
        assertEquals(2L, BloodMoonData.dayOf(50000L));
    }

    @Test void endedByAtDawnBoundary() {
        // a blood moon ending on day 5 is NOT over while still on day 5, and IS over once day 6 begins
        assertFalse(BloodMoonData.endedBy(5 * 24000L + 15000L, 5L));
        assertTrue(BloodMoonData.endedBy(6 * 24000L, 5L));
        assertTrue(BloodMoonData.endedBy(6 * 24000L + 1000L, 5L));
    }
}
