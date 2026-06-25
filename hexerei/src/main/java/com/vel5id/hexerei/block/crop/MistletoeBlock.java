package com.vel5id.hexerei.block.crop;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Mistletoe: a parasitic staged crop that grows on wood instead of farmland.
 * Identical to {@link WitchCropBlock} (AGE, growth, bonemeal, CropDrops) except it may only be
 * planted on logs or leaves. v1 = on-top-of-wood placement (canSurvive routes through mayPlaceOn).
 */
public class MistletoeBlock extends WitchCropBlock {
    public MistletoeBlock(int maxAge, boolean water, boolean bonemealBig, boolean mindrake,
                          boolean snowbell, boolean wormwood,
                          Supplier<Item> seed, Supplier<Item> produce, @Nullable Supplier<Item> bonus,
                          Properties props) {
        super(maxAge, water, bonemealBig, mindrake, snowbell, wormwood, seed, produce, bonus, props);
    }

    @Override
    protected boolean mayPlaceOn(BlockState ground, BlockGetter w, BlockPos pos) {
        return ground.is(BlockTags.LOGS) || ground.is(BlockTags.LEAVES);
    }
}
