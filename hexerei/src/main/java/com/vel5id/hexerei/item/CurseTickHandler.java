package com.vel5id.hexerei.item;

import com.vel5id.hexerei.soul.Bond;
import com.vel5id.hexerei.soul.Disposition;
import com.vel5id.hexerei.soul.HexereiCapabilities;
import com.vel5id.hexerei.soul.PlayerSoulData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeMod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Drives curse bonds once per second (server-only, on the 20-tick boundary): each curse {@link Bond} in a
 * player's {@code PlayerSoulData.marks} inflicts its domain debuff (full strength, or ~20% for a caster's
 * echo) and ticks the spirit's grip down; when the grip is spent the spirit departs (bond removed).
 *
 * <p>Attribute modifiers are refreshed <em>self-correctingly</em>: every tick all known curse modifiers are
 * removed, then exactly those for the still-active curses are re-added — so a lifted curse leaves no stale
 * modifier, and stacking identical curses extends duration without compounding the debuff. Potions are
 * refreshed each tick and lapse ~2s after the last active curse (vanilla replaces equal/stronger).
 */
public final class CurseTickHandler {
    private CurseTickHandler() {}

    private static final int EFFECT_DURATION = 40;

    // Transient-modifier UUIDs per (kind, strength, role). The reach UUID is shared across BLOCK+ENTITY reach
    // (they are distinct AttributeInstances, so a shared UUID is allowed).
    private static final UUID CLUMSY_REACH_FULL = UUID.fromString("a1b2c3d4-0001-0f0f-8a0a-000000000001");
    private static final UUID CLUMSY_REACH_ECHO = UUID.fromString("a1b2c3d4-0001-0f0f-8a0a-000000000002");
    private static final UUID CLUMSY_GRAV_FULL  = UUID.fromString("a1b2c3d4-0002-0f0f-8a0a-000000000003");
    private static final UUID CLUMSY_GRAV_ECHO  = UUID.fromString("a1b2c3d4-0002-0f0f-8a0a-000000000004");
    private static final UUID UNLUCKY_FULL      = UUID.fromString("a1b2c3d4-0003-0f0f-8a0a-000000000005");
    private static final UUID UNLUCKY_ECHO      = UUID.fromString("a1b2c3d4-0003-0f0f-8a0a-000000000006");

    public static void tick(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            processPlayer(p);
        }
    }

    /** Process one player's curse bonds. Exposed so GameTests can drive a mock player directly. */
    public static void processPlayer(Player player) {
        java.util.Optional<PlayerSoulData> psd = player.getCapability(HexereiCapabilities.PLAYER_SOUL).resolve();
        if (psd.isEmpty()) {
            return;
        }
        PlayerSoulData sd = psd.get();

        List<Bond> original = sd.marks();
        List<Bond> rebuilt = new ArrayList<>();
        Set<Curse.CurseEffect> active = new HashSet<>();

        for (Bond b : original) {
            Curse.Strength s = Curse.isEcho(b) ? Curse.Strength.ECHO : Curse.Strength.FULL;
            Curse.CurseEffect fx = Curse.effectFor(b.domain(), s);
            if (fx == null) {
                rebuilt.add(b);             // a non-curse mark — leave untouched
                continue;
            }
            float fear = Curse.gripAfter(b.disposition().fear());
            if (Curse.isSpent(fear)) {
                continue;                   // spirit departs — drop the bond
            }
            active.add(fx);
            Disposition d = b.disposition();
            rebuilt.add(b.withDisposition(new Disposition(d.debt(), d.resentment(), fear, d.loyalty())));
        }
        original.clear();
        original.addAll(rebuilt);

        applyEffects(player, active);
    }

    private static void applyEffects(Player player, Set<Curse.CurseEffect> active) {
        Attribute blockReach = ForgeMod.BLOCK_REACH.get();
        Attribute entityReach = ForgeMod.ENTITY_REACH.get();
        Attribute gravity = ForgeMod.ENTITY_GRAVITY.get();

        // 1. clear every known curse modifier (no stale modifier survives a lifted curse)
        remove(player, blockReach, CLUMSY_REACH_FULL, CLUMSY_REACH_ECHO);
        remove(player, entityReach, CLUMSY_REACH_FULL, CLUMSY_REACH_ECHO);
        remove(player, gravity, CLUMSY_GRAV_FULL, CLUMSY_GRAV_ECHO);
        remove(player, Attributes.LUCK, UNLUCKY_FULL, UNLUCKY_ECHO);

        // 2. re-apply exactly the active curses
        for (Curse.CurseEffect fx : active) {
            switch (fx.kind()) {
                case CLUMSY -> {
                    Curse.ClumsyValues v = Curse.clumsy(fx.strength());
                    boolean full = fx.strength() == Curse.Strength.FULL;
                    UUID reachId = full ? CLUMSY_REACH_FULL : CLUMSY_REACH_ECHO;
                    UUID gravId = full ? CLUMSY_GRAV_FULL : CLUMSY_GRAV_ECHO;
                    add(player, blockReach, reachId, "curse_clumsy_reach", v.reachFactor() - 1.0, AttributeModifier.Operation.MULTIPLY_TOTAL);
                    add(player, entityReach, reachId, "curse_clumsy_reach", v.reachFactor() - 1.0, AttributeModifier.Operation.MULTIPLY_TOTAL);
                    add(player, gravity, gravId, "curse_clumsy_grav", v.gravityFactor() - 1.0, AttributeModifier.Operation.MULTIPLY_TOTAL);
                }
                case UNLUCKY -> {
                    Curse.UnluckyValues v = Curse.unlucky(fx.strength());
                    UUID id = fx.strength() == Curse.Strength.FULL ? UNLUCKY_FULL : UNLUCKY_ECHO;
                    add(player, Attributes.LUCK, id, "curse_unlucky", v.luckAmount(), AttributeModifier.Operation.ADDITION);
                    player.addEffect(instance(MobEffects.UNLUCK, v.unluckAmp()));
                }
                case WEAK -> player.addEffect(instance(MobEffects.WEAKNESS, Curse.weaknessAmp(fx.strength())));
            }
        }
    }

    private static void remove(Player player, Attribute attr, UUID... ids) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) {
            return;
        }
        for (UUID id : ids) {
            inst.removeModifier(id);
        }
    }

    private static void add(Player player, Attribute attr, UUID id, String name, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) {
            return;
        }
        inst.addTransientModifier(new AttributeModifier(id, name, amount, op));
    }

    private static MobEffectInstance instance(MobEffect effect, int amplifier) {
        return new MobEffectInstance(effect, EFFECT_DURATION, amplifier, true, false, true);
    }
}
