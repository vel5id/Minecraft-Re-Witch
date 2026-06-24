package com.vel5id.hexerei.ritual;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WaningMoonRiteTest {

    @Test void midnightOf_fromMorningGoesToTonight() {
        assertEquals(18000L, WaningMoonRite.midnightOf(1000L));
    }

    @Test void midnightOf_atMidnightStays() {
        assertEquals(18000L, WaningMoonRite.midnightOf(18000L));
    }

    @Test void midnightOf_pastMidnightRollsToNextNight() {
        assertEquals(42000L, WaningMoonRite.midnightOf(20000L)); // never moves time backward
    }

    @Test void midnightOf_acrossManyDaysStaysOnMidnight() {
        assertEquals(42000L, WaningMoonRite.midnightOf(42000L)); // already a later midnight
        assertEquals(0L + 18000L + 24000L * 2, WaningMoonRite.midnightOf(50000L));
    }

    @Test void inRange_trueWithinRadiusFalseBeyond() {
        Vec3 c = new Vec3(0, 0, 0);
        assertTrue(WaningMoonRite.inRange(c, new Vec3(WaningMoonRite.AURA_RADIUS, 0, 0)));   // exactly on the edge
        assertTrue(WaningMoonRite.inRange(c, new Vec3(5, 0, 0)));
        assertFalse(WaningMoonRite.inRange(c, new Vec3(WaningMoonRite.AURA_RADIUS + 0.01, 0, 0)));
    }
}
