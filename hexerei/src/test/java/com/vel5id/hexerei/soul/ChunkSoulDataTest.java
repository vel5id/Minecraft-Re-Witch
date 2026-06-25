package com.vel5id.hexerei.soul;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChunkSoulDataTest {

    @Test void disturbanceIsKeyedByDomain() {
        ChunkSoulData d = new ChunkSoulData();
        d.addDisturbance(Correspondence.FOREST, 30f);
        d.addDisturbance(Correspondence.DEATH, 10f);
        assertEquals(30f, d.getDisturbance(Correspondence.FOREST), 1e-4);
        assertEquals(10f, d.getDisturbance(Correspondence.DEATH), 1e-4);
        assertEquals(0f, d.getDisturbance(Correspondence.WATER), 1e-4);
    }

    @Test void addCapsAt100() {
        ChunkSoulData d = new ChunkSoulData();
        d.addDisturbance(Correspondence.FOREST, 80f);
        d.addDisturbance(Correspondence.FOREST, 80f);
        assertEquals(100f, d.getDisturbance(Correspondence.FOREST), 1e-4);
    }

    @Test void decaySettlesToPerDomainScarFloor() {
        ChunkSoulData d = new ChunkSoulData();
        d.addDisturbance(Correspondence.FOREST, 10f); // floor += 1.0
        for (int i = 0; i < 60; i++) d.decayTick();
        assertEquals(1.0f, d.getDisturbance(Correspondence.FOREST), 1e-4); // ancient scar persists
    }

    @Test void apply_routesAnActsDisturbanceByDomain() {
        ChunkSoulData d = new ChunkSoulData();
        // taking act: interactionStrength 1, magnitude 2 -> forest 2, death 1
        Act take = new Act(Map.of(Correspondence.FOREST, 1f, Correspondence.DEATH, 0.5f), -1f, 0f, 0f, 2f);
        d.apply(take);
        assertEquals(2f, d.getDisturbance(Correspondence.FOREST), 1e-4);
        assertEquals(1f, d.getDisturbance(Correspondence.DEATH), 1e-4);
    }

    @Test void roundTripsThroughNbt() {
        ChunkSoulData d = new ChunkSoulData();
        d.addDisturbance(Correspondence.FOREST, 40f);
        CompoundTag tag = d.serializeNBT();
        ChunkSoulData loaded = new ChunkSoulData();
        loaded.deserializeNBT(tag);
        assertEquals(40f, loaded.getDisturbance(Correspondence.FOREST), 1e-4);
        for (int i = 0; i < 100; i++) loaded.decayTick();
        assertEquals(4.0f, loaded.getDisturbance(Correspondence.FOREST), 1e-4); // floor 40*0.1 survived round-trip
    }
}
