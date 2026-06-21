package com.vel5id.hexerei.block.crop;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CropDropsTest {
    @Test void immatureDropsOneSeedNoProduce() {
        CropDrops.Roll r = CropDrops.roll(RandomSource.create(1L), false, 0, false, false);
        assertEquals(1, r.seeds());
        assertEquals(0, r.produce());
        assertFalse(r.icyNeedle());
    }

    @Test void matureNormalDropsOneProduceAndUpToThreeSeeds() {
        for (long s = 0; s < 20; s++) {
            CropDrops.Roll r = CropDrops.roll(RandomSource.create(s), true, 0, false, false);
            assertEquals(1, r.produce());
            assertTrue(r.seeds() >= 0 && r.seeds() <= 3, "seeds=" + r.seeds());
        }
    }

    @Test void fortuneAddsSeedRolls() {
        CropDrops.Roll r = CropDrops.roll(RandomSource.create(3L), true, 3, false, false); // 6 rolls
        assertTrue(r.seeds() <= 6);
    }

    @Test void matureMindrakeOneSeedMaybeProduce() {
        CropDrops.Roll r = CropDrops.roll(RandomSource.create(2L), true, 0, true, false);
        assertEquals(1, r.seeds());
        assertTrue(r.produce() == 0 || r.produce() == 1);
        assertFalse(r.icyNeedle());
    }

    @Test void matureSnowbellMayDropIcyNeedle() {
        boolean sawNeedle = false;
        for (long s = 0; s < 60 && !sawNeedle; s++) {
            sawNeedle = CropDrops.roll(RandomSource.create(s), true, 0, false, true).icyNeedle();
        }
        assertTrue(sawNeedle);
    }
}
