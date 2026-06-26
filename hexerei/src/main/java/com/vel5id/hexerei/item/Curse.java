package com.vel5id.hexerei.item;

import com.vel5id.hexerei.soul.Bond;
import com.vel5id.hexerei.soul.Correspondence;
import com.vel5id.hexerei.soul.Disposition;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * Pure logic for curses (Модель §7 — a free {@link Bond} laid on a player's {@code marks}). Maps a sealed
 * spirit's {@link Correspondence} domain to a curse kind, and — critically — defines the caster's **echo**:
 * every successful cast lays a milder (~20%, one tier weaker) copy on the caster, so curses cannot be spammed
 * for free (Article III: the weapon's edge points both ways).
 *
 * <p>The curse's finite lifetime lives in the bond's {@code disposition.fear} (the spirit's waning grip);
 * {@link #isEcho} reads the {@code _echo} suffix on the bond's {@code spiritType} to pick FULL vs ECHO values.
 * All constants are first-pass {@code [UNVERIFIED]}; the {@code CurseTest} keeps them honest.
 */
public final class Curse {
    private Curse() {}

    /** Fear at cast; one grip tick per second → ~10 min of haunting. [UNVERIFIED] */
    public static final int CURSE_TICKS = 600;
    /** Disturbance written to the spirit's domain when a curse is cast (weaponizing it). */
    public static final int CURSE_DISTURBANCE = 40;
    /** Suffix on a curse bond's {@code spiritType} marking the caster's milder echo. */
    public static final String ECHO_SUFFIX = "_echo";

    public enum Strength { FULL, ECHO }
    public enum Kind { CLUMSY, UNLUCKY, WEAK }

    public record CurseEffect(Kind kind, Strength strength) {}

    /** The curse a sealed spirit of {@code domain} inflicts at {@code strength}, or {@code null} if none. */
    @Nullable
    public static CurseEffect effectFor(Correspondence domain, Strength strength) {
        Kind k;
        if (domain == Correspondence.STONE) k = Kind.CLUMSY;
        else if (domain == Correspondence.THRESHOLD) k = Kind.UNLUCKY;
        else if (domain == Correspondence.DEATH) k = Kind.WEAK;
        else return null;
        return new CurseEffect(k, strength);
    }

    /** Clumsiness (Неуклюжесть): reach multiplier and gravity multiplier — ECHO is ~20% of the effect. */
    public record ClumsyValues(double reachFactor, double gravityFactor) {}
    public static ClumsyValues clumsy(Strength s) {
        return s == Strength.ECHO ? new ClumsyValues(0.88, 1.12) : new ClumsyValues(0.60, 1.40);
    }

    /** Unluckiness (Неудачливость): additive luck change + Unluck potion amplifier. */
    public record UnluckyValues(double luckAmount, int unluckAmp) {}
    public static UnluckyValues unlucky(Strength s) {
        return s == Strength.ECHO ? new UnluckyValues(-0.4, 0) : new UnluckyValues(-2.0, 1);
    }

    /** Weakness (Слабость): the Weakness potion amplifier. */
    public static int weaknessAmp(Strength s) {
        return s == Strength.ECHO ? 0 : 1;
    }

    // ---- grip / lifetime (fear = the spirit's remaining hold) ----

    public static float initialGrip() {
        return CURSE_TICKS;
    }

    /** One second of haunting loosens the grip by one. */
    public static float gripAfter(float fear) {
        return fear - 1f;
    }

    public static boolean isSpent(float fear) {
        return fear <= 0f;
    }

    /** True if this curse bond is the caster's milder echo (marked by the {@code spiritType} suffix). */
    public static boolean isEcho(Bond bond) {
        return bond.spiritType().getPath().endsWith(ECHO_SUFFIX);
    }

    /** The {@code spiritType} for a curse of {@code domain}; an echo appends {@link #ECHO_SUFFIX}. */
    public static ResourceLocation spiritTypeFor(Correspondence domain, Strength s) {
        String base = domain.key() + "_curse";
        return ResourceLocation.fromNamespaceAndPath("hexerei", s == Strength.ECHO ? base + ECHO_SUFFIX : base);
    }
}
