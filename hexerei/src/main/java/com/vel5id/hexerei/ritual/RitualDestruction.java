package com.vel5id.hexerei.ritual;

import com.vel5id.hexerei.network.HexereiNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * The destruction penalty fired when a bound ritual sigil or one of its ring runes is broken.
 * Breaking a circle is "dirty": it taints the chunk by the bound rite's {@code powerCost/4} and
 * fouls nearby players with a brief Weakness + Mining Fatigue backlash.
 */
public final class RitualDestruction {
    private RitualDestruction() {}

    /** Taint floor used when the bound rite id is unknown (MANIFEST_CHALK power cost). */
    private static final int DEFAULT_POWER_COST = 40;
    private static final int DEBUFF_TICKS = 100; // 5 s
    private static final double PLAYER_RADIUS = 4.0;

    /**
     * Apply the area penalty for breaking a bound circle at {@code sigilPos}: taint the chunk by
     * {@code boundRite.powerCost/4} (cap + permanent floor handled by ChunkSoulData) and
     * apply Weakness I + Mining Fatigue I for 100t to players within radius 4, with a WITCH burst
     * and a low WITHER_DEATH cue.
     *
     * @param boundRiteId the rite the broken circle was bound to (may be empty/unknown)
     */
    public static void penalize(ServerLevel level, BlockPos sigilPos, String boundRiteId) {
        RitualRecipe rite = RitualRecipes.BY_ID.get(boundRiteId);
        int cost = (rite != null) ? rite.powerCost() : DEFAULT_POWER_COST;
        float taint = cost / 4f;

        ChunkPos cp = new ChunkPos(sigilPos);
        com.vel5id.hexerei.soul.Disturbance.add(level, cp, com.vel5id.hexerei.soul.Correspondence.DEATH, taint);

        AABB area = new AABB(sigilPos).inflate(PLAYER_RADIUS);
        List<Player> players = level.getEntitiesOfClass(Player.class, area);
        for (Player p : players) {
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, DEBUFF_TICKS, 0));
            p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, DEBUFF_TICKS, 0));
        }

        level.sendParticles(ParticleTypes.WITCH,
                sigilPos.getX() + 0.5, sigilPos.getY() + 0.5, sigilPos.getZ() + 0.5,
                40, 0.8, 0.4, 0.8, 0.1);
        level.playSound(null, sigilPos, SoundEvents.WITHER_DEATH, SoundSource.BLOCKS, 0.6f, 0.5f);
    }
}
