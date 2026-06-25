package com.vel5id.hexerei.soul;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * The seal that distinguishes an amulet from a free ritual bond (Модель §2):
 * a {@link Bond} with {@code seal == null} is a free/active mark; a non-null
 * seal means the bond is sealed into an item, drawing debt for a lasting effect.
 *
 * <p>{@code integrity} ∈ [0,1] is the seal's hold; resentment grinds it down
 * (захват амулета). When it reaches 0 the seal breaks outward.
 */
public record SealRef(float integrity) {

    public static final Codec<SealRef> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("integrity").forGetter(SealRef::integrity)
    ).apply(i, SealRef::new));

    public boolean isBroken() {
        return integrity <= 0f;
    }
}
