package com.vel5id.hexerei.ritual;

import com.vel5id.hexerei.power.TaintLevel;
import com.vel5id.hexerei.ritual.TaintPunishment.Effect;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** The pure punishment ladder: which debuffs each {@link TaintLevel} inflicts. */
class TaintLadderTest {

    private static List<String> ids(TaintLevel level) {
        return TaintPunishment.effectsFor(level).stream()
                .map(Effect::effectId)
                .collect(Collectors.toList());
    }

    @Test void noneInflictsNothing() {
        assertTrue(TaintPunishment.effectsFor(TaintLevel.NONE).isEmpty());
    }

    @Test void lowInflictsHunger() {
        assertEquals(List.of("minecraft:hunger"), ids(TaintLevel.LOW));
    }

    @Test void mediumInflictsHungerAndWeakness() {
        assertEquals(List.of("minecraft:hunger", "minecraft:weakness"), ids(TaintLevel.MEDIUM));
    }

    @Test void highInflictsHungerWeaknessAndWither() {
        assertEquals(List.of("minecraft:hunger", "minecraft:weakness", "minecraft:wither"), ids(TaintLevel.HIGH));
    }

    @Test void ladderIsMonotonic() {
        // Each rung is a superset of the one below — punishment only grows with taint.
        assertTrue(ids(TaintLevel.LOW).containsAll(ids(TaintLevel.NONE)));
        assertTrue(ids(TaintLevel.MEDIUM).containsAll(ids(TaintLevel.LOW)));
        assertTrue(ids(TaintLevel.HIGH).containsAll(ids(TaintLevel.MEDIUM)));
    }

    @Test void allAmplifiersAreLevelOne() {
        for (TaintLevel level : TaintLevel.values()) {
            for (Effect e : TaintPunishment.effectsFor(level)) {
                assertEquals(0, e.amplifier(), e.effectId() + " should be level I (amplifier 0)");
            }
        }
    }

    @Test void refreshDurationExceedsPulseCadence() {
        // Duration must outlast the 200-tick pulse so the debuff never gaps while the player stays.
        assertTrue(TaintPunishment.REFRESH_TICKS > 200);
        assertEquals(220, TaintPunishment.REFRESH_TICKS);
    }

    @Test void everyEffectIdIsVanillaNamespaced() {
        for (TaintLevel level : TaintLevel.values()) {
            for (Effect e : TaintPunishment.effectsFor(level)) {
                assertTrue(e.effectId().startsWith("minecraft:"), e.effectId() + " must be a vanilla effect");
            }
        }
    }
}
