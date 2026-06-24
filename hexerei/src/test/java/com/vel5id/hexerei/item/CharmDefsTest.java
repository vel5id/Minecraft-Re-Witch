package com.vel5id.hexerei.item;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CharmDefsTest {

    @Test void byIdRoundTripsEveryCharm() {
        for (CharmDef def : CharmDefs.ALL) {
            assertSame(def, CharmDefs.byId(def.id()), "byId should resolve " + def.id());
        }
    }

    @Test void byIdReturnsNullForUnknown() {
        assertNull(CharmDefs.byId("not_a_charm"));
    }

    @Test void everyEffectIdIsMinecraftNamespacedWithPath() {
        for (CharmDef def : CharmDefs.ALL) {
            String[] parts = def.effectId().split(":", 2);
            assertEquals(2, parts.length, def.id() + " effectId must be namespaced");
            assertEquals("minecraft", parts[0], def.id() + " effect must be vanilla");
            assertFalse(parts[1].isEmpty(), def.id() + " effect path must be non-empty");
        }
    }

    @Test void amplifiersAreNonNegative() {
        for (CharmDef def : CharmDefs.ALL) {
            assertTrue(def.amplifier() >= 0, def.id() + " amplifier must be >= 0");
        }
    }

    @Test void idsAreUnique() {
        assertEquals(CharmDefs.ALL.size(), CharmDefs.BY_ID.size());
    }

    @Test void expectedCatalogContents() {
        assertEquals(3, CharmDefs.ALL.size());
        assertEquals(ActiveMode.ALWAYS, CharmDefs.WARD.mode());
        assertEquals(ActiveMode.LOW_HEALTH, CharmDefs.BLOODLUST.mode());
        assertEquals(6, CharmDefs.BLOODLUST.param());
        assertEquals(ActiveMode.AURA_DEBUFF, CharmDefs.HEXBANE.mode());
        assertEquals(5, CharmDefs.HEXBANE.param());
    }
}
