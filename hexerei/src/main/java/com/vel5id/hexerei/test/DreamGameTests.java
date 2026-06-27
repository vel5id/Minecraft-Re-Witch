package com.vel5id.hexerei.test;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.soul.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
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
}
