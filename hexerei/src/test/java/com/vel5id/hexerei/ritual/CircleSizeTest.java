package com.vel5id.hexerei.ritual;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CircleSizeTest {
    @Test void small_has12Positions() {
        assertEquals(12, CircleSize.SMALL.ringPositions(new BlockPos(0, 0, 0)).size());
    }

    @Test void small_isComplete_whenAllGlyphs() {
        BlockPos center = new BlockPos(0, 0, 0);
        Set<BlockPos> ring = new HashSet<>(CircleSize.SMALL.ringPositions(center));
        assertTrue(CircleSize.SMALL.isComplete(ring::contains, center));
    }

    @Test void small_notComplete_whenOneMissing() {
        BlockPos center = new BlockPos(0, 0, 0);
        Set<BlockPos> ring = new HashSet<>(CircleSize.SMALL.ringPositions(center));
        ring.remove(ring.iterator().next());
        assertFalse(CircleSize.SMALL.isComplete(ring::contains, center));
    }

    @Test void medium_has20Positions() {
        assertEquals(20, CircleSize.MEDIUM.ringPositions(new BlockPos(0, 0, 0)).size());
    }

    @Test void medium_isComplete_whenAllGlyphs() {
        BlockPos center = new BlockPos(0, 0, 0);
        Set<BlockPos> ring = new HashSet<>(CircleSize.MEDIUM.ringPositions(center));
        assertTrue(CircleSize.MEDIUM.isComplete(ring::contains, center));
    }

    @Test void medium_notComplete_whenOneMissing() {
        BlockPos center = new BlockPos(0, 0, 0);
        Set<BlockPos> ring = new HashSet<>(CircleSize.MEDIUM.ringPositions(center));
        ring.remove(ring.iterator().next());
        assertFalse(CircleSize.MEDIUM.isComplete(ring::contains, center));
    }

    @Test void labelKeysDifferBySize() {
        assertNotEquals(CircleSize.SMALL.circleLabelKey(), CircleSize.MEDIUM.circleLabelKey());
    }
}
