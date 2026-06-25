package com.vel5id.hexerei.soul;

import java.util.Map;

/**
 * Essence sourcing (Статья III петля): essence is FREED by taking from the world — felling,
 * breaking, sacrificing — never granted for free. The amount scales with how much an {@link Act}
 * takes (its negative reciprocity) and its magnitude. The same take that frees essence also raises
 * {@code disturbance[domain]} and {@code debt} (written via {@link Integration}) — power and danger
 * are one source.
 *
 * Pure — no Minecraft dependency.
 */
public final class EssenceSource {
    private EssenceSource() {}

    /** Essence yielded per unit magnitude of a full take. [UNVERIFIED] balance knob (DESIGN-NOTES). */
    public static final float ESSENCE_PER_MAGNITUDE = 10f;

    /** Essence freed by an act — proportional to how much it TAKES (−reciprocity) and its scale. */
    public static float essenceFrom(Act act) {
        float take = Math.max(0f, -act.reciprocity());
        return ESSENCE_PER_MAGNITUDE * act.magnitude() * take;
    }

    /**
     * A break/harvest of a living/rooted block as a release {@link Act}: a full take
     * (reciprocity −1) of the block's {@link Correspondence}, carrying its defilement and scale.
     */
    public static Act breakRelease(Correspondence domain, float magnitude, float defilement) {
        return new Act(Map.of(domain, 1.0f), -1f, 0f, defilement, magnitude);
    }
}
