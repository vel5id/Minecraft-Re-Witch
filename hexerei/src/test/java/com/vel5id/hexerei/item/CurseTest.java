package com.vel5id.hexerei.item;

import com.vel5id.hexerei.soul.Bond;
import com.vel5id.hexerei.soul.Correspondence;
import com.vel5id.hexerei.soul.Disposition;
import com.vel5id.hexerei.soul.SealRef;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Pure logic for the curse system (no MC runtime). Mirrors {@code SealedAmuletTest}'s role. */
class CurseTest {

    private static Bond curseBond(Correspondence domain, boolean echo) {
        return new Bond(
                UUID.randomUUID(),
                Curse.spiritTypeFor(domain, echo ? Curse.Strength.ECHO : Curse.Strength.FULL),
                domain,
                new Disposition(0f, 0f, Curse.initialGrip(), 0f),
                null,
                List.of(),
                List.of(),
                0L, 0L);
    }

    // ---- effectFor ----

    @Test void effectFor_mapsThreeDomainsAtBothStrengths() {
        assertEquals(Curse.Kind.CLUMSY, Curse.effectFor(Correspondence.STONE, Curse.Strength.FULL).kind());
        assertEquals(Curse.Kind.UNLUCKY, Curse.effectFor(Correspondence.THRESHOLD, Curse.Strength.FULL).kind());
        assertEquals(Curse.Kind.WEAK, Curse.effectFor(Correspondence.DEATH, Curse.Strength.FULL).kind());
        assertEquals(Curse.Strength.ECHO, Curse.effectFor(Correspondence.STONE, Curse.Strength.ECHO).strength());
        assertEquals(Curse.Strength.FULL, Curse.effectFor(Correspondence.DEATH, Curse.Strength.FULL).strength());
    }

    @Test void effectFor_unmappedDomainsHaveNoCurse() {
        for (Correspondence d : new Correspondence[]{Correspondence.FOREST, Correspondence.WATER, Correspondence.SKY}) {
            assertNull(Curse.effectFor(d, Curse.Strength.FULL), d + " should have no curse");
        }
    }

    // ---- FULL vs ECHO values (echo ≈ 20% strength) ----

    @Test void clumsyEchoIsMuchWeakerThanFull() {
        Curse.ClumsyValues full = Curse.clumsy(Curse.Strength.FULL);
        Curse.ClumsyValues echo = Curse.clumsy(Curse.Strength.ECHO);
        // reach reduction: full 0.40, echo 0.12 → echo ≤ 30% of full reduction (and strictly weaker)
        double fullReachReduction = 1.0 - full.reachFactor();
        double echoReachReduction = 1.0 - echo.reachFactor();
        assertTrue(echoReachReduction < fullReachReduction, "echo must reduce reach less than full");
        assertTrue(echoReachReduction <= 0.30 * fullReachReduction, "echo ~20%, got " + (echoReachReduction / fullReachReduction));
        assertTrue(echo.gravityFactor() < full.gravityFactor(), "echo must add less gravity than full");
    }

    @Test void unluckyEchoIsOneTierDown() {
        Curse.UnluckyValues full = Curse.unlucky(Curse.Strength.FULL);
        Curse.UnluckyValues echo = Curse.unlucky(Curse.Strength.ECHO);
        assertTrue(echo.luckAmount() > full.luckAmount(), "echo must subtract less luck (less negative)");
        assertEquals(0, echo.unluckAmp());
        assertEquals(1, full.unluckAmp());
    }

    @Test void weaknessEchoIsOneTierDown() {
        assertEquals(1, Curse.weaknessAmp(Curse.Strength.FULL));
        assertEquals(0, Curse.weaknessAmp(Curse.Strength.ECHO));
    }

    // ---- grip / finiteness ----

    @Test void curseReachesSpentInExactlyCurseTicks() {
        float fear = Curse.initialGrip();
        int steps = 0;
        while (!Curse.isSpent(fear) && steps < Curse.CURSE_TICKS + 10) {
            fear = Curse.gripAfter(fear);
            steps++;
        }
        assertEquals(Curse.CURSE_TICKS, steps, "a curse must spend exactly CURSE_TICKS ticks");
    }

    @Test void isSpentBoundary() {
        assertFalse(Curse.isSpent(1f));
        assertTrue(Curse.isSpent(0f));
        assertTrue(Curse.isSpent(-0.1f));
    }

    // ---- isEcho / spiritTypeFor ----

    @Test void isEchoReadsTheSuffix() {
        assertTrue(Curse.isEcho(curseBond(Correspondence.STONE, true)));
        assertFalse(Curse.isEcho(curseBond(Correspondence.STONE, false)));
    }

    @Test void spiritTypeForProducesDistinctEchoPaths() {
        ResourceLocation full = Curse.spiritTypeFor(Correspondence.STONE, Curse.Strength.FULL);
        ResourceLocation echo = Curse.spiritTypeFor(Correspondence.STONE, Curse.Strength.ECHO);
        assertEquals("hexerei:stone_curse", full.toString());
        assertEquals("hexerei:stone_curse_echo", echo.toString());
        assertTrue(Curse.isEcho(new Bond(UUID.randomUUID(), echo, Correspondence.STONE,
                Disposition.EMPTY, null, List.of(), List.of(), 0L, 0L)));
        assertFalse(Curse.isEcho(new Bond(UUID.randomUUID(), full, Correspondence.STONE,
                Disposition.EMPTY, new SealRef(1f), List.of(), List.of(), 0L, 0L)));
    }
}
