package com.vel5id.hexerei.block;

import com.vel5id.hexerei.blockentity.AltarBlockEntity;
import com.vel5id.hexerei.item.ArtefactItem;
import com.vel5id.hexerei.registry.HexereiBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import javax.annotation.Nullable;

public class AltarBlock extends Block implements EntityBlock {
    public static final BooleanProperty ALTAR_JOINED = BooleanProperty.create("joined");
    public static final IntegerProperty TAINT_LEVEL = IntegerProperty.create("taint_level", 0, 3);

    public AltarBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(ALTAR_JOINED, false).setValue(TAINT_LEVEL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ALTAR_JOINED, TAINT_LEVEL);
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
        if (type != HexereiBlockEntities.ALTAR.get()) return null;
        if (level.isClientSide) {
            return (lvl, pos, st, be) -> ((AltarBlockEntity) be).clientTick(lvl, pos, st);
        }
        return (lvl, pos, st, be) -> AltarBlockEntity.serverTick(lvl, pos, st, (AltarBlockEntity) be);
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
                // Only the core holds the artefact; drop it before the BE is torn down so it isn't lost.
                ItemStack artefact = be.getArtefact();
                if (!artefact.isEmpty() && be.isCore()) {
                    be.setArtefact(ItemStack.EMPTY);
                    Block.popResource(level, pos, artefact);
                }
                be.updateMultiblock(pos);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof AltarBlockEntity be) {
            if (level.isClientSide) {
                BlockPos core = be.corePos();
                if (FMLEnvironment.dist == Dist.CLIENT) {
                    com.vel5id.hexerei.client.AltarScreen.open(core);
                }
                return ItemInteractionResult.sidedSuccess(true);
            }
            // Server: artefact place/swap/retrieve on a formed altar, before the existing revalidate path.
            AltarBlockEntity core = be.coreBe();
            if (core != null) {
                if (held.getItem() instanceof ArtefactItem) {
                    // Place or swap: store the new artefact (count 1), return the previous one to the player.
                    ItemStack prev = core.setArtefact(held.copyWithCount(1));
                    held.shrink(1);
                    if (!prev.isEmpty()) {
                        giveOrDrop(player, prev);
                    }
                    return ItemInteractionResult.SUCCESS;
                }
                // Offer a spirit-bearing reagent: consume one, free essence (diminishing returns).
                com.vel5id.hexerei.soul.ReagentDescriptor reagent =
                        com.vel5id.hexerei.soul.ReagentRegistry.get(
                                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem()));
                if (reagent != null) {
                    core.offer(reagent);
                    held.shrink(1);
                    if (level instanceof net.minecraft.server.level.ServerLevel sl) {
                        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL,
                                pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 8, 0.25, 0.2, 0.25, 0.02);
                    }
                    level.playSound(null, pos, net.minecraft.sounds.SoundEvents.SOUL_ESCAPE.value(),
                            net.minecraft.sounds.SoundSource.BLOCKS, 0.5f, 1.2f);
                    return ItemInteractionResult.SUCCESS;
                }
                if (held.isEmpty() && player.isShiftKeyDown() && !core.getArtefact().isEmpty()) {
                    // Shift + empty hand retrieves the artefact.
                    giveOrDrop(player, core.setArtefact(ItemStack.EMPTY));
                    return ItemInteractionResult.SUCCESS;
                }
            }
            be.revalidateAndUpdate();
            return ItemInteractionResult.sidedSuccess(false);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Vanilla give-or-drop: add to the player's inventory, else drop at their feet. */
    private static void giveOrDrop(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
