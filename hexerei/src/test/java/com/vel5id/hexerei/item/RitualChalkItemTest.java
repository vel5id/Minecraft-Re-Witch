package com.vel5id.hexerei.item;

import com.vel5id.hexerei.network.CycleRiteC2SPacket;
import com.vel5id.hexerei.ritual.RitualRecipe;
import com.vel5id.hexerei.ritual.RitualRecipes;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the rite-selection NBT path and {@link CycleRiteC2SPacket#cycleIndex}.
 *
 * <p>These run without a Minecraft/Forge runtime. The NBT branch is tested through
 * {@link RitualRecipes#fromTag(CompoundTag)}, the pure static helper that
 * {@code RitualChalkItem.getSelectedRecipe(ItemStack)} delegates to. Directly
 * instantiating {@code RitualChalkItem} would trigger MC's item registry bootstrap
 * (via {@code Item → FeatureElement → BuiltInRegistries}) which is unavailable in
 * plain JUnit.</p>
 */
class RitualChalkItemTest {

    // -----------------------------------------------------------------------
    // RitualRecipes.fromTag — null tag (mirrors the no-NBT path)
    // -----------------------------------------------------------------------

    @Test
    void fromTag_nullTag_returnsFirstRecipe() {
        RitualRecipe result = RitualRecipes.fromTag(null);
        assertNotNull(result, "null tag must not return null when ALL is non-empty");
        assertEquals(RitualRecipes.ALL.get(0), result, "null tag must return the first recipe");
    }

    // -----------------------------------------------------------------------
    // RitualRecipes.fromTag — real NBT path (the branch previously untested)
    // -----------------------------------------------------------------------

    @Test
    void fromTag_knownId_returnsMatchingRecipe() {
        CompoundTag tag = new CompoundTag();
        tag.putString("hexerei:rite", "hexerei:tempest");

        RitualRecipe result = RitualRecipes.fromTag(tag);
        assertNotNull(result, "fromTag must not return null for a known rite id");
        assertEquals(RitualRecipes.TEMPEST, result,
                "CompoundTag with hexerei:rite=hexerei:tempest must resolve to TEMPEST");
    }

    @Test
    void fromTag_unknownId_fallsBackToDefault() {
        CompoundTag tag = new CompoundTag();
        tag.putString("hexerei:rite", "hexerei:nonexistent");

        RitualRecipe result = RitualRecipes.fromTag(tag);
        assertNotNull(result, "fallback must not be null when ALL is non-empty");
        assertEquals(RitualRecipes.ALL.get(0), result,
                "unknown rite id in CompoundTag must fall back to first recipe");
    }

    @Test
    void fromTag_missingKey_fallsBackToDefault() {
        // tag exists but has no "hexerei:rite" entry → getString returns ""
        CompoundTag tag = new CompoundTag();

        RitualRecipe result = RitualRecipes.fromTag(tag);
        assertEquals(RitualRecipes.ALL.get(0), result,
                "tag with no hexerei:rite key must fall back to first recipe");
    }

    // -----------------------------------------------------------------------
    // CycleRiteC2SPacket.cycleIndex — extracted helper, size=3
    // -----------------------------------------------------------------------

    @Test
    void cycleIndex_forwardWrap_lastToFirst() {
        // size=3: index 2 + delta 1 → wraps to 0
        assertEquals(0, CycleRiteC2SPacket.cycleIndex(2, 1, 3),
                "forward wrap from index 2 in size-3 list must give 0");
    }

    @Test
    void cycleIndex_backwardWrap_firstToLast() {
        // size=3: index 0 + delta -1 → wraps to 2
        assertEquals(2, CycleRiteC2SPacket.cycleIndex(0, -1, 3),
                "backward wrap from index 0 in size-3 list must give 2");
    }

    @Test
    void cycleIndex_noWrap_midList() {
        // size=3: index 1 + delta 1 → 2 (no wrap)
        assertEquals(2, CycleRiteC2SPacket.cycleIndex(1, 1, 3),
                "mid-list forward step must not wrap");
    }

    @Test
    void cycleIndex_floorMod_avoidsNegativeRemainder() {
        // Java % gives -1 for (0 + -1) % 3; floorMod must return 2
        int raw = (0 + (-1)) % 3;
        int safe = CycleRiteC2SPacket.cycleIndex(0, -1, 3);
        assertNotEquals(raw, safe, "raw % must differ from floorMod for negative delta");
        assertEquals(2, safe, "floorMod must yield 2 for cycleIndex(0,-1,3)");
    }
}
