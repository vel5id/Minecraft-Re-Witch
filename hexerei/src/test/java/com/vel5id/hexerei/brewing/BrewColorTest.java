package com.vel5id.hexerei.brewing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BrewColorTest {
    @Test void emptyIsWater() {
        assertEquals(BrewColor.WATER, BrewColor.blend(List.of()));
    }

    @Test void singleColorUnchanged() {
        assertEquals(0xFFFFFF, BrewColor.blend(List.of(0xFFFFFF)));
        assertEquals(0x123456, BrewColor.blend(List.of(0x123456)));
    }

    @Test void averagesTwoColors() {
        // red + blue -> (127,0,127)
        assertEquals(0x7F007F, BrewColor.blend(List.of(0xFF0000, 0x0000FF)));
    }
}
