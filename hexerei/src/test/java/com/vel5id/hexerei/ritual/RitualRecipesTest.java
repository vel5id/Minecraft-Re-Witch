package com.vel5id.hexerei.ritual;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RitualRecipesTest {
    @Test void matchesTempestWithCircleAndSacrifice() {
        assertEquals(RitualRecipes.TEMPEST,
                RitualRecipes.match(true, "hexerei:mandrake_root").orElse(null));
    }

    @Test void incompleteCircleDoesNotMatch() {
        assertTrue(RitualRecipes.match(false, "hexerei:mandrake_root").isEmpty());
    }

    @Test void wrongSacrificeDoesNotMatch() {
        assertTrue(RitualRecipes.match(true, "minecraft:diamond").isEmpty());
    }

    @Test void tempestHasExpectedCost() {
        assertEquals(100, RitualRecipes.TEMPEST.powerCost());
        assertTrue(RitualRecipes.TEMPEST.requiresSmallCircle());
    }
}
