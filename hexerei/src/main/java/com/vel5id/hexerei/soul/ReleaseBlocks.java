package com.vel5id.hexerei.soul;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Which blocks, when broken/harvested, are a TAKE from the world that frees essence (Статья III).
 * Maps a block's registry id → its {@link Correspondence} domain, scale (magnitude) and defilement.
 * Breaking a grown witch-crop, mushroom or blood moss disturbs its domain and feeds the altar's
 * reservoir; ordinary blocks are absent and free nothing.
 *
 * <p>Keyed by {@link ResourceLocation} so it is unit-testable without a Minecraft level. Values are
 * first-pass {@code [UNVERIFIED]} (DESIGN-NOTES).
 */
public final class ReleaseBlocks {
    private ReleaseBlocks() {}

    public record Release(Correspondence domain, float magnitude, float defilement) {}

    private static final Map<ResourceLocation, Release> RELEASES = new HashMap<>();

    private static void put(String path, Correspondence domain, float magnitude, float defilement) {
        RELEASES.put(new ResourceLocation("hexerei", path), new Release(domain, magnitude, defilement));
    }

    static {
        // witch crops (breaking a grown one is a take)
        put("mandrake",   Correspondence.THRESHOLD, 1.0f, 0.10f);
        put("mindrake",   Correspondence.THRESHOLD, 1.0f, 0.15f);
        put("belladonna", Correspondence.DEATH,     0.6f, 0.20f);
        put("wolfsbane",  Correspondence.DEATH,     0.6f, 0.20f);
        put("crowseye",   Correspondence.DEATH,     0.5f, 0.20f);
        put("wormwood",   Correspondence.THRESHOLD, 0.5f, 0.05f);
        put("celandine",  Correspondence.FOREST,    0.5f, 0.00f);
        put("hops",       Correspondence.FOREST,    0.5f, 0.00f);
        put("mistletoe",  Correspondence.FOREST,    0.6f, 0.05f);
        put("sandwort",   Correspondence.STONE,     0.5f, 0.00f);
        put("garlic",     Correspondence.THRESHOLD, 0.5f, 0.00f);
        put("artichoke",  Correspondence.WATER,     0.5f, 0.00f);
        put("hellebore",  Correspondence.WATER,     0.5f, 0.05f);
        // mushrooms & moss
        put("puffball",   Correspondence.THRESHOLD, 0.4f, 0.00f);
        put("webcap",     Correspondence.THRESHOLD, 0.4f, 0.05f);
        put("zevanty",    Correspondence.THRESHOLD, 0.4f, 0.00f);
        put("blood_moss", Correspondence.DEATH,     0.6f, 0.10f);
    }

    /** The release for a block id, or {@code null} if breaking it frees nothing. */
    public static Release get(ResourceLocation id) {
        return RELEASES.get(id);
    }
}
