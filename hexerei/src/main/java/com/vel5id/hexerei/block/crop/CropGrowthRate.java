package com.vel5id.hexerei.block.crop;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Hexerei herb-crop growth-rate calculation: 1.0 base + fertile-soil bonus over the 3x3 below,
 * crowding halve, mindrake /1.5. Uses FarmBlock.MOISTURE as the fertility check.
 */
public final class CropGrowthRate {
    private CropGrowthRate() {}

    public static float compute(Level level, BlockPos pos, Block crop, boolean mindrake) {
        float total = 1.0F;
        BlockPos below = pos.below();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                float pts = 0.0F;
                BlockState soil = level.getBlockState(below.offset(dx, 0, dz));
                if (soil.getBlock() instanceof FarmBlock) {
                    pts = 1.0F;
                    if (soil.getValue(FarmBlock.MOISTURE) > 0) {
                        pts = 3.0F;
                    }
                }
                if (dx != 0 || dz != 0) {
                    pts /= 4.0F;
                }
                total += pts;
            }
        }
        boolean nsX = isCrop(level, pos.west(), crop) || isCrop(level, pos.east(), crop);
        boolean nsZ = isCrop(level, pos.north(), crop) || isCrop(level, pos.south(), crop);
        boolean diag = isCrop(level, pos.west().north(), crop) || isCrop(level, pos.east().north(), crop)
                || isCrop(level, pos.west().south(), crop) || isCrop(level, pos.east().south(), crop);
        if (diag || (nsX && nsZ)) {
            total /= 2.0F;
        }
        if (mindrake) {
            total /= 1.5F;
        }
        return total;
    }

    private static boolean isCrop(Level level, BlockPos pos, Block crop) {
        return level.getBlockState(pos).is(crop);
    }
}
