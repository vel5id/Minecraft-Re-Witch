package com.vel5id.hexerei.ritual;

import java.util.Locale;

/**
 * The 8 vanilla moon phases (index 0 = FULL .. 4 = NEW) with a per-phase ritual effect/taint modifier.
 * Pure — no Minecraft runtime, so the phase math is unit-testable. A full moon strengthens rites and
 * cleans the land; a new moon weakens and dirties them. The effect*taint product stays near 1.0 at the
 * extremes, so a phase is a trade, never a free lunch.
 */
public enum LunarPhase {
    FULL(1.25f, 0.80f),
    WANING_GIBBOUS(1.10f, 0.90f),
    LAST_QUARTER(1.00f, 1.00f),
    WANING_CRESCENT(0.95f, 1.10f),
    NEW(0.80f, 1.30f),
    WAXING_CRESCENT(0.95f, 1.10f),
    FIRST_QUARTER(1.00f, 1.00f),
    WAXING_GIBBOUS(1.10f, 0.90f);

    private static final LunarPhase[] BY_INDEX = values();

    private final float effectMul;
    private final float taintMul;

    LunarPhase(float effectMul, float taintMul) {
        this.effectMul = effectMul;
        this.taintMul = taintMul;
    }

    /** The phase for a vanilla moon-phase index, wrapping safely so a bad index never throws. */
    public static LunarPhase fromIndex(int moonPhase) {
        return BY_INDEX[Math.floorMod(moonPhase, 8)];
    }

    public float effectMul() {
        return effectMul;
    }

    public float taintMul() {
        return taintMul;
    }

    public boolean isFull() {
        return this == FULL;
    }

    public boolean isNew() {
        return this == NEW;
    }

    /** Lang key for the phase name, e.g. {@code hexerei.moon.full}. */
    public String nameKey() {
        return "hexerei.moon." + name().toLowerCase(Locale.ROOT);
    }

    /** Lang key for the "rites strengthened/weakened/neutral" tooltip line. */
    public String effectKey() {
        if (effectMul > 1f) {
            return "hexerei.moon.effect.strong";
        }
        return effectMul < 1f ? "hexerei.moon.effect.weak" : "hexerei.moon.effect.neutral";
    }
}
