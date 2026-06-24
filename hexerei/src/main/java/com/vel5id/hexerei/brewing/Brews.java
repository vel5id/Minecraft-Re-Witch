package com.vel5id.hexerei.brewing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The Hexerei brew catalog (starter set). */
public final class Brews {
    private Brews() {}

    public static final Brew SLEEPING_DRAUGHT = new Brew(
            "sleeping_draught", "brew.hexerei.sleeping_draught", 0x4B2E83, 50,
            List.of(new BrewEffect("minecraft:slowness", 1, 400),
                    new BrewEffect("minecraft:blindness", 0, 200)));

    public static final Brew FRAILTY = new Brew(
            "frailty", "brew.hexerei.frailty", 0x6B8E23, 30,
            List.of(new BrewEffect("minecraft:weakness", 1, 600),
                    new BrewEffect("minecraft:mining_fatigue", 0, 300)));

    public static final Brew WITCHS_SIGHT = new Brew(
            "witchs_sight", "brew.hexerei.witchs_sight", 0x2E6E6E, 40,
            List.of(new BrewEffect("minecraft:night_vision", 0, 1800),
                    new BrewEffect("minecraft:water_breathing", 0, 1800)));

    public static final Brew BLOODWORT_TONIC = new Brew(
            "bloodwort_tonic", "brew.hexerei.bloodwort_tonic", 0x8B1A1A, 60,
            List.of(new BrewEffect("minecraft:strength", 0, 1200),
                    new BrewEffect("minecraft:resistance", 0, 600)));

    public static final Brew HAGS_SWIFTNESS = new Brew(
            "hags_swiftness", "brew.hexerei.hags_swiftness", 0x4A7A3A, 40,
            List.of(new BrewEffect("minecraft:speed", 1, 1800),
                    new BrewEffect("minecraft:jump_boost", 0, 1800)));

    public static final Brew WITHERING_BILE = new Brew(
            "withering_bile", "brew.hexerei.withering_bile", 0x1A1020, 30,
            List.of(new BrewEffect("minecraft:poison", 1, 200),
                    new BrewEffect("minecraft:wither", 0, 100)));

    public static final Map<String, Brew> BY_ID = build();

    private static Map<String, Brew> build() {
        Map<String, Brew> m = new LinkedHashMap<>();
        m.put(SLEEPING_DRAUGHT.id(), SLEEPING_DRAUGHT);
        m.put(FRAILTY.id(), FRAILTY);
        m.put(WITCHS_SIGHT.id(), WITCHS_SIGHT);
        m.put(BLOODWORT_TONIC.id(), BLOODWORT_TONIC);
        m.put(HAGS_SWIFTNESS.id(), HAGS_SWIFTNESS);
        m.put(WITHERING_BILE.id(), WITHERING_BILE);
        return m;
    }

    public static Brew byId(String id) {
        return BY_ID.get(id);
    }

    /** 1-based position of a brew id in catalog order, or 0 if unknown — drives the item-model override predicate. */
    public static int indexOf(String id) {
        int i = 1;
        for (String key : BY_ID.keySet()) {
            if (key.equals(id)) {
                return i;
            }
            i++;
        }
        return 0;
    }
}
