package com.vel5id.hexerei.soul;

import java.util.Map;

/**
 * Pure-logic engine for the "dream = read" verb: a deterministic read of
 * accumulated soul-state that writes nothing. All inputs are caller-normalized
 * player terms ({@code debtN}, {@code marksN} in [0,1]) and the raw chunk
 * disturbance map (0..{@link #DISTURBANCE_FULL}).
 *
 * <p>This is a stateless function cube; every method is a pure function of its
 * arguments. Use {@link #read} to obtain a complete {@link DreamReading}.
 */
public final class DreamResolver {

    private DreamResolver() {}

    /** Maximum raw disturbance value, mirroring {@code ChunkSoulData.MAX_DISTURBANCE}. */
    public static final float DISTURBANCE_FULL = 100f;

    /**
     * The loudest single domain in the disturbance map, normalized to [0,1].
     * An empty map or a map whose every value is zero yields 0.
     */
    public static float ambient(Map<Correspondence, Float> disturbance) {
        if (disturbance.isEmpty()) {
            return 0f;
        }
        float maxValue = 0f;
        for (float v : disturbance.values()) {
            if (v > maxValue) {
                maxValue = v;
            }
        }
        return SoulMath.clamp01(maxValue / DISTURBANCE_FULL);
    }

    /**
     * Lucidity of the dream: how clearly the soul perceives the domain's voice.
     * Debt and ambient land-disturbance each erode clarity equally.
     */
    public static float clarity(float debtN, Map<Correspondence, Float> disturbance) {
        return SoulMath.clamp01(1f - 0.5f * debtN - 0.5f * ambient(disturbance));
    }

    /**
     * Oppressive weight bearing on the dreamer. Debt contributes most heavily,
     * marks moderately, and land-disturbance fills the remainder.
     */
    public static float dread(float debtN, float marksN, Map<Correspondence, Float> disturbance) {
        return SoulMath.clamp01(0.4f * debtN + 0.3f * marksN + 0.3f * ambient(disturbance));
    }

    /**
     * The domain with the greatest disturbance value. Returns {@code null} if
     * the map is empty or every value is &le; 0. Ties are broken by
     * {@link Correspondence} declaration order (earlier wins).
     */
    public static Correspondence dominantDomain(Map<Correspondence, Float> disturbance) {
        Correspondence best = null;
        float bestValue = 0f;
        for (Correspondence c : Correspondence.values()) {
            Float v = disturbance.get(c);
            if (v != null && v > bestValue) {
                bestValue = v;
                best = c;
            }
        }
        return best;
    }

    /**
     * Perform a full dream read: clarity, dread, nightmare threshold, and the
     * dominant domain combined into one immutable record.
     *
     * <p>A {@code nightmare} occurs when dread reaches at least 0.5
     * <i>and</i> clarity falls below 0.4 — the soul is too burdened and the
     * vision too murky for a lucid dream.
     */
    public static DreamReading read(float debtN, float marksN, Map<Correspondence, Float> disturbance) {
        float c = clarity(debtN, disturbance);
        float d = dread(debtN, marksN, disturbance);
        boolean nightmare = d >= 0.5f && c < 0.4f;
        Correspondence dom = dominantDomain(disturbance);
        return new DreamReading(c, d, nightmare, dom);
    }
}
