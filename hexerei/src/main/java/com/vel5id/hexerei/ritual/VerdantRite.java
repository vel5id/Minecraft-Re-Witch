package com.vel5id.hexerei.ritual;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** White-magic growth: bonemeals nearby crops (vanilla and Hexerei alike) up to a cap. */
public final class VerdantRite implements Rite {
    /** One growth per glyph of the small circle — cheap, but never a one-cast full-field harvest. */
    public static final int MAX_GROWTHS = 12;
    private static final int TAINT_COST = 60; // mirrors the recipe's powerCost; taint = cost/4

    /**
     * The 7x7x3 sweep offsets {dx,dy,dz} around the center (dy in [-1,1]). Pure — unit-tested.
     * The plant layer (dy=0, where crops are planted) is swept FIRST so the limited growth budget
     * favors tended crops over floor grass at dy=-1.
     */
    public static List<int[]> cells() {
        List<int[]> out = new ArrayList<>(7 * 7 * 3);
        for (int dy : new int[]{0, 1, -1}) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    out.add(new int[]{dx, dy, dz});
                }
            }
        }
        return out;
    }

    @Override
    public void perform(ServerLevel level, BlockPos center) {
        RandomSource rng = level.getRandom();
        // The amplifier/purifier on the funding altar scales the growth budget (effectMul, 1.0 with no artefact).
        // Clamped to [1, 2*MAX_GROWTHS] so a deep purifier never zeroes the rite and an amplifier can't run away.
        int budget = Math.max(1, Math.min(2 * MAX_GROWTHS,
                Math.round(MAX_GROWTHS * RitualContext.currentEffectMul())));
        int grown = 0;
        for (int[] c : cells()) {
            if (grown >= budget) {
                break;
            }
            BlockPos p = center.offset(c[0], c[1], c[2]);
            BlockState bs = level.getBlockState(p);
            if (bs.getBlock() instanceof BonemealableBlock bm
                    && bm.isValidBonemealTarget(level, p, bs, false)
                    && bm.isBonemealSuccess(level, rng, p, bs)) {
                bm.performBonemeal(level, rng, p, bs);
                grown++;
            }
        }

        level.playSound(null, center, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 0.8f, 1.0f);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5,
                40, 2.0, 0.5, 2.0, 0.0);

        Rites.addRitualTaint(level, new ChunkPos(center), TAINT_COST / 4f);
    }
}
