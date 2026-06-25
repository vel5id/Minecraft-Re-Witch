package com.vel5id.hexerei.ritual;

import com.vel5id.hexerei.registry.HexereiItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

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

    // White-magic growth. Sacrifice is artichoke (not mandrake) so it doesn't collide with Tempest's match.
    public static final RitualRecipe VERDANT = new RitualRecipe(
            "hexerei:verdant", CircleSize.SMALL, "hexerei:artichoke", 60,
            new VerdantRite(), "ritual.hexerei.verdant");

    // The generic ritual-crafting bridge: drops 8 ritual chalk. Future charm-crafting reuses SpawnItemRite.
    public static final RitualRecipe MANIFEST_CHALK = new RitualRecipe(
            "hexerei:manifest_chalk", CircleSize.SMALL, "hexerei:wormwood", 40,
            new SpawnItemRite(() -> new ItemStack(HexereiItems.RITUAL_CHALK.get(), 8)),
            "ritual.hexerei.manifest_chalk");

    public static final RitualRecipe BOUND_BEAST = new RitualRecipe(
            "hexerei:bound_beast", CircleSize.SMALL, "hexerei:wolfsbane", 120,
            new BoundBeastRite(), "ritual.hexerei.bound_beast");

    public static final RitualRecipe WANING_MOON = new RitualRecipe(
            "hexerei:waning_moon", CircleSize.MEDIUM, "hexerei:belladonna_flower", 150,
            new WaningMoonRite(), "ritual.hexerei.waning_moon");

    // The capstone dark rite: forces night and ignites a Blood Moon. MEDIUM + wolfsbane (distinct from
    // Waning Moon's belladonna on the same MEDIUM ring). Top of the power ladder.
    public static final RitualRecipe ECLIPSE = new RitualRecipe(
            "hexerei:eclipse", CircleSize.MEDIUM, "hexerei:wolfsbane", 220,
            new EclipseRite(), "ritual.hexerei.eclipse");

    // Order = chalk scroll order: gentle/cheap first, expensive/aggressive last. TEMPEST stays index 0
    // to preserve the saved-NBT default and existing GameTest expectations.
    public static final List<RitualRecipe> ALL =
            List.of(TEMPEST, VERDANT, MANIFEST_CHALK, BOUND_BEAST, WANING_MOON, ECLIPSE);

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
