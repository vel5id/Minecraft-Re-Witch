package com.vel5id.hexerei.block.crop;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CropGrowthTest {
    @Test void hugeRateAlwaysGrows() {
        // f=1000 -> (int)(25/1000)+1 = 1 -> nextInt(1)==0 always true.
        for (long s = 0; s < 20; s++) {
            assertTrue(CropGrowth.shouldGrow(RandomSource.create(s), 1000.0F));
        }
    }

    @Test void rate25GivesBothOutcomes() {
        // f=25 -> (int)(25/25)+1 = 2 -> nextInt(2)==0 ~50%; over one RNG stream both outcomes occur.
        RandomSource r = RandomSource.create(42L);
        boolean grew = false, stayed = false;
        for (int i = 0; i < 300 && !(grew && stayed); i++) {
            if (CropGrowth.shouldGrow(r, 25.0F)) grew = true; else stayed = true;
        }
        assertTrue(grew && stayed);
    }

    @Test void rateJustAbove25AlwaysGrows() {
        // f=26 -> (int)(25/26)=0 -> nextInt(1)==0 always true.
        for (long s = 0; s < 20; s++) {
            assertTrue(CropGrowth.shouldGrow(RandomSource.create(s), 26.0F));
        }
    }

    @Test void bonemealPlusOneWhenNotBig() {
        assertEquals(3, CropGrowth.bonemealIncrease(RandomSource.create(1L), 2, 7, false));
    }

    @Test void bonemealBigClampsToMax() {
        int v = CropGrowth.bonemealIncrease(RandomSource.create(5L), 3, 4, true); // 3 + [2..4] -> clamp 4
        assertEquals(4, v);
    }

    @Test void bonemealBigInRange() {
        for (long s = 0; s < 20; s++) {
            int v = CropGrowth.bonemealIncrease(RandomSource.create(s), 0, 7, true);
            assertTrue(v >= 2 && v <= 7, "v=" + v);
        }
    }
}
