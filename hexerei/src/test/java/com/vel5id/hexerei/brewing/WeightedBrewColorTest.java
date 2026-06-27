package com.vel5id.hexerei.brewing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gate for {@link BrewColor#blend(List, List)} — a weighted variant where each
 * color carries an integer weight (e.g. multiple units of one reagent biasing the brew).
 * This test is the immutable delegation contract; the implementation is written to satisfy it.
 */
class WeightedBrewColorTest {

    @Test void emptyIsWater() {
        assertEquals(BrewColor.WATER, BrewColor.blend(List.of(), List.of()));
    }

    @Test void zeroTotalWeightIsWater() {
        assertEquals(BrewColor.WATER, BrewColor.blend(List.of(0xFF0000, 0x00FF00), List.of(0, 0)));
    }

    @Test void equalWeightsMatchUnweighted() {
        int weighted = BrewColor.blend(List.of(0xFF0000, 0x0000FF), List.of(1, 1));
        int plain = BrewColor.blend(List.of(0xFF0000, 0x0000FF));
        assertEquals(plain, weighted);
        assertEquals(0x7F007F, weighted);
    }

    @Test void weightBiasesTowardHeavierColor() {
        // red weight 2, blue weight 1 -> r=(2*255+0)/3=170=0xAA, g=0, b=(0+255)/3=85=0x55
        assertEquals(0xAA0055, BrewColor.blend(List.of(0xFF0000, 0x0000FF), List.of(2, 1)));
    }

    @Test void mismatchedSizesThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> BrewColor.blend(List.of(0xFF0000), List.of(1, 2)));
    }

    @Test void negativeWeightThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> BrewColor.blend(List.of(0xFF0000, 0x0000FF), List.of(2, -1)));
    }
}
