package com.vel5id.hexerei.ritual;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HungeringAltarTest {

    @Test void noDisturbance_noPenalty() {
        assertEquals(0, HungeringAltar.penaltyAmplifier(0f));
        assertEquals(0, HungeringAltar.penaltyAmplifier(HungeringAltar.DISTURB_PER_AMP - 0.01f));
    }

    @Test void penaltyGrowsWithDisturbance() {
        assertEquals(1, HungeringAltar.penaltyAmplifier(HungeringAltar.DISTURB_PER_AMP));
        assertEquals(2, HungeringAltar.penaltyAmplifier(HungeringAltar.DISTURB_PER_AMP * 2));
    }

    @Test void penaltyIsCapped() {
        assertEquals(HungeringAltar.MAX_AMP,
                HungeringAltar.penaltyAmplifier(HungeringAltar.DISTURB_PER_AMP * 100));
    }
}
