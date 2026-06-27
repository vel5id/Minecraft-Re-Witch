package com.vel5id.hexerei.soul;

import org.junit.jupiter.api.Test;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The persistence path the restart/relog anti-stranding recovery depends on: a DreamState must survive
 * serialize → deserialize unchanged. No Minecraft runtime needed (plain NBT + value types).
 */
class DreamStateRoundTripTest {
    private static final ResourceKey<Level> NETHER =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("minecraft", "the_nether"));

    private static DreamState roundTrip(DreamState a) {
        DreamState b = new DreamState();
        b.deserializeNBT(null, a.serializeNBT(null));   // provider is unused by DreamState
        return b;
    }

    @Test void begunStateRoundTripsExactly() {
        DreamState a = new DreamState();
        a.begin(NETHER, new BlockPos(10, 70, -5), 1234L);
        DreamState b = roundTrip(a);
        assertTrue(b.dreaming());
        assertEquals(1234L, b.wakeTick());
        assertEquals(NETHER, b.returnDim());
        assertEquals(new BlockPos(10, 70, -5), b.returnPos());
    }

    @Test void freshStateRoundTripsNotDreamingWithNullTarget() {
        DreamState b = roundTrip(new DreamState());   // never began
        assertFalse(b.dreaming());
        assertNull(b.returnDim());
        assertNull(b.returnPos());
    }

    @Test void clearedStateRoundTripsNotDreaming() {
        DreamState a = new DreamState();
        a.begin(NETHER, new BlockPos(1, 2, 3), 99L);
        a.clear();
        assertFalse(roundTrip(a).dreaming());
    }
}
