package com.vel5id.hexerei.soul;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A mark a bond lays on its holder (Модель §2): "the forest remembers, you are
 * marked" = {@code Mark(BIOME, FOREST, 0.5)}. Marks raise the resentment floor
 * (Грамматика decay) so attention can heal a domain only down to what the marks
 * still justify.
 */
public record Mark(MarkScope scope, Correspondence domain, float severity) {

    public static final Codec<Mark> CODEC = RecordCodecBuilder.create(i -> i.group(
            MarkScope.CODEC.fieldOf("scope").forGetter(Mark::scope),
            Correspondence.CODEC.fieldOf("domain").forGetter(Mark::domain),
            Codec.FLOAT.fieldOf("severity").forGetter(Mark::severity)
    ).apply(i, Mark::new));
}
