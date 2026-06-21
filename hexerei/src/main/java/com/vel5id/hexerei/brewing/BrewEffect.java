package com.vel5id.hexerei.brewing;

/** A status effect a brew applies on drink, as plain data (resolved to a MobEffect at apply time). */
public record BrewEffect(String effectId, int amplifier, int durationTicks) {}
