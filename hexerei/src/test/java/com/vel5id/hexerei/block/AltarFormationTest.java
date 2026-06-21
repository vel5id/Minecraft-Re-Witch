package com.vel5id.hexerei.block;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AltarFormationTest {
    private static Set<BlockPos> rect(int w, int d) {
        Set<BlockPos> s = new HashSet<>();
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                s.add(new BlockPos(x, 64, z));
            }
        }
        return s;
    }

    @Test void twoByThreeForms() {
        Set<BlockPos> s = rect(2, 3); // 6 cells
        BlockPos core = AltarFormation.findCore(s, new BlockPos(0, 64, 0));
        assertNotNull(core);
        assertTrue(s.contains(core));
    }

    @Test void threeByTwoForms() {
        Set<BlockPos> s = rect(3, 2);
        assertNotNull(AltarFormation.findCore(s, new BlockPos(0, 64, 0)));
    }

    @Test void twoByTwoDoesNotForm() { // 4 cells != 6
        assertNull(AltarFormation.findCore(rect(2, 2), new BlockPos(0, 64, 0)));
    }

    @Test void straightLineOfSixDoesNotForm() { // each end has 1 neighbour (<2)
        Set<BlockPos> s = new HashSet<>();
        for (int x = 0; x < 6; x++) {
            s.add(new BlockPos(x, 64, 0));
        }
        assertNull(AltarFormation.findCore(s, new BlockPos(0, 64, 0)));
    }

    @Test void threeByThreeDoesNotForm() { // 9 cells; centre has 4 neighbours (>3) and size != 6
        assertNull(AltarFormation.findCore(rect(3, 3), new BlockPos(0, 64, 0)));
    }

    @Test void coreIsDeterministicFirstVisited() {
        Set<BlockPos> s = rect(2, 3);
        assertEquals(new BlockPos(0, 64, 0), AltarFormation.findCore(s, new BlockPos(0, 64, 0)));
    }

    @Test void verticalStackIgnored() { // formation is horizontal only
        Set<BlockPos> s = new HashSet<>();
        for (int y = 0; y < 6; y++) {
            s.add(new BlockPos(0, 64 + y, 0));
        }
        assertNull(AltarFormation.findCore(s, new BlockPos(0, 64, 0)));
    }
}
