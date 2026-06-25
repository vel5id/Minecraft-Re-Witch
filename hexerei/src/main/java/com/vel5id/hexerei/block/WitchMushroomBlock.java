package com.vel5id.hexerei.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Shared block for Hexerei's three small mushrooms (zevanty, puffball, webcap).
 * Mirrors vanilla MushroomBlock: a {@link BushBlock} with the small-mushroom shape that survives on
 * any solid-top block when it is dark (raw brightness &lt; 13) or has sky access. No growth stages,
 * no huge variant (v1). Renders as a single cross-sprite. Drops itself via loot JSON.
 */
public class WitchMushroomBlock extends BushBlock {
    private static final VoxelShape SHAPE = Block.box(5.0D, 0.0D, 5.0D, 11.0D, 6.0D, 11.0D);

    private final boolean glow;

    public WitchMushroomBlock(boolean glow, Properties props) {
        super(props);
        this.glow = glow;
    }

    /** Whether this mushroom emits light (zevanty). The light level is set via block Properties. */
    public boolean glows() {
        return glow;
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter w, BlockPos p, CollisionContext c) {
        return SHAPE;
    }

    // Mirrors vanilla MushroomBlock survival: solid support below, and either dark or sky-lit.
    @Override
    protected boolean mayPlaceOn(BlockState ground, BlockGetter w, BlockPos pos) {
        return ground.isSolidRender(w, pos);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState groundState = level.getBlockState(below);
        if (!mayPlaceOn(groundState, level, below)) {
            return false;
        }
        return level.getRawBrightness(pos, 0) < 13 || level.canSeeSky(pos);
    }
}
