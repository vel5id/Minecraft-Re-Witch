package com.vel5id.hexerei.ritual;

import com.vel5id.hexerei.blockentity.AltarBlockEntity;
import com.vel5id.hexerei.power.AltarPowerManager;
import com.vel5id.hexerei.soul.ChunkSoulData;
import com.vel5id.hexerei.soul.Correspondence;
import com.vel5id.hexerei.soul.HexereiAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Awakens the nearest altar into a {@link HungeringAltar} (a binding act — paid by its sacrifice, not
 * essence, so it bootstraps the loop). It leaves an immediate scar of DEATH disturbance so the region
 * already begins to answer.
 */
public final class HungeringRite implements Rite {

    /** Initial DEATH disturbance the awakening burns into the place. [UNVERIFIED] knob. */
    private static final float AWAKENING_SCAR = 30f;

    @Override
    public void perform(ServerLevel level, BlockPos center) {
        AltarPowerManager.get(level).closest(level, center).ifPresent(src -> {
            if (src instanceof AltarBlockEntity altar) {
                altar.setHungering(true);
            }
        });

        LevelChunk chunk = level.getChunkAt(center);
        ChunkSoulData soul = chunk.getData(HexereiAttachments.CHUNK_SOUL);
        soul.addDisturbance(Correspondence.DEATH, AWAKENING_SCAR);
        chunk.setUnsaved(true);

        level.playSound(null, center, SoundEvents.WITHER_SPAWN, SoundSource.BLOCKS, 0.7f, 0.6f);
        level.sendParticles(ParticleTypes.SOUL,
                center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5,
                40, 1.5, 0.6, 1.5, 0.05);
    }
}
