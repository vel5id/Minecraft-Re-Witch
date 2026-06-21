package com.vel5id.hexerei.block.ritual;

import com.vel5id.hexerei.ritual.RitualActivation;
import net.minecraft.core.BlockPos;
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
        RitualActivation.Result result = RitualActivation.tryPerform((ServerLevel) level, pos);
        if (result == RitualActivation.Result.SUCCESS) {
            level.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 0.6F, 1.2F);
        } else {
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 0.8F);
        }
        return InteractionResult.CONSUME;
    }
}
