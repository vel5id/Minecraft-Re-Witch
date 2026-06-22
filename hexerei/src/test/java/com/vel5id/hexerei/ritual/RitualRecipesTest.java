package com.vel5id.hexerei.ritual;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class RitualRecipesTest {
    private static java.util.function.Predicate<BlockPos> fullSmallRing(BlockPos center) {
        Set<BlockPos> ring = new HashSet<>(RitualCircle.smallRing(center));
        return ring::contains;
    }

    @Test void matchesTempestWithCircleAndSacrifice() {
        BlockPos center = new BlockPos(0, 0, 0);
        assertEquals(RitualRecipes.TEMPEST,
                RitualRecipes.match(fullSmallRing(center), center, "hexerei:mandrake_root").orElse(null));
    }

    @Test void incompleteCircleDoesNotMatch() {
        BlockPos center = new BlockPos(0, 0, 0);
        assertTrue(RitualRecipes.match(p -> false, center, "hexerei:mandrake_root").isEmpty());
    }

    @Test void wrongSacrificeDoesNotMatch() {
        BlockPos center = new BlockPos(0, 0, 0);
        assertTrue(RitualRecipes.match(fullSmallRing(center), center, "minecraft:diamond").isEmpty());
    }

    @Test void tempestHasExpectedProperties() {
        assertEquals(100, RitualRecipes.TEMPEST.powerCost());
        assertEquals(CircleSize.SMALL, RitualRecipes.TEMPEST.circleSize());
        assertEquals("hexerei:tempest", RitualRecipes.TEMPEST.id());
    }

    @Test void all_containsTempest() {
        assertTrue(RitualRecipes.ALL.contains(RitualRecipes.TEMPEST));
    }
}
