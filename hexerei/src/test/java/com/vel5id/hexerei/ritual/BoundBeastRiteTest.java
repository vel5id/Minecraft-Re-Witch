package com.vel5id.hexerei.ritual;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoundBeastRiteTest {

    @Test void spawnPosIsBlockCenterOneAboveCircle() {
        Vec3 p = BoundBeastRite.spawnPos(new BlockPos(10, 64, -7));
        assertEquals(10.5, p.x, 1e-9);
        assertEquals(65.0, p.y, 1e-9);
        assertEquals(-6.5, p.z, 1e-9);
    }
}
