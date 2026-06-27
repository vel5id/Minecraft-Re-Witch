package com.vel5id.hexerei.soul;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import static org.junit.jupiter.api.Assertions.*;

class DreamNormalizeTest {
    private static final float EPS = 1e-5f;

    private static Bond mark(float fear) {
        return new Bond(UUID.randomUUID(),
                ResourceLocation.fromNamespaceAndPath("hexerei", "test"),
                Correspondence.THRESHOLD, new Disposition(0f, 0f, fear, 0f),
                null, List.of(), List.of(), 0L, 0L);
    }

    @Test void debtN_normalizesAndClamps() {
        assertEquals(0.0f, DreamNormalize.debtN(0f), EPS);
        assertEquals(0.5f, DreamNormalize.debtN(5f), EPS);    // 5/10
        assertEquals(1.0f, DreamNormalize.debtN(10f), EPS);
        assertEquals(1.0f, DreamNormalize.debtN(20f), EPS);   // clamp high
        assertEquals(0.0f, DreamNormalize.debtN(-3f), EPS);   // clamp low
    }

    @Test void marksN_sumsCarriedFearClamped() {
        assertEquals(0.0f, DreamNormalize.marksN(List.of()), EPS);
        assertEquals(0.5f, DreamNormalize.marksN(List.of(mark(1.0f), mark(1.5f))), EPS); // 2.5/5
        assertEquals(1.0f, DreamNormalize.marksN(List.of(mark(3f), mark(3f))), EPS);     // 6/5 clamp
    }
}
