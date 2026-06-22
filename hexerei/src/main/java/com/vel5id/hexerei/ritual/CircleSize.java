package com.vel5id.hexerei.ritual;

import net.minecraft.core.BlockPos;
import java.util.List;
import java.util.function.Predicate;

public enum CircleSize {
    SMALL {
        @Override public List<BlockPos> ringPositions(BlockPos center) {
            return RitualCircle.smallRing(center);
        }
        @Override public boolean isComplete(Predicate<BlockPos> isGlyph, BlockPos center) {
            return RitualCircle.isSmallComplete(isGlyph, center);
        }
    };

    public abstract List<BlockPos> ringPositions(BlockPos center);
    public abstract boolean isComplete(Predicate<BlockPos> isGlyph, BlockPos center);
}
