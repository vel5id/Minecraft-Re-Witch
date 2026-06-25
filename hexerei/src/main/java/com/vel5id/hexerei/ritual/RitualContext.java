package com.vel5id.hexerei.ritual;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * The taint/effect multipliers of the altar funding the rite currently being performed, carried on a
 * server-thread {@link ThreadLocal}. {@code RitualActivation} {@link #begin begins} a context around the
 * {@code Rite.perform} call and {@link #end() ends} it in a {@code finally}; rites read the multipliers via
 * {@link Rites#addRitualTaint} (taint) and {@link #effectMul()} (effect, opt-in).
 *
 * <p>Also carries the {@code caster} — the player who activated the rite (Модель: "who released this act"),
 * so a {@code Rite} can address its caster without changing the {@code Rite.perform} signature. Set from the
 * activation site ({@code RitualSigilBlock.use} has the right-clicking player); {@code null} when the rite
 * is driven without a player (GameTests, /setblock).
 *
 * <p>Level ticks run single-threaded on the server thread, so the {@link ThreadLocal} is safe; with no
 * context set (no altar, or outside a ritual), both multipliers default to {@code 1.0} and the caster is
 * {@code null} — behaviour unchanged.
 */
public final class RitualContext {
    private static final ThreadLocal<RitualContext> CURRENT = new ThreadLocal<>();

    private final float taintMul;
    private final float effectMul;
    @Nullable private final UUID caster;

    private RitualContext(float taintMul, float effectMul, @Nullable UUID caster) {
        this.taintMul = taintMul;
        this.effectMul = effectMul;
        this.caster = caster;
    }

    /** Installs the funding altar's multipliers and the activating caster for the current thread. */
    public static void begin(float taintMul, float effectMul, @Nullable UUID caster) {
        CURRENT.set(new RitualContext(taintMul, effectMul, caster));
    }

    /** Backward-compatible begin with no caster (GameTests / player-less activation). */
    public static void begin(float taintMul, float effectMul) {
        begin(taintMul, effectMul, null);
    }

    /** Clears the current thread's context. Safe to call without a matching {@link #begin}. */
    public static void end() {
        CURRENT.remove();
    }

    /** The current context, or {@code null} if none is installed. */
    public static RitualContext current() {
        return CURRENT.get();
    }

    public float taintMul() {
        return taintMul;
    }

    public float effectMul() {
        return effectMul;
    }

    /** The UUID of the player who activated the current rite, or {@code null} if there is none. */
    @Nullable
    public UUID caster() {
        return caster;
    }

    /** Convenience: the current rite's caster UUID, or {@code null} with no context / no caster. */
    @Nullable
    public static UUID currentCaster() {
        RitualContext c = CURRENT.get();
        return c == null ? null : c.caster;
    }

    /** The current taint multiplier, or {@code 1.0} with no context. */
    public static float currentTaintMul() {
        RitualContext c = CURRENT.get();
        return c == null ? 1.0f : c.taintMul;
    }

    /** The current effect multiplier, or {@code 1.0} with no context. */
    public static float currentEffectMul() {
        RitualContext c = CURRENT.get();
        return c == null ? 1.0f : c.effectMul;
    }
}
