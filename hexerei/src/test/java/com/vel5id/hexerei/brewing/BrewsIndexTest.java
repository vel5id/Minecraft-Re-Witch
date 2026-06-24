package com.vel5id.hexerei.brewing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BrewsIndexTest {

    @Test void indexOfReturnsCatalogPosition() {
        assertEquals(1, Brews.indexOf("sleeping_draught"));
        assertEquals(2, Brews.indexOf("frailty"));
        assertEquals(3, Brews.indexOf("witchs_sight"));
        assertEquals(4, Brews.indexOf("bloodwort_tonic"));
        assertEquals(5, Brews.indexOf("hags_swiftness"));
        assertEquals(6, Brews.indexOf("withering_bile"));
    }

    @Test void indexOfUnknownIsZero() {
        assertEquals(0, Brews.indexOf("not_a_brew"));
        assertEquals(0, Brews.indexOf(""));
    }

    @Test void everyCatalogBrewHasAUniqueIndex() {
        // the predicate float (indexOf/10) must be distinct per brew and match the brew.json overrides
        var seen = new java.util.HashSet<Integer>();
        for (String id : Brews.BY_ID.keySet()) {
            int idx = Brews.indexOf(id);
            assertTrue(idx >= 1 && idx <= Brews.BY_ID.size(), id + " index in range");
            assertTrue(seen.add(idx), "duplicate index for " + id);
        }
    }
}
