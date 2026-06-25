package com.vel5id.hexerei.registry;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.block.AltarBlock;
import com.vel5id.hexerei.block.BloodMossBlock;
import com.vel5id.hexerei.block.WitchMushroomBlock;
import com.vel5id.hexerei.block.cauldron.CauldronBlock;
import com.vel5id.hexerei.block.ritual.RitualSigilBlock;
import com.vel5id.hexerei.block.ritual.RuneBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class HexereiBlocks {
    private HexereiBlocks() {}

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, HexereiMod.MODID);

    public static final RegistryObject<Block> ALTAR = BLOCKS.register("altar",
            () -> new AltarBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(2.0F)              // hardness 2.0F
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()));

    public static final RegistryObject<Block> CAULDRON = BLOCKS.register("cauldron",
            () -> new CauldronBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(2.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    public static final RegistryObject<Block> RUNE = BLOCKS.register("rune",
            () -> new RuneBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SAND)
                    .strength(0.2F)
                    .noCollission()
                    .noOcclusion()
                    .sound(SoundType.SAND)));

    public static final RegistryObject<Block> RITUAL_SIGIL = BLOCKS.register("ritual_sigil",
            () -> new RitualSigilBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(0.6F)
                    .noCollission()
                    .noOcclusion()
                    .sound(SoundType.SAND)));

    public static final RegistryObject<Block> TAINTED_GROUND = BLOCKS.register("tainted_ground",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(0.6F)
                    .sound(SoundType.GRAVEL)));

    public static final RegistryObject<Block> CHARRED_STONE = BLOCKS.register("charred_stone",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(1.5F)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()));

    // --- slice H: ground decoration + mushrooms ---
    // Blood Moss: flat carpet-style red ground cover, non-spreading v1; drops self via loot JSON.
    public static final RegistryObject<Block> BLOOD_MOSS = BLOCKS.register("blood_moss",
            () -> new BloodMossBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .noCollission()
                    .instabreak()
                    .sound(SoundType.MOSS_CARPET)));

    // Zevanty: glowing mushroom (light level 8). Mushrooms drop self via loot JSON (no getDrops override).
    public static final RegistryObject<Block> ZEVANTY = BLOCKS.register("zevanty",
            () -> new WitchMushroomBlock(true, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .noCollission()
                    .instabreak()
                    .sound(SoundType.GRASS)
                    .lightLevel(s -> 8)
                    .randomTicks()));

    public static final RegistryObject<Block> PUFFBALL = BLOCKS.register("puffball",
            () -> new WitchMushroomBlock(false, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_WHITE)
                    .noCollission()
                    .instabreak()
                    .sound(SoundType.GRASS)
                    .randomTicks()));

    public static final RegistryObject<Block> WEBCAP = BLOCKS.register("webcap",
            () -> new WitchMushroomBlock(false, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .noCollission()
                    .instabreak()
                    .sound(SoundType.GRASS)
                    .randomTicks()));
}
