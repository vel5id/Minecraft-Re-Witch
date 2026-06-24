package com.vel5id.hexerei.item;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CharmChargeTest {

    // ---- nextCharge ----
    @Test void drainsWhenActiveAndNoAltar() {
        assertEquals(4, CharmCharge.nextCharge(5, true, false));
    }

    @Test void holdsWhenInactiveAndNoAltar() {
        assertEquals(5, CharmCharge.nextCharge(5, false, false));
    }

    @Test void rechargesNearAltarAndCapsAtMax() {
        assertEquals(CharmCharge.MAX_CHARGE, CharmCharge.nextCharge(598, true, true)); // 598+5 clamped to 600, not 603
    }

    @Test void neverDrainsBelowZero() {
        assertEquals(0, CharmCharge.nextCharge(0, true, false));
    }

    @Test void rechargeTakesPriorityOverDrainWhenBothApply() {
        // active AND near an altar: should gain (recharge), not lose (drain)
        assertEquals(105, CharmCharge.nextCharge(100, true, true));
    }

    @Test void fullChargeWhileActiveFlickersOneBelowMax() {
        // At the cap the recharge branch (current < MAX) is skipped, so an active charm drains 1;
        // the next tick refills it. Net effect near an altar is a harmless 600<->599 flicker.
        assertEquals(CharmCharge.MAX_CHARGE - 1, CharmCharge.nextCharge(CharmCharge.MAX_CHARGE, true, true));
    }

    @Test void fullChargeWhileInactiveNearAltarHolds() {
        // Inactive at the cap: neither branch fires, charge is stable.
        assertEquals(CharmCharge.MAX_CHARGE, CharmCharge.nextCharge(CharmCharge.MAX_CHARGE, false, true));
    }

    // ---- playerCondition ----
    @Test void alwaysModeIgnoresHealth() {
        assertTrue(CharmCharge.playerCondition(CharmDefs.WARD, 20.0f));
        assertTrue(CharmCharge.playerCondition(CharmDefs.WARD, 1.0f));
    }

    @Test void auraDebuffModeIgnoresHealth() {
        assertTrue(CharmCharge.playerCondition(CharmDefs.HEXBANE, 20.0f));
    }

    @Test void lowHealthTrueAtThresholdFalseAbove() {
        assertTrue(CharmCharge.playerCondition(CharmDefs.BLOODLUST, 6.0f));  // health == param
        assertTrue(CharmCharge.playerCondition(CharmDefs.BLOODLUST, 5.0f));  // below
        assertFalse(CharmCharge.playerCondition(CharmDefs.BLOODLUST, 7.0f)); // above
    }

    // ---- isEffective ----
    @Test void isEffectiveFalseAtZeroChargeEvenWhenWanted() {
        assertFalse(CharmCharge.isEffective(0, true));
    }

    @Test void isEffectiveFalseWhenNotWanted() {
        assertFalse(CharmCharge.isEffective(100, false));
    }

    @Test void isEffectiveTrueWhenChargedAndWanted() {
        assertTrue(CharmCharge.isEffective(1, true));
    }
}
