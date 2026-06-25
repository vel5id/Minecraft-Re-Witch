package com.vel5id.hexerei.item;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** The catalog of altar artefacts. One {@link ArtefactDef} per artefact item — mirrors how rites are listed in {@code RitualRecipes.ALL}. */
public final class ArtefactDefs {
    private ArtefactDefs() {}

    // PURIFIER: halves the taint a rite leaves, no power/effect change.
    public static final ArtefactDef BONE_CHARM =
            new ArtefactDef("bone_charm", 0.5f, 1.0f, 0);
    // PURIFIER (deep): strongest cleanser — quarter taint, but slightly weakens the rite as the cost of mercy.
    public static final ArtefactDef WAX_POPPET =
            new ArtefactDef("wax_poppet", 0.25f, 0.9f, 0);
    // AMPLIFIER: greed — +50 % effect but doubles taint; also raises enhancementLevel to 1.
    public static final ArtefactDef OBSIDIAN_SKULL =
            new ArtefactDef("obsidian_skull", 2.0f, 1.5f, 1);

    public static final List<ArtefactDef> ALL = List.of(BONE_CHARM, WAX_POPPET, OBSIDIAN_SKULL);

    public static final Map<String, ArtefactDef> BY_ID = ALL.stream()
            .collect(Collectors.toUnmodifiableMap(ArtefactDef::id, d -> d));

    /** The artefact with this item-id path, or {@code null} if none — callers treat null as "not an artefact". */
    @Nullable
    public static ArtefactDef byItemId(String id) {
        return BY_ID.get(id);
    }
}
