package com.vel5id.hexerei.item;

/**
 * An altar artefact's static definition. Pure data — the artefact <em>item</em> carries one of these
 * (identity-by-item-type, like {@link CharmDef}); nothing about it lives in the stack's NBT.
 *
 * <p>An artefact placed in the altar's single slot scales the rituals that altar funds:
 * <ul>
 *   <li>{@code taintMul} multiplies the taint a rite leaves (a purifier &lt;1, an amplifier &gt;1),</li>
 *   <li>{@code effectMul} scales a rite's magnitude (advisory — only opt-in rites read it this slice),</li>
 *   <li>{@code enhancement} raises the core's dormant {@code enhancementLevel} (gating seam for heavier rites).</li>
 * </ul>
 *
 * @param id          the registry path of the artefact item (e.g. {@code "bone_charm"})
 * @param taintMul    factor applied to a funded rite's base taint write
 * @param effectMul   factor applied to a funded rite's effect magnitude (advisory)
 * @param enhancement the {@code enhancementLevel} this artefact confers on its core
 */
public record ArtefactDef(String id, float taintMul, float effectMul, int enhancement) {}
