package com.vel5id.hexerei.ritual;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RitualCircleTest {
    private static final int[][] SMALL = {
            {0, -2}, {1, -2}, {2, -1}, {2, 0}, {2, 1}, {1, 2},
            {0, 2}, {-1, 2}, {-2, 1}, {-2, 0}, {-2, -1}, {-1, -2}
    };

    private static Set<BlockPos> ring(BlockPos center) {
        Set<BlockPos> s = new HashSet<>();
        for (int[] o : SMALL) {
            s.add(center.offset(o[0], 0, o[1]));
        }
        return s;
    }

    @Test void smallSizeIsTwelve() {
        assertEquals(12, RitualCircle.smallSize());
    }

    @Test void completeRingIsComplete() {
        BlockPos c = new BlockPos(50, 64, 50);
        Set<BlockPos> glyphs = ring(c);
        assertTrue(RitualCircle.isSmallComplete(glyphs::contains, c));
    }

    @Test void missingOneGlyphIsIncomplete() {
        BlockPos c = new BlockPos(50, 64, 50);
        Set<BlockPos> glyphs = ring(c);
        glyphs.remove(c.offset(2, 0, 0)); // drop one ring cell
        assertFalse(RitualCircle.isSmallComplete(glyphs::contains, c));
    }

    @Test void emptyIsIncomplete() {
        BlockPos c = new BlockPos(50, 64, 50);
        assertFalse(RitualCircle.isSmallComplete(p -> false, c));
    }

    // --- MEDIUM ring (radius-3, 20 cells) ---
    private static final int[][] MEDIUM = {
            {-2, -3}, {-1, -3}, {0, -3}, {1, -3}, {2, -3},
            {3, -2}, {3, -1}, {3, 0}, {3, 1}, {3, 2},
            {2, 3}, {1, 3}, {0, 3}, {-1, 3}, {-2, 3},
            {-3, 2}, {-3, 1}, {-3, 0}, {-3, -1}, {-3, -2}
    };

    private static Set<BlockPos> mediumRing(BlockPos center) {
        Set<BlockPos> s = new HashSet<>();
        for (int[] o : MEDIUM) {
            s.add(center.offset(o[0], 0, o[1]));
        }
        return s;
    }

    @Test void mediumSizeIsTwenty() {
        assertEquals(20, RitualCircle.mediumSize());
        assertEquals(20, RitualCircle.mediumRing(new BlockPos(0, 0, 0)).size());
    }

    @Test void mediumRingMatchesPinnedTable() {
        BlockPos c = new BlockPos(50, 64, 50);
        assertEquals(mediumRing(c), new HashSet<>(RitualCircle.mediumRing(c)));
    }

    @Test void mediumRingAllAtChebyshevDistanceThreeOnCenterLayer() {
        BlockPos c = new BlockPos(0, 0, 0);
        for (BlockPos p : RitualCircle.mediumRing(c)) {
            assertEquals(3, Math.max(Math.abs(p.getX()), Math.abs(p.getZ())), "cell must be Chebyshev-3: " + p);
            assertEquals(0, p.getY(), "ring stays on the center Y-layer");
        }
    }

    @Test void mediumCompleteRingIsComplete() {
        BlockPos c = new BlockPos(50, 64, 50);
        Set<BlockPos> glyphs = mediumRing(c);
        assertTrue(RitualCircle.isMediumComplete(glyphs::contains, c));
    }

    @Test void mediumMissingOneGlyphIsIncomplete() {
        BlockPos c = new BlockPos(50, 64, 50);
        Set<BlockPos> glyphs = mediumRing(c);
        glyphs.remove(c.offset(3, 0, 0));
        assertFalse(RitualCircle.isMediumComplete(glyphs::contains, c));
    }

    @Test void mediumEmptyIsIncomplete() {
        assertFalse(RitualCircle.isMediumComplete(p -> false, new BlockPos(50, 64, 50)));
    }
}
