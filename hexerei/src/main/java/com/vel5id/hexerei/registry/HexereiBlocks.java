package com.vel5id.hexerei.registry;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.block.AltarBlock;
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
}
