package com.vel5id.hexerei.ritual;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class VerdantRiteTest {

    @Test void cellsCover7x7x3DistinctCenteredOffsets() {
        List<int[]> cells = VerdantRite.cells();
        assertEquals(7 * 7 * 3, cells.size());
        Set<String> seen = new HashSet<>();
        for (int[] c : cells) {
            assertTrue(seen.add(c[0] + "," + c[1] + "," + c[2]), "duplicate offset");
            assertTrue(Math.abs(c[0]) <= 3 && Math.abs(c[2]) <= 3, "dx/dz within radius 3");
            assertTrue(Math.abs(c[1]) <= 1, "dy within [-1,1]");
        }
        assertTrue(seen.contains("0,0,0"), "sweep is centered on the circle");
    }

    @Test void maxGrowthsMatchesSmallGlyphCount() {
        assertEquals(12, VerdantRite.MAX_GROWTHS);
    }
}
