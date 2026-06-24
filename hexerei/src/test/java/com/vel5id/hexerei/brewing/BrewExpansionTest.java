package com.vel5id.hexerei.brewing;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Covers the Witch's Brew Expansion: the 4 new brews, recipe distinctness, and data integrity. */
class BrewExpansionTest {

    @Test void witchsSightMatchesAnyOrder() {
        assertEquals(Brews.WITCHS_SIGHT,
                BrewRecipes.match(List.of("hexerei:icy_needle", "hexerei:artichoke")).orElse(null));
        assertEquals(Brews.WITCHS_SIGHT,
                BrewRecipes.match(List.of("hexerei:artichoke", "hexerei:icy_needle")).orElse(null));
    }

    @Test void bloodwortTonicMatchesAnyOrder() {
        assertEquals(Brews.BLOODWORT_TONIC,
                BrewRecipes.match(List.of("hexerei:mandrake_root", "hexerei:wolfsbane")).orElse(null));
        assertEquals(Brews.BLOODWORT_TONIC,
                BrewRecipes.match(List.of("hexerei:wolfsbane", "hexerei:mandrake_root")).orElse(null));
    }

    @Test void hagsSwiftnessMatchesAnyOrder() {
        assertEquals(Brews.HAGS_SWIFTNESS,
                BrewRecipes.match(List.of("hexerei:artichoke", "hexerei:wormwood")).orElse(null));
        assertEquals(Brews.HAGS_SWIFTNESS,
                BrewRecipes.match(List.of("hexerei:wormwood", "hexerei:artichoke")).orElse(null));
    }

    @Test void witheringBileMatchesAnyOrder() {
        assertEquals(Brews.WITHERING_BILE,
                BrewRecipes.match(List.of("hexerei:belladonna_flower", "hexerei:icy_needle")).orElse(null));
        assertEquals(Brews.WITHERING_BILE,
                BrewRecipes.match(List.of("hexerei:icy_needle", "hexerei:belladonna_flower")).orElse(null));
    }

    @Test void allRecipeMultisetsAreDistinct() {
        Set<Map<String, Integer>> seen = new HashSet<>();
        for (BrewRecipe r : BrewRecipes.RECIPES) {
            Map<String, Integer> ms = multiset(r.ingredientIds());
            assertTrue(seen.add(ms), "duplicate ingredient multiset: " + ms);
        }
        assertEquals(BrewRecipes.RECIPES.size(), seen.size());
    }

    @Test void newRecipesAreAcceptedFromEmptyCauldron() {
        assertTrue(BrewRecipes.canAccept(List.of(), "hexerei:icy_needle"));
        assertTrue(BrewRecipes.canAccept(List.of(), "hexerei:artichoke"));
    }

    @Test void everyEffectIsWellFormedAndPositive() {
        for (Brew brew : Brews.BY_ID.values()) {
            for (BrewEffect e : brew.effects()) {
                String id = e.effectId();
                assertTrue(id.startsWith("minecraft:"), "effect id not minecraft-namespaced: " + id);
                assertFalse(id.endsWith(":"), "effect id has empty path: " + id);
                assertTrue(e.amplifier() >= 0, "negative amplifier in " + brew.id());
                assertTrue(e.durationTicks() > 0, "non-positive duration in " + brew.id());
            }
        }
    }

    @Test void everyRecipeIngredientHasATint() {
        for (BrewRecipe r : BrewRecipes.RECIPES) {
            for (String id : r.ingredientIds()) {
                assertTrue(BrewRecipes.INGREDIENT_COLORS.containsKey(id),
                        "ingredient missing from INGREDIENT_COLORS: " + id);
            }
        }
    }

    @Test void orphanCropsNowUsed() {
        // icy_needle and artichoke were grown but unused before this feature
        assertTrue(BrewRecipes.isIngredient("hexerei:icy_needle"));
        assertTrue(BrewRecipes.isIngredient("hexerei:artichoke"));
    }

    private static Map<String, Integer> multiset(List<String> ids) {
        Map<String, Integer> m = new HashMap<>();
        for (String s : ids) {
            m.merge(s, 1, Integer::sum);
        }
        return m;
    }
}
