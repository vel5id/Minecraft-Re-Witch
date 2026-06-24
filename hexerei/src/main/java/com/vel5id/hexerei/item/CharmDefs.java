package com.vel5id.hexerei.item;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** The catalog of combat charms. One {@link CharmDef} per charm item — mirrors {@code RitualRecipes.ALL}/{@code BY_ID}. */
public final class CharmDefs {
    private CharmDefs() {}

    // Always-on Resistance I — a steady defensive ward.
    public static final CharmDef WARD =
            new CharmDef("ward", "item.hexerei.ward_charm", "minecraft:resistance", 0, ActiveMode.ALWAYS, 0);
    // Strength I, but only when the wearer is at <= 3 hearts — high-risk berserker reward.
    public static final CharmDef BLOODLUST =
            new CharmDef("bloodlust", "item.hexerei.bloodlust_charm", "minecraft:strength", 0, ActiveMode.LOW_HEALTH, 6);
    // Weakness I to hostile mobs within 5 blocks — a melee-range curse aura.
    public static final CharmDef HEXBANE =
            new CharmDef("hexbane", "item.hexerei.hexbane_charm", "minecraft:weakness", 0, ActiveMode.AURA_DEBUFF, 5);

    public static final List<CharmDef> ALL = List.of(WARD, BLOODLUST, HEXBANE);

    public static final Map<String, CharmDef> BY_ID = ALL.stream()
            .collect(Collectors.toUnmodifiableMap(CharmDef::id, d -> d));

    /** The charm with this id, or {@code null} if none — callers treat null as "not a charm". */
    @Nullable
    public static CharmDef byId(String id) {
        return BY_ID.get(id);
    }
}
