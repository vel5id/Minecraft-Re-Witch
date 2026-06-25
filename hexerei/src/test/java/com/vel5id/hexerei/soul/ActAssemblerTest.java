package com.vel5id.hexerei.soul;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ActAssemblerTest {

    @Test void singleReagent_mapsToItsAct() {
        ReagentDescriptor r = new ReagentDescriptor(Correspondence.FOREST, -0.5f, 0f, 0.2f, 0.6f);
        Act a = r.toAct();
        assertEquals(1.0f, a.domainWeight(Correspondence.FOREST), 1e-6);
        assertEquals(-0.5f, a.reciprocity(), 1e-6);
        assertEquals(0.2f, a.defilement(), 1e-6);
        assertEquals(0.6f, a.magnitude(), 1e-6);
    }

    @Test void assemble_mixesDomains_and_magnitudeWeightsPolarity() {
        ReagentDescriptor take = new ReagentDescriptor(Correspondence.FOREST, -1f, 0f, 0f, 2f);
        ReagentDescriptor gift = new ReagentDescriptor(Correspondence.DEATH, +1f, 0f, 0f, 1f);
        Act a = ActAssembler.assemble(List.of(take, gift));
        assertEquals(1f, a.domainWeight(Correspondence.FOREST), 1e-5);
        assertEquals(1f, a.domainWeight(Correspondence.DEATH), 1e-5);
        assertEquals(3f, a.magnitude(), 1e-5);
        assertEquals(-1f / 3f, a.reciprocity(), 1e-5);   // (-1*2 + 1*1)/3
    }

    @Test void assemble_sameDomainReagentsAddWeight() {
        ReagentDescriptor a1 = new ReagentDescriptor(Correspondence.WATER, 0f, 0f, 0f, 1f);
        ReagentDescriptor a2 = new ReagentDescriptor(Correspondence.WATER, 0f, 0f, 0f, 1f);
        Act a = ActAssembler.assemble(List.of(a1, a2));
        assertEquals(2f, a.domainWeight(Correspondence.WATER), 1e-5);
        assertEquals(2f, a.magnitude(), 1e-5);
    }

    @Test void assemble_empty_isNullAct() {
        Act a = ActAssembler.assemble(List.of());
        assertEquals(0f, a.magnitude(), 1e-6);
        assertTrue(a.domain().isEmpty());
    }
}
