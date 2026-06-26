package com.vel5id.hexerei.test;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.item.AmuletItem;
import com.vel5id.hexerei.item.AmuletTickHandler;
import com.vel5id.hexerei.item.CharmPouchItem;
import com.vel5id.hexerei.item.SealedAmulet;
import com.vel5id.hexerei.registry.HexereiItems;
import com.vel5id.hexerei.soul.Bond;
import com.vel5id.hexerei.soul.Correspondence;
import com.vel5id.hexerei.soul.Disposition;
import com.vel5id.hexerei.soul.HexereiAttachments;
import com.vel5id.hexerei.soul.Mark;
import com.vel5id.hexerei.soul.MarkScope;
import com.vel5id.hexerei.soul.SealRef;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import java.util.UUID;

@GameTestHolder(HexereiMod.MODID)
@PrefixGameTestTemplate(false)
public class AmuletGameTests {

    // ---- helpers ----

    /** A freshly sealed amulet of {@code domain} (full integrity, zeroed disposition). */
    private static ItemStack amulet(Correspondence domain) {
        ItemStack stack = new ItemStack(HexereiItems.AMULET.get());
        Bond bond = new Bond(
                UUID.randomUUID(),
                ResourceLocation.fromNamespaceAndPath("hexerei", domain.key() + "_warden"),
                domain,
                Disposition.EMPTY,
                new SealRef(SealedAmulet.FULL_INTEGRITY),
                List.of(new Mark(MarkScope.DOMAIN, domain, SealedAmulet.SEAL_MARK_SEVERITY)),
                List.of(), 0L, 0L);
        AmuletItem.writeBond(stack, bond);
        return stack;
    }

    /** A pouch holding a single amulet in slot 0. */
    private static ItemStack pouchWith(ItemStack amulet) {
        ItemStack pouch = new ItemStack(HexereiItems.CHARM_POUCH.get());
        ItemStackHandler handler = CharmPouchItem.createHandler();
        handler.setStackInSlot(0, amulet);
        CharmPouchItem.writeHandler(pouch, handler);
        return pouch;
    }

    private static Player playerAt(GameTestHelper h, BlockPos rel) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos abs = h.absolutePos(rel);
        player.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    // ---- tests ----

    /** A worn forest amulet grants Resistance (the wood-warden's endurance). */
    @GameTest(template = "empty", batch = "amulet", timeoutTicks = 100)
    public void forestAmuletGrantsResistance(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Player player = playerAt(h, new BlockPos(2, 2, 2));
        player.getInventory().setItem(0, pouchWith(amulet(Correspondence.FOREST)));

        AmuletTickHandler.processPlayer(level, player);

        if (player.getEffect(MobEffects.DAMAGE_RESISTANCE) == null) {
            h.fail("a worn forest amulet should grant Resistance");
        } else {
            h.succeed();
        }
    }

    /** Wearing accrues debt and grinds the seal (Article III cost — not free power). */
    @GameTest(template = "empty", batch = "amulet", timeoutTicks = 100)
    public void wearingAccruesDebtAndGrindsSeal(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Player player = playerAt(h, new BlockPos(2, 2, 2));
        ItemStack pouch = pouchWith(amulet(Correspondence.FOREST));
        UUID bondId = AmuletItem.readBond(CharmPouchItem.contents(pouch).get(0)).bondId();
        player.getInventory().setItem(0, pouch);

        AmuletTickHandler.processPlayer(level, player);

        Bond worn = AmuletItem.readBond(CharmPouchItem.contents(player.getInventory().getItem(0)).get(0));
        if (worn == null) {
            h.fail("amulet vanished from the pouch");
        } else if (worn.disposition().debt() <= 0f) {
            h.fail("wearing should accrue debt; got " + worn.disposition().debt());
        } else if (worn.seal().integrity() >= SealedAmulet.FULL_INTEGRITY) {
            h.fail("the seal should grind; integrity still " + worn.seal().integrity());
        } else if (!player.getData(HexereiAttachments.PLAYER_SOUL).amulets().contains(bondId)) {
            h.fail("the worn bond should be reconciled into PlayerSoulData.amulets");
        } else {
            h.succeed();
        }
    }

    /** A threshold amulet grants Strength only at/below its health threshold. */
    @GameTest(template = "empty", batch = "amulet", timeoutTicks = 100)
    public void thresholdAmuletGatedByLowHealth(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Player player = playerAt(h, new BlockPos(2, 2, 2));
        player.getInventory().setItem(0, pouchWith(amulet(Correspondence.THRESHOLD)));

        AmuletTickHandler.processPlayer(level, player); // full health: no Strength
        if (player.getEffect(MobEffects.DAMAGE_BOOST) != null) {
            h.fail("threshold amulet must not trigger at full health");
            return;
        }

        player.setHealth(6.0f); // drop to the threshold: Strength applies
        AmuletTickHandler.processPlayer(level, player);
        if (player.getEffect(MobEffects.DAMAGE_BOOST) == null) {
            h.fail("threshold amulet must trigger at <= 6 health");
        } else {
            h.succeed();
        }
    }

    /** A death amulet's aura weakens a nearby hostile, not the wearer. */
    @GameTest(template = "empty", batch = "amulet", timeoutTicks = 100)
    public void deathAmuletAuraWeakensNearbyMonster(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Player player = playerAt(h, new BlockPos(2, 2, 2));
        player.getInventory().setItem(0, pouchWith(amulet(Correspondence.DEATH)));
        Zombie zombie = h.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 2)); // 1 block away, within radius 5

        AmuletTickHandler.processPlayer(level, player);

        if (zombie.getEffect(MobEffects.WEAKNESS) == null) {
            h.fail("death amulet should weaken a hostile within 5 blocks");
        } else if (player.getEffect(MobEffects.WEAKNESS) != null) {
            h.fail("death amulet must debuff mobs, not the wearer");
        } else {
            h.succeed();
        }
    }

    /** The pouch accepts a sealed amulet but rejects non-amulets and nested pouches. */
    @GameTest(template = "empty", batch = "amulet", timeoutTicks = 100)
    public void pouchAcceptsAmuletsRejectsOthers(GameTestHelper h) {
        ItemStackHandler handler = CharmPouchItem.createHandler();
        ItemStack amulet = amulet(Correspondence.FOREST);
        if (!handler.isItemValid(0, amulet)) {
            h.fail("pouch must accept a sealed amulet");
        } else if (handler.isItemValid(1, new ItemStack(net.minecraft.world.item.Items.DIAMOND))) {
            h.fail("pouch must reject a non-amulet");
        } else if (handler.isItemValid(1, new ItemStack(HexereiItems.CHARM_POUCH.get()))) {
            h.fail("pouch must reject a nested pouch");
        } else {
            h.succeed();
        }
    }

    /** A blank (un-sealed) amulet item carries no effect and is rejected by the pouch. */
    @GameTest(template = "empty", batch = "amulet", timeoutTicks = 100)
    public void blankAmuletIsNotWearable(GameTestHelper h) {
        ItemStack blank = new ItemStack(HexereiItems.AMULET.get());
        if (AmuletItem.isSealed(blank)) {
            h.fail("a fresh amulet must not read as sealed");
        } else if (CharmPouchItem.createHandler().isItemValid(0, blank)) {
            h.fail("pouch must reject an un-sealed amulet");
        } else {
            h.succeed();
        }
    }
}
