package com.vel5id.hexerei.soul;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReleaseBlocksTest {

    private static ResourceLocation hex(String p) {
        return new ResourceLocation("hexerei", p);
    }

    @Test void grownCrop_freesEssenceOfItsDomain() {
        ReleaseBlocks.Release r = ReleaseBlocks.get(hex("mandrake"));
        assertNotNull(r);
        assertEquals(Correspondence.THRESHOLD, r.domain());
        // a take of magnitude 1.0 yields ESSENCE_PER_MAGNITUDE essence
        Act act = EssenceSource.breakRelease(r.domain(), r.magnitude(), r.defilement());
        assertEquals(EssenceSource.ESSENCE_PER_MAGNITUDE * r.magnitude(),
                EssenceSource.essenceFrom(act), 1e-4);
    }

    @Test void ordinaryBlock_freesNothing() {
        assertNull(ReleaseBlocks.get(new ResourceLocation("minecraft", "stone")));
        assertNull(ReleaseBlocks.get(hex("altar")));
    }

    @Test void deadlyHerbsCarryDefilement() {
        assertTrue(ReleaseBlocks.get(hex("belladonna")).defilement() > 0f);
        assertTrue(ReleaseBlocks.get(hex("wolfsbane")).defilement() > 0f);
        assertEquals(0f, ReleaseBlocks.get(hex("celandine")).defilement(), 1e-6); // cleansing herb, no defile
    }

    @Test void breakReleaseDisturbsTheRightDomain() {
        ReleaseBlocks.Release r = ReleaseBlocks.get(hex("blood_moss"));
        Act act = EssenceSource.breakRelease(r.domain(), r.magnitude(), r.defilement());
        // disturbance lands on DEATH, not elsewhere
        assertTrue(Integration.disturbanceDelta(act).getOrDefault(Correspondence.DEATH, 0f) > 0f);
        assertFalse(Integration.disturbanceDelta(act).containsKey(Correspondence.FOREST));
    }
}
