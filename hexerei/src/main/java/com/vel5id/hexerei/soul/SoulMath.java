package com.vel5id.hexerei.soul;

/** Tiny shared numeric helpers for the vector core. No Minecraft dependency. */
public final class SoulMath {
    private SoulMath() {}

    /** Clamp to [0,1]. */
    public static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    /** Clamp to [-1,1]. */
    public static float clampSigned(float v) {
        return v < -1f ? -1f : (v > 1f ? 1f : v);
    }
}
