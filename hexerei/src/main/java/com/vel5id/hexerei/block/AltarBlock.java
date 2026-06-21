package com.vel5id.hexerei.block;

import com.vel5id.hexerei.blockentity.AltarBlockEntity;
import com.vel5id.hexerei.registry.HexereiBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;

public class AltarBlock extends Block implements EntityBlock {
    public static final BooleanProperty ALTAR_JOINED = BooleanProperty.create("joined");

    public AltarBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(ALTAR_JOINED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ALTAR_JOINED);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AltarBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return type == HexereiBlockEntities.ALTAR.get()
                ? (lvl, pos, st, be) -> AltarBlockEntity.serverTick(lvl, pos, st, (AltarBlockEntity) be)
                : null;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moved) {
        super.onPlace(state, level, pos, oldState, moved);
        // Fires on ALL placements (item, /setblock, worldgen, GameTest) — unlike setPlacedBy.
        // Guard against the JOINED-flag self-change (oldState already an altar) to avoid recursion.
        if (!oldState.is(this) && level.getBlockEntity(pos) instanceof AltarBlockEntity be) {
            be.updateMultiblock(null);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof AltarBlockEntity be) {
                be.updateMultiblock(pos);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof AltarBlockEntity be) {
            if (level.isClientSide) {
                BlockPos core = be.corePos();
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> com.vel5id.hexerei.client.AltarScreen.open(core));
            } else {
                be.revalidateAndUpdate();
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }
}
