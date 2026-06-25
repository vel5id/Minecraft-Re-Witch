package com.vel5id.hexerei.ritual;

import com.vel5id.hexerei.power.AltarPowerManager;
import com.vel5id.hexerei.power.ChunkTaintData;
import com.vel5id.hexerei.power.IPowerSource;
import com.vel5id.hexerei.power.TaintLevel;
import com.vel5id.hexerei.registry.HexereiBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Executed every 200 server ticks (≈10 s).  For each registered altar, mutates
 * nearby blocks according to the chunk's current TaintLevel:
 * <ul>
 *   <li>GRASS_BLOCK  → hexerei:tainted_ground  (prob 15 %, max 6 per pulse, LOW+)</li>
 *   <li>DANDELION / POPPY → WITHER_ROSE        (prob 20 %, max 2 per pulse, MEDIUM+)</li>
 *   <li>STONE / COBBLESTONE → hexerei:charred_stone (prob 5 %, max 2 per pulse, HIGH only)</li>
 * </ul>
 */
public final class WorldTaintAura {
    private WorldTaintAura() {}

    private static final int RADIUS = 5;

    public static void pulse(ServerLevel level) {
        ChunkTaintData taintData = ChunkTaintData.get(level);
        RandomSource rng = level.getRandom();

        // Snapshot the source list to avoid ConcurrentModificationException if
        // block mutations trigger any registration changes.
        List<IPowerSource> sources = new ArrayList<>(AltarPowerManager.get(level).allSources());

        for (IPowerSource src : sources) {
            if (src == null || src.isPowerInvalid()) continue;

            BlockPos center = src.getLocation();
            TaintLevel tl = taintData.getLevel(new ChunkPos(center));
            if (tl.ordinal() < TaintLevel.LOW.ordinal()) continue;

            int grassCount  = 0;
            int flowerCount = 0;
            int stoneCount  = 0;

            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        BlockPos p = center.offset(dx, dy, dz);
                        if (!level.isLoaded(p)) continue;

                        BlockState bs = level.getBlockState(p);

                        // GRASS_BLOCK → tainted_ground (LOW+, prob 15 %, max 6)
                        if (grassCount < 6
                                && bs.is(Blocks.GRASS_BLOCK)
                                && rng.nextFloat() < 0.15f) {
                            level.setBlock(p, HexereiBlocks.TAINTED_GROUND.get().defaultBlockState(), 3);
                            grassCount++;
                            continue;
                        }

                        // DANDELION / POPPY → WITHER_ROSE (MEDIUM+, prob 20 %, max 2)
                        if (flowerCount < 2
                                && tl.ordinal() >= TaintLevel.MEDIUM.ordinal()
                                && (bs.is(Blocks.DANDELION) || bs.is(Blocks.POPPY))
                                && rng.nextFloat() < 0.20f) {
                            level.setBlock(p, Blocks.WITHER_ROSE.defaultBlockState(), 3);
                            flowerCount++;
                            continue;
                        }

                        // STONE / COBBLESTONE → charred_stone (HIGH only, prob 5 %, max 2)
                        if (stoneCount < 2
                                && tl == TaintLevel.HIGH
                                && (bs.is(Blocks.STONE) || bs.is(Blocks.COBBLESTONE))
                                && rng.nextFloat() < 0.05f) {
                            level.setBlock(p, HexereiBlocks.CHARRED_STONE.get().defaultBlockState(), 3);
                            stoneCount++;
                        }
                    }
                }
            }
        }
    }

    /**
     * Punishes every survival/adventure player standing in a tainted chunk, on the same 200-tick pulse
     * cadence as {@link #pulse}. Each player is judged by the {@link TaintLevel} of their <em>own</em>
     * chunk (taint outlives the altar that caused it — punishment is per-player, not per-altar). Creative
     * and spectator players are immune. The {@link TaintPunishment} ladder resolves to vanilla effects,
     * applied for {@link TaintPunishment#REFRESH_TICKS} (220) ticks so the debuff never gaps while the
     * player stays and lapses ~1 s after they leave.
     */
    public static void punishPlayers(ServerLevel level) {
        ChunkTaintData taintData = ChunkTaintData.get(level);
        for (ServerPlayer player : level.players()) {
            if (player.isCreative() || player.isSpectator()) continue;
            TaintLevel tl = taintData.getLevel(new ChunkPos(player.blockPosition()));
            applyLadder(player, tl);
        }
    }

    /** Applies the {@link TaintPunishment} ladder for {@code tl} to {@code player} (resolving vanilla effect ids). */
    private static void applyLadder(ServerPlayer player, TaintLevel tl) {
        for (TaintPunishment.Effect e : TaintPunishment.effectsFor(tl)) {
            MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(new ResourceLocation(e.effectId()));
            if (effect == null) continue;
            // ambient, hidden particles, visible HUD icon — mirrors CharmTickHandler so the player sees why.
            player.addEffect(new MobEffectInstance(
                    effect, TaintPunishment.REFRESH_TICKS, e.amplifier(), true, false, true));
        }
    }
}
