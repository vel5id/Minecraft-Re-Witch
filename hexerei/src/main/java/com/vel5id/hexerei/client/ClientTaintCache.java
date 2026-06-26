package com.vel5id.hexerei.client;

import com.vel5id.hexerei.power.TaintLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side read-only mirror of per-chunk taint, fed by {@code TaintSyncS2CPacket}.
 *
 * <p>Intentionally NOT {@code @OnlyIn(Dist.CLIENT)}: the common S2C payload handler references it, so the
 * class must be loadable on a dedicated server (where its playToClient handler never runs). It holds only
 * a plain map of common types, so loading it server-side is harmless.
 */
public final class ClientTaintCache {
    private ClientTaintCache() {}
    private static final Map<Long, Float> CACHE = new HashMap<>();

    public static void set(long chunkPos, float value) { CACHE.put(chunkPos, value); }
    public static float get(long chunkPos) { return CACHE.getOrDefault(chunkPos, 0f); }
    public static TaintLevel getLevel(ChunkPos pos) { return TaintLevel.fromValue(get(pos.toLong())); }
    public static void clear() { CACHE.clear(); }
}
