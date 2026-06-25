package com.vel5id.hexerei.ritual;

import com.vel5id.hexerei.power.AltarPowerManager;
import com.vel5id.hexerei.power.BloodMoonData;
import com.vel5id.hexerei.power.IPowerSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

/**
 * Server-side blood-moon upkeep on the existing 200-tick world pulse: clear at dawn, trickle ambient taint
 * into each altar's chunk, and embolden hostiles near survival players. Keeps {@code HexereiLevelEvents} thin,
 * paralleling {@code WorldTaintAura}.
 */
public final class BloodMoonPulse {
    private BloodMoonPulse() {}

    private static final int MOB_BUFF_RADIUS = 24;
    private static final int BUFF_TICKS = 220; // > the 200-tick pulse so the buff never gaps

    public static void tick(ServerLevel level) {
        BloodMoonData data = BloodMoonData.get(level);
        if (!data.isActive()) {
            return;
        }
        if (data.shouldClear(level)) {
            data.clear(level);
            return;
        }

        // The land sickens: a small ambient taint tick into each active altar's chunk.
        for (IPowerSource src : AltarPowerManager.get(level).allSources()) {
            if (src == null || src.isPowerInvalid()) {
                continue;
            }
            Rites.addRitualTaint(level, new ChunkPos(src.getLocation()), BloodMoonData.BLOOD_AMBIENT_TAINT);
        }

        // Hostiles emboldened near survival/adventure players.
        for (ServerPlayer p : level.players()) {
            if (p.isCreative() || p.isSpectator()) {
                continue;
            }
            AABB box = p.getBoundingBox().inflate(MOB_BUFF_RADIUS);
            for (Monster mob : level.getEntitiesOfClass(Monster.class, box)) {
                mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, BUFF_TICKS, 0, true, false, false));
                mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, BUFF_TICKS, 0, true, false, false));
            }
        }
    }
}
