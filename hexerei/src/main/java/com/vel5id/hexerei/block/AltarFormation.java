package com.vel5id.hexerei.block;

import net.minecraft.core.BlockPos;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Flood-fill validity rule for a Hexerei altar multiblock formation. */
public final class AltarFormation {
    public static final int ELEMENTS_IN_COMPLETE_ALTAR = 6;

    private AltarFormation() {}

    // North, South, East, West — horizontal neighbours only.
    private static final int[][] HORIZONTAL = {{0, 0, -1}, {0, 0, 1}, {1, 0, 0}, {-1, 0, 0}};

    /**
     * @return the core (first BFS-visited cell) if {@code origin} belongs to a valid complete altar,
     *         else {@code null}. Validity: every visited cell has 2-3 same-set horizontal neighbours,
     *         and exactly {@value #ELEMENTS_IN_COMPLETE_ALTAR} cells are connected (a flat 2x3 slab).
     */
    @Nullable
    public static BlockPos findCore(Set<BlockPos> altarBlocks, BlockPos origin) {
        if (!altarBlocks.contains(origin)) {
            return null;
        }
        List<BlockPos> visited = new ArrayList<>();
        Set<BlockPos> visitedSet = new HashSet<>();
        Deque<BlockPos> toVisit = new ArrayDeque<>();
        toVisit.add(origin);
        boolean valid = true;
        while (!toVisit.isEmpty()) {
            BlockPos coord = toVisit.poll();
            if (visitedSet.contains(coord)) {
                continue;
            }
            int neighbours = 0;
            for (int[] d : HORIZONTAL) {
                BlockPos n = coord.offset(d[0], d[1], d[2]);
                if (altarBlocks.contains(n)) {
                    neighbours++;
                    if (!visitedSet.contains(n) && !toVisit.contains(n)) {
                        toVisit.add(n);
                    }
                }
            }
            if (neighbours < 2 || neighbours > 3) {
                valid = false;
            }
            visited.add(coord);
            visitedSet.add(coord);
        }
        return (valid && visited.size() == ELEMENTS_IN_COMPLETE_ALTAR) ? visited.get(0) : null;
    }
}
