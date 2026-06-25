package com.vel5id.hexerei.ritual;

import com.vel5id.hexerei.power.BloodMoonData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;

/** The capstone dark rite: forces night, ignites a Blood Moon, and stains the land heavily. */
public final class EclipseRite implements Rite {
    private static final int TAINT_COST = 40; // above Tempest's 25 — the top of the rite range

    @Override
    public void perform(ServerLevel level, BlockPos center) {
        BloodMoonData.get(level).ignite(level); // jumps to night, activates the event, syncs clients

        level.playSound(null, center, SoundEvents.WITHER_SPAWN, SoundSource.BLOCKS, 0.7f, 0.6f);
        for (BlockPos glyph : RitualCircle.mediumRing(center)) {
            level.sendParticles(ParticleTypes.SOUL,
                    glyph.getX() + 0.5, glyph.getY() + 0.3, glyph.getZ() + 0.5,
                    2, 0.0, 0.1, 0.0, 0.0);
        }
        level.sendParticles(ParticleTypes.WITCH,
                center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5,
                40, 1.5, 0.5, 1.5, 0.1);

        // Heavy taint, scaled by the live RitualContext (on a blood-moon eclipse it self-amplifies its dirtiness).
        Rites.addRitualTaint(level, new ChunkPos(center), TAINT_COST);
    }
}
