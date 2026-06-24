package com.vel5id.hexerei.blockentity;

/** Pure cauldron rendering math (RGB unpack), factored out so it is unit-testable without a Minecraft runtime. */
public final class CauldronVisuals {
    private CauldronVisuals() {}

    /** Unpack a 0xRRGGBB int into {r, g, b} floats in [0, 1] — used to tint boiling bubbles by the brew color. */
    public static float[] rgb(int color) {
        return new float[]{
                ((color >> 16) & 0xFF) / 255f,
                ((color >> 8) & 0xFF) / 255f,
                (color & 0xFF) / 255f,
        };
    }
}
