package com.vel5id.hexerei.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ArtefactDefsTest {

    @Test void byItemIdRoundTripsEveryArtefact() {
        for (ArtefactDef def : ArtefactDefs.ALL) {
            assertSame(def, ArtefactDefs.byItemId(def.id()), "byItemId should resolve " + def.id());
        }
    }

    @Test void byItemIdReturnsNullForUnknown() {
        assertNull(ArtefactDefs.byItemId("not_an_artefact"));
    }

    @Test void idsAreUnique() {
        assertEquals(ArtefactDefs.ALL.size(), ArtefactDefs.BY_ID.size());
    }

    @Test void expectedCatalogContents() {
        assertEquals(3, ArtefactDefs.ALL.size());
    }

    @Test void boneCharmTriple() {
        ArtefactDef d = ArtefactDefs.BONE_CHARM;
        assertEquals("bone_charm", d.id());
        assertEquals(0.5f, d.taintMul());
        assertEquals(1.0f, d.effectMul());
        assertEquals(0, d.enhancement());
    }

    @Test void waxPoppetTriple() {
        ArtefactDef d = ArtefactDefs.WAX_POPPET;
        assertEquals("wax_poppet", d.id());
        assertEquals(0.25f, d.taintMul());
        assertEquals(0.9f, d.effectMul());
        assertEquals(0, d.enhancement());
    }

    @Test void obsidianSkullTriple() {
        ArtefactDef d = ArtefactDefs.OBSIDIAN_SKULL;
        assertEquals("obsidian_skull", d.id());
        assertEquals(2.0f, d.taintMul());
        assertEquals(1.5f, d.effectMul());
        assertEquals(1, d.enhancement());
    }

    @Test void purifiersDampenAndAmplifierGreedsTaint() {
        // Purifiers leave less taint than the baseline; the amplifier leaves more.
        assertTrue(ArtefactDefs.BONE_CHARM.taintMul() < 1.0f);
        assertTrue(ArtefactDefs.WAX_POPPET.taintMul() < 1.0f);
        assertTrue(ArtefactDefs.OBSIDIAN_SKULL.taintMul() > 1.0f);
    }
}
