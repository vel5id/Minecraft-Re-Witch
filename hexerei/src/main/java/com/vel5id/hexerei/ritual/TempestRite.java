package com.vel5id.hexerei.ritual;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Summons a thunderstorm. */
public final class TempestRite implements Rite {
    private final int durationTicks;

    public TempestRite(int durationTicks) {
        this.durationTicks = durationTicks;
    }

    @Override
    public void perform(ServerLevel level, BlockPos center) {
        // clearTime=0, weatherTime=duration, raining=true, thundering=true
        level.setWeatherParameters(0, durationTicks, true, true);
    }
}
