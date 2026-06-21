package com.vel5id.hexerei.power;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Tracks registered altar power sources on a per-ServerLevel basis, so each world
 * keeps its own independent set of altars.
 */
public final class AltarPowerManager {
    private static final Map<ServerLevel, AltarPowerManager> MANAGERS = new WeakHashMap<>();

    public static AltarPowerManager get(ServerLevel level) {
        return MANAGERS.computeIfAbsent(level, l -> new AltarPowerManager());
    }

    private final List<IPowerSource> sources = new ArrayList<>();

    public void register(IPowerSource src) {
        if (sources.contains(src)) {
            return;
        }
        sources.removeIf(s -> s == null || s.isPowerInvalid() || s.getLocation().equals(src.getLocation()));
        sources.add(src);
    }

    public void unregister(IPowerSource src) {
        sources.remove(src);
        sources.removeIf(s -> s == null || s.isPowerInvalid());
    }

    /** Distance-sorted, in-range sources. Each altar's own range governs whether it is included. */
    public List<RelativePowerSource> query(Level level, BlockPos from) {
        List<RelativePowerSource> out = new ArrayList<>();
        for (Iterator<IPowerSource> it = sources.iterator(); it.hasNext(); ) {
            IPowerSource s = it.next();
            if (s == null || s.isPowerInvalid()) {
                it.remove();
                continue;
            }
            RelativePowerSource r = new RelativePowerSource(s, from);
            if (r.isInWorld(level) && r.isInRange()) {
                out.add(r);
            }
        }
        out.sort(Comparator.comparingDouble(RelativePowerSource::distanceSq));
        return out;
    }

    public Optional<IPowerSource> closest(Level level, BlockPos from) {
        List<RelativePowerSource> all = query(level, from);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0).source());
    }
}
