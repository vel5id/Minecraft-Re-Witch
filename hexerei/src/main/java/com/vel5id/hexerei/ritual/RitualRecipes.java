package com.vel5id.hexerei.ritual;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import javax.annotation.Nullable;
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

    /**
     * Resolves a {@link RitualRecipe} from an item's NBT tag, or returns the first recipe as default.
     * This is the pure-logic layer called by {@code RitualChalkItem.getSelectedRecipe(ItemStack)}.
     *
     * @param tag the stack's compound tag, or {@code null} when absent
     */
    public static RitualRecipe fromTag(@Nullable CompoundTag tag) {
        if (tag != null) {
            RitualRecipe r = BY_ID.get(tag.getString("hexerei:rite"));
            if (r != null) return r;
        }
        return ALL.isEmpty() ? null : ALL.get(0);
    }

    public static Optional<RitualRecipe> match(Predicate<BlockPos> isGlyph, BlockPos center, String sacrificeId) {
        for (RitualRecipe r : ALL) {
            if (r.circleSize().isComplete(isGlyph, center) && r.sacrificeId().equals(sacrificeId)) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }
}
