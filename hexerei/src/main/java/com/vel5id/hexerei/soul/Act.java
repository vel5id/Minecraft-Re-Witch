package com.vel5id.hexerei.soul;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Vector of action (Грамматика §1): the transient delta computed from the
 * properties of the reagents/source of a single deed. It is NOT stored — it is
 * an operator that {@link Integration} folds into stored {@code State}.
 *
 * <ul>
 *   <li>{@code domain} — correspondence weights ({forest:0.8, death:0.2}); reagents mix by adding.</li>
 *   <li>{@code reciprocity} ∈ [-1,+1] — −1 taking/изъятие … +1 gift/подношение.</li>
 *   <li>{@code binding} ∈ [-1,+1] — −1 release/разрыв … +1 bind/seal/печать.</li>
 *   <li>{@code defilement} ∈ [0,1] — killing kin, profaning the ancient/sacred.</li>
 *   <li>{@code magnitude} ≥ 0 — scale (saedling ≪ ancient grove).</li>
 * </ul>
 *
 * Pure value type — no Minecraft dependency, fully unit-testable.
 */
public record Act(Map<Correspondence, Float> domain,
                  float reciprocity,
                  float binding,
                  float defilement,
                  float magnitude) {

    /** Defensive immutable copy of the domain map; never null. */
    public Act {
        domain = domain == null ? Map.of() : Map.copyOf(domain);
    }

    /** Weight of this act in a single domain (0 if it does not touch it). */
    public float domainWeight(Correspondence d) {
        return domain.getOrDefault(d, 0f);
    }

    /**
     * How strongly this act perturbs the world, in [0,1]. The "|interaction|"
     * term of the disturbance integration (Грамматика §2): any taking, sealing,
     * tearing or defiling stirs the place; a perfectly neutral act does not.
     */
    public float interactionStrength() {
        return SoulMath.clamp01(Math.abs(reciprocity) + Math.abs(binding) + defilement);
    }

    /**
     * Σ-combination of reagent acts (Грамматика §1: "реагенты смешиваются").
     * Domain weights add; magnitude adds; the polarity axes
     * (reciprocity/binding/defilement) are magnitude-weighted means so a large
     * reagent dominates the mix. Empty input → the null act (magnitude 0).
     */
    public static Act sum(List<Act> acts) {
        Map<Correspondence, Float> dom = new EnumMap<>(Correspondence.class);
        float mag = 0f, wr = 0f, wb = 0f, wd = 0f;
        for (Act a : acts) {
            for (Map.Entry<Correspondence, Float> e : a.domain.entrySet()) {
                dom.merge(e.getKey(), e.getValue(), Float::sum);
            }
            mag += a.magnitude;
            wr += a.reciprocity * a.magnitude;
            wb += a.binding * a.magnitude;
            wd += a.defilement * a.magnitude;
        }
        float recip = mag > 0f ? wr / mag : 0f;
        float bind = mag > 0f ? wb / mag : 0f;
        float defile = mag > 0f ? wd / mag : 0f;
        return new Act(dom, recip, bind, defile, mag);
    }
}
