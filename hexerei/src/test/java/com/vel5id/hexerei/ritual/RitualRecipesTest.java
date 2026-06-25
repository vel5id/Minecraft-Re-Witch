package com.vel5id.hexerei.ritual;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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

    @Test void all_hasSixRecipesWithTempestFirst() {
        assertEquals(6, RitualRecipes.ALL.size()); // + ECLIPSE
        assertSame(RitualRecipes.ECLIPSE, RitualRecipes.ALL.get(RitualRecipes.ALL.size() - 1)); // eclipse last
        assertSame(RitualRecipes.TEMPEST, RitualRecipes.ALL.get(0)); // index 0 preserves the saved-NBT default
    }

    @Test void byId_roundTripsEveryRecipe() {
        for (RitualRecipe r : RitualRecipes.ALL) {
            assertSame(r, RitualRecipes.BY_ID.get(r.id()), "BY_ID must resolve " + r.id());
        }
        assertEquals(RitualRecipes.ALL.size(), RitualRecipes.BY_ID.size());
    }

    @Test void fromTag_defaultsToTempestForNullOrUnknown() {
        assertSame(RitualRecipes.TEMPEST, RitualRecipes.fromTag(null));
        CompoundTag unknown = new CompoundTag();
        unknown.putString("hexerei:rite", "hexerei:not_a_rite");
        assertSame(RitualRecipes.TEMPEST, RitualRecipes.fromTag(unknown));
    }

    @Test void fromTag_resolvesSelectedRite() {
        CompoundTag tag = new CompoundTag();
        tag.putString("hexerei:rite", "hexerei:waning_moon");
        assertSame(RitualRecipes.WANING_MOON, RitualRecipes.fromTag(tag));
    }

    @Test void match_smallCircleDistinguishesTempestFromVerdantBySacrifice() {
        // Verdant uses artichoke (not mandrake), so it no longer collides with Tempest on a SMALL circle.
        BlockPos center = new BlockPos(0, 0, 0);
        assertEquals(RitualRecipes.TEMPEST,
                RitualRecipes.match(fullSmallRing(center), center, "hexerei:mandrake_root").orElse(null));
        assertEquals(RitualRecipes.VERDANT,
                RitualRecipes.match(fullSmallRing(center), center, "hexerei:artichoke").orElse(null));
    }

    @Test void match_mediumCircleFiresWaningMoon() {
        BlockPos center = new BlockPos(0, 0, 0);
        Set<BlockPos> medium = new HashSet<>(RitualCircle.mediumRing(center));
        assertEquals(RitualRecipes.WANING_MOON,
                RitualRecipes.match(medium::contains, center, "hexerei:belladonna_flower").orElse(null));
    }

    @Test void match_smallSacrificeDoesNotFireMediumRite() {
        // belladonna only matches WANING_MOON, which needs a MEDIUM circle — a small ring must not match it.
        BlockPos center = new BlockPos(0, 0, 0);
        assertTrue(RitualRecipes.match(fullSmallRing(center), center, "hexerei:belladonna_flower").isEmpty());
    }
}
