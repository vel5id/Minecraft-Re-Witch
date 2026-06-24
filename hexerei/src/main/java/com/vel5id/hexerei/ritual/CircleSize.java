package com.vel5id.hexerei.ritual;

import net.minecraft.core.BlockPos;
import java.util.List;
import java.util.function.Predicate;

public enum CircleSize {
    SMALL("item.hexerei.ritual_chalk.circle.small") {
        @Override public List<BlockPos> ringPositions(BlockPos center) {
            return RitualCircle.smallRing(center);
        }
        @Override public boolean isComplete(Predicate<BlockPos> isGlyph, BlockPos center) {
            return RitualCircle.isSmallComplete(isGlyph, center);
        }
    },
    MEDIUM("item.hexerei.ritual_chalk.circle.medium") {
        @Override public List<BlockPos> ringPositions(BlockPos center) {
            return RitualCircle.mediumRing(center);
        }
        @Override public boolean isComplete(Predicate<BlockPos> isGlyph, BlockPos center) {
            return RitualCircle.isMediumComplete(isGlyph, center);
        }
    };

    private final String circleLabelKey;

    CircleSize(String circleLabelKey) {
        this.circleLabelKey = circleLabelKey;
    }

    /** Lang key for the chalk's "<size> circle · %d glyphs" label — picked per size so MEDIUM isn't mislabelled "Small". */
    public String circleLabelKey() {
        return circleLabelKey;
    }

    public abstract List<BlockPos> ringPositions(BlockPos center);
    public abstract boolean isComplete(Predicate<BlockPos> isGlyph, BlockPos center);
}
