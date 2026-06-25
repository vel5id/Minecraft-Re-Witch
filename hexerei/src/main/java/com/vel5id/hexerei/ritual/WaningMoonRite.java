package com.vel5id.hexerei.ritual;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ChunkPos;

/** Drags the world to midnight and curses nearby hostiles with weakness and slowness — a battlefield curse. */
public final class WaningMoonRite implements Rite {
    /** Reach of the curse, in blocks. Out-ranges the 5-block Hexbane charm aura — a 150-power ritual should. */
    public static final int AURA_RADIUS = 10;
    private static final int DEBUFF_TICKS = 600; // 30s
    private static final int TAINT_COST = 150;   // mirrors the recipe's powerCost; taint = cost/4
    private static final long MIDNIGHT = 18000L;  // midnight within a 24000-tick day

    /** The next day-time tick that lands on midnight, never moving backward. Pure — unit-tested. */
    public static long midnightOf(long dayTime) {
        long base = dayTime - Math.floorMod(dayTime, 24000L) + MIDNIGHT;
        return base >= dayTime ? base : base + 24000L;
    }

    /** True if {@code mob} is within {@link #AURA_RADIUS} of {@code center}. Pure — unit-tested. */
    public static boolean inRange(Vec3 center, Vec3 mob) {
        return center.distanceToSqr(mob) <= (double) AURA_RADIUS * AURA_RADIUS;
    }

    @Override
    public void perform(ServerLevel level, BlockPos center) {
        level.setDayTime(midnightOf(level.getDayTime()));

        Vec3 c = Vec3.atCenterOf(center);
        AABB box = new AABB(center).inflate(AURA_RADIUS);
        for (Monster mob : level.getEntitiesOfClass(Monster.class, box)) {
            if (inRange(c, mob.position())) {
                mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, DEBUFF_TICKS, 0));
                mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DEBUFF_TICKS, 0));
            }
        }

        level.playSound(null, center, SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.BLOCKS, 0.9f, 0.8f);
        for (BlockPos glyph : RitualCircle.mediumRing(center)) {
            level.sendParticles(ParticleTypes.WITCH,
                    glyph.getX() + 0.5, glyph.getY() + 0.3, glyph.getZ() + 0.5,
                    2, 0.0, 0.1, 0.0, 0.0);
        }

        Rites.addRitualTaint(level, new ChunkPos(center), TAINT_COST / 4f);
    }
}
