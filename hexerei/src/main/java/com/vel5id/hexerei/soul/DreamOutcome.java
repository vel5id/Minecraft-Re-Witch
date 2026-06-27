package com.vel5id.hexerei.soul;

/**
 * The immutable result of drinking the Sleep-Brew: whether the witch crossed
 * the threshold, the essence that was spent, and — if she fell into a
 * nightmare — the dread-scaled penalty the waking world will demand.
 *
 * <p>The caller reads this record and applies the effects: deduct
 * {@code essenceSpent}, and if {@code nightmare} is true, invoke a fear Act
 * scaled by {@code dreadPenalty} (already in [0,1]).
 *
 * @see DreamOnset#onDrink(float, DreamReading)
 */
public record DreamOutcome(boolean entered, float essenceSpent, boolean nightmare,
                           float dreadPenalty) {
}
