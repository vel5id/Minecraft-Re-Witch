package com.vel5id.hexerei.test;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.soul.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-world GameTests for the Dreaming Draught entry pipeline ({@link DreamEntry}).
 *
 * <p>Player instances come from {@code makeMockPlayer(GameType.SURVIVAL)}, matching
 * the pattern in {@link CurseGameTests}. This avoids the null-connection NPE that
 * {@code makeMockServerPlayerInLevel()} causes when {@code addEffect} syncs to the
 * client. {@code DreamEntry.onDrink} accepts {@link Player} after its signature was
 * widened for testability; the production {@code ServerPlayer} path is unaffected.
 */
@GameTestHolder(HexereiMod.MODID)
@PrefixGameTestTemplate(false)
public class DreamGameTests {

    /** Place a mock player at a test-relative position (mirrors CurseGameTests pattern). */
    private static Player playerAt(GameTestHelper h, BlockPos rel) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos abs = h.absolutePos(rel);
        player.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    /**
     * (a) Too little essence → the draught fizzles: no crossing, essence untouched,
     * no dreaming effect applied.
     */
    @GameTest(template = "empty")
    public void dream_poorEssence_fizzles(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player p = playerAt(helper, new BlockPos(2, 2, 2));
        PlayerSoulData psd = p.getData(HexereiAttachments.PLAYER_SOUL);
        psd.addEssence(DreamOnset.SCRY_COST - 0.5f);   // below the cost — cannot cross

        DreamEntry.onDrink(p, level);

        helper.assertTrue(psd.essence() == DreamOnset.SCRY_COST - 0.5f,
                "essence must be untouched on a fizzle");
        helper.assertTrue(p.getEffect(MobEffects.NIGHT_VISION) == null,
                "no dreaming effect on a fizzle");
        helper.succeed();
    }

    /**
     * (b) Calm soul (no debt, no marks, no disturbance) → crosses: essence debited by
     * SCRY_COST, Night Vision applied, no new fear mark added.
     */
    @GameTest(template = "empty")
    public void dream_calm_entersAndCharges(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player p = playerAt(helper, new BlockPos(2, 2, 2));
        PlayerSoulData psd = p.getData(HexereiAttachments.PLAYER_SOUL);
        psd.addEssence(5f);                             // affordable, no debt/marks → calm
        int marksBefore = psd.marks().size();

        DreamEntry.onDrink(p, level);

        helper.assertTrue(Math.abs(psd.essence() - (5f - DreamOnset.SCRY_COST)) < 1e-4f,
                "essence debited by SCRY_COST");
        helper.assertTrue(p.getEffect(MobEffects.NIGHT_VISION) != null,
                "calm dream grants Night Vision");
        helper.assertTrue(psd.marks().size() == marksBefore,
                "calm dream lays no fear mark");
        helper.succeed();
    }

    /**
     * (c) Burdened soul (debt=10, THRESHOLD disturbance=50) → nightmare: a fear mark
     * is added to PlayerSoulData, THRESHOLD disturbance rises in ChunkSoulData, and Nausea
     * (MobEffects.CONFUSION) is applied. Seeding makes this deterministic:
     * debtN=1.0, ambient=0.5 → dread=0.55 ≥ 0.5, clarity=0.25 < 0.4 → nightmare=true.
     * Disturbance is seeded below MAX (100) so DreamEntry's addDisturbance write is detectable.
     */
    @GameTest(template = "empty")
    public void dream_nightmare_marksAndDisturbs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player p = playerAt(helper, new BlockPos(2, 2, 2));
        PlayerSoulData psd = p.getData(HexereiAttachments.PLAYER_SOUL);
        psd.addEssence(5f);
        psd.setTotalDebt(10f);                          // debtN → 1.0
        var chunk = level.getChunkAt(p.blockPosition());
        ChunkSoulData csd = chunk.getData(HexereiAttachments.CHUNK_SOUL);
        csd.addDisturbance(Correspondence.THRESHOLD, 50f);   // ambient=0.5; clarity=0.25<0.4, dread=0.55≥0.5 → nightmare
        int marksBefore = psd.marks().size();
        float disturbBefore = csd.getDisturbance(Correspondence.THRESHOLD);

        DreamEntry.onDrink(p, level);

        helper.assertTrue(psd.marks().size() == marksBefore + 1,
                "nightmare lays a fear mark");
        helper.assertTrue(csd.getDisturbance(Correspondence.THRESHOLD) > disturbBefore,
                "nightmare raises THRESHOLD disturbance");
        helper.assertTrue(p.getEffect(MobEffects.CONFUSION) != null,
                "nightmare applies Nausea (CONFUSION)");
        helper.succeed();
    }

    /**
     * (3a) Sealing snapshots the inventory and empties it; restoring brings it back verbatim and
     * discards anything acquired in the dream. Uses a mock Player (no cross-dimension teleport).
     */
    @GameTest(template = "empty")
    public void dream_inventorySeal_roundTrips(GameTestHelper helper) {
        Player p = playerAt(helper, new BlockPos(2, 2, 2));
        p.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
        // Armor slot: Inventory has items[0..35], armor[0..3], offhand[40].
        // Helmet = armor[3] → flat index 36+3 = 39. (Slot 100 is the old InventoryMenu
        // container-slot convention and is out of bounds for the raw Inventory API.)
        p.getInventory().setItem(39, new ItemStack(Items.IRON_HELMET, 1));
        DreamState st = p.getData(HexereiAttachments.DREAM_STATE);

        DreamInventory.sealInto(p, st);
        helper.assertTrue(st.sealed(), "seal sets the sealed flag");
        helper.assertTrue(p.getInventory().isEmpty(), "seal empties the live inventory");

        // Simulate loot picked up inside the dream — it must NOT survive the wake.
        p.getInventory().setItem(5, new ItemStack(Items.DIRT, 64));

        DreamInventory.restoreFrom(p, st);
        helper.assertFalse(st.sealed(), "restore unseals");
        helper.assertTrue(p.getInventory().getItem(0).getItem() == Items.DIAMOND
                && p.getInventory().getItem(0).getCount() == 3, "diamonds restored verbatim");
        helper.assertTrue(p.getInventory().getItem(39).getItem() == Items.IRON_HELMET,
                "armor restored verbatim");
        helper.assertTrue(p.getInventory().getItem(5).isEmpty(), "dream-acquired loot discarded");
        helper.succeed();
    }

    /**
     * (3a) restoreFrom is a safe no-op when the state is not sealed, and idempotent after a restore —
     * the property the login-recovery / stale-flag branches rely on so a missed death never doubles
     * or wipes a normal inventory.
     */
    @GameTest(template = "empty")
    public void dream_restore_isNoOpWhenUnsealed(GameTestHelper helper) {
        Player p = playerAt(helper, new BlockPos(2, 2, 2));
        p.getInventory().setItem(0, new ItemStack(Items.EMERALD, 7));
        DreamState st = p.getData(HexereiAttachments.DREAM_STATE);

        // Not sealed → restore must change nothing.
        DreamInventory.restoreFrom(p, st);
        helper.assertTrue(p.getInventory().getItem(0).getItem() == Items.EMERALD
                && p.getInventory().getItem(0).getCount() == 7, "unsealed restore leaves inventory intact");

        // Seal, restore once (brings items back, unseals), then a second restore is a no-op.
        DreamInventory.sealInto(p, st);
        DreamInventory.restoreFrom(p, st);
        helper.assertFalse(st.sealed(), "first restore unseals");
        DreamInventory.restoreFrom(p, st);  // idempotent — must not wipe the just-restored inventory
        helper.assertTrue(p.getInventory().getItem(0).getItem() == Items.EMERALD
                && p.getInventory().getItem(0).getCount() == 7, "second restore is a no-op");
        helper.succeed();
    }
}
