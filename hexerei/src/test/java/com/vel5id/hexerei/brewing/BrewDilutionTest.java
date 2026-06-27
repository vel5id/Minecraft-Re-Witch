package com.vel5id.hexerei.brewing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gate for {@link BrewColor#dilute(int, int)} — blending one part of a brew color toward
 * {@code waterParts} of the {@link BrewColor#WATER} base (adding water to the cauldron dilutes
 * the tint). The immutable delegation contract; the implementation is written to satisfy it.
 */
class BrewDilutionTest {

    @Test void zeroWaterLeavesColorUnchanged() {
        assertEquals(0xFF0000, BrewColor.dilute(0xFF0000, 0));
        assertEquals(0x123456, BrewColor.dilute(0x123456, 0));
    }

    @Test void oneWaterEqualsEqualWeightBlend() {
        int diluted = BrewColor.dilute(0xFF0000, 1);
        int expected = BrewColor.blend(java.util.List.of(0xFF0000, BrewColor.WATER), java.util.List.of(1, 1));
        assertEquals(expected, diluted);
    }

    @Test void threePartsWaterApproachesWater() {
        // 1 part red (0xFF0000) + 3 parts water (0x3F76E4):
        // R=(255+3*63)/4=111=0x6F, G=(0+3*118)/4=88=0x58, B=(0+3*228)/4=171=0xAB
        assertEquals(0x6F58AB, BrewColor.dilute(0xFF0000, 3));
    }

    @Test void negativeWaterThrows() {
        assertThrows(IllegalArgumentException.class, () -> BrewColor.dilute(0xFF0000, -1));
    }
}
