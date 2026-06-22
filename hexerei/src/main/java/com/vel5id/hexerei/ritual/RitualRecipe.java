package com.vel5id.hexerei.ritual;

/** A ritual: an id + required circle size + sacrifice item + altar-power cost + a rite. */
public record RitualRecipe(String id, CircleSize circleSize, String sacrificeId, int powerCost, Rite rite, String nameKey) {}
