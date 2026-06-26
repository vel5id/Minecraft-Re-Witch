package com.vel5id.hexerei.soul;

/**
 * Ritual/brew outcome as a function of STATE, never a flat dice roll
 * (Грамматика §6–7). Success is {@code align · standing · placePenalty}; a poor
 * result is not "you failed, try again" — it is the vector resolving somewhere
 * other than intended, and that resolution feeds the loop.
 *
 * <p>The four failure modes (Грамматика §7):
 * <ul>
 *   <li>{@code MISALIGNMENT} — the act was poorly assembled; it resolves toward its <i>actual</i> vector.</li>
 *   <li>{@code RESISTANCE} — a resentful domain fights the binding and may invert it.</li>
 *   <li>{@code OVERREACH} — magnitude beyond the caster's standing; the unpaid excess brands a self-mark.</li>
 *   <li>{@code DEFILEMENT} — a high-defilement act gone wrong; the domain answers in kind.</li>
 * </ul>
 *
 * The thresholds are the sanctioned numeric knobs (documented in DESIGN-NOTES).
 */
public final class Resolution {
    private Resolution() {}

    public enum Outcome { SUCCESS, MISALIGNMENT, RESISTANCE, OVERREACH, DEFILEMENT }

    static final float DEBT_WEIGHT = 0.5f;       // how much debt erodes standing
    static final float LOYALTY_WEIGHT = 0.5f;    // how much loyalty lifts standing
    static final float SUCCESS_THRESHOLD = 0.5f; // below this, the act resolves as a failure mode
    static final float RESENT_RESIST = 0.5f;     // domain resentment at/above which binding is fought
    static final float DEFILE_THRESHOLD = 0.5f;  // defilement at/above which a botch profanes
    static final float STANDING_CAPACITY = 3f;   // magnitude a unit of standing can safely carry

    /** Caster's footing in [0,1]: low debt + high loyalty → the rite pours (Грамматика §6). */
    public static float standing(Disposition caster) {
        return SoulMath.clamp01(1f - DEBT_WEIGHT * caster.debt() + LOYALTY_WEIGHT * caster.loyalty());
    }

    /**
     * Alignment of the act with the domain's mood in [0,1]. A resentful domain
     * resists taking/binding but accepts offerings more readily (Грамматика §6).
     */
    public static float align(Act act, float domainResentment) {
        float base = SoulMath.clamp01(1f - domainResentment);
        if (act.reciprocity() > 0f) {
            return SoulMath.clamp01(base + act.reciprocity() * domainResentment); // offering eased
        }
        return base; // taking/binding meets the resistance
    }

    /** A muddied place distorts the result (Грамматика §6). */
    public static float placePenalty(float disturbance) {
        return SoulMath.clamp01(1f - disturbance);
    }

    /** Composite success/quality in [0,1] — the value a rite's effect scales by. */
    public static float success(Act act, float domainResentment, Disposition caster, float disturbance) {
        return align(act, domainResentment) * standing(caster) * placePenalty(disturbance);
    }

    /** Classify how the act resolves; the order encodes precedence among failure causes. */
    public static Outcome classify(Act act, float domainResentment, Disposition caster, float disturbance) {
        return classify(act, domainResentment, caster, disturbance, act.magnitude());
    }

    /**
     * As {@link #classify(Act, float, Disposition, float)} but with an explicit {@code reachMagnitude} for
     * the OVERREACH test — the demand the caster actually bears, which can differ from the act's total
     * magnitude. A ritual's rune ring inflates {@code act.magnitude()} (the runes still write their domain
     * and binding), but the witch's reach is her sacrifice, not the apparatus; the ring is the channel,
     * not an overreach. Defaulting {@code reachMagnitude = act.magnitude()} reproduces the simple form.
     */
    public static Outcome classify(Act act, float domainResentment, Disposition caster, float disturbance,
                                   float reachMagnitude) {
        float s = success(act, domainResentment, caster, disturbance);
        if (act.defilement() >= DEFILE_THRESHOLD && s < SUCCESS_THRESHOLD) {
            return Outcome.DEFILEMENT;
        }
        if (act.binding() > 0f && domainResentment >= RESENT_RESIST && s < SUCCESS_THRESHOLD) {
            return Outcome.RESISTANCE;
        }
        if (reachMagnitude > STANDING_CAPACITY * standing(caster)) {
            return Outcome.OVERREACH;
        }
        if (s < SUCCESS_THRESHOLD) {
            return Outcome.MISALIGNMENT;
        }
        return Outcome.SUCCESS;
    }
}
