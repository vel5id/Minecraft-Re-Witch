package com.vel5id.hexerei.blockentity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CauldronVisualsTest {

    @Test void rgbUnpacksEachChannel() {
        float[] c = CauldronVisuals.rgb(0xFF8000);
        assertEquals(1.0f, c[0], 1e-6);          // 0xFF
        assertEquals(0x80 / 255f, c[1], 1e-6);
        assertEquals(0.0f, c[2], 1e-6);          // 0x00
    }

    @Test void rgbWaterColorRoundTrips() {
        float[] c = CauldronVisuals.rgb(0x3F76E4); // BrewColor.WATER
        assertEquals(0x3F / 255f, c[0], 1e-6);
        assertEquals(0x76 / 255f, c[1], 1e-6);
        assertEquals(0xE4 / 255f, c[2], 1e-6);
    }

    @Test void rgbBlackAndWhiteBounds() {
        assertArrayEquals(new float[]{0f, 0f, 0f}, CauldronVisuals.rgb(0x000000), 1e-6f);
        assertArrayEquals(new float[]{1f, 1f, 1f}, CauldronVisuals.rgb(0xFFFFFF), 1e-6f);
    }
}
