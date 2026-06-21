package com.vel5id.hexerei.registry;

import com.vel5id.hexerei.HexereiMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class HexereiTags {
    private HexereiTags() {}

    /** Blocks that, placed directly under a cauldron, bring it to a boil. */
    public static final TagKey<Block> CAULDRON_HEAT_SOURCES =
            BlockTags.create(new ResourceLocation(HexereiMod.MODID, "cauldron_heat_sources"));
}
