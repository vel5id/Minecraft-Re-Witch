package com.vel5id.hexerei.item;

import com.vel5id.hexerei.soul.Correspondence;

import javax.annotation.Nullable;

/**
 * Pure logic for a sealed amulet (Модель §5/§7 — the "запечатать" verb): how its bound spirit's
 * {@link Correspondence} domain maps to an effect (the spirit's "nature"), and how wearing it
 * accrues soul-debt and grinds the seal. This replaces the flat charge/recharge model — the cost
 * of a worn amulet is the witch's own debt, not altar nature-power (Article III: power = debt).
 *
 * <p>All constants are first-pass {@code [UNVERIFIED]}; the {@code SealedAmuletTest} lifespan band
 * keeps them honest (an amulet must be finite-lived — no permanent free power — yet not instant).
 */
public final class SealedAmulet {
    private SealedAmulet() {}

    // --- balance knobs (all [UNVERIFIED]) ---
    /** bond.disposition.debt += this each second the amulet is worn. */
    public static final float DRAW_RATE = 0.0005f;
    /** seal.integrity -= GRIND_RATE * resentment each second worn (resentment grinds the seal). */
    public static final float GRIND_RATE = 0.0005f;
    /** Severity of the Mark laid on the witch when a spirit is sealed (raises the resentment floor). */
    public static final float SEAL_MARK_SEVERITY = 0.5f;
    /** Disturbance written to the spirit's domain when it is sealed (the act disturbs its place). */
    public static final int SEAL_DISTURBANCE = 30;
    /** Disturbance released when a worn seal finally shatters (the spirit breaks outward). */
    public static final int BREAK_DISTURBANCE = 50;
    /** A fresh seal's full hold. */
    public static final float FULL_INTEGRITY = 1.0f;

    /** A sealed spirit's granted effect: the vanilla mob-effect, its amplifier, and when it applies. */
    public record AmuletEffect(String effectId, int amplifier, ActiveMode mode, int param) {}

    /**
     * The effect a sealed spirit of this domain grants — its "nature", diegetically (not a lookup of
     * unrelated buffs). Returns {@code null} for domains with no amulet recipe yet.
     *
     * <ul>
     *   <li><b>FOREST</b> → Resistance (the wood-warden's endurance) — always on while worn.</li>
     *   <li><b>DEATH</b> → Weakness projected onto hostiles within {@code param} blocks (malice outward).</li>
     *   <li><b>THRESHOLD</b> → Strength, but only while the wearer is at/below {@code param} hearts
     *       (the threshold-spirit stirs near death).</li>
     * </ul>
     */
    @Nullable
    public static AmuletEffect effectFor(Correspondence domain) {
        if (domain == Correspondence.FOREST) {
            return new AmuletEffect("minecraft:resistance", 0, ActiveMode.ALWAYS, 0);
        }
        if (domain == Correspondence.DEATH) {
            return new AmuletEffect("minecraft:weakness", 0, ActiveMode.AURA_DEBUFF, 5);
        }
        if (domain == Correspondence.THRESHOLD) {
            return new AmuletEffect("minecraft:strength", 0, ActiveMode.LOW_HEALTH, 6);
        }
        return null;
    }

    /** Per-second debt gain while worn — the Article III cost of keeping a spirit bound. */
    public static float debtDelta() {
        return DRAW_RATE;
    }

    /**
     * The spirit's current grievance: unpaid debt over loyalty. Loyalty (won by a future APPEASE/FEED
     * rite) offsets resentment, so a tended spirit is slow to grind its seal.
     */
    public static float resentmentFor(float debt, float loyalty) {
        return Math.max(0f, debt - loyalty);
    }

    /** Per-second seal grind from resentment (negative = integrity lost; zero when resentment is zero). */
    public static float integrityDelta(float resentment) {
        return -GRIND_RATE * resentment;
    }

    /** A seal at/below zero integrity has shattered and must release the spirit. */
    public static boolean shouldBreak(float integrity) {
        return integrity <= 0f;
    }

    /** Whether the amulet's effect applies this tick based solely on its trigger condition + wearer health. */
    public static boolean playerCondition(AmuletEffect fx, float playerHealth) {
        return fx.mode() != ActiveMode.LOW_HEALTH || playerHealth <= fx.param();
    }
}
