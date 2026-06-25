package com.vel5id.hexerei.soul;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReagentRegistryTest {

    private static ResourceLocation hex(String path) {
        return new ResourceLocation("hexerei", path);
    }

    @Test void knownReagent_resolvesWithDomain() {
        ReagentDescriptor mandrake = ReagentRegistry.get(hex("mandrake_root"));
        assertNotNull(mandrake, "mandrake_root must be a seeded reagent");
        assertTrue(mandrake.magnitude() > 0f);
        assertNotNull(mandrake.domain());
    }

    @Test void unknownReagent_isNull() {
        assertNull(ReagentRegistry.get(hex("not_a_reagent")));
        assertFalse(ReagentRegistry.has(hex("not_a_reagent")));
    }

    @Test void seededRoster_isNonTrivial() {
        // a few core ritual reagents/herbs must be present
        for (String id : new String[]{"mandrake_root", "belladonna_flower", "wolfsbane", "wormwood"}) {
            assertTrue(ReagentRegistry.has(hex(id)), id + " should be a known reagent");
        }
    }

    @Test void descriptorAssemblesIntoAct() {
        ReagentDescriptor d = ReagentRegistry.get(hex("belladonna_flower"));
        Act a = d.toAct();
        assertEquals(1.0f, a.domainWeight(d.domain()), 1e-6);
        assertEquals(d.magnitude(), a.magnitude(), 1e-6);
    }
}
