package com.vel5id.hexerei.soul;

/**
 * Pure-logic core for the "drink the Sleep-Brew → cross the threshold" verb.
 * This is a stateless gate: it WRITES nothing, spends nothing, and returns an
 * {@link DreamOutcome} the integration layer applies.
 *
 * <p>If the witch cannot afford the scrying cost she does not cross and
 * nothing is spent.  A crossing into a nightmare carries a dread-scaled
 * penalty the caller turns into a fear Act; the penalty is gated on the
 * {@code nightmare} flag, never on raw dread alone.
 *
 * @see DreamResolver#read(float, float, java.util.Map)
 */
public final class DreamOnset {

    private DreamOnset() {}

    /** Essence required to cross the threshold into a dream. */
    public static final float SCRY_COST = 1.0f;

    /**
     * Attempt to cross into the dream described by {@code reading}.
     *
     * @param essence the witch's current essence pool
     * @param reading the dream glimpsed by {@link DreamResolver#read}
     * @return an immutable outcome encoding whether the crossing succeeded,
     *         the essence spent, and any nightmare penalty
     */
    public static DreamOutcome onDrink(float essence, DreamReading reading) {
        if (essence < SCRY_COST) {
            return new DreamOutcome(false, 0f, false, 0f);
        }
        boolean nightmare = reading.nightmare();
        float dreadPenalty = nightmare ? reading.dread() : 0f;
        return new DreamOutcome(true, SCRY_COST, nightmare, dreadPenalty);
    }
}
