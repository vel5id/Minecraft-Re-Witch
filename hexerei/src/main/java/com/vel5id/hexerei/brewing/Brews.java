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

    public static final Map<String, Brew> BY_ID = build();

    private static Map<String, Brew> build() {
        Map<String, Brew> m = new LinkedHashMap<>();
        m.put(SLEEPING_DRAUGHT.id(), SLEEPING_DRAUGHT);
        m.put(FRAILTY.id(), FRAILTY);
        return m;
    }

    public static Brew byId(String id) {
        return BY_ID.get(id);
    }
}
