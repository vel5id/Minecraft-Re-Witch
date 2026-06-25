package com.vel5id.hexerei.ritual;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The artefact-multiplier seam: {@link RitualContext} default, and {@link Rites#scaledTaint} arithmetic.
 * The world-touching {@code Rites.addRitualTaint} is covered by a GameTest (it boots Minecraft); here we
 * pin the pure parts it delegates to.
 */
class RitualTaintScalingTest {

    @AfterEach void clearContext() {
        // Defensive — a leaked context would poison sibling tests via the shared thread.
        RitualContext.end();
    }

    @Test void defaultMultipliersAreOneWithoutContext() {
        assertEquals(1.0f, RitualContext.currentTaintMul());
        assertEquals(1.0f, RitualContext.currentEffectMul());
        assertNull(RitualContext.current());
    }

    @Test void beginInstallsThenEndRestoresDefault() {
        RitualContext.begin(2.0f, 1.5f);
        assertEquals(2.0f, RitualContext.currentTaintMul());
        assertEquals(1.5f, RitualContext.currentEffectMul());
        assertNotNull(RitualContext.current());
        assertEquals(2.0f, RitualContext.current().taintMul());
        assertEquals(1.5f, RitualContext.current().effectMul());

        RitualContext.end();
        assertEquals(1.0f, RitualContext.currentTaintMul());
        assertEquals(1.0f, RitualContext.currentEffectMul());
        assertNull(RitualContext.current());
    }

    @Test void scaledTaintMultipliesBaseByMultiplier() {
        for (float mul : new float[]{0.25f, 0.5f, 1.0f, 2.0f}) {
            assertEquals(10f * mul, Rites.scaledTaint(10f, mul), 1e-6f);
        }
    }

    @Test void verdantBaseScalesPerArtefact() {
        // Verdant writes base 15 (powerCost 60 / 4): bone_charm -> 7.5, obsidian_skull -> 30.
        assertEquals(7.5f, Rites.scaledTaint(15f, 0.5f), 1e-6f);
        assertEquals(30f, Rites.scaledTaint(15f, 2.0f), 1e-6f);
    }

    @Test void tempestBaseScalesPerArtefact() {
        // Tempest writes a flat base 25: bone_charm -> 12.5, obsidian_skull -> 50.
        assertEquals(12.5f, Rites.scaledTaint(25f, 0.5f), 1e-6f);
        assertEquals(50f, Rites.scaledTaint(25f, 2.0f), 1e-6f);
    }
}
