package com.vel5id.hexerei;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SanityTest {
    @Test void modIdIsHexerei() {
        assertEquals("hexerei", HexereiMod.MODID);
    }
}
