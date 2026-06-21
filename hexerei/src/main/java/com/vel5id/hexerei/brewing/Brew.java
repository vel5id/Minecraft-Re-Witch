package com.vel5id.hexerei.brewing;

import java.util.List;

/** A finished brew: identity, display name key, liquid color, altar-power cost, and drink effects. Pure data. */
public record Brew(String id, String nameKey, int color, int power, List<BrewEffect> effects) {}
