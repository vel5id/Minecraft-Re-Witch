package com.vel5id.hexerei.soul;

import org.junit.jupiter.api.Test;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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

    private static ListTag sampleSnapshot() {
        // Two synthetic inventory entries — shaped like Inventory#save output (a "Slot" byte),
        // but using plain NBT so no Minecraft registry/bootstrap is needed in a pure unit test.
        ListTag list = new ListTag();
        CompoundTag a = new CompoundTag();
        a.putByte("Slot", (byte) 0);
        a.putString("id", "minecraft:diamond");
        a.putByte("Count", (byte) 3);
        list.add(a);
        CompoundTag b = new CompoundTag();
        b.putByte("Slot", (byte) 100);
        b.putString("id", "minecraft:iron_helmet");
        b.putByte("Count", (byte) 1);
        list.add(b);
        return list;
    }

    @Test void sealedSnapshotRoundTripsExactly() {
        DreamState a = new DreamState();
        a.begin(NETHER, new BlockPos(1, 2, 3), 42L);
        a.seal(sampleSnapshot());
        DreamState b = roundTrip(a);
        assertTrue(b.sealed(), "sealed flag must survive the round-trip");
        assertEquals(sampleSnapshot(), b.invSnapshot(), "snapshot NBT must survive verbatim");
    }

    @Test void unsealDropsSnapshotAndFlag() {
        DreamState a = new DreamState();
        a.seal(sampleSnapshot());
        a.unseal();
        assertFalse(a.sealed());
        assertTrue(a.invSnapshot().isEmpty(), "unseal drops the snapshot");
        DreamState b = roundTrip(a);
        assertFalse(b.sealed(), "an unsealed state round-trips unsealed");
        assertTrue(b.invSnapshot().isEmpty());
    }

    @Test void freshStateIsUnsealedWithEmptySnapshot() {
        DreamState s = new DreamState();
        assertFalse(s.sealed());
        assertTrue(s.invSnapshot().isEmpty());
    }
}
