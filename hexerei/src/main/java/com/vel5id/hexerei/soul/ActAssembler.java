package com.vel5id.hexerei.soul;

import java.util.List;

/**
 * Sums the reagents of a ritual/brew into a single {@link Act} (Грамматика §1: reagent
 * properties --Σ--> Act). The whole DAG entry point: reagents in, the resolved act out.
 * Pure — no Minecraft dependency.
 */
public final class ActAssembler {
    private ActAssembler() {}

    /** Combine reagent descriptors into one {@link Act}. Empty list → the null act. */
    public static Act assemble(List<ReagentDescriptor> reagents) {
        return Act.sum(reagents.stream().map(ReagentDescriptor::toAct).toList());
    }
}
