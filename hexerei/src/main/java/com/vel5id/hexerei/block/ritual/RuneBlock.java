package com.vel5id.hexerei.block.ritual;

import com.vel5id.hexerei.blockentity.RitualSigilBlockEntity;
import com.vel5id.hexerei.registry.HexereiBlocks;
import com.vel5id.hexerei.ritual.RitualDestruction;
import com.vel5id.hexerei.ritual.RuneShapes.Dir8;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * A flat 1px chalk decal that connects to neighbouring runes both orthogonally (N/E/S/W) and
 * diagonally (NE/SE/SW/NW), like vanilla {@code redstone_wire}/{@code tripwire}. Connections are
 * stored as eight boolean blockstate properties and rendered with a multipart blockstate; the pure
 * boolean -> (shape, rotation) mapping lives in {@link com.vel5id.hexerei.ritual.RuneShapes}.
 *
 * <p>Replaces the old {@code ritual_glyph} decal as the ring block of a ritual circle. The
 * connection booleans are purely cosmetic to {@code RitualActivation}, which only tests for the
 * presence of a rune at the ring offsets.
 */
public class RuneBlock extends Block {
    // 12x12 inset, 1px tall — smaller and shallower than the 16x16x2 sigil.
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 1.0, 14);

    public static final BooleanProperty N = BooleanProperty.create("n");
    public static final BooleanProperty E = BooleanProperty.create("e");
    public static final BooleanProperty S = BooleanProperty.create("s");
    public static final BooleanProperty W = BooleanProperty.create("w");
    public static final BooleanProperty NE = BooleanProperty.create("ne");
    public static final BooleanProperty SE = BooleanProperty.create("se");
    public static final BooleanProperty SW = BooleanProperty.create("sw");
    public static final BooleanProperty NW = BooleanProperty.create("nw");

    public RuneBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(N, false).setValue(E, false).setValue(S, false).setValue(W, false)
                .setValue(NE, false).setValue(SE, false).setValue(SW, false).setValue(NW, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(N, E, S, W, NE, SE, SW, NW);
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
        if (!canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return withConnections(level, pos, state);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext ctx) {
        return withConnections(ctx.getLevel(), ctx.getClickedPos(), defaultBlockState());
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                BlockPos fromPos, boolean moving) {
        if (!level.isClientSide) {
            BlockState updated = withConnections(level, pos, state);
            if (updated != state) {
                level.setBlock(pos, updated, 2);
            }
        }
        super.neighborChanged(state, level, pos, block, fromPos, moving);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        // Only a genuine rune removal (block type change) triggers the penalty — not a connection
        // boolean update, which also routes through onRemove with the same block.
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

    /** Scans the eight neighbours and returns {@code state} with all eight connection booleans set. */
    private static BlockState withConnections(LevelReader level, BlockPos pos, BlockState state) {
        return state
                .setValue(N, isRune(level, pos, Dir8.N))
                .setValue(E, isRune(level, pos, Dir8.E))
                .setValue(S, isRune(level, pos, Dir8.S))
                .setValue(W, isRune(level, pos, Dir8.W))
                .setValue(NE, isRune(level, pos, Dir8.NE))
                .setValue(SE, isRune(level, pos, Dir8.SE))
                .setValue(SW, isRune(level, pos, Dir8.SW))
                .setValue(NW, isRune(level, pos, Dir8.NW));
    }

    private static boolean isRune(LevelReader level, BlockPos pos, Dir8 dir) {
        return level.getBlockState(pos.offset(dir.dx, 0, dir.dz)).is(HexereiBlocks.RUNE.get());
    }
}
