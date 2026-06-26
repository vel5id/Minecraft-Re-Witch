package com.vel5id.hexerei.ritual;

import com.vel5id.hexerei.item.Curse;
import com.vel5id.hexerei.item.TaglockItem;
import com.vel5id.hexerei.soul.Bond;
import com.vel5id.hexerei.soul.Correspondence;
import com.vel5id.hexerei.soul.Disposition;
import com.vel5id.hexerei.soul.HexereiAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.UUID;

/**
 * The "create free bond" verb (Модель §7), weaponized: binds a spirit of {@code domain} onto a named victim
 * found via a {@link TaglockItem} dropped on the circle. The curse lands as a {@link Bond} in the victim's
 * {@code PlayerSoulData.marks}; the **caster bears an echo** (a milder copy) of the same curse — so curses
 * cannot be spammed for free (Article III: the weapon's edge points both ways).
 *
 * <p>One class, domain in the ctor (the SealAmuletRite pattern); the three domain recipes are three instances.
 * If no bound taglock is on the circle, or the target is offline, the rite **botches** (the reagent was
 * already consumed by activation; the taglock is discarded; disturbance is written) — never a free no-op.
 */
public final class CurseRite implements Rite {

    private static final double TAGLOCK_RADIUS = 2.5;

    private final Correspondence domain;

    public CurseRite(Correspondence domain) {
        this.domain = domain;
    }

    @Override
    public void perform(ServerLevel level, BlockPos center) {
        ItemEntity taglockEntity = findBoundTaglock(level, center);
        if (taglockEntity == null) {
            botch(level, center);
            return;
        }
        UUID targetUuid = TaglockItem.getTarget(taglockEntity.getItem());
        taglockEntity.discard();
        ServerPlayer victim = targetUuid == null ? null
                : level.getServer().getPlayerList().getPlayer(targetUuid);
        if (victim == null) {
            botch(level, center);   // target not in the world — the curse finds no mark
            return;
        }

        layCurse(level, victim, Curse.Strength.FULL);

        // The echo on the caster (anti-spam). No echo if self-targeted or the caster is absent.
        UUID casterUuid = RitualContext.currentCaster();
        if (casterUuid != null && !casterUuid.equals(targetUuid)) {
            ServerPlayer caster = level.getServer().getPlayerList().getPlayer(casterUuid);
            if (caster != null) {
                layCurse(level, caster, Curse.Strength.ECHO);
            }
        }

        level.playSound(null, center, SoundEvents.SOUL_ESCAPE, SoundSource.BLOCKS, 0.9f, 0.7f);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                center.getX() + 0.5, center.getY() + 1.0, center.getZ() + 0.5,
                20, 0.4, 0.6, 0.4, 0.04);
        Rites.addRitualTaint(level, new ChunkPos(center), domain, Curse.CURSE_DISTURBANCE);
    }

    private ItemEntity findBoundTaglock(ServerLevel level, BlockPos center) {
        AABB box = new AABB(center).inflate(TAGLOCK_RADIUS);
        for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, box,
                e -> e.isAlive() && TaglockItem.hasTarget(e.getItem()))) {
            return ie;
        }
        return null;
    }

    /** The rite botched — no bound taglock or target offline. Consumed reagent + taglock, disturbance written. */
    private void botch(ServerLevel level, BlockPos center) {
        level.playSound(null, center, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4f, 0.6f);
        level.sendParticles(ParticleTypes.SMOKE,
                center.getX() + 0.5, center.getY() + 0.6, center.getZ() + 0.5,
                16, 0.4, 0.3, 0.4, 0.02);
        Rites.addRitualTaint(level, new ChunkPos(center), domain, Curse.CURSE_DISTURBANCE / 2f);
    }

    /** Writes a curse bond into the player's marks (a free, un-sealed bond whose fear is the spirit's grip). */
    private void layCurse(ServerLevel level, ServerPlayer player, Curse.Strength strength) {
        long now = level.getGameTime();
        Bond curse = new Bond(
                UUID.randomUUID(),
                Curse.spiritTypeFor(domain, strength),
                domain,
                new Disposition(0f, 0f, Curse.initialGrip(), 0f),
                null,
                List.of(),
                List.of(),
                now, now);
        player.getData(HexereiAttachments.PLAYER_SOUL).addMark(curse);
    }
}
