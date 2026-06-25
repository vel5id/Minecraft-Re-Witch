package com.vel5id.hexerei.soul;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DecayTest {

    @Test void debtNeverDecays() {
        Disposition d = new Disposition(5f, 10f, 0f, 0f);
        for (int i = 0; i < 100; i++) d = Decay.decay(d, Decay.resentmentFloor(5f, 0f), 1f);
        assertEquals(5f, d.debt(), 1e-5);   // debt carried through untouched
    }

    @Test void resentmentDriftsDownByRate() {
        Disposition d = new Disposition(0f, 10f, 0f, 0f);
        d = Decay.decay(d, 0f, 1f);
        assertEquals(9.5f, d.resentment(), 1e-5);   // -RESENTMENT_RATE
    }

    @Test void resentmentFloorsAtDebtPlusMarks() {
        float floor = Decay.resentmentFloor(5f, 0f);  // debt 5, no marks
        Disposition d = new Disposition(5f, 20f, 0f, 0f);
        for (int i = 0; i < 200; i++) d = Decay.decay(d, floor, 1f);
        assertEquals(5f, d.resentment(), 1e-5);   // settles at the floor, not zero
    }

    @Test void marksRaiseTheFloor() {
        float floor = Decay.resentmentFloor(2f, 1.5f);  // debt 2 + marks 1.5
        assertEquals(3.5f, floor, 1e-6);
        Disposition d = new Disposition(2f, 20f, 0f, 0f);
        for (int i = 0; i < 200; i++) d = Decay.decay(d, floor, 1f);
        assertEquals(3.5f, d.resentment(), 1e-5);
    }

    @Test void loyaltyAndFearFadeTowardZero() {
        Disposition d = new Disposition(0f, 0f, 8f, 8f);
        for (int i = 0; i < 100; i++) d = Decay.decay(d, 0f, 1f);
        assertEquals(0f, d.loyalty(), 1e-5);
        assertEquals(0f, d.fear(), 1e-5);
    }
}
