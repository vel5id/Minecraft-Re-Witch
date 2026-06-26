package com.vel5id.hexerei.test;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.block.crop.WitchCropBlock;
import com.vel5id.hexerei.registry.HexereiCrops;
import com.vel5id.hexerei.registry.HexereiItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(HexereiMod.MODID)
@PrefixGameTestTemplate(false)
public class CropGameTests {

    private static WitchCropBlock belladonna() {
        return (WitchCropBlock) HexereiCrops.BELLADONNA.get();
    }

    @GameTest(template = "empty", batch = "crops", timeoutTicks = 100)
    public void matureCropDropsProduce(GameTestHelper h) {
        // Deterministic: compute getDrops from an explicit mature state (no entity spawning / arena coupling).
        BlockPos crop = new BlockPos(18, 2, 18);
        WitchCropBlock b = belladonna();
        h.setBlock(crop.below(), Blocks.FARMLAND);
        BlockState mature = b.defaultBlockState().setValue(b.age(), b.maxAge());
        h.setBlock(crop, mature);
        List<ItemStack> drops = Block.getDrops(mature, h.getLevel(), h.absolutePos(crop), null);
        boolean hasFlower = drops.stream().anyMatch(s -> s.is(HexereiItems.BELLADONNA_FLOWER.get()));
        if (!hasFlower) {
            h.fail("mature belladonna did not drop a flower; drops=" + drops);
        } else {
            h.succeed();
        }
    }

    @GameTest(template = "empty", batch = "crops", timeoutTicks = 100)
    public void immatureCropDropsSeedNotProduce(GameTestHelper h) {
        BlockPos crop = new BlockPos(18, 2, 18);
        WitchCropBlock b = belladonna();
        h.setBlock(crop.below(), Blocks.FARMLAND);
        BlockState young = b.defaultBlockState(); // age 0
        h.setBlock(crop, young);
        List<ItemStack> drops = Block.getDrops(young, h.getLevel(), h.absolutePos(crop), null);
        boolean hasSeed = drops.stream().anyMatch(s -> s.is(HexereiCrops.seedItem("belladonna")));
        boolean hasFlower = drops.stream().anyMatch(s -> s.is(HexereiItems.BELLADONNA_FLOWER.get()));
        if (!hasSeed || hasFlower) {
            h.fail("immature belladonna should drop only a seed; drops=" + drops);
        } else {
            h.succeed();
        }
    }

    @GameTest(template = "empty", batch = "crops", timeoutTicks = 100)
    public void bonemealAdvancesAge(GameTestHelper h) {
        BlockPos crop = new BlockPos(18, 2, 18);
        WitchCropBlock b = belladonna();
        h.setBlock(crop.below(), Blocks.FARMLAND);
        h.setBlock(crop, b.defaultBlockState()); // age 0
        BlockState placed = h.getBlockState(crop);
        b.performBonemeal(h.getLevel(), h.getLevel().getRandom(), h.absolutePos(crop), placed);
        h.succeedWhen(() -> h.assertTrue(
                h.getBlockState(crop).getValue(b.age()) > 0, "bonemeal did not advance crop age"));
    }

    @GameTest(template = "empty", batch = "crops", timeoutTicks = 100)
    public void cropPlantsOnlyOnFarmland(GameTestHelper h) {
        // Distinct positions (no mutating one block) so each canSurvive check is independent.
        WitchCropBlock b = belladonna();
        BlockState seed = b.defaultBlockState();
        BlockState matureCrop = b.defaultBlockState().setValue(b.age(), b.maxAge());

        BlockPos onF = new BlockPos(10, 2, 10);
        BlockPos onD = new BlockPos(14, 2, 14);
        BlockPos onC = new BlockPos(18, 2, 18);
        h.setBlock(onF.below(), Blocks.FARMLAND);
        h.setBlock(onD.below(), Blocks.DIRT);
        h.setBlock(onC.below(), matureCrop);

        boolean onFarmland = seed.canSurvive(h.getLevel(), h.absolutePos(onF));
        boolean onDirt = seed.canSurvive(h.getLevel(), h.absolutePos(onD));
        boolean onCrop = seed.canSurvive(h.getLevel(), h.absolutePos(onC)); // no stacking
        if (onFarmland && !onDirt && !onCrop) {
            h.succeed();
        } else {
            h.fail("placement rules wrong: farmland=" + onFarmland + " dirt=" + onDirt + " onCrop=" + onCrop);
        }
    }
}
