package com.vel5id.hexerei.power;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class ChunkTaintData extends SavedData {
    private static final String KEY = "hexerei_taint";
    private final Map<Long, Float> taint = new HashMap<>();
    private final Map<Long, Float> floor = new HashMap<>();

    public static ChunkTaintData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ChunkTaintData::load, ChunkTaintData::new, KEY);
    }

    public void addTaint(ChunkPos pos, float amount) {
        long k = pos.toLong();
        float cur = taint.getOrDefault(k, 0f);
        float next = Math.min(100f, cur + amount);
        taint.put(k, next);
        floor.put(k, Math.min(100f, floor.getOrDefault(k, 0f) + amount * 0.1f));
        setDirty();
    }

    public float getTaint(ChunkPos pos) {
        return taint.getOrDefault(pos.toLong(), 0f);
    }

    public TaintLevel getLevel(ChunkPos pos) {
        return TaintLevel.fromValue(getTaint(pos));
    }

    /** Call every 1200 server ticks (60s). Decays taint by 0.5, never below permanent floor. */
    public void decayTick() {
        boolean changed = false;
        for (Long k : new HashSet<>(taint.keySet())) {
            float cur = taint.get(k);
            float flr = floor.getOrDefault(k, 0f);
            float next = Math.max(flr, cur - 0.5f);
            if (next != cur) { taint.put(k, next); changed = true; }
        }
        if (changed) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<Long, Float> e : taint.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLong("p", e.getKey());
            t.putFloat("t", e.getValue());
            t.putFloat("f", floor.getOrDefault(e.getKey(), 0f));
            list.add(t);
        }
        tag.put("entries", list);
        return tag;
    }

    public static ChunkTaintData load(CompoundTag tag) {
        ChunkTaintData d = new ChunkTaintData();
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            long k = t.getLong("p");
            d.taint.put(k, t.getFloat("t"));
            d.floor.put(k, t.getFloat("f"));
        }
        return d;
    }
}
