package com.vel5id.hexerei.client;

import com.vel5id.hexerei.power.TaintLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class ClientTaintCache {
    private ClientTaintCache() {}
    private static final Map<Long, Float> CACHE = new HashMap<>();

    public static void set(long chunkPos, float value) { CACHE.put(chunkPos, value); }
    public static float get(long chunkPos) { return CACHE.getOrDefault(chunkPos, 0f); }
    public static TaintLevel getLevel(ChunkPos pos) { return TaintLevel.fromValue(get(pos.toLong())); }
    public static void clear() { CACHE.clear(); }
}
