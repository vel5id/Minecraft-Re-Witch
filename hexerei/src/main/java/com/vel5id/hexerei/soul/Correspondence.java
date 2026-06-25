package com.vel5id.hexerei.soul;

import com.mojang.serialization.Codec;

/**
 * The dictionary of the Law: the domains a spirit/bond belongs to and that
 * {@link Act}s, {@code disturbance} and {@code resentment} are keyed by.
 *
 * <p>Kept deliberately small (Constitution Art. VI — low entry threshold): every
 * new axis is new complexity the player must learn. Reagents, rites, brews and
 * taint writes all tag themselves with one or more of these.
 */
public enum Correspondence {
    FOREST,      // лес — growth, root, parasite, green
    STONE,       // камень — the ancient, the buried, the worked
    WATER,       // вода — flow, dissolution, the drowned
    DEATH,       // смерть — rot, bone, the felled
    SKY,         // небо/буря — storm, moon, the high airs
    THRESHOLD;   // порог — doors, dreams, the in-between

    /** Serializes by {@link #key()}; unknown keys fail the codec rather than silently dropping. */
    public static final Codec<Correspondence> CODEC = Codec.STRING.comapFlatMap(
            s -> {
                Correspondence c = byKey(s);
                return c != null
                        ? com.mojang.serialization.DataResult.success(c)
                        : com.mojang.serialization.DataResult.error(() -> "Unknown correspondence: " + s);
            },
            Correspondence::key);

    /** Stable lowercase id used in NBT/datapack keys (never the enum name directly). */
    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Parse a {@link #key()} back to a constant, or null if unknown. */
    public static Correspondence byKey(String key) {
        if (key == null) return null;
        for (Correspondence c : values()) {
            if (c.key().equals(key)) return c;
        }
        return null;
    }
}
