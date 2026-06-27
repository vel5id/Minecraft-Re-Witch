package com.vel5id.hexerei.brewing;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Pure brew-recipe registry + matcher (keyed on item ids so it needs no game runtime). */
public final class BrewRecipes {
    private BrewRecipes() {}

    public static final List<BrewRecipe> RECIPES = List.of(
            new BrewRecipe(List.of("hexerei:mandrake_root", "hexerei:belladonna_flower"), Brews.SLEEPING_DRAUGHT),
            new BrewRecipe(List.of("hexerei:wolfsbane", "hexerei:wormwood"), Brews.FRAILTY),
            new BrewRecipe(List.of("hexerei:icy_needle", "hexerei:artichoke"), Brews.WITCHS_SIGHT),
            new BrewRecipe(List.of("hexerei:mandrake_root", "hexerei:wolfsbane"), Brews.BLOODWORT_TONIC),
            new BrewRecipe(List.of("hexerei:artichoke", "hexerei:wormwood"), Brews.HAGS_SWIFTNESS),
            new BrewRecipe(List.of("hexerei:belladonna_flower", "hexerei:icy_needle"), Brews.WITHERING_BILE),
            new BrewRecipe(List.of("hexerei:mandrake_root", "hexerei:wormwood"), Brews.DREAMING_DRAUGHT));

    /** Per-ingredient liquid tint, blended while brewing (before a recipe matches). */
    public static final Map<String, Integer> INGREDIENT_COLORS = Map.of(
            "hexerei:mandrake_root", 0xC2A878,
            "hexerei:belladonna_flower", 0x5A2A82,
            "hexerei:wolfsbane", 0x7FA0C0,
            "hexerei:wormwood", 0x6B8E23,
            "hexerei:icy_needle", 0xB0E0E6,
            "hexerei:artichoke", 0x4A6E3A);

    /** Exact multiset match: the added ingredients must equal a recipe's ingredient multiset. */
    public static Optional<Brew> match(List<String> addedIds) {
        Map<String, Integer> added = multiset(addedIds);
        for (BrewRecipe r : RECIPES) {
            if (multiset(r.ingredientIds()).equals(added)) {
                return Optional.of(r.result());
            }
        }
        return Optional.empty();
    }

    /** True if adding {@code newId} to {@code current} stays a sub-multiset of some recipe (intake filter). */
    public static boolean canAccept(List<String> current, String newId) {
        Map<String, Integer> next = multiset(current);
        next.merge(newId, 1, Integer::sum);
        for (BrewRecipe r : RECIPES) {
            if (isSubMultiset(next, multiset(r.ingredientIds()))) {
                return true;
            }
        }
        return false;
    }

    /** True if {@code id} appears in any recipe. */
    public static boolean isIngredient(String id) {
        for (BrewRecipe r : RECIPES) {
            if (r.ingredientIds().contains(id)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSubMultiset(Map<String, Integer> sub, Map<String, Integer> sup) {
        for (Map.Entry<String, Integer> e : sub.entrySet()) {
            if (e.getValue() > sup.getOrDefault(e.getKey(), 0)) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, Integer> multiset(List<String> ids) {
        Map<String, Integer> m = new HashMap<>();
        for (String s : ids) {
            m.merge(s, 1, Integer::sum);
        }
        return m;
    }
}
