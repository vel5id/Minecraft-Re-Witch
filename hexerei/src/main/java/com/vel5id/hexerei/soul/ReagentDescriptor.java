package com.vel5id.hexerei.soul;

import java.util.Map;

/**
 * The static, per-reagent vector data (Грамматика §1: "свойства реагентов") from which a
 * ritual/brew {@link Act} is summed. One reagent carries a single dominant {@link Correspondence}
 * plus the polarity axes; mixing reagents is {@link Act#sum}.
 *
 * <p>Pure value type — no Minecraft dependency. The item→descriptor mapping lives in
 * {@link ReagentRegistry}; combining them is {@link ActAssembler}.
 */
public record ReagentDescriptor(Correspondence domain,
                                float reciprocity,
                                float binding,
                                float defilement,
                                float magnitude) {

    /** This reagent as a single-domain {@link Act} (domain weight 1.0). */
    public Act toAct() {
        return new Act(Map.of(domain, 1.0f), reciprocity, binding, defilement, magnitude);
    }
}
