package com.vel5id.hexerei.soul;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The bond "spirit ↔ witch" — the single source of truth (Модель §2). A mob
 * ({@code PresenceEntity}) is a disposable projection of this record; the record
 * outlives chunk unload, death and restart.
 *
 * <p>Ritual and amulet are the SAME type in two states, told apart only by
 * {@code seal}: {@code seal == null} → a free/active bond (ritual result, living
 * in a chunk or on the player); {@code seal != null} → sealed into an amulet
 * (permanent effect at the price of growing debt).
 *
 * Pure record + {@link Codec}; uses Mojang/MC value types only (no registry
 * bootstrap), so it round-trips in a plain unit test.
 */
public record Bond(UUID bondId,
                   ResourceLocation spiritType,
                   Correspondence domain,
                   Disposition disposition,
                   SealRef seal,                 // nullable: null = free, non-null = sealed
                   List<Mark> marks,
                   List<LedgerEntry> ledger,
                   long bornTick,
                   long lastResolvedTick) {

    public Bond {
        marks = marks == null ? List.of() : List.copyOf(marks);
        ledger = ledger == null ? List.of() : List.copyOf(ledger);
    }

    public static final Codec<Bond> CODEC = RecordCodecBuilder.create(in -> in.group(
            UUIDUtil.CODEC.fieldOf("bondId").forGetter(Bond::bondId),
            ResourceLocation.CODEC.fieldOf("spiritType").forGetter(Bond::spiritType),
            Correspondence.CODEC.fieldOf("domain").forGetter(Bond::domain),
            Disposition.CODEC.fieldOf("disposition").forGetter(Bond::disposition),
            SealRef.CODEC.optionalFieldOf("seal").forGetter(b -> Optional.ofNullable(b.seal())),
            Mark.CODEC.listOf().optionalFieldOf("marks", List.of()).forGetter(Bond::marks),
            LedgerEntry.CODEC.listOf().optionalFieldOf("ledger", List.of()).forGetter(Bond::ledger),
            Codec.LONG.fieldOf("bornTick").forGetter(Bond::bornTick),
            Codec.LONG.fieldOf("lastResolvedTick").forGetter(Bond::lastResolvedTick)
    ).apply(in, (id, st, dom, disp, sealOpt, marks, ledger, born, last) ->
            new Bond(id, st, dom, disp, sealOpt.orElse(null), marks, ledger, born, last)));

    /** True once this bond has been sealed into an amulet (Модель §2). */
    public boolean isSealed() {
        return seal != null;
    }

    /** Resentment can heal no lower than this (Грамматика decay): debt + Σ mark severity. */
    public float resentmentFloor() {
        float marksSeverity = 0f;
        for (Mark m : marks) marksSeverity += m.severity();
        return disposition.debt() + marksSeverity;
    }

    public Bond withDisposition(Disposition d) {
        return new Bond(bondId, spiritType, domain, d, seal, marks, ledger, bornTick, lastResolvedTick);
    }

    public Bond withSeal(SealRef s) {
        return new Bond(bondId, spiritType, domain, disposition, s, marks, ledger, bornTick, lastResolvedTick);
    }
}
