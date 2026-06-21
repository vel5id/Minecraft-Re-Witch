package com.vel5id.hexerei.brewing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BrewRecipesTest {
    @Test void matchesSleepingDraughtAnyOrder() {
        assertEquals(Brews.SLEEPING_DRAUGHT,
                BrewRecipes.match(List.of("hexerei:mandrake_root", "hexerei:belladonna_flower")).orElse(null));
        assertEquals(Brews.SLEEPING_DRAUGHT,
                BrewRecipes.match(List.of("hexerei:belladonna_flower", "hexerei:mandrake_root")).orElse(null));
    }

    @Test void matchesFrailty() {
        assertEquals(Brews.FRAILTY,
                BrewRecipes.match(List.of("hexerei:wolfsbane", "hexerei:wormwood")).orElse(null));
    }

    @Test void partialDoesNotMatch() {
        assertTrue(BrewRecipes.match(List.of("hexerei:mandrake_root")).isEmpty());
    }

    @Test void wrongComboDoesNotMatch() {
        assertTrue(BrewRecipes.match(List.of("hexerei:mandrake_root", "hexerei:wolfsbane")).isEmpty());
    }

    @Test void canAcceptProgressesTowardRecipe() {
        assertTrue(BrewRecipes.canAccept(List.of(), "hexerei:mandrake_root"));
        assertTrue(BrewRecipes.canAccept(List.of("hexerei:mandrake_root"), "hexerei:belladonna_flower"));
    }

    @Test void canAcceptRejectsCrossRecipeAndOverfill() {
        // mandrake + wolfsbane share no recipe
        assertFalse(BrewRecipes.canAccept(List.of("hexerei:mandrake_root"), "hexerei:wolfsbane"));
        // recipe already complete; adding a 3rd exceeds the multiset
        assertFalse(BrewRecipes.canAccept(List.of("hexerei:mandrake_root", "hexerei:belladonna_flower"), "hexerei:mandrake_root"));
    }

    @Test void canAcceptRejectsNonIngredient() {
        assertFalse(BrewRecipes.canAccept(List.of(), "minecraft:diamond"));
    }

    @Test void isIngredient() {
        assertTrue(BrewRecipes.isIngredient("hexerei:wormwood"));
        assertFalse(BrewRecipes.isIngredient("minecraft:diamond"));
    }
}
