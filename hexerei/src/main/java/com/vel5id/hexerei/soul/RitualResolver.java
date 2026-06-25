package com.vel5id.hexerei.soul;

import java.util.Map;

/**
 * Resolves a ritual's {@link Act} against the place's state (Грамматика §6–7): success is a function of
 * how aligned the act is with the domain's mood and how muddied the place is — never a flat dice roll.
 * A resisted/misaligned cast does not "fail for free": the caller still spent the sacrifice, and the
 * botch feeds the loop (more disturbance).
 *
 * <p>Pure — no Minecraft dependency. (The caster's standing is neutral for now: the activation entry
 * point has no player handle; a witch's debt/loyalty folds in once a caster is threaded through.)
 */
public final class RitualResolver {
    private RitualResolver() {}

    /** Disturbance value treated as "fully resentful/muddied" (the 0–100 taint scale). */
    public static final float DISTURB_NORM = 100f;
    /** Disturbance a botched cast burns into the domain that resisted it. [UNVERIFIED] */
    public static final float BOTCH_DISTURBANCE = 10f;

    public record Resolved(Resolution.Outcome outcome, float quality) {}

    /** Resolve the act against the dominant domain's disturbance and the place's total disturbance. */
    public static Resolved resolve(Act act, float domainDisturbance, float totalDisturbance) {
        Disposition neutralCaster = Disposition.EMPTY;   // standing 1.0 until a caster is threaded in
        float domainResentment = SoulMath.clamp01(domainDisturbance / DISTURB_NORM);
        float place = SoulMath.clamp01(totalDisturbance / DISTURB_NORM);
        return new Resolved(
                Resolution.classify(act, domainResentment, neutralCaster, place),
                Resolution.success(act, domainResentment, neutralCaster, place));
    }

    public static boolean isSuccess(Resolution.Outcome outcome) {
        return outcome == Resolution.Outcome.SUCCESS;
    }

    /** The domain an act leans on most (where its disturbance lands and what resists it). */
    public static Correspondence dominantDomain(Act act) {
        Correspondence best = null;
        float bestWeight = 0f;
        for (Map.Entry<Correspondence, Float> e : act.domain().entrySet()) {
            if (e.getValue() > bestWeight) {
                bestWeight = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }
}
