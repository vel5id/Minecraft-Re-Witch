package com.vel5id.hexerei.power;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RelativePowerSourceTest {
    /** Minimal fake; getRange() controls the range filter (the radius argument is advisory). */
    static final class Fake implements IPowerSource {
        final BlockPos pos;
        final float range;
        final float power;

        Fake(BlockPos pos, float range, float power) {
            this.pos = pos;
            this.range = range;
            this.power = power;
        }

        public Level getWorld() { return null; }
        public BlockPos getLocation() { return pos; }
        public boolean isLocationEqual(BlockPos p) { return pos.equals(p); }
        public boolean consumePower(float r) { return r <= power; }
        public float getCurrentPower() { return power; }
        public float getRange() { return range; }
        public int getEnhancementLevel() { return 0; }
        public boolean isPowerInvalid() { return false; }
    }

    @Test void distanceAndRange() {
        Fake f = new Fake(new BlockPos(10, 64, 0), 16f, 100f);
        RelativePowerSource r = new RelativePowerSource(f, new BlockPos(0, 64, 0));
        assertEquals(100.0, r.distanceSq(), 0.001); // 10^2
        assertTrue(r.isInRange());                  // 100 <= 16^2 = 256
    }

    @Test void outOfRange() {
        Fake f = new Fake(new BlockPos(20, 64, 0), 16f, 100f);
        RelativePowerSource r = new RelativePowerSource(f, new BlockPos(0, 64, 0));
        assertFalse(r.isInRange());                 // 400 > 256
    }
}
