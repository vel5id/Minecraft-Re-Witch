package com.vel5id.hexerei.test;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.item.CharmCharge;
import com.vel5id.hexerei.item.CharmItem;
import com.vel5id.hexerei.item.CharmPouchItem;
import com.vel5id.hexerei.item.CharmTickHandler;
import com.vel5id.hexerei.power.AltarPowerManager;
import com.vel5id.hexerei.power.IPowerSource;
import com.vel5id.hexerei.registry.HexereiItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.ItemStackHandler;

@GameTestHolder(HexereiMod.MODID)
@PrefixGameTestTemplate(false)
public class CharmPouchGameTests {

    // ---- helpers ----

    private static ItemStack charm(net.minecraft.world.item.Item item, int charge) {
        ItemStack stack = new ItemStack(item);
        CharmItem.setCharge(stack, charge);
        return stack;
    }

    /** A pouch holding a single charm in slot 0. */
    private static ItemStack pouchWith(ItemStack charm) {
        ItemStack pouch = new ItemStack(HexereiItems.CHARM_POUCH.get());
        ItemStackHandler handler = CharmPouchItem.createHandler();
        handler.setStackInSlot(0, charm);
        CharmPouchItem.writeHandler(pouch, handler);
        return pouch;
    }

    private static int firstCharmCharge(ItemStack pouch) {
        return CharmItem.getCharge(CharmPouchItem.contents(pouch).get(0));
    }

    private static Player playerAt(GameTestHelper h, BlockPos rel) {
        Player player = h.makeMockPlayer();
        BlockPos abs = h.absolutePos(rel);
        player.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    // ---- tests ----

    /** A carried, charged Ward Charm grants Resistance. */
    @GameTest(template = "empty", batch = "charm", timeoutTicks = 100)
    public void wardCharmAppliesResistanceWhenCharged(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Player player = playerAt(h, new BlockPos(2, 2, 2));
        player.getInventory().setItem(0, pouchWith(charm(HexereiItems.WARD_CHARM.get(), CharmCharge.MAX_CHARGE)));

        CharmTickHandler.processPlayer(level, player);

        if (player.getEffect(MobEffects.DAMAGE_RESISTANCE) == null) {
            h.fail("ward charm should grant Resistance while charged");
        } else {
            h.succeed();
        }
    }

    /** A depleted charm is dormant — it grants nothing. */
    @GameTest(template = "empty", batch = "charm", timeoutTicks = 100)
    public void dormantCharmAppliesNothing(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Player player = playerAt(h, new BlockPos(2, 2, 2));
        player.getInventory().setItem(0, pouchWith(charm(HexereiItems.WARD_CHARM.get(), 0)));

        CharmTickHandler.processPlayer(level, player);

        if (player.getEffect(MobEffects.DAMAGE_RESISTANCE) != null) {
            h.fail("a 0-charge charm must be dormant");
        } else {
            h.succeed();
        }
    }

    /** Near a powered altar, a charm recharges and the altar is debited for the gain. */
    @GameTest(template = "empty", batch = "charm", timeoutTicks = 100)
    public void chargeRechargesAndDebitsAltar(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        BlockPos rel = new BlockPos(2, 2, 2);
        Player player = playerAt(h, rel);
        FakeAltar fake = new FakeAltar(level, h.absolutePos(rel), 100f);
        AltarPowerManager.get(level).register(fake);
        player.getInventory().setItem(0, pouchWith(charm(HexereiItems.WARD_CHARM.get(), 100)));

        CharmTickHandler.processPlayer(level, player);

        int after = firstCharmCharge(player.getInventory().getItem(0));
        float expectedCost = CharmCharge.RECHARGE_POWER_PER_UNIT * CharmCharge.RECHARGE; // 0.2 * 5 = 1.0
        AltarPowerManager.get(level).unregister(fake);
        if (after != 100 + CharmCharge.RECHARGE) {
            h.fail("expected charge 100 -> " + (100 + CharmCharge.RECHARGE) + ", got " + after);
        } else if (Math.abs(fake.power - (100f - expectedCost)) > 0.01f) {
            h.fail("expected altar debited " + expectedCost + " (100 -> " + (100f - expectedCost) + "), got " + fake.power);
        } else {
            h.succeed();
        }
    }

    /** Bloodlust only triggers when the wearer is at or below its health threshold. */
    @GameTest(template = "empty", batch = "charm", timeoutTicks = 100)
    public void bloodlustGatedByLowHealth(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Player player = playerAt(h, new BlockPos(2, 2, 2));
        player.getInventory().setItem(0, pouchWith(charm(HexereiItems.BLOODLUST_CHARM.get(), CharmCharge.MAX_CHARGE)));

        // full health: no Strength
        CharmTickHandler.processPlayer(level, player);
        if (player.getEffect(MobEffects.DAMAGE_BOOST) != null) {
            h.fail("bloodlust must not trigger at full health");
            return;
        }

        // drop to the threshold (3 hearts): Strength applies
        player.setHealth(6.0f);
        CharmTickHandler.processPlayer(level, player);
        if (player.getEffect(MobEffects.DAMAGE_BOOST) == null) {
            h.fail("bloodlust must trigger at <= 6 health");
        } else {
            h.succeed();
        }
    }

    /** Hexbane's aura weakens a hostile mob within range, not the wearer. */
    @GameTest(template = "empty", batch = "charm", timeoutTicks = 100)
    public void hexbaneDebuffsNearbyMonster(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Player player = playerAt(h, new BlockPos(2, 2, 2));
        player.getInventory().setItem(0, pouchWith(charm(HexereiItems.HEXBANE_CHARM.get(), CharmCharge.MAX_CHARGE)));
        Zombie zombie = h.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 2)); // 1 block away, within radius 5

        CharmTickHandler.processPlayer(level, player);

        if (zombie.getEffect(MobEffects.WEAKNESS) == null) {
            h.fail("hexbane should weaken a hostile within 5 blocks");
        } else if (player.getEffect(MobEffects.WEAKNESS) != null) {
            h.fail("hexbane must debuff mobs, not the wearer");
        } else {
            h.succeed();
        }
    }

    /** Pouch NBT round-trips contents, and the handler rejects non-charms and nested pouches. */
    @GameTest(template = "empty", batch = "charm", timeoutTicks = 100)
    public void pouchNbtRoundTripAndValidity(GameTestHelper h) {
        ItemStackHandler handler = CharmPouchItem.createHandler();
        handler.setStackInSlot(0, charm(HexereiItems.WARD_CHARM.get(), 321));
        handler.setStackInSlot(1, charm(HexereiItems.HEXBANE_CHARM.get(), 60));

        ItemStack pouch = new ItemStack(HexereiItems.CHARM_POUCH.get());
        CharmPouchItem.writeHandler(pouch, handler);
        java.util.List<ItemStack> contents = CharmPouchItem.contents(pouch);

        if (contents.size() != 2) {
            h.fail("expected 2 charms after round-trip, got " + contents.size());
        } else if (CharmItem.getCharge(contents.get(0)) != 321 || CharmItem.getCharge(contents.get(1)) != 60) {
            h.fail("charges did not survive NBT round-trip");
        } else if (handler.isItemValid(2, new ItemStack(net.minecraft.world.item.Items.DIAMOND))) {
            h.fail("handler must reject a non-charm");
        } else if (handler.isItemValid(2, new ItemStack(HexereiItems.CHARM_POUCH.get()))) {
            h.fail("handler must reject a nested pouch");
        } else {
            h.succeed();
        }
    }

    /** Minimal in-range power source (mirrors CauldronGameTests.FakeAltar). */
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
