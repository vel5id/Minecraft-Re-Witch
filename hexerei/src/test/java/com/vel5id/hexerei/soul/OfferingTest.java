package com.vel5id.hexerei.soul;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OfferingTest {

    @Test void firstOffering_fullYield() {
        assertEquals(1f, Offering.yieldMultiplier(0f), 1e-6);
        assertEquals(EssenceSource.ESSENCE_PER_MAGNITUDE * 1f, Offering.essence(1f, 0f), 1e-4);
    }

    @Test void satiationHalvesYield() {
        assertEquals(0.5f, Offering.yieldMultiplier(1f), 1e-6);
        assertEquals(0.25f, Offering.yieldMultiplier(3f), 1e-6);
        assertEquals(EssenceSource.ESSENCE_PER_MAGNITUDE * 0.5f, Offering.essence(1f, 1f), 1e-4);
    }

    @Test void bulkFeedingOneDomain_diminishes() {
        float sat = 0f, total = 0f;
        for (int i = 0; i < 5; i++) {           // offer the same magnitude-1 reagent 5 times
            total += Offering.essence(1f, sat);
            sat = Offering.satiationAfter(sat, 1f);
        }
        // 10 + 5 + 3.33 + 2.5 + 2 = ~22.83, far less than 5 * 10 = 50 at full yield
        assertTrue(total < 25f, "bulk feeding should be sharply diminished, was " + total);
        assertTrue(total > 20f);
    }

    @Test void satiationDecaysToZero() {
        float sat = Offering.satiationAfter(0f, 2f);   // 2.0
        assertEquals(0f, Offering.decay(sat, 5f), 1e-6);
        assertEquals(1f, Offering.decay(sat, 1f), 1e-6); // 2 - 1*1
    }
}
