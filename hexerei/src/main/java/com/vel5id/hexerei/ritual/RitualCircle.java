package com.vel5id.hexerei.ritual;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** Pure ritual-circle geometry. A small circle is 12 glyphs in a radius-2 ring on the center's Y-layer. */
public final class RitualCircle {
    private RitualCircle() {}

    // 12 ring positions at radius 2 (dx, dz) around the center.
    private static final int[][] SMALL = {
            {0, -2}, {1, -2}, {2, -1}, {2, 0}, {2, 1}, {1, 2},
            {0, 2}, {-1, 2}, {-2, 1}, {-2, 0}, {-2, -1}, {-1, -2}
    };

    public static int smallSize() {
        return SMALL.length;
    }

    /** The 12 small-ring positions around {@code center} (same Y-layer). */
    public static List<BlockPos> smallRing(BlockPos center) {
        List<BlockPos> ring = new ArrayList<>(SMALL.length);
        for (int[] o : SMALL) {
            ring.add(center.offset(o[0], 0, o[1]));
        }
        return ring;
    }

    /** True if every small-ring position around {@code center} satisfies {@code isGlyph}. */
    public static boolean isSmallComplete(Predicate<BlockPos> isGlyph, BlockPos center) {
        for (int[] o : SMALL) {
            if (!isGlyph.test(center.offset(o[0], 0, o[1]))) {
                return false;
            }
        }
        return true;
    }
}
