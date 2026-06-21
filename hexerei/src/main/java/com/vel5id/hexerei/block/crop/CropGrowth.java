package com.vel5id.hexerei.block.crop;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** Hexerei herb-crop growth and bonemeal math. */
public final class CropGrowth {
    private CropGrowth() {}

    /** Grows when rand.nextInt((int)(25/f)+1) == 0. Integer truncation of 25/f preserved. */
    public static boolean shouldGrow(RandomSource r, float growthRate) {
        return r.nextInt((int) (25.0F / growthRate) + 1) == 0;
    }

    /** Bonemeal: big -> current + random[2..maxAge]; else current + 1. Clamped to maxAge. */
    public static int bonemealIncrease(RandomSource r, int current, int maxAge, boolean big) {
        int next = big ? current + Mth.nextInt(r, 2, maxAge) : current + 1;
        return Math.min(next, maxAge);
    }
}
