package com.vel5id.hexerei.soul;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gate for the dream-world READ core ({@link DreamResolver} + {@link DreamReading}) — a deterministic
 * read of accumulated soul State (dream = the {@code read} verb; writes nothing). The immutable
 * delegation contract; the implementation is written to satisfy it.
 *
 * <p>Inputs: caller-normalized player terms {@code debtN}/{@code marksN} in [0,1], plus the raw chunk
 * disturbance map (0..{@code DISTURBANCE_FULL}=100).
 */
class DreamResolverTest {

    private static final float EPS = 1e-5f;

    @Test void ambient_isLoudestDomainNormalized() {
        assertEquals(0.0f, DreamResolver.ambient(Map.of()), EPS);
        assertEquals(0.5f, DreamResolver.ambient(Map.of(Correspondence.DEATH, 50f)), EPS);
        assertEquals(0.8f, DreamResolver.ambient(Map.of(Correspondence.DEATH, 50f, Correspondence.SKY, 80f)), EPS);
        assertEquals(0.0f, DreamResolver.ambient(Map.of(Correspondence.STONE, 0f)), EPS);
    }

    @Test void clarity_calmSoulInCalmLandIsClear() {
        assertEquals(1.0f, DreamResolver.clarity(0f, Map.of()), EPS);
        // both murk terms maxed -> mute
        assertEquals(0.0f, DreamResolver.clarity(1f, Map.of(Correspondence.DEATH, 100f)), EPS);
        // 1 - 0.5*0.6 - 0.5*0.2 = 0.6
        assertEquals(0.6f, DreamResolver.clarity(0.6f, Map.of(Correspondence.WATER, 20f)), EPS);
    }

    @Test void dread_risesWithDebtMarksAndLand() {
        assertEquals(0.0f, DreamResolver.dread(0f, 0f, Map.of()), EPS);
        // 0.4 + 0.3 + 0.3 = 1
        assertEquals(1.0f, DreamResolver.dread(1f, 1f, Map.of(Correspondence.DEATH, 100f)), EPS);
        // 0.4*0.6 + 0 + 0.3*0.2 = 0.30
        assertEquals(0.30f, DreamResolver.dread(0.6f, 0f, Map.of(Correspondence.WATER, 20f)), EPS);
    }

    @Test void dominantDomain_argmaxWithDeterministicTieBreakAndNulls() {
        assertEquals(Correspondence.WATER, DreamResolver.dominantDomain(Map.of(Correspondence.WATER, 20f)));
        // tie 60/60: enum order FOREST,STONE,WATER,DEATH,SKY,THRESHOLD -> DEATH (earlier) wins
        assertEquals(Correspondence.DEATH,
                DreamResolver.dominantDomain(Map.of(Correspondence.DEATH, 60f, Correspondence.SKY, 60f)));
        assertEquals(Correspondence.DEATH,
                DreamResolver.dominantDomain(Map.of(Correspondence.FOREST, 10f, Correspondence.DEATH, 90f, Correspondence.SKY, 30f)));
        assertNull(DreamResolver.dominantDomain(Map.of()));
        assertNull(DreamResolver.dominantDomain(Map.of(Correspondence.STONE, 0f))); // all <= 0
    }

    @Test void read_burdenedStateCurdlesIntoNightmare() {
        DreamReading r = DreamResolver.read(1f, 1f, Map.of(Correspondence.DEATH, 100f));
        assertEquals(0.0f, r.clarity(), EPS);
        assertEquals(1.0f, r.dread(), EPS);
        assertTrue(r.nightmare());
        assertEquals(Correspondence.DEATH, r.dominantDomain());
    }

    @Test void read_calmStateIsAClearDreamlessNoNightmare() {
        DreamReading r = DreamResolver.read(0f, 0f, Map.of());
        assertEquals(1.0f, r.clarity(), EPS);
        assertEquals(0.0f, r.dread(), EPS);
        assertFalse(r.nightmare());
        assertNull(r.dominantDomain());
    }

    @Test void nightmare_requiresBothHighDreadAndLowClarity() {
        // high dread but still clear -> NOT a nightmare (clarity gate):
        // debtN=0, marksN=1, ambient=0.7 -> dread=0.3+0.21=0.51 (>=0.5), clarity=1-0.35=0.65 (>=0.4)
        DreamReading clear = DreamResolver.read(0f, 1f, Map.of(Correspondence.DEATH, 70f));
        assertTrue(clear.dread() >= 0.5f);
        assertFalse(clear.nightmare());

        // burdened AND murky -> nightmare: debtN=0.8, marksN=0.2, ambient=0.8
        // clarity=1-0.4-0.4=0.2 (<0.4), dread=0.32+0.06+0.24=0.62 (>=0.5)
        DreamReading curdled = DreamResolver.read(0.8f, 0.2f, Map.of(Correspondence.DEATH, 80f));
        assertEquals(0.2f, curdled.clarity(), EPS);
        assertEquals(0.62f, curdled.dread(), EPS);
        assertTrue(curdled.nightmare());
    }
}
