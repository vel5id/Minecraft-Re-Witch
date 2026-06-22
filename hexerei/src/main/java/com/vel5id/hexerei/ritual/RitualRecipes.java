package com.vel5id.hexerei.ritual;

import net.minecraft.core.BlockPos;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Pure ritual registry + matcher. */
public final class RitualRecipes {
    private RitualRecipes() {}

    public static final RitualRecipe TEMPEST = new RitualRecipe(
            "hexerei:tempest", CircleSize.SMALL, "hexerei:mandrake_root", 100,
            new TempestRite(12000), "ritual.hexerei.tempest");

    public static final List<RitualRecipe> ALL = List.of(TEMPEST);

    public static final Map<String, RitualRecipe> BY_ID = ALL.stream()
            .collect(Collectors.toUnmodifiableMap(RitualRecipe::id, r -> r));

    public static Optional<RitualRecipe> match(Predicate<BlockPos> isGlyph, BlockPos center, String sacrificeId) {
        for (RitualRecipe r : ALL) {
            if (r.circleSize().isComplete(isGlyph, center) && r.sacrificeId().equals(sacrificeId)) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }
}
