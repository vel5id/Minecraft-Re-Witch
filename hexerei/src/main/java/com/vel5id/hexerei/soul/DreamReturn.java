package com.vel5id.hexerei.soul;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Where a waking dreamer is sent: the saved overworld target if it was recorded, else a fallback
 * (the overworld spawn). Pure value logic — no Minecraft runtime — so the wake path's targeting is
 * unit-tested independent of teleport.
 */
public record DreamReturn(ResourceKey<Level> dim, BlockPos pos) {

    /** The saved {@code (dim,pos)} when BOTH are present, otherwise the fallback. */
    public static DreamReturn resolveTarget(ResourceKey<Level> savedDim, BlockPos savedPos,
                                            ResourceKey<Level> fallbackDim, BlockPos fallbackPos) {
        if (savedDim != null && savedPos != null) {
            return new DreamReturn(savedDim, savedPos);
        }
        return new DreamReturn(fallbackDim, fallbackPos);
    }
}
