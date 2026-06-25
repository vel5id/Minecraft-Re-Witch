package com.vel5id.hexerei.soul;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResolutionTest {

    private static Act act(float reciprocity, float binding, float defilement, float magnitude) {
        return new Act(Map.of(Correspondence.FOREST, 1f), reciprocity, binding, defilement, magnitude);
    }

    @Test void standing_lowDebtHighLoyaltyPours() {
        assertEquals(1.0f, Resolution.standing(new Disposition(0f, 0f, 0f, 0f)), 1e-5);
        assertEquals(0.5f, Resolution.standing(new Disposition(1f, 0f, 0f, 0f)), 1e-5); // 1 - 0.5*1
        assertEquals(1.0f, Resolution.standing(new Disposition(0f, 0f, 0f, 1f)), 1e-5); // clamp(1 + 0.5)
    }

    @Test void align_offeringEasedInResentfulDomain_takingResisted() {
        Act offering = act(+1f, 0f, 0f, 1f);
        Act taking = act(-1f, 0f, 0f, 1f);
        assertEquals(1.0f, Resolution.align(offering, 0.8f), 1e-5); // 0.2 + 1*0.8 = 1
        assertEquals(0.2f, Resolution.align(taking, 0.8f), 1e-5);   // base only
    }

    @Test void success_isProductOfTheThreeFactors() {
        Act taking = act(-1f, 0f, 0f, 1f);
        // align(0.2 dom resent 0.8) * standing(debt1 -> 0.5) * place(disturbance 0.2 -> 0.8)
        float s = Resolution.success(taking, 0.8f, new Disposition(1f, 0f, 0f, 0f), 0.2f);
        assertEquals(0.2f * 0.5f * 0.8f, s, 1e-5);
    }

    @Test void classify_success_whenAlignedAndAffordable() {
        Act offering = act(+1f, 0f, 0f, 1f);
        Resolution.Outcome o = Resolution.classify(offering, 0f, new Disposition(0f, 0f, 0f, 1f), 0f);
        assertEquals(Resolution.Outcome.SUCCESS, o);
    }

    @Test void classify_defilement_whenProfaneActBotches() {
        Act defile = act(-1f, 0f, 0.8f, 1f);
        Resolution.Outcome o = Resolution.classify(defile, 0.8f, new Disposition(1f, 0f, 0f, 0f), 0f);
        assertEquals(Resolution.Outcome.DEFILEMENT, o);
    }

    @Test void classify_resistance_whenBindingAngryDomain() {
        Act bind = act(0f, 1f, 0f, 1f);
        Resolution.Outcome o = Resolution.classify(bind, 0.8f, new Disposition(1f, 0f, 0f, 0f), 0f);
        assertEquals(Resolution.Outcome.RESISTANCE, o);
    }

    @Test void classify_overreach_whenMagnitudeBeyondStanding() {
        Act big = act(+1f, 0f, 0f, 10f);   // aligned (success high) but far too large
        Resolution.Outcome o = Resolution.classify(big, 0f, new Disposition(0f, 0f, 0f, 0f), 0f);
        assertEquals(Resolution.Outcome.OVERREACH, o);  // 10 > 3 * standing(1)
    }

    @Test void classify_misalignment_whenLowSuccessButNoSpecificCause() {
        Act take = act(-1f, 0f, 0f, 1f);   // no defile, no binding, small magnitude
        // success = align(domain 0 -> 1) * standing(debt1 ->0.5) * place(disturbance .2 ->0.8) = 0.4 < 0.5
        Resolution.Outcome o = Resolution.classify(take, 0f, new Disposition(1f, 0f, 0f, 0f), 0.2f);
        assertEquals(Resolution.Outcome.MISALIGNMENT, o);
    }
}
