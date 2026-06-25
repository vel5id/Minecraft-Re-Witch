package com.vel5id.hexerei.soul;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BondCodecTest {

    private static Bond sample(SealRef seal) {
        return new Bond(
                UUID.fromString("00000000-0000-0000-0000-0000000000aa"),
                new ResourceLocation("hexerei", "forest_warden"),
                Correspondence.FOREST,
                new Disposition(1.5f, 0.6f, 0f, 0.2f),
                seal,
                List.of(new Mark(MarkScope.BIOME, Correspondence.FOREST, 0.5f)),
                List.of(new LedgerEntry(LedgerType.RELEASED, 1f, 100L),
                        new LedgerEntry(LedgerType.SEALED, 0.5f, 120L)),
                100L, 120L);
    }

    private static Bond roundTrip(Bond bond) {
        JsonElement json = Bond.CODEC.encodeStart(JsonOps.INSTANCE, bond)
                .getOrThrow(false, e -> fail("encode failed: " + e));
        return Bond.CODEC.parse(JsonOps.INSTANCE, json)
                .getOrThrow(false, e -> fail("decode failed: " + e));
    }

    @Test void roundTrip_sealedBond_preservesEverything() {
        Bond bond = sample(new SealRef(0.75f));
        Bond back = roundTrip(bond);
        assertEquals(bond, back);
        assertTrue(back.isSealed());
        assertEquals(0.75f, back.seal().integrity(), 1e-6);
    }

    @Test void roundTrip_freeBond_nullSealSurvives() {
        Bond bond = sample(null);
        Bond back = roundTrip(bond);
        assertEquals(bond, back);
        assertFalse(back.isSealed());
        assertNull(back.seal());
    }

    @Test void resentmentFloor_isDebtPlusMarkSeverity() {
        Bond bond = sample(null);              // debt 1.5 + one mark severity 0.5
        assertEquals(2.0f, bond.resentmentFloor(), 1e-6);
    }

    @Test void unknownCorrespondence_failsCodec() {
        // a hand-built json with a bogus domain must not silently parse
        JsonElement json = Bond.CODEC.encodeStart(JsonOps.INSTANCE, sample(null))
                .getOrThrow(false, e -> fail(e));
        json.getAsJsonObject().addProperty("domain", "void");
        assertTrue(Bond.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
    }
}
