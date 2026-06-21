package com.vel5id.hexerei.brewing;

import java.util.List;

/** Maps an ingredient multiset (item ids, e.g. "hexerei:mandrake_root") to a resulting brew. */
public record BrewRecipe(List<String> ingredientIds, Brew result) {}
