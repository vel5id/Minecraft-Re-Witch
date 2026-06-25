package com.vel5id.hexerei.soul;

/**
 * Offering a spirit-bearing reagent to the altar (a deliberate sacrifice — the alternative to the
 * Hungering Altar's passive drain). Extracting the item's spirit frees essence, but the altar grows
 * SATED on a domain: offer the same domain in bulk and each yields less ("the spirits of identical
 * things, given together, are thin"). Satiation decays over time. Pure — no Minecraft dependency.
 */
public final class Offering {
    private Offering() {}

    static final float SATIATION_GAIN = 1.0f;    // satiation added per unit magnitude offered
    static final float SATIATION_DECAY = 1.0f;   // satiation shed per decay step

    /** Diminishing yield as a domain gets sated: 1 at satiation 0, halving by +1. */
    public static float yieldMultiplier(float satiation) {
        return 1f / (1f + Math.max(0f, satiation));
    }

    /** Essence freed by offering a reagent of {@code magnitude} into a domain at {@code satiation}. */
    public static float essence(float magnitude, float satiation) {
        return EssenceSource.ESSENCE_PER_MAGNITUDE * magnitude * yieldMultiplier(satiation);
    }

    /** Satiation after an offering raises it (anti-grind on bulk-feeding one domain). */
    public static float satiationAfter(float satiation, float magnitude) {
        return Math.max(0f, satiation) + SATIATION_GAIN * magnitude;
    }

    /** Satiation eased back toward 0 over a decay step (the altar's hunger returns). */
    public static float decay(float satiation, float steps) {
        return Math.max(0f, satiation - SATIATION_DECAY * steps);
    }
}
