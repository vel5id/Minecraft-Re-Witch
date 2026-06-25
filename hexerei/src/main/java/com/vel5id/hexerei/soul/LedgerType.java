package com.vel5id.hexerei.soul;

import com.mojang.serialization.Codec;
import java.util.Locale;

/** Kinds of remembered event in a {@link Bond}'s ledger (Модель §2, append-only). */
public enum LedgerType {
    RELEASED,  // spirit freed from the world (a take)
    APPEASED,  // an offering ritual (reciprocity > 0)
    BETRAYED,  // bound thing broken — big resentment jump
    FED,       // a gift
    SEALED,    // bound into an amulet
    SPOKE;     // the spirit pressed its grievance

    public static final Codec<LedgerType> CODEC = Codec.STRING.xmap(
            s -> LedgerType.valueOf(s.toUpperCase(Locale.ROOT)),
            s -> s.name().toLowerCase(Locale.ROOT));
}
