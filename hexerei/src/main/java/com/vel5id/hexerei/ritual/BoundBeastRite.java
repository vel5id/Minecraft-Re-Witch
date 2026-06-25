package com.vel5id.hexerei.ritual;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * Summons a wolf familiar at the circle. Owner-binding is deferred (the {@link Rite} contract has no player
 * handle), so the wolf is spawned un-owned but spawn-persistent — see the design's "owner-aware" follow-up.
 */
public final class BoundBeastRite implements Rite {
    private static final int TAINT_COST = 120; // mirrors the recipe's powerCost; taint = cost/4

    /** Where the familiar appears: one block above the circle center. Pure — unit-tested. */
    public static Vec3 spawnPos(BlockPos center) {
        return new Vec3(center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5);
    }

    @Override
    public void perform(ServerLevel level, BlockPos center) {
        Wolf wolf = EntityType.WOLF.create(level);
        if (wolf != null) {
            Vec3 at = spawnPos(center);
            wolf.moveTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360f, 0f);
            wolf.setHealth(wolf.getMaxHealth());
            wolf.setPersistenceRequired();   // a summoned familiar shouldn't despawn
            level.addFreshEntity(wolf);
        }

        level.playSound(null, center, SoundEvents.WOLF_HOWL, SoundSource.BLOCKS, 0.9f, 1.0f);
        level.sendParticles(ParticleTypes.SOUL,
                center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5,
                16, 0.4, 0.5, 0.4, 0.02);

        Rites.addRitualTaint(level, new ChunkPos(center), com.vel5id.hexerei.soul.Correspondence.FOREST, TAINT_COST / 4f);
    }
}
