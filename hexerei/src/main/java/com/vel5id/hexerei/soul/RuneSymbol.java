package com.vel5id.hexerei.soul;

import java.util.Locale;

/**
 * The diegetic language of the ritual circle (Статья VI): a rune is a SYMBOL of a
 * {@link Correspondence} domain, not a connecting line. Three runes per domain, eighteen in all —
 * drawing one writes its domain into the rite's {@link Act} (wired when rituals read the vector model).
 *
 * Pure — no Minecraft dependency. The block projection is {@code RuneBlock}; the glyph textures are
 * {@code block/rune_<key>}.
 */
public enum RuneSymbol {
    // forest — growth, root, green
    SPROUT(Correspondence.FOREST), BRANCH(Correspondence.FOREST), ROOT(Correspondence.FOREST),
    // stone — the ancient, the buried, the worked
    FACET(Correspondence.STONE), PEAK(Correspondence.STONE), CRACK(Correspondence.STONE),
    // water — flow, dissolution, the drowned
    WAVE(Correspondence.WATER), DROP(Correspondence.WATER), STREAM(Correspondence.WATER),
    // death — rot, bone, the felled
    BONE(Correspondence.DEATH), SKULL(Correspondence.DEATH), WITHER(Correspondence.DEATH),
    // sky — storm, moon, the high airs
    MOON(Correspondence.SKY), BOLT(Correspondence.SKY), STAR(Correspondence.SKY),
    // threshold — doors, dreams, the in-between
    DOOR(Correspondence.THRESHOLD), EYE(Correspondence.THRESHOLD), SPIRAL(Correspondence.THRESHOLD);

    public static final RuneSymbol[] VALUES = values();

    private final Correspondence domain;

    RuneSymbol(Correspondence domain) {
        this.domain = domain;
    }

    public Correspondence domain() {
        return domain;
    }

    /** Stable lowercase id used in blockstate/texture keys (e.g. {@code rune_sprout}). */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static RuneSymbol byKey(String key) {
        for (RuneSymbol s : VALUES) {
            if (s.key().equals(key)) return s;
        }
        return null;
    }

    /** The symbol after this one in declaration order (wraps) — for chalk cycling. */
    public RuneSymbol next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }
}
