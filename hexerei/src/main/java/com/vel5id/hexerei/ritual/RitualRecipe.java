package com.vel5id.hexerei.ritual;

/** A ritual: a required circle + a sacrifice item + an altar-power cost -> a rite. */
public record RitualRecipe(boolean requiresSmallCircle, String sacrificeId, int powerCost, Rite rite, String nameKey) {}
