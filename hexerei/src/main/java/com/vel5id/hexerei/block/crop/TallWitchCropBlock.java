package com.vel5id.hexerei.block.crop;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * A {@link WitchCropBlock} whose mature upward (second) segment is a distinct TOP state, so the
 * bottom and top blocks can render different textures. Used by hops: the bottom block shows the
 * lower bine, the top block shows the cone-laden upper bine — the two halves of one tall plant,
 * so nothing floats. Only tall crops carry the {@code top} property; ordinary crops do not.
 */
public class TallWitchCropBlock extends WitchCropBlock {
    public static final BooleanProperty TOP = BooleanProperty.create("top");

    public TallWitchCropBlock(int maxAge, boolean water, boolean bonemealBig, boolean mindrake,
                              boolean snowbell, boolean wormwood,
                              Supplier<Item> seed, Supplier<Item> produce, @Nullable Supplier<Item> bonus,
                              Properties props) {
        super(maxAge, water, bonemealBig, mindrake, snowbell, wormwood, seed, produce, bonus, props);
        // base ctor registered a default with only age set; pin top=false for the planted bottom.
        registerDefaultState(defaultBlockState().setValue(TOP, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        super.createBlockStateDefinition(b);   // adds age (STASH_MAX is set in the base ctor's super() call)
        b.add(TOP);
    }

    @Override
    protected BlockState upperBlockState() {
        return defaultBlockState().setValue(TOP, true).setValue(age(), 0);
    }
}
