package com.vel5id.hexerei.soul;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EssenceSourceTest {

    @Test void takeFreesEssence_proportionalToMagnitude() {
        Act fullTake = new Act(java.util.Map.of(Correspondence.FOREST, 1f), -1f, 0f, 0f, 2f);
        assertEquals(EssenceSource.ESSENCE_PER_MAGNITUDE * 2f, EssenceSource.essenceFrom(fullTake), 1e-4);
    }

    @Test void offeringFreesNoEssence() {
        Act offering = new Act(java.util.Map.of(Correspondence.FOREST, 1f), +1f, 0f, 0f, 2f);
        assertEquals(0f, EssenceSource.essenceFrom(offering), 1e-6);
    }

    @Test void partialTakeScalesByReciprocity() {
        Act halfTake = new Act(java.util.Map.of(Correspondence.STONE, 1f), -0.5f, 0f, 0f, 2f);
        assertEquals(EssenceSource.ESSENCE_PER_MAGNITUDE * 2f * 0.5f,
                EssenceSource.essenceFrom(halfTake), 1e-4);
    }

    @Test void breakRelease_isAFullTakeOfTheDomain() {
        Act a = EssenceSource.breakRelease(Correspondence.FOREST, 1.5f, 0.2f);
        assertEquals(-1f, a.reciprocity(), 1e-6);
        assertEquals(1.0f, a.domainWeight(Correspondence.FOREST), 1e-6);
        assertEquals(0.2f, a.defilement(), 1e-6);
        assertEquals(1.5f, a.magnitude(), 1e-6);
        assertEquals(EssenceSource.ESSENCE_PER_MAGNITUDE * 1.5f, EssenceSource.essenceFrom(a), 1e-4);
    }
}
