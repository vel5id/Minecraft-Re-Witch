package com.vel5id.hexerei.power;

import com.vel5id.hexerei.network.HexereiNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Per-{@link ServerLevel} blood-moon state (a single flag + the day it ends). Mirrors {@code ChunkTaintData}'s
 * SavedData shape. The pure day/night math is unit-testable without a Minecraft runtime.
 */
public class BloodMoonData extends SavedData {
    private static final String KEY = "hexerei_bloodmoon";

    /** While a blood moon is active, rites are this much stronger and dirtier; the land sickens per pulse. */
    public static final float RITUAL_EFFECT_MUL = 1.5f;
    public static final float BLOOD_TAINT_MUL = 1.5f;
    public static final float BLOOD_AMBIENT_TAINT = 0.5f;
    private static final long NIGHT = 13000L; // vanilla nightfall

    private boolean active = false;
    private long endsAtDay = 0L;

    public static BloodMoonData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(BloodMoonData::load, BloodMoonData::new, KEY);
    }

    public boolean isActive() {
        return active;
    }

    /** Ignite a blood moon: jump to the upcoming nightfall, mark active for that night, and sync clients. */
    public void ignite(ServerLevel level) {
        long night = nightStart(level.getDayTime());
        level.setDayTime(night);
        this.active = true;
        this.endsAtDay = dayOf(night); // occupies this night; clears at the following dawn
        setDirty();
        HexereiNetwork.sendBloodMoonSync(level, true);
    }

    public void clear(ServerLevel level) {
        if (!active) {
            return;
        }
        this.active = false;
        setDirty();
        HexereiNetwork.sendBloodMoonSync(level, false);
    }

    /** True if the blood moon is active and its night has passed (dawn reached) — checked on the world pulse. */
    public boolean shouldClear(ServerLevel level) {
        return active && endedBy(level.getDayTime(), endsAtDay);
    }

    // ---- pure time math (unit-tested, no MC runtime) ----

    /** The integer game-day index for a day-time. */
    public static long dayOf(long dayTime) {
        return Math.floorDiv(dayTime, 24000L);
    }

    /** The next tick that lands on nightfall (13000), never moving backward — like {@code WaningMoonRite.midnightOf}. */
    public static long nightStart(long dayTime) {
        long base = dayTime - Math.floorMod(dayTime, 24000L) + NIGHT;
        return base >= dayTime ? base : base + 24000L;
    }

    /** True once the dawn after {@code endsAtDay} has passed. */
    public static boolean endedBy(long currentDayTime, long endsAtDay) {
        return dayOf(currentDayTime) > endsAtDay;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("active", active);
        tag.putLong("endsAtDay", endsAtDay);
        return tag;
    }

    public static BloodMoonData load(CompoundTag tag) {
        BloodMoonData d = new BloodMoonData();
        d.active = tag.getBoolean("active");
        d.endsAtDay = tag.getLong("endsAtDay");
        return d;
    }
}
