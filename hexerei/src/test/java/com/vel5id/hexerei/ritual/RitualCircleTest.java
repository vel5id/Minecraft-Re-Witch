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
}
