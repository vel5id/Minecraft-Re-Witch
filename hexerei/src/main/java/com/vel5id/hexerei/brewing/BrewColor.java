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
}
