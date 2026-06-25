package com.vel5id.hexerei.ritual;

import com.vel5id.hexerei.power.TaintLevel;

import java.util.List;

/**
 * The pure taint-punishment ladder: which debuffs a player standing in a chunk of a given {@link TaintLevel}
 * suffers. Server-side {@code WorldTaintAura.punishPlayers} resolves each {@link Effect}'s vanilla id to a
 * {@code MobEffect} and applies it; this class stays free of any Minecraft runtime so the mapping is
 * unit-testable without booting the game.
 *
 * <p>Refresh trick: every effect is applied for {@link #REFRESH_TICKS} (220) ticks on the existing 200-tick
 * pulse — duration &gt; cadence so the debuff never gaps while the player stays, and lapses ~1 s after leaving.
 */
public final class TaintPunishment {
    private TaintPunishment() {}

    /** Effect duration, in ticks: one 200-tick pulse period + ~1 s of slack (mirrors the brief's magic number). */
    public static final int REFRESH_TICKS = 220;

    /** A single vanilla debuff to apply: its {@code minecraft:}-namespaced id and amplifier (0 = level I). */
    public record Effect(String effectId, int amplifier) {}

    private static final Effect HUNGER = new Effect("minecraft:hunger", 0);
    private static final Effect WEAKNESS = new Effect("minecraft:weakness", 0);
    private static final Effect WITHER = new Effect("minecraft:wither", 0);

    /**
     * The debuffs to apply at a given chunk taint level:
     * <ul>
     *   <li>NONE   &rarr; none (clean land is safe)</li>
     *   <li>LOW    &rarr; Hunger I</li>
     *   <li>MEDIUM &rarr; Hunger I + Weakness I</li>
     *   <li>HIGH   &rarr; Hunger I + Weakness I + Wither I</li>
     * </ul>
     */
    public static List<Effect> effectsFor(TaintLevel level) {
        return switch (level) {
            case NONE -> List.of();
            case LOW -> List.of(HUNGER);
            case MEDIUM -> List.of(HUNGER, WEAKNESS);
            case HIGH -> List.of(HUNGER, WEAKNESS, WITHER);
        };
    }
}
