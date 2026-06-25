package com.vel5id.hexerei.soul;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One append-only line in a {@link Bond}'s memory (Модель §2): what happened,
 * when, and how big. Disposition drift and dialogue context are read from these.
 */
public record LedgerEntry(LedgerType type, float magnitude, long tick) {

    public static final Codec<LedgerEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            LedgerType.CODEC.fieldOf("type").forGetter(LedgerEntry::type),
            Codec.FLOAT.fieldOf("magnitude").forGetter(LedgerEntry::magnitude),
            Codec.LONG.fieldOf("tick").forGetter(LedgerEntry::tick)
    ).apply(i, LedgerEntry::new));
}
