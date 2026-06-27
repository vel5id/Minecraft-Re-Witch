package com.vel5id.hexerei.soul;

import java.util.List;

/**
 * Stateless normalizer that collapses concrete soul values into the caller-
 * normalized [0,1] terms consumed by {@link DreamResolver}.
 *
 * <p>Full-scale debt is {@link #DEBT_FULL}; full-scale carried-fear across
 * marks is {@link #MARKS_FULL}. Both are clamped via {@link SoulMath#clamp01}.
 */
public final class DreamNormalize {

    private DreamNormalize() {}

    /** The debt value that saturates {@link #debtN} to 1.0. */
    public static final float DEBT_FULL = 10f;

    /** The sum of {@code mark.disposition().fear()} that saturates {@link #marksN} to 1.0. */
    public static final float MARKS_FULL = 5f;

    /**
     * Normalize raw accumulated debt to [0,1].
     *
     * @param totalDebt the raw debt value (e.g. {@link Disposition#debt()})
     * @return {@code SoulMath.clamp01(totalDebt / DEBT_FULL)}
     */
    public static float debtN(float totalDebt) {
        return SoulMath.clamp01(totalDebt / DEBT_FULL);
    }

    /**
     * Normalize the total carried fear across a list of marks to [0,1].
     * Each mark contributes its {@link Disposition#fear()} value.
     *
     * @param marks the marks to sum over (empty list → 0)
     * @return {@code SoulMath.clamp01(Σ fear / MARKS_FULL)}
     */
    public static float marksN(List<Bond> marks) {
        float sum = 0f;
        for (Bond b : marks) {
            sum += b.disposition().fear();
        }
        return SoulMath.clamp01(sum / MARKS_FULL);
    }
}
