package com.vel5id.hexerei.item;

/**
 * A charm's static definition: its identity, the vanilla effect it grants, and when it grants it.
 * Pure data — the charm <em>item</em> carries one of these; charge lives in the stack's NBT, not here.
 *
 * @param effectId a {@code minecraft:}-namespaced mob-effect id resolved at apply time
 * @param param    mode-dependent: health threshold for {@link ActiveMode#LOW_HEALTH}, aura radius for {@link ActiveMode#AURA_DEBUFF}
 */
public record CharmDef(String id, String nameKey, String effectId, int amplifier, ActiveMode mode, int param) {}
