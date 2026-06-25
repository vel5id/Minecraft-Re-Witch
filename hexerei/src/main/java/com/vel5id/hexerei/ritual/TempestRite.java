package com.vel5id.hexerei.ritual;

import com.vel5id.hexerei.registry.HexereiBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Summons a thunderstorm. */
public final class TempestRite implements Rite {
    private final int durationTicks;

    public TempestRite(int durationTicks) {
        this.durationTicks = durationTicks;
    }

    @Override
    public void perform(ServerLevel level, BlockPos center) {
        // clearTime=0, weatherTime=duration, raining=true, thundering=true
        level.setWeatherParameters(0, durationTicks, true, true);

        level.playSound(null, center, SoundEvents.WITHER_DEATH,
                SoundSource.BLOCKS, 0.6f, 0.5f);

        level.sendParticles(ParticleTypes.WITCH,
                center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5,
                60, 1.5, 0.5, 1.5, 0.1);

        List<BlockPos> ring = RitualCircle.smallRing(center);
        java.util.Collections.shuffle(ring);
        for (int i = 0; i < Math.min(8, ring.size()); i++) {
            BlockPos rp = ring.get(i);
            level.sendParticles(ParticleTypes.ENCHANT,
                    rp.getX() + 0.5, rp.getY() + 0.1, rp.getZ() + 0.5,
                    1, 0, 0, 0, 0);
        }

        var rng = level.getRandom();
        int charred = 0;
        for (int dx = -2; dx <= 2 && charred < 5; dx++) {
            for (int dz = -2; dz <= 2 && charred < 5; dz++) {
                BlockPos p = center.offset(dx, -1, dz);
                BlockState bs = level.getBlockState(p);
                if ((bs.is(Blocks.GRASS_BLOCK) || bs.is(Blocks.STONE) || bs.is(Blocks.COBBLESTONE))
                        && rng.nextFloat() < 0.5f) {
                    level.setBlock(p, HexereiBlocks.CHARRED_STONE.get().defaultBlockState(), 3);
                    charred++;
                }
            }
        }

        Rites.addRitualTaint(level, new ChunkPos(center), com.vel5id.hexerei.soul.Correspondence.SKY, 25f);
    }
}
