package com.vel5id.hexerei.power;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PowerSourceTest {
    @Test void powerIsMinCountLimitTimesFactor() {
        PowerSource s = new PowerSource(4, 20);
        assertEquals(0, s.getPower());
        for (int i = 0; i < 5; i++) s.increment();
        assertEquals(20, s.getPower());            // min(5,20)*4
        for (int i = 0; i < 100; i++) s.increment();
        assertEquals(80, s.getPower());            // min(105,20)*4
    }

    @Test void dragonEggValue() {
        PowerSource s = new PowerSource(250, 1);
        s.increment();
        s.increment();
        assertEquals(250, s.getPower());           // min(2,1)*250
    }

    @Test void tableHasExpectedVanillaValues() {
        assertEntry(AltarPowerTable.TAG_SAPLING, 4, 20);
        assertEntry(AltarPowerTable.TAG_LOG, 2, 50);
        assertEntry(AltarPowerTable.TAG_LEAVES, 3, 100);
        assertEntry(AltarPowerTable.CATCHALL, 2, 4);
        assertEntry(AltarPowerTable.FLOWER, 4, 30);
        assertEntry(AltarPowerTable.VANILLA.get("minecraft:grass_block"), 2, 80);
        assertEntry(AltarPowerTable.VANILLA.get("minecraft:dragon_egg"), 250, 1);
        assertEntry(AltarPowerTable.VANILLA.get("minecraft:farmland"), 1, 100);
        assertEntry(AltarPowerTable.VANILLA.get("minecraft:water"), 1, 50);
        assertEntry(AltarPowerTable.VANILLA.get("minecraft:grass"), 3, 50);
    }

    private static void assertEntry(AltarPowerTable.Entry e, int factor, int limit) {
        assertNotNull(e);
        assertEquals(factor, e.factor());
        assertEquals(limit, e.limit());
    }
}
