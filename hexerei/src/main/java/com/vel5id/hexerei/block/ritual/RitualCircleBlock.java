package com.vel5id.hexerei.block.ritual;

import com.vel5id.hexerei.ritual.RitualActivation;
import com.vel5id.hexerei.ritual.RitualCircle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/** The center of a ritual circle; right-click to attempt the ritual. */
public class RitualCircleBlock extends Block {
    public RitualCircleBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ServerLevel sl = (ServerLevel) level;

        level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.8f, 1.8f);
        List<BlockPos> ring = RitualCircle.smallRing(pos);
        for (int i = 0; i < 20; i++) {
            final BlockPos rp = ring.get(i % ring.size());
            final int delay = i;
            sl.getServer().tell(new net.minecraft.server.TickTask(delay, () ->
                sl.sendParticles(ParticleTypes.PORTAL,
                        rp.getX() + 0.5, rp.getY() + 0.1, rp.getZ() + 0.5,
                        1, 0.1, 0.1, 0.1, 0.05)
            ));
        }

        RitualActivation.Result result = RitualActivation.tryPerform(sl, pos);
        if (result == RitualActivation.Result.SUCCESS) {
            level.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 0.6F, 1.2F);
        } else {
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 0.8F);
        }
        return InteractionResult.CONSUME;
    }
}
