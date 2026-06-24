package com.vel5id.hexerei.item;

/** When a charm's buff applies. {@code param} on the {@link CharmDef} is interpreted per mode. */
public enum ActiveMode {
    /** Buff always active while charged. */
    ALWAYS,
    /** Buff active only while the wearer's health is at or below {@code param}. */
    LOW_HEALTH,
    /** Buff (a debuff) applied to hostile mobs within {@code param} blocks, not to the wearer. */
    AURA_DEBUFF
}
