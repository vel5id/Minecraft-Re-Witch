package com.vel5id.hexerei.soul;

import com.mojang.serialization.Codec;
import java.util.Locale;

/** Reach of a {@link Mark} the world lays on the witch (Модель §2). */
public enum MarkScope {
    BIOME,   // "this forest remembers you"
    DOMAIN,  // every forest, anywhere
    GLOBAL;  // the world at large

    public static final Codec<MarkScope> CODEC = Codec.STRING.xmap(
            s -> MarkScope.valueOf(s.toUpperCase(Locale.ROOT)),
            s -> s.name().toLowerCase(Locale.ROOT));
}
