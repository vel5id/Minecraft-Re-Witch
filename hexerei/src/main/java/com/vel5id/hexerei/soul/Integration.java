package com.vel5id.hexerei.soul;

import java.util.EnumMap;
import java.util.Map;

/**
 * The integration map (Грамматика §2): the ONE function that turns an {@link Act}
 * into a {@code State} delta. "Никто не пишет в State напрямую — только через Act."
 * Every writer of {@link Disposition} / disturbance goes through here.
 *
 * <pre>
 *   debt        += max(0, −reciprocity) · magnitude · w
 *   loyalty     += max(0, +reciprocity) · magnitude · w
 *   resentment  += defilement           · magnitude · w
 *   disturbance[D] += interactionStrength · magnitude · w(D)
 *   sealIntegrity ← clamp01(integrity + binding · magnitude)
 * </pre>
 * where {@code w} is the act's weight in the bond's domain.
 */
public final class Integration {
    private Integration() {}

    /** Disposition delta an act lays on a bond of {@code bondDomain} (fear is untouched here). */
    public static Disposition deltaFor(Act act, Correspondence bondDomain) {
        float w = act.domainWeight(bondDomain);
        float mag = act.magnitude();
        float debt = Math.max(0f, -act.reciprocity()) * mag * w;
        float loyalty = Math.max(0f, act.reciprocity()) * mag * w;
        float resentment = act.defilement() * mag * w;
        return new Disposition(debt, resentment, 0f, loyalty);
    }

    /** Fold an act into a bond's disposition — the only sanctioned write path. */
    public static Disposition integrate(Disposition current, Act act, Correspondence bondDomain) {
        return current.plus(deltaFor(act, bondDomain));
    }

    /** Per-domain disturbance the act stirs into the place it happened (Грамматика §1). */
    public static Map<Correspondence, Float> disturbanceDelta(Act act) {
        float s = act.interactionStrength() * act.magnitude();
        Map<Correspondence, Float> out = new EnumMap<>(Correspondence.class);
        for (Map.Entry<Correspondence, Float> e : act.domain().entrySet()) {
            out.put(e.getKey(), s * e.getValue());
        }
        return out;
    }

    /** Seal integrity after an act: positive binding strengthens, negative tears (Грамматика §2). */
    public static float sealAfter(float integrity, Act act) {
        return SoulMath.clamp01(integrity + act.binding() * act.magnitude());
    }
}
