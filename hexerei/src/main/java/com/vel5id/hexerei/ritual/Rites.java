package com.vel5id.hexerei.ritual;

import com.vel5id.hexerei.network.HexereiNetwork;
import com.vel5id.hexerei.power.ChunkTaintData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** Shared ritual helpers. Every rite routes its taint write through here so artefact scaling is universal. */
public final class Rites {
    private Rites() {}

    /**
     * The taint a rite actually writes: its {@code base} cost scaled by the funding altar's multiplier.
     * Pure — unit-tested ({@code base * mul}); the world-side {@link #addRitualTaint} delegates to it.
     */
    public static float scaledTaint(float base, float taintMul) {
        return base * taintMul;
    }

    /**
     * Adds {@code base} taint (scaled by the current {@link RitualContext}'s taint multiplier) to the chunk
     * and syncs it to clients. With no context installed the multiplier is {@code 1.0} — behaviour unchanged.
     */
    public static void addRitualTaint(ServerLevel level, ChunkPos cp, float base) {
        float scaled = scaledTaint(base, RitualContext.currentTaintMul());
        ChunkTaintData.get(level).addTaint(cp, scaled);
        HexereiNetwork.sendTaintSync(level, cp);
    }
}
