package com.vel5id.hexerei.soul;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Item-id → {@link ReagentDescriptor} table: the static "what each reagent means" data the
 * {@link ActAssembler} reads. Seeded in code for now (a datapack-driven source is a later slice);
 * values are first-pass and documented {@code [UNVERIFIED]} in DESIGN-NOTES.
 *
 * <p>Domains follow the {@link Correspondence} dictionary. {@code reciprocity<0} = a take (creates
 * debt), {@code >0} = an offering; {@code defilement} = profaning the deadly/poisonous.
 */
public final class ReagentRegistry {
    private ReagentRegistry() {}

    private static final Map<ResourceLocation, ReagentDescriptor> REAGENTS = new HashMap<>();

    private static void put(String path, Correspondence domain,
                            float reciprocity, float binding, float defilement, float magnitude) {
        REAGENTS.put(ResourceLocation.fromNamespaceAndPath("hexerei", path),
                new ReagentDescriptor(domain, reciprocity, binding, defilement, magnitude));
    }

    static {
        // — roots & deadly herbs (death / threshold) —
        put("mandrake_root",     Correspondence.THRESHOLD, -0.30f, 0f, 0.10f, 1.0f); // screaming root, between worlds
        put("mindrake_bulb",     Correspondence.THRESHOLD, -0.30f, 0f, 0.15f, 1.0f);
        put("belladonna_flower", Correspondence.DEATH,      0.00f, 0f, 0.20f, 0.6f); // deadly nightshade
        put("wolfsbane",         Correspondence.DEATH,      0.00f, 0f, 0.20f, 0.6f); // aconite, poison
        put("crowseye_berry",    Correspondence.DEATH,      0.00f, 0f, 0.20f, 0.5f);
        put("wormwood",          Correspondence.THRESHOLD,  0.00f, 0f, 0.05f, 0.5f); // bitter, dream
        // — green & cleansing (forest) —
        put("celandine",         Correspondence.FOREST,     0.20f, 0f, 0.00f, 0.5f); // cleansing flower (offering)
        put("hops",              Correspondence.FOREST,     0.00f, 0f, 0.00f, 0.5f); // sedative
        put("mistletoe_sprig",   Correspondence.FOREST,    -0.20f, 0f, 0.05f, 0.6f); // parasite — a small take
        // — stone & ward —
        put("sandwort",          Correspondence.STONE,      0.00f, 0.10f, 0.00f, 0.5f); // resistance
        put("garlic",            Correspondence.THRESHOLD,  0.00f, 0.20f, 0.00f, 0.5f); // ward (binding)
        // — water & frost (water / sky) —
        put("artichoke",         Correspondence.WATER,      0.00f, 0f, 0.00f, 0.5f);
        put("hellebore",         Correspondence.WATER,      0.00f, 0f, 0.05f, 0.5f);   // frost rose
        put("icy_needle",        Correspondence.WATER,      0.00f, 0f, 0.00f, 0.4f);
        // — fungal & moss (threshold / death) —
        put("glowing_spore",     Correspondence.THRESHOLD,  0.00f, 0f, 0.00f, 0.4f);
        put("blood_moss",        Correspondence.DEATH,      0.00f, 0f, 0.10f, 0.6f);
    }

    /** The descriptor for an item id, or {@code null} if it is not a reagent. */
    public static ReagentDescriptor get(ResourceLocation id) {
        return REAGENTS.get(id);
    }

    public static boolean has(ResourceLocation id) {
        return REAGENTS.containsKey(id);
    }
}
