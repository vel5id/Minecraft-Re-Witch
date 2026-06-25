package com.vel5id.hexerei.ritual;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * The Hungering Altar (WARRANTLY Статья III, spatial): once a rite makes an altar <i>hungering</i>
 * it drains the life around it for essence AND curses a wide region — penalties on
 * {@link #PENALTY_CHUNK_RADIUS} chunks in every direction, growing with the place's accumulated
 * {@code disturbance}. Power and danger, one and the same, written across the land.
 */
public final class HungeringAltar {
    private HungeringAltar() {}

    public static final int DRAIN_RADIUS = 5;            // blocks the altar reaches to eat life
    public static final int DRAIN_INTERVAL = 40;         // ticks between bites (2s)
    public static final int PENALTY_INTERVAL = 100;      // ticks between regional curse refreshes (5s)
    public static final int PENALTY_CHUNK_RADIUS = 6;    // 6 chunks each way -> 96-block radius
    static final float DISTURB_PER_AMP = 30f;            // disturbance per +1 debuff amplifier
    static final int MAX_AMP = 3;

    /** Debuff amplifier from the place's total disturbance, 0 until the first threshold. Pure. */
    public static int penaltyAmplifier(float disturbance) {
        return Math.max(0, Math.min(MAX_AMP, (int) (disturbance / DISTURB_PER_AMP)));
    }

    /** Curse every player within {@link #PENALTY_CHUNK_RADIUS} chunks of a hungering altar. */
    public static void applyRegionalPenalty(ServerLevel level, BlockPos altar, float disturbance) {
        int amp = penaltyAmplifier(disturbance);
        double r = PENALTY_CHUNK_RADIUS * 16.0;
        int dur = PENALTY_INTERVAL + 40;   // outlast the refresh interval so it never flickers off
        for (ServerPlayer p : level.getPlayers(pl -> pl.blockPosition().closerThan(altar, r))) {
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, dur, amp, true, false, true));
            if (amp >= 2) {
                p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, dur, 0, true, false, true));
            }
        }
    }
}
