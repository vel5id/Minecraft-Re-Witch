package com.vel5id.hexerei.block.ritual;

import com.vel5id.hexerei.blockentity.RitualSigilBlockEntity;
import com.vel5id.hexerei.registry.HexereiBlocks;
import com.vel5id.hexerei.ritual.RitualDestruction;
import com.vel5id.hexerei.soul.RuneSymbol;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * A flat 1px chalk decal showing one {@link RuneSymbol} glyph (Статья VI — the diegetic language of
 * the circle). Eighteen symbols, three per {@link com.vel5id.hexerei.soul.Correspondence} domain,
 * stored as the {@code symbol} blockstate property. The chalk picks the glyph; the ritual matcher
 * tests only for a rune's PRESENCE at the ring offsets, so the symbol is free to mean a domain later.
 *
 * <p>Replaces the old redstone-like connection system (eight boolean properties) — runes no longer
 * connect; each is its own symbol.
 */
public class RuneBlock extends Block {
    // 12x12 inset, 1px tall — smaller and shallower than the 16x16x2 sigil.
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 1.0, 14);

    public static final int SYMBOL_COUNT = RuneSymbol.VALUES.length; // 18
    public static final IntegerProperty SYMBOL = IntegerProperty.create("symbol", 0, SYMBOL_COUNT - 1);

    public RuneBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SYMBOL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SYMBOL);
    }

    /** The glyph this rune shows. */
    public static RuneSymbol symbolOf(BlockState state) {
        return RuneSymbol.VALUES[state.getValue(SYMBOL)];
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
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction dir, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        // Only a genuine rune removal (block type change) breaks a bound circle — not a symbol swap,
        // which routes through onRemove with the same block.
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl) {
            BlockPos sigilPos = findBoundSigil(level, pos);
            if (sigilPos != null) {
                BlockEntity be = level.getBlockEntity(sigilPos);
                if (be instanceof RitualSigilBlockEntity sigil && sigil.isBound()) {
                    RitualDestruction.penalize(sl, sigilPos, sigil.boundRiteId());
                    sigil.unbind(); // the circle is broken — it must be re-drawable
                }
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    /**
     * Scans the neighbourhood up to radius 3 for a bound ritual sigil whose ring this rune belongs
     * to. Returns the sigil position, or {@code null} when none is found.
     */
    @Nullable
    private static BlockPos findBoundSigil(Level level, BlockPos pos) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos p = pos.offset(dx, 0, dz);
                if (level.getBlockState(p).is(HexereiBlocks.RITUAL_SIGIL.get())) {
                    return p;
                }
            }
        }
        return null;
    }
}
