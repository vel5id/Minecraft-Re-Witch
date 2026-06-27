package com.vel5id.hexerei.brewing;

import java.util.List;

/** Pure liquid-color blending for the cauldron. */
public final class BrewColor {
    private BrewColor() {}

    public static final int WATER = 0x3F76E4; // vanilla water tint

    /** Average-blend a list of 0xRRGGBB colors; empty -> water. */
    public static int blend(List<Integer> colors) {
        if (colors.isEmpty()) {
            return WATER;
        }
        long r = 0, g = 0, b = 0;
        for (int c : colors) {
            r += (c >> 16) & 0xFF;
            g += (c >> 8) & 0xFF;
            b += c & 0xFF;
        }
        int n = colors.size();
        return ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n);
    }

    /** Weighted average-blend of 0xRRGGBB colors with integer weights;
     *  empty list or total weight zero -> water. */
    public static int blend(List<Integer> colors, List<Integer> weights) {
        if (colors.size() != weights.size()) {
            throw new IllegalArgumentException("colors and weights must have the same size");
        }
        long totalWeight = 0;
        for (int w : weights) {
            if (w < 0) {
                throw new IllegalArgumentException("weights must be non-negative");
            }
            totalWeight += w;
        }
        if (colors.isEmpty() || totalWeight == 0) {
            return WATER;
        }
        long r = 0, g = 0, b = 0;
        for (int i = 0; i < colors.size(); i++) {
            int c = colors.get(i);
            int w = weights.get(i);
            r += (long) ((c >> 16) & 0xFF) * w;
            g += (long) ((c >> 8) & 0xFF) * w;
            b += (long) (c & 0xFF) * w;
        }
        return ((int) (r / totalWeight) << 16) | ((int) (g / totalWeight) << 8) | (int) (b / totalWeight);
    }
}
