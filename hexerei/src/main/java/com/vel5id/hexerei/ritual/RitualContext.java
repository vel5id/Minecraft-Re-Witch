package com.vel5id.hexerei.ritual;

/**
 * The taint/effect multipliers of the altar funding the rite currently being performed, carried on a
 * server-thread {@link ThreadLocal}. {@code RitualActivation} {@link #begin(float, float) begins} a context
 * around the {@code Rite.perform} call and {@link #end() ends} it in a {@code finally}; rites read the
 * multipliers via {@link Rites#addRitualTaint} (taint) and {@link #effectMul()} (effect, opt-in).
 *
 * <p>This avoids changing the {@code Rite.perform} signature (reserved for the Living-magic player handle).
 * Level ticks run single-threaded on the server thread, so the {@code ThreadLocal} is safe; with no context
 * set (no altar, or outside a ritual), both multipliers default to {@code 1.0} — behaviour unchanged.
 */
public final class RitualContext {
    private static final ThreadLocal<RitualContext> CURRENT = new ThreadLocal<>();

    private final float taintMul;
    private final float effectMul;

    private RitualContext(float taintMul, float effectMul) {
        this.taintMul = taintMul;
        this.effectMul = effectMul;
    }

    /** Installs the funding altar's multipliers for the current thread. Pair with {@link #end()} in a finally. */
    public static void begin(float taintMul, float effectMul) {
        CURRENT.set(new RitualContext(taintMul, effectMul));
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
