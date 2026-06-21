package com.vel5id.hexerei.ritual;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** The effect a ritual produces when performed at the circle center. */
public interface Rite {
    void perform(ServerLevel level, BlockPos center);
}
