package com.vel5id.hexerei.soul;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RitualResolverTest {

    private static Act forestRite() {
        // a binding rite leaning on FOREST
        return new Act(Map.of(Correspondence.FOREST, 1f), 0f, 0.4f, 0f, 1f);
    }

    @Test void calmDomain_succeeds() {
        RitualResolver.Resolved r = RitualResolver.resolve(forestRite(), 0f, 0f);
        assertSame(Resolution.Outcome.SUCCESS, r.outcome());
        assertTrue(r.quality() >= 0.5f);
    }

    @Test void heavilyDisturbedDomain_doesNotSucceed() {
        // domain disturbance 80/100 → strong resistance to a binding rite
        RitualResolver.Resolved r = RitualResolver.resolve(forestRite(), 80f, 80f);
        assertNotSame(Resolution.Outcome.SUCCESS, r.outcome());
        assertTrue(r.quality() < 0.5f);
    }

    @Test void qualityFallsAsDisturbanceRises() {
        float calm = RitualResolver.resolve(forestRite(), 0f, 0f).quality();
        float muddy = RitualResolver.resolve(forestRite(), 50f, 50f).quality();
        assertTrue(muddy < calm, "a disturbed place should lower ritual quality");
    }

    @Test void dominantDomain_picksStrongestWeight() {
        Act mixed = new Act(Map.of(Correspondence.FOREST, 0.3f, Correspondence.DEATH, 0.9f), 0f, 0f, 0f, 1f);
        assertSame(Correspondence.DEATH, RitualResolver.dominantDomain(mixed));
        assertNull(RitualResolver.dominantDomain(new Act(Map.of(), 0f, 0f, 0f, 0f)));
    }

    @Test void fullRingInflatesMagnitude_butReachIsTheSacrifice_soCalmSucceeds() {
        // A full rune ring (12 runes × 0.3) + a sacrifice (~1.0) sums to magnitude ~4.6, past
        // STANDING_CAPACITY (3). The ring is the ritual's channel, not the witch's overreach — her reach
        // is the sacrifice. Without the reach split, every complete ritual would OVERREACH even when calm.
        Act fullRing = new Act(Map.of(Correspondence.FOREST, 12f), 0f, 0.2f, 0f, 4.6f);
        // Legacy reach (== act.magnitude 4.6) trips OVERREACH:
        assertSame(Resolution.Outcome.OVERREACH, RitualResolver.resolve(fullRing, 0f, 0f).outcome());
        // Reach = the sacrifice's magnitude (1.0): a calm place succeeds (the documented Slice-D intent):
        assertSame(Resolution.Outcome.SUCCESS, RitualResolver.resolve(fullRing, 0f, 0f, 1.0f).outcome());
    }
}
