package com.vel5id.hexerei.soul;

/**
 * Asymmetric decay (Грамматика "Угасание"): attention heals the world's regard,
 * but debt does not dissolve. A uniform fade to zero would erase the Article III
 * loop, so:
 *
 * <ul>
 *   <li>{@code debt} — NEVER decays here; only gifting acts (reciprocity&gt;0) pay it down.</li>
 *   <li>{@code resentment} — drifts down toward a floor of {@code debt + marks} (never below what debt justifies).</li>
 *   <li>{@code loyalty} — drifts toward base (0) if unfed; a bond must be kept fed.</li>
 *   <li>{@code fear} — fades toward 0.</li>
 * </ul>
 *
 * Rates are per decay tick (the project decays on a 1200-tick cadence); they are
 * the permitted numeric balance knobs (Грамматика "дефолты"), documented in DESIGN-NOTES.
 */
public final class Decay {
    private Decay() {}

    public static final float RESENTMENT_RATE = 0.5f;
    public static final float LOYALTY_RATE = 0.25f;
    public static final float FEAR_RATE = 0.5f;

    /** The lowest resentment can settle to: justified by standing debt + active marks. */
    public static float resentmentFloor(float debt, float marksSeverity) {
        return debt + marksSeverity;
    }

    /**
     * Advance one decay step of {@code dt} ticks. {@code resentmentFloor} is the
     * caller-supplied floor (see {@link Bond#resentmentFloor()}); debt is carried through unchanged.
     */
    public static Disposition decay(Disposition d, float resentmentFloor, float dt) {
        float resent = Math.max(resentmentFloor, d.resentment() - RESENTMENT_RATE * dt);
        float loyal = Math.max(0f, d.loyalty() - LOYALTY_RATE * dt);
        float fear = Math.max(0f, d.fear() - FEAR_RATE * dt);
        return new Disposition(d.debt(), resent, fear, loyal);
    }
}
