package com.vel5id.hexerei.soul;

import com.vel5id.hexerei.network.HexereiNetwork;
import com.vel5id.hexerei.power.TaintLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Server-side gateway to a chunk's per-domain {@code disturbance} ({@link ChunkSoulData}) — the single
 * store of "how disturbed a place is" (Грамматика §1). Replaces the old scalar {@code ChunkTaintData}:
 * a take writes {@link #add}, the world reads {@link #total}/{@link #level}. Decay is lazy (applied on
 * access), so there is no all-loaded-chunks sweep.
 */
public final class Disturbance {
    private Disturbance() {}

    private static ChunkSoulData of(ServerLevel level, ChunkPos cp) {
        LevelChunk chunk = level.getChunk(cp.x, cp.z);
        ChunkSoulData soul = chunk.getData(HexereiAttachments.CHUNK_SOUL);
        if (soul != null) soul.lazyDecay(level.getGameTime());
        return soul;
    }

    /** Stir disturbance of {@code domain} into the chunk and sync the new level to watchers. */
    public static void add(ServerLevel level, ChunkPos cp, Correspondence domain, float amount) {
        ChunkSoulData soul = of(level, cp);
        if (soul == null || amount <= 0f) return;
        soul.addDisturbance(domain, amount);
        level.getChunk(cp.x, cp.z).setUnsaved(true);
        HexereiNetwork.sendTaintSync(level, cp);
    }

    /** Total disturbance across all domains in the chunk (decay applied). */
    public static float total(ServerLevel level, ChunkPos cp) {
        ChunkSoulData soul = of(level, cp);
        return soul == null ? 0f : soul.totalDisturbance();
    }

    /** Disturbance of a single domain in the chunk (decay applied). */
    public static float domainTotal(ServerLevel level, ChunkPos cp, Correspondence domain) {
        ChunkSoulData soul = of(level, cp);
        return soul == null ? 0f : soul.getDisturbance(domain);
    }

    /** The chunk's visual unrest band (the former taint ladder), from total disturbance. */
    public static TaintLevel level(ServerLevel level, ChunkPos cp) {
        return TaintLevel.fromValue(total(level, cp));
    }
}
