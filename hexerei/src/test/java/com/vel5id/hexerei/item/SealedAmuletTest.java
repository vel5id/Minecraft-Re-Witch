package com.vel5id.hexerei.item;

import com.vel5id.hexerei.soul.Bond;
import com.vel5id.hexerei.soul.Correspondence;
import com.vel5id.hexerei.soul.Disposition;
import com.vel5id.hexerei.soul.Mark;
import com.vel5id.hexerei.soul.MarkScope;
import com.vel5id.hexerei.soul.SealRef;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure logic for the sealed-amulet system (no MC runtime). Mirrors the retired {@code CharmChargeTest}'s
 * role, but over the vector model: domain→effect mapping, debt/resentment/integrity math, the finite-
 * lifespan invariant (Article III), and the NbtOps storage round-trip the {@code AmuletItem} relies on.
 */
class SealedAmuletTest {

    // ---- effectFor ----

    @Test void effectFor_mapsThreeDomainsToTheirNatures() {
        SealedAmulet.AmuletEffect forest = SealedAmulet.effectFor(Correspondence.FOREST);
        assertNotNull(forest);
        assertEquals("minecraft:resistance", forest.effectId());
        assertEquals(ActiveMode.ALWAYS, forest.mode());

        SealedAmulet.AmuletEffect death = SealedAmulet.effectFor(Correspondence.DEATH);
        assertNotNull(death);
        assertEquals("minecraft:weakness", death.effectId());
        assertEquals(ActiveMode.AURA_DEBUFF, death.mode());
        assertEquals(5, death.param());

        SealedAmulet.AmuletEffect threshold = SealedAmulet.effectFor(Correspondence.THRESHOLD);
        assertNotNull(threshold);
        assertEquals("minecraft:strength", threshold.effectId());
        assertEquals(ActiveMode.LOW_HEALTH, threshold.mode());
        assertEquals(6, threshold.param());
    }

    @Test void effectFor_unmappedDomainsHaveNoAmuletYet() {
        for (Correspondence d : new Correspondence[]{Correspondence.STONE, Correspondence.WATER, Correspondence.SKY}) {
            assertNull(SealedAmulet.effectFor(d), d + " should have no amulet recipe yet");
        }
    }

    // ---- debt / resentment / integrity ----

    @Test void debtDeltaIsDrawRate() {
        assertEquals(SealedAmulet.DRAW_RATE, SealedAmulet.debtDelta(), 1e-9);
    }

    @Test void resentmentIsUnpaidDebtOverLoyalty() {
        assertEquals(0f, SealedAmulet.resentmentFor(0f, 0f), 1e-9);
        assertEquals(2f, SealedAmulet.resentmentFor(2f, 0f), 1e-9);
        assertEquals(0f, SealedAmulet.resentmentFor(1f, 3f), 1e-9);   // loyalty covers the debt
        assertEquals(1f, SealedAmulet.resentmentFor(3f, 2f), 1e-9);   // one over
    }

    @Test void integrityDeltaIsNegativeProportionalToResentment() {
        assertEquals(0f, SealedAmulet.integrityDelta(0f), 1e-9);
        assertTrue(SealedAmulet.integrityDelta(2f) < 0f);
        assertEquals(-SealedAmulet.GRIND_RATE * 2f, SealedAmulet.integrityDelta(2f), 1e-9);
    }

    @Test void shouldBreakAtAndBelowZero() {
        assertTrue(SealedAmulet.shouldBreak(0f));
        assertTrue(SealedAmulet.shouldBreak(-0.01f));
        assertFalse(SealedAmulet.shouldBreak(0.001f));
    }

    // ---- lifespan invariant: finite + reasonable (no permanent free power, no instant break) ----

    @Test void wornAmuletBreaksWithinReasonableBandAndNotInstantly() {
        float debt = 0f;
        float integrity = SealedAmulet.FULL_INTEGRITY;
        int brokeAt = -1;
        int maxSeconds = 20000; // ~5.5h of continuous wear — an un-tended amulet must break well before this
        for (int s = 0; s < maxSeconds; s++) {
            debt += SealedAmulet.debtDelta();
            integrity += SealedAmulet.integrityDelta(SealedAmulet.resentmentFor(debt, 0f));
            if (SealedAmulet.shouldBreak(integrity)) {
                brokeAt = s;
                break;
            }
        }
        assertNotEquals(-1, brokeAt, "an amulet must eventually break (no permanent free power — Article III)");
        assertTrue(brokeAt > 100, "amulet broke after only " + brokeAt + "s — far too fast");
        assertTrue(brokeAt < 15000, "amulet lasted " + brokeAt + "s — too close to permanent");
    }

    @Test void loyaltyKeepsTheSealIntact() {
        // A tended spirit (loyalty offsets resentment) does not grind its seal — the APPEASE/FEED payoff.
        float debt = 0f;
        float integrity = SealedAmulet.FULL_INTEGRITY;
        float loyalty = 1000f; // vastly exceeds any debt accrued in the window
        for (int s = 0; s < 5000; s++) {
            debt += SealedAmulet.debtDelta();
            integrity += SealedAmulet.integrityDelta(SealedAmulet.resentmentFor(debt, loyalty));
        }
        assertFalse(SealedAmulet.shouldBreak(integrity), "loyalty must keep the seal from grinding");
    }

    // ---- playerCondition ----

    @Test void lowHealthConditionAtThreshold() {
        SealedAmulet.AmuletEffect fx = SealedAmulet.effectFor(Correspondence.THRESHOLD);
        assertFalse(SealedAmulet.playerCondition(fx, 20f));
        assertTrue(SealedAmulet.playerCondition(fx, 6f));  // == param
        assertTrue(SealedAmulet.playerCondition(fx, 1f));
    }

    @Test void alwaysModeAppliesRegardlessOfHealth() {
        SealedAmulet.AmuletEffect fx = SealedAmulet.effectFor(Correspondence.FOREST);
        assertTrue(SealedAmulet.playerCondition(fx, 20f));
        assertTrue(SealedAmulet.playerCondition(fx, 1f));
    }

    // ---- storage: a sealed Bond round-trips through NbtOps (the mechanism AmuletItem stores it) ----

    @Test void sealedBondRoundTripsThroughNbtOps() {
        Bond bond = new Bond(
                UUID.fromString("00000000-0000-0000-0000-0000000000aa"),
                new ResourceLocation("hexerei", "forest_warden"),
                Correspondence.FOREST,
                new Disposition(1.4f, 0.5f, 0f, 0f),
                new SealRef(0.75f),
                List.of(new Mark(MarkScope.DOMAIN, Correspondence.FOREST, SealedAmulet.SEAL_MARK_SEVERITY)),
                List.of(),
                100L, 120L);
        Tag nbt = Bond.CODEC.encodeStart(NbtOps.INSTANCE, bond)
                .getOrThrow(false, e -> fail("encode failed: " + e));
        Bond back = Bond.CODEC.parse(NbtOps.INSTANCE, nbt)
                .getOrThrow(false, e -> fail("decode failed: " + e));
        assertEquals(bond, back);
        assertTrue(back.isSealed());
        assertEquals(0.75f, back.seal().integrity(), 1e-6);
    }
}
