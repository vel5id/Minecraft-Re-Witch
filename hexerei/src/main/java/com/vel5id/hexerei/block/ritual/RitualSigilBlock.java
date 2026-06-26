package com.vel5id.hexerei.block.ritual;

import com.vel5id.hexerei.blockentity.RitualSigilBlockEntity;
import com.vel5id.hexerei.ritual.RitualActivation;
import com.vel5id.hexerei.ritual.RitualCircle;
import com.vel5id.hexerei.ritual.RitualDestruction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The center of a ritual circle: a flat 2px chalk decal hosting a {@link RitualSigilBlockEntity}
 * that records the bound rite. Right-click with a bare hand to attempt the ritual; needs a sturdy
 * block below and pops if it loses support.
 */
public class RitualSigilBlock extends Block implements EntityBlock {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 2.0, 16); // flat decal, proud of the runes

    public RitualSigilBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return !canSurvive(state, level, pos)
                ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RitualSigilBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RitualSigilBlockEntity sigil && sigil.isBound()) {
                RitualDestruction.penalize(sl, pos, sigil.boundRiteId());
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
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

        RitualActivation.Result result = RitualActivation.tryPerform(sl, pos, player);
        if (result == RitualActivation.Result.SUCCESS) {
            level.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 0.6F, 1.2F);
        } else {
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 0.8F);
        }
        return InteractionResult.CONSUME;
    }
}
