package com.vel5id.hexerei.block.cauldron;

import com.vel5id.hexerei.blockentity.CauldronBlockEntity;
import com.vel5id.hexerei.registry.HexereiBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/** The Witch's Cauldron block: a cauldron-shaped container backed by {@link CauldronBlockEntity}. */
public class CauldronBlock extends Block implements EntityBlock {
    // Full block minus an open interior, so dropped items fall in and rest on the bottom.
    private static final VoxelShape SHAPE =
            Shapes.join(Shapes.block(), Block.box(2, 4, 2, 14, 16, 14), BooleanOp.ONLY_FIRST);

    public CauldronBlock(Properties properties) {
        super(properties);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CauldronBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return type == HexereiBlockEntities.CAULDRON.get()
                ? (lvl, pos, st, be) -> CauldronBlockEntity.serverTick(lvl, pos, st, (CauldronBlockEntity) be)
                : null;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CauldronBlockEntity be)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);

        if (held.is(Items.WATER_BUCKET)) {
            if (be.isFilled()) {
                return InteractionResult.PASS; // already full — no phantom swing
            }
            if (!level.isClientSide && be.fillWater() && !player.getAbilities().instabuild) {
                player.setItemInHand(hand, new ItemStack(Items.BUCKET));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (held.is(Items.BUCKET)) {
            if (!be.hasWater()) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide) {
                be.drain(); // rinse out a wrong/incomplete mix
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                    ItemStack waterBucket = new ItemStack(Items.WATER_BUCKET);
                    if (held.isEmpty()) {
                        player.setItemInHand(hand, waterBucket);
                    } else if (!player.getInventory().add(waterBucket)) {
                        player.drop(waterBucket, false);
                    }
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (held.is(Items.GLASS_BOTTLE)) {
            if (level.isClientSide) {
                return be.isReady() ? InteractionResult.SUCCESS : InteractionResult.PASS;
            }
            ItemStack brew = be.collectBrew();
            if (brew == null) {
                return InteractionResult.PASS;
            }
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
            if (!player.getInventory().add(brew)) {
                player.drop(brew, false);
            }
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }
}
