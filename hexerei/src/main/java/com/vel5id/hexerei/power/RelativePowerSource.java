package com.vel5id.hexerei.power;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Transient query-time wrapper. Squared distances only (no sqrt). */
public final class RelativePowerSource {
    private final IPowerSource source;
    private final double distanceSq;
    private final double rangeSq;

    public RelativePowerSource(IPowerSource source, BlockPos from) {
        this.source = source;
        this.distanceSq = source.getLocation().distSqr(from);
        float range = source.getRange();
        this.rangeSq = (double) range * range;
    }

    public IPowerSource source() {
        return source;
    }

    public double distanceSq() {
        return distanceSq;
    }

    public boolean isInWorld(Level level) {
        return source.getWorld() == level;
    }

    public boolean isInRange() {
        return distanceSq <= rangeSq;
    }
}
