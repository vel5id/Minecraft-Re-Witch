package com.vel5id.hexerei.soul;

import org.junit.jupiter.api.Test;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import static org.junit.jupiter.api.Assertions.*;

class DreamReturnTest {
    private static ResourceKey<Level> key(String path) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("minecraft", path));
    }
    private static final ResourceKey<Level> OVERWORLD = key("overworld");
    private static final ResourceKey<Level> NETHER = key("the_nether");
    private static final BlockPos SPAWN = new BlockPos(0, 64, 0);

    @Test void savedTargetIsReturnedWhenPresent() {
        BlockPos saved = new BlockPos(10, 70, -5);
        DreamReturn r = DreamReturn.resolveTarget(NETHER, saved, OVERWORLD, SPAWN);
        assertEquals(NETHER, r.dim());
        assertEquals(saved, r.pos());
    }

    @Test void nullSavedDimFallsBack() {
        DreamReturn r = DreamReturn.resolveTarget(null, new BlockPos(1, 2, 3), OVERWORLD, SPAWN);
        assertEquals(OVERWORLD, r.dim());
        assertEquals(SPAWN, r.pos());
    }

    @Test void nullSavedPosFallsBack() {
        DreamReturn r = DreamReturn.resolveTarget(NETHER, null, OVERWORLD, SPAWN);
        assertEquals(OVERWORLD, r.dim());
        assertEquals(SPAWN, r.pos());
    }
}
