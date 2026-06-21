package com.vel5id.hexerei.ritual;

import java.util.List;
import java.util.Optional;

/** Pure ritual registry + matcher. */
public final class RitualRecipes {
    private RitualRecipes() {}

    public static final RitualRecipe TEMPEST = new RitualRecipe(
            true, "hexerei:mandrake_root", 100, new TempestRite(12000), "ritual.hexerei.tempest");

    public static final List<RitualRecipe> RECIPES = List.of(TEMPEST);

    /** Match an activation (is the small circle complete? what sacrifice id is present?) to a ritual. */
    public static Optional<RitualRecipe> match(boolean smallCircleComplete, String sacrificeId) {
        for (RitualRecipe r : RECIPES) {
            boolean circleOk = !r.requiresSmallCircle() || smallCircleComplete;
            if (circleOk && r.sacrificeId().equals(sacrificeId)) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }
}
