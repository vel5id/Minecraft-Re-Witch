package com.vel5id.hexerei.test;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.item.Curse;
import com.vel5id.hexerei.item.CurseTickHandler;
import com.vel5id.hexerei.item.TaglockItem;
import com.vel5id.hexerei.soul.Bond;
import com.vel5id.hexerei.soul.Correspondence;
import com.vel5id.hexerei.soul.Disposition;
import com.vel5id.hexerei.soul.HexereiAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

@GameTestHolder(HexereiMod.MODID)
@PrefixGameTestTemplate(false)
public class CurseGameTests {

    /** Helper: a curse bond of {@code domain} with given strength and fear. */
    private static Bond curseBond(Correspondence domain, Curse.Strength strength, float fear) {
        return new Bond(
                UUID.randomUUID(),
                Curse.spiritTypeFor(domain, strength),
                domain,
                new Disposition(0f, 0f, fear, 0f),
                null,
                List.of(),
                List.of(),
                0L, 0L);
    }

    private static Player playerAt(GameTestHelper h, BlockPos rel) {
        Player player = h.makeMockPlayer(GameType.SURVIVAL);
        BlockPos abs = h.absolutePos(rel);
        player.setPos(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
        player.setHealth(player.getMaxHealth());
        return player;
    }

    /** Right-clicking a player with a taglock binds it to them. */
    @GameTest(template = "empty", batch = "curse", timeoutTicks = 100)
    public void taglockBindsToPlayer(GameTestHelper h) {
        Player victim = playerAt(h, new BlockPos(2, 2, 2));
        Player user = playerAt(h, new BlockPos(3, 2, 2));
        ItemStack taglock = new ItemStack(com.vel5id.hexerei.registry.HexereiItems.TAGLOCK.get());
        taglock.getItem().interactLivingEntity(taglock, user, victim, InteractionHand.MAIN_HAND);
        if (!TaglockItem.hasTarget(taglock)) {
            h.fail("taglock should bind to a player");
        } else if (!victim.getUUID().equals(TaglockItem.getTarget(taglock))) {
            h.fail("taglock should store the victim's UUID");
        } else {
            h.succeed();
        }
    }

    /** A worn curse applies its debuff (Weakness for DEATH) and ticks its fear down. */
    @GameTest(template = "empty", batch = "curse", timeoutTicks = 100)
    public void curseAppliesWeaknessAndTicksFear(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        Player player = playerAt(h, new BlockPos(2, 2, 2));
        // Plant a fresh WEAK/FULL curse into the player's marks
        float initialFear = Curse.initialGrip();
        player.getData(HexereiAttachments.PLAYER_SOUL)
                .addMark(curseBond(Correspondence.DEATH, Curse.Strength.FULL, initialFear));

        CurseTickHandler.processPlayer(player);

        // Should have Weakness II (amp 1) applied
        if (player.getEffect(MobEffects.WEAKNESS) == null) {
            h.fail("a DEATH curse should apply Weakness");
        } else if (player.getEffect(MobEffects.WEAKNESS).getAmplifier() != 1) {
            h.fail("FULL curse should be Weakness II (amp 1); got " + player.getEffect(MobEffects.WEAKNESS).getAmplifier());
        } else {
            // After one tick, fear should have decremented
            java.util.Optional<com.vel5id.hexerei.soul.PlayerSoulData> psd = java.util.Optional.of(player.getData(HexereiAttachments.PLAYER_SOUL));
            if (psd.isEmpty() || psd.get().marks().isEmpty()) {
                h.fail("curse bond should still be present after one tick");
            } else {
                float fear = psd.get().marks().get(0).disposition().fear();
                if (fear >= initialFear) {
                    h.fail("fear should decrement each tick; still " + fear);
                } else {
                    h.succeed();
                }
            }
        }
    }

    /** An echo curse applies a weaker debuff (Weakness I instead of II). */
    @GameTest(template = "empty", batch = "curse", timeoutTicks = 100)
    public void echoCurseIsWeaker(GameTestHelper h) {
        Player player = playerAt(h, new BlockPos(2, 2, 2));
        player.getData(HexereiAttachments.PLAYER_SOUL)
                .addMark(curseBond(Correspondence.DEATH, Curse.Strength.ECHO, Curse.initialGrip()));

        CurseTickHandler.processPlayer(player);

        if (player.getEffect(MobEffects.WEAKNESS) == null) {
            h.fail("echo curse should still apply Weakness");
        } else if (player.getEffect(MobEffects.WEAKNESS).getAmplifier() != 0) {
            h.fail("ECHO curse should be Weakness I (amp 0); got " + player.getEffect(MobEffects.WEAKNESS).getAmplifier());
        } else {
            h.succeed();
        }
    }

    /** After exactly CURSE_TICKS calls, the curse bond is removed from marks. */
    @GameTest(template = "empty", batch = "curse", timeoutTicks = 100)
    public void curseLiftsWhenSpent(GameTestHelper h) {
        Player player = playerAt(h, new BlockPos(2, 2, 2));
        float fear = 2f; // a curse with 2 ticks left
        player.getData(HexereiAttachments.PLAYER_SOUL)
                .addMark(curseBond(Correspondence.DEATH, Curse.Strength.FULL, fear));

        CurseTickHandler.processPlayer(player); // fear 2 -> 1
        CurseTickHandler.processPlayer(player); // fear 1 -> 0 (spent -> removed)

        java.util.Optional<com.vel5id.hexerei.soul.PlayerSoulData> psd = java.util.Optional.of(player.getData(HexereiAttachments.PLAYER_SOUL));
        if (psd.isPresent() && !psd.get().marks().isEmpty()) {
            h.fail("curse should be removed once spent");
        } else {
            h.succeed();
        }
    }

    /** An unbound taglock has no target. */
    @GameTest(template = "empty", batch = "curse", timeoutTicks = 100)
    public void freshTaglockIsUnbound(GameTestHelper h) {
        ItemStack taglock = new ItemStack(com.vel5id.hexerei.registry.HexereiItems.TAGLOCK.get());
        if (TaglockItem.hasTarget(taglock)) {
            h.fail("a freshly crafted taglock must be unbound");
        } else {
            h.succeed();
        }
    }
}
