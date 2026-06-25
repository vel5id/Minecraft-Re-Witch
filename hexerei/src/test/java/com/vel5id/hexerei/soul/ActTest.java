package com.vel5id.hexerei.soul;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ActTest {

    private static Act take(Correspondence d, float mag) {
        return new Act(Map.of(d, 1.0f), -1f, 0f, 0f, mag);
    }

    @Test void domainWeight_missingDomainIsZero() {
        Act a = new Act(Map.of(Correspondence.FOREST, 0.8f), 0f, 0f, 0f, 1f);
        assertEquals(0.8f, a.domainWeight(Correspondence.FOREST), 1e-6);
        assertEquals(0f, a.domainWeight(Correspondence.DEATH), 1e-6);
    }

    @Test void sum_mixesDomainsAdditively() {
        Act a = new Act(Map.of(Correspondence.FOREST, 0.8f, Correspondence.DEATH, 0.2f), 0f, 0f, 0f, 1f);
        Act b = new Act(Map.of(Correspondence.FOREST, 0.5f), 0f, 0f, 0f, 1f);
        Act s = Act.sum(List.of(a, b));
        assertEquals(1.3f, s.domainWeight(Correspondence.FOREST), 1e-5);
        assertEquals(0.2f, s.domainWeight(Correspondence.DEATH), 1e-5);
    }

    @Test void sum_magnitudeAdds_polarityIsMagnitudeWeightedMean() {
        Act a = take(Correspondence.FOREST, 2f);      // reciprocity -1, mag 2
        Act b = new Act(Map.of(Correspondence.DEATH, 1.0f), +1f, 0f, 0f, 1f); // +1, mag 1
        Act s = Act.sum(List.of(a, b));
        assertEquals(3f, s.magnitude(), 1e-6);
        // (-1*2 + 1*1) / 3 = -1/3
        assertEquals(-1f / 3f, s.reciprocity(), 1e-5);
    }

    @Test void sum_empty_isNullAct() {
        Act s = Act.sum(List.of());
        assertEquals(0f, s.magnitude(), 1e-6);
        assertTrue(s.domain().isEmpty());
        assertEquals(0f, s.reciprocity(), 1e-6);
    }

    @Test void interactionStrength_clampsToOne() {
        Act a = new Act(Map.of(Correspondence.FOREST, 1f), -1f, -1f, 0.5f, 1f); // 1+1+0.5 = 2.5
        assertEquals(1.0f, a.interactionStrength(), 1e-6);
        Act calm = new Act(Map.of(Correspondence.FOREST, 1f), 0f, 0f, 0f, 1f);
        assertEquals(0f, calm.interactionStrength(), 1e-6);
    }

    @Test void domainMapIsDefensivelyImmutable() {
        Act a = new Act(Map.of(Correspondence.FOREST, 1f), 0f, 0f, 0f, 1f);
        assertThrows(UnsupportedOperationException.class, () -> a.domain().put(Correspondence.DEATH, 1f));
    }
}
