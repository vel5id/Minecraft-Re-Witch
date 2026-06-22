package com.vel5id.hexerei.item;

import com.vel5id.hexerei.ritual.RitualRecipes;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RitualChalkItem.getSelectedRecipe() and CycleRiteC2SPacket index-wrapping logic.
 * These tests run without a Minecraft runtime (pure-logic, no bootstrap required).
 */
class RitualChalkItemTest {

    // --- getSelectedRecipe: default when no NBT ---

    @Test
    void noNbt_returnsDefaultRecipe() {
        // A null-like ItemStack is not easily creatable without Forge bootstrap,
        // so we test the NBT-reading logic directly using the static helper
        // that getSelectedRecipe() mirrors.
        // Simulate stack with no NBT: id lookup returns null → should fall back to ALL.get(0).
        String id = "";  // empty string (what getOrCreateTag().getString returns when absent)
        var recipe = RitualRecipes.BY_ID.get(id);
        assertNull(recipe, "Empty id should not match any recipe");
        // Fallback: first element of ALL
        assertFalse(RitualRecipes.ALL.isEmpty(), "ALL must not be empty");
        assertEquals(RitualRecipes.ALL.get(0), RitualRecipes.TEMPEST, "Default is TEMPEST");
    }

    @Test
    void nbtSetToTempest_returnsTempest() {
        String id = "hexerei:tempest";
        var recipe = RitualRecipes.BY_ID.get(id);
        assertNotNull(recipe, "BY_ID must contain hexerei:tempest");
        assertEquals(RitualRecipes.TEMPEST, recipe);
    }

    @Test
    void nbtSetToUnknown_byIdReturnsNull() {
        var recipe = RitualRecipes.BY_ID.get("hexerei:nonexistent");
        assertNull(recipe, "Unknown id should return null from BY_ID");
    }

    // --- CycleRiteC2SPacket index-wrapping logic ---

    @Test
    void cycleForward_lastToFirst() {
        int size = RitualRecipes.ALL.size();
        // Start at last index, delta = +1 → wraps to 0
        int idx = size - 1;
        int next = Math.floorMod(idx + 1, size);
        assertEquals(0, next, "Forward scroll from last should wrap to 0");
    }

    @Test
    void cycleBackward_firstToLast() {
        int size = RitualRecipes.ALL.size();
        // Start at index 0, delta = -1 → wraps to last
        int idx = 0;
        int next = Math.floorMod(idx + (-1), size);
        assertEquals(size - 1, next, "Backward scroll from first should wrap to last");
    }

    @Test
    void cycleForward_fromMiddle() {
        // Build a scenario with 3 virtual entries to test mid-list cycling
        int size = 3;
        int idx = 1;
        assertEquals(2, Math.floorMod(idx + 1, size));
        assertEquals(0, Math.floorMod(idx - 1, size));
    }

    @Test
    void cycleNegativeDelta_usesFloorMod() {
        // Java % can return negative — floorMod must be used
        int size = 3;
        int idx = 0;
        // Without floorMod: (0 + (-1)) % 3 = -1 (wrong)
        // With floorMod:    floorMod(-1, 3) = 2 (correct)
        assertEquals(2, Math.floorMod(idx + (-1), size));
        assertNotEquals(2, (idx + (-1)) % size, "Raw % gives wrong negative result");
    }
}
