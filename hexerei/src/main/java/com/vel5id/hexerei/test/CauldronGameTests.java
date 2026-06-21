package com.vel5id.hexerei.test;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.blockentity.CauldronBlockEntity;
import com.vel5id.hexerei.brewing.Brews;
import com.vel5id.hexerei.item.BrewItem;
import com.vel5id.hexerei.power.AltarPowerManager;
import com.vel5id.hexerei.power.IPowerSource;
import com.vel5id.hexerei.registry.HexereiBlocks;
import com.vel5id.hexerei.registry.HexereiItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(HexereiMod.MODID)
@PrefixGameTestTemplate(false)
public class CauldronGameTests {

    private static void spawnIngredient(GameTestHelper h, BlockPos relCauldron, Item item) {
        BlockPos abs = h.absolutePos(relCauldron);
        ItemEntity ie = new ItemEntity(h.getLevel(),
                abs.getX() + 0.5, abs.getY() + 0.4, abs.getZ() + 0.5, new ItemStack(item));
        ie.setDeltaMovement(Vec3.ZERO);
        h.getLevel().addFreshEntity(ie);
    }

    /** Core loop, no altar needed: water + heat -> boiling; absorbs the two herbs -> recipe matches. */
    @GameTest(template = "empty", batch = "cauldron", timeoutTicks = 200)
    public void cauldronAbsorbsAndMatches(GameTestHelper h) {
        BlockPos caul = new BlockPos(18, 2, 18);
        h.setBlock(caul, HexereiBlocks.CAULDRON.get());
        h.setBlock(caul.below(), Blocks.MAGMA_BLOCK);
        ((CauldronBlockEntity) h.getBlockEntity(caul)).fillWater();
        h.runAfterDelay(110, () -> {
            spawnIngredient(h, caul, HexereiItems.MANDRAKE_ROOT.get());
            spawnIngredient(h, caul, HexereiItems.BELLADONNA_FLOWER.get());
        });
        h.runAfterDelay(150, () -> {
            CauldronBlockEntity be = (CauldronBlockEntity) h.getBlockEntity(caul);
            if (be == null || !be.isBoiling()) {
                h.fail("cauldron not boiling");
            } else if (be.readyBrew() != Brews.SLEEPING_DRAUGHT) {
                h.fail("cauldron did not match Sleeping Draught; ready=" + be.readyBrew());
            } else {
                h.succeed();
            }
        });
    }

    /** Power gating: a ready cauldron with NO altar in range cannot be collected. */
    @GameTest(template = "empty", batch = "cauldron", timeoutTicks = 200)
    public void cauldronWithoutAltarNotCollectable(GameTestHelper h) {
        BlockPos caul = new BlockPos(18, 2, 18);
        h.setBlock(caul, HexereiBlocks.CAULDRON.get());
        h.setBlock(caul.below(), Blocks.MAGMA_BLOCK);
        ((CauldronBlockEntity) h.getBlockEntity(caul)).fillWater();
        h.runAfterDelay(110, () -> {
            spawnIngredient(h, caul, HexereiItems.MANDRAKE_ROOT.get());
            spawnIngredient(h, caul, HexereiItems.BELLADONNA_FLOWER.get());
        });
        h.runAfterDelay(150, () -> {
            CauldronBlockEntity be = (CauldronBlockEntity) h.getBlockEntity(caul);
            if (be == null || be.readyBrew() != Brews.SLEEPING_DRAUGHT) {
                h.fail("cauldron not ready");
            } else if (be.isPowered() || be.collectBrew() != null) {
                h.fail("cauldron should NOT be collectable without altar power");
            } else {
                h.succeed();
            }
        });
    }

    /**
     * Full positive path, deterministic: inject a powered source into the manager (isolating the
     * cauldron's collect+consume from real-altar formation), then collect and assert power was spent.
     */
    @GameTest(template = "empty", batch = "cauldron", timeoutTicks = 200)
    public void cauldronCollectsWithPower(GameTestHelper h) {
        BlockPos caul = new BlockPos(18, 2, 18);
        h.setBlock(caul, HexereiBlocks.CAULDRON.get());
        h.setBlock(caul.below(), Blocks.MAGMA_BLOCK);
        ((CauldronBlockEntity) h.getBlockEntity(caul)).fillWater();
        FakeAltar fake = new FakeAltar(h.getLevel(), h.absolutePos(caul), 1000f);
        AltarPowerManager.get(h.getLevel()).register(fake);
        h.runAfterDelay(110, () -> {
            spawnIngredient(h, caul, HexereiItems.MANDRAKE_ROOT.get());
            spawnIngredient(h, caul, HexereiItems.BELLADONNA_FLOWER.get());
        });
        h.runAfterDelay(150, () -> {
            CauldronBlockEntity be = (CauldronBlockEntity) h.getBlockEntity(caul);
            if (be == null || be.readyBrew() != Brews.SLEEPING_DRAUGHT) {
                h.fail("cauldron not ready");
                return;
            }
            ItemStack brew = be.collectBrew();
            if (brew == null) {
                h.fail("collectBrew failed despite available power");
            } else if (BrewItem.brewOf(brew) != Brews.SLEEPING_DRAUGHT) {
                h.fail("wrong brew collected: " + BrewItem.brewOf(brew));
            } else if (Math.abs(fake.power - 950f) > 0.01f) {
                h.fail("expected 50 power consumed (1000->950); got " + fake.power);
            } else {
                AltarPowerManager.get(h.getLevel()).unregister(fake);
                h.succeed();
            }
        });
    }

    /** Minimal in-range power source for the deterministic collect test. */
    private static final class FakeAltar implements IPowerSource {
        private final Level level;
        private final BlockPos pos;
        private float power;

        FakeAltar(Level level, BlockPos pos, float power) {
            this.level = level;
            this.pos = pos;
            this.power = power;
        }

        @Override public Level getWorld() { return level; }
        @Override public BlockPos getLocation() { return pos; }
        @Override public boolean isLocationEqual(BlockPos p) { return pos.equals(p); }
        @Override public boolean consumePower(float r) {
            if (power >= r) { power -= r; return true; }
            return false;
        }
        @Override public float getCurrentPower() { return power; }
        @Override public float getRange() { return 16f; }
        @Override public int getEnhancementLevel() { return 0; }
        @Override public boolean isPowerInvalid() { return false; }
    }
}
