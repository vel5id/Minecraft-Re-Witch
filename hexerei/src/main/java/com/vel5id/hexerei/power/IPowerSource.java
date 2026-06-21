package com.vel5id.hexerei.power;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Contract for anything that can supply power to nearby altars, located by a {@link BlockPos}. */
public interface IPowerSource {
    Level getWorld();

    BlockPos getLocation();

    boolean isLocationEqual(BlockPos location);

    /** Atomic all-or-nothing debit. Server-only; false on client / when no core. */
    boolean consumePower(float requiredPower);

    /** Current stored core power. Sentinels: -1 no core, -2 client. */
    float getCurrentPower();

    /** Reach in blocks = 16 * rangeScale. */
    float getRange();

    int getEnhancementLevel();

    boolean isPowerInvalid();
}
