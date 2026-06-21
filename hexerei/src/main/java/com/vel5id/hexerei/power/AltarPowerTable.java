package com.vel5id.hexerei.power;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * (factor, limit) power values used when an altar tallies the natural blocks around it.
 * power(source) = min(count, limit) * factor.
 */
public final class AltarPowerTable {
    private AltarPowerTable() {}

    public record Entry(int factor, int limit) {}

    // Tag-based sources, matched against block tags.
    public static final Entry TAG_SAPLING = new Entry(4, 20);
    public static final Entry TAG_LOG = new Entry(2, 50);
    public static final Entry TAG_LEAVES = new Entry(3, 100);

    // instanceof FlowerBlock/CropBlock catch-all (other-mod flowers and crops).
    public static final Entry CATCHALL = new Entry(2, 4);

    // Small flowers (yellow and red small flowers): 4/30 each.
    public static final Entry FLOWER = new Entry(4, 30);

    // Hexerei's own ritual crops: 4/20 each. Applied to any of the mod's grown crop blocks.
    public static final Entry CROP = new Entry(4, 20);

    /** Fixed vanilla blocks, keyed by registry id. */
    public static final Map<String, Entry> VANILLA = build();

    private static Map<String, Entry> build() {
        Map<String, Entry> m = new LinkedHashMap<>();
        m.put("minecraft:grass_block",          new Entry(2, 80));
        m.put("minecraft:dirt",                 new Entry(1, 80));
        m.put("minecraft:farmland",             new Entry(1, 100));
        m.put("minecraft:grass",                new Entry(3, 50));   // short grass plant
        m.put("minecraft:fern",                 new Entry(3, 50));   // fern variant of the short grass plant
        m.put("minecraft:wheat",                new Entry(4, 20));
        m.put("minecraft:water",                new Entry(1, 50));
        m.put("minecraft:brown_mushroom",       new Entry(3, 20));
        m.put("minecraft:red_mushroom",         new Entry(3, 20));
        m.put("minecraft:cactus",               new Entry(3, 50));
        m.put("minecraft:sugar_cane",           new Entry(3, 50));
        m.put("minecraft:pumpkin",              new Entry(4, 20));
        m.put("minecraft:pumpkin_stem",         new Entry(3, 20));
        m.put("minecraft:brown_mushroom_block", new Entry(3, 20));
        m.put("minecraft:red_mushroom_block",   new Entry(3, 20));
        m.put("minecraft:melon",                new Entry(4, 20));
        m.put("minecraft:melon_stem",           new Entry(3, 20));
        m.put("minecraft:vine",                 new Entry(2, 50));
        m.put("minecraft:mycelium",             new Entry(1, 80));
        m.put("minecraft:dragon_egg",           new Entry(250, 1));
        m.put("minecraft:cocoa",                new Entry(3, 20));
        m.put("minecraft:carrots",              new Entry(4, 20));
        m.put("minecraft:potatoes",             new Entry(4, 20));
        return m;
    }
}
