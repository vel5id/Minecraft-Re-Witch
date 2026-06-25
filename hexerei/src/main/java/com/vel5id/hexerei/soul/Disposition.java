package com.vel5id.hexerei.soul;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Stored numeric motivators of a {@link Bond} (Статья V / Грамматика §1):
 * the State that {@link Act}s integrate into and that behaviour, dialogue and
 * ritual success read out of. Pure value type.
 *
 * <ul>
 *   <li>{@code debt} — how much the witch owes this spirit; grows while a seal draws, never decays on its own.</li>
 *   <li>{@code resentment} — grievance; grows from defilement and unpaid debt, floors at {@code debt + marks}.</li>
 *   <li>{@code fear} — fright; decays toward 0.</li>
 *   <li>{@code loyalty} — won by appeasement/feeding; decays toward base if unfed.</li>
 * </ul>
 */
public record Disposition(float debt, float resentment, float fear, float loyalty) {

    public static final Disposition EMPTY = new Disposition(0f, 0f, 0f, 0f);

    public static final Codec<Disposition> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("debt").forGetter(Disposition::debt),
            Codec.FLOAT.fieldOf("resentment").forGetter(Disposition::resentment),
            Codec.FLOAT.fieldOf("fear").forGetter(Disposition::fear),
            Codec.FLOAT.fieldOf("loyalty").forGetter(Disposition::loyalty)
    ).apply(i, Disposition::new));

    /** Component-wise addition — the only way an {@link Act} delta lands (via {@link Integration}). */
    public Disposition plus(Disposition d) {
        return new Disposition(debt + d.debt, resentment + d.resentment, fear + d.fear, loyalty + d.loyalty);
    }
}
