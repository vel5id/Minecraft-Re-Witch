package com.vel5id.hexerei.soul;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IntegrationTest {

    @Test void taking_createsDebt_noLoyalty() {
        Act take = new Act(Map.of(Correspondence.FOREST, 1f), -1f, 0f, 0f, 2f);
        Disposition d = Integration.deltaFor(take, Correspondence.FOREST);
        assertEquals(2f, d.debt(), 1e-5);     // max(0,1)*2*1
        assertEquals(0f, d.loyalty(), 1e-5);
        assertEquals(0f, d.resentment(), 1e-5);
    }

    @Test void gifting_growsLoyalty_noDebt() {
        Act gift = new Act(Map.of(Correspondence.FOREST, 1f), +1f, 0f, 0f, 2f);
        Disposition d = Integration.deltaFor(gift, Correspondence.FOREST);
        assertEquals(0f, d.debt(), 1e-5);
        assertEquals(2f, d.loyalty(), 1e-5);
    }

    @Test void defilement_growsResentment_scaledByMagnitudeAndWeight() {
        Act defile = new Act(Map.of(Correspondence.FOREST, 1f), 0f, 0f, 0.5f, 2f);
        Disposition d = Integration.deltaFor(defile, Correspondence.FOREST);
        assertEquals(1.0f, d.resentment(), 1e-5);   // 0.5 * 2 * 1
    }

    @Test void partialDomainWeight_scalesTheDelta() {
        Act take = new Act(Map.of(Correspondence.FOREST, 0.5f), -1f, 0f, 0f, 2f);
        Disposition d = Integration.deltaFor(take, Correspondence.FOREST);
        assertEquals(1.0f, d.debt(), 1e-5);   // 1 * 2 * 0.5
    }

    @Test void integrate_addsOntoExistingDisposition() {
        Disposition start = new Disposition(1f, 1f, 0f, 0f);
        Act take = new Act(Map.of(Correspondence.FOREST, 1f), -1f, 0f, 0f, 1f);
        Disposition after = Integration.integrate(start, take, Correspondence.FOREST);
        assertEquals(2f, after.debt(), 1e-5);
    }

    @Test void disturbanceDelta_keyedByDomain() {
        Act take = new Act(Map.of(Correspondence.FOREST, 1f, Correspondence.DEATH, 0.5f), -1f, 0f, 0f, 2f);
        Map<Correspondence, Float> dist = Integration.disturbanceDelta(take);
        // interactionStrength = min(1, 1) = 1 ; *magnitude 2
        assertEquals(2f, dist.get(Correspondence.FOREST), 1e-5);
        assertEquals(1f, dist.get(Correspondence.DEATH), 1e-5);
    }

    @Test void sealAfter_bindingStrengthens_tearWeakens() {
        assertEquals(0.9f, Integration.sealAfter(0.5f, new Act(Map.of(), 0f, 0.4f, 0f, 1f)), 1e-5);
        assertEquals(0.0f, Integration.sealAfter(0.5f, new Act(Map.of(), 0f, -1f, 0f, 1f)), 1e-5);
        assertEquals(1.0f, Integration.sealAfter(0.8f, new Act(Map.of(), 0f, 1f, 0f, 1f)), 1e-5);
    }
}
