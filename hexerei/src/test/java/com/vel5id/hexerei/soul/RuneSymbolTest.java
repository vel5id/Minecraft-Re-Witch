package com.vel5id.hexerei.soul;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RuneSymbolTest {

    @Test void eighteenSymbols_threePerDomain() {
        assertEquals(18, RuneSymbol.VALUES.length);
        Map<Correspondence, Integer> count = new EnumMap<>(Correspondence.class);
        for (RuneSymbol s : RuneSymbol.VALUES) {
            count.merge(s.domain(), 1, Integer::sum);
        }
        for (Correspondence c : Correspondence.values()) {
            assertEquals(3, count.getOrDefault(c, 0), c + " must have exactly 3 runes");
        }
    }

    @Test void keyRoundTrips() {
        for (RuneSymbol s : RuneSymbol.VALUES) {
            assertSame(s, RuneSymbol.byKey(s.key()));
        }
        assertNull(RuneSymbol.byKey("not_a_rune"));
    }

    @Test void nextCyclesThroughAllAndWraps() {
        RuneSymbol s = RuneSymbol.VALUES[0];
        for (int i = 0; i < RuneSymbol.VALUES.length; i++) s = s.next();
        assertSame(RuneSymbol.VALUES[0], s); // full loop returns to start
    }
}
