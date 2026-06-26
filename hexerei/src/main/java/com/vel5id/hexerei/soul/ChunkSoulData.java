package com.vel5id.hexerei.soul;

import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.EnumMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Per-chunk soul state (Модель §3): how disturbed the place is, keyed BY DOMAIN
 * (Грамматика §1 — the key correction over scalar taint, so the world can answer
 * <i>which</i> domain is angry), plus the bonds rooted here.
 *
 * <p>Decay is asymmetric: disturbance settles toward 0, but a permanent per-domain
 * floor (10% of every addition) holds forever — the ancient scar. This is the
 * per-domain successor to the scalar {@code ChunkTaintData} the loop used before.
 *
 * <p>On NeoForge this rides the {@code CHUNK_SOUL} data attachment; mutators must call
 * {@code chunk.setUnsaved(true)} so the change persists to the region file.
 */
public class ChunkSoulData implements INBTSerializable<CompoundTag> {

    /** Per-addition fraction that scars permanently (mirrors the old scalar taint floor). */
    static final float SCAR_FRACTION = 0.1f;
    static final float MAX_DISTURBANCE = 100f;
    static final float DECAY_PER_TICK = 0.5f;
    static final long DECAY_INTERVAL = 1200L;   // a decay step every 60s, matching the old taint cadence

    private static final Codec<Map<Correspondence, Float>> DOMAIN_MAP =
            Codec.unboundedMap(Correspondence.CODEC, Codec.FLOAT);

    private final Map<Correspondence, Float> disturbance = new EnumMap<>(Correspondence.class);
    private final Map<Correspondence, Float> floor = new EnumMap<>(Correspondence.class);
    private final List<Bond> rootedBonds = new ArrayList<>();
    private long lastDecayTick = 0L;            // for lazy decay on access (no all-chunk iteration)

    public float getDisturbance(Correspondence d) {
        return disturbance.getOrDefault(d, 0f);
    }

    /** Sum of disturbance across all domains — the place's overall unrest. */
    public float totalDisturbance() {
        float sum = 0f;
        for (float v : disturbance.values()) sum += v;
        return sum;
    }

    public Map<Correspondence, Float> disturbanceView() {
        return new EnumMap<>(disturbance);
    }

    public List<Bond> rootedBonds() {
        return rootedBonds;
    }

    public void addRootedBond(Bond bond) {
        rootedBonds.add(bond);
    }

    /** Stir disturbance into one domain; 10% of the applied delta scars permanently. */
    public void addDisturbance(Correspondence d, float amount) {
        if (amount <= 0f) return;
        float cur = disturbance.getOrDefault(d, 0f);
        float next = Math.min(MAX_DISTURBANCE, cur + amount);
        float applied = next - cur;
        disturbance.put(d, next);
        floor.merge(d, applied * SCAR_FRACTION, Float::sum);
    }

    /** Apply an {@link Act}'s full per-domain disturbance delta (Грамматика §2). */
    public void apply(Act act) {
        for (Map.Entry<Correspondence, Float> e : Integration.disturbanceDelta(act).entrySet()) {
            addDisturbance(e.getKey(), e.getValue());
        }
    }

    /** One decay step: every domain settles toward its permanent floor, never below. */
    public void decayTick() {
        for (Correspondence d : new ArrayList<>(disturbance.keySet())) {
            float cur = disturbance.get(d);
            float flr = floor.getOrDefault(d, 0f);
            float next = Math.max(flr, cur - DECAY_PER_TICK);
            disturbance.put(d, next);
        }
    }

    /**
     * Catch up decay for the time since this chunk was last touched (lazy — applied on access, so no
     * all-loaded-chunks sweep is needed). {@code now} is the level game-time. Attention heals, but the
     * per-domain scar floor holds (Грамматика).
     */
    public void lazyDecay(long now) {
        if (lastDecayTick <= 0L) {
            lastDecayTick = now;
            return;
        }
        long steps = (now - lastDecayTick) / DECAY_INTERVAL;
        if (steps <= 0L) return;
        float amount = DECAY_PER_TICK * steps;
        for (Correspondence d : new ArrayList<>(disturbance.keySet())) {
            float flr = floor.getOrDefault(d, 0f);
            disturbance.put(d, Math.max(flr, disturbance.get(d) - amount));
        }
        lastDecayTick += steps * DECAY_INTERVAL;
    }

    // Registry-free codecs (NbtOps) — the attachment's HolderLookup.Provider is unused; the no-arg
    // overloads remain the source of truth and keep the unit tests provider-agnostic.
    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        return serializeNBT();
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        deserializeNBT(tag);
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put("disturbance", DOMAIN_MAP.encodeStart(NbtOps.INSTANCE, disturbance).result().orElseGet(CompoundTag::new));
        tag.put("floor", DOMAIN_MAP.encodeStart(NbtOps.INSTANCE, floor).result().orElseGet(CompoundTag::new));
        tag.put("rootedBonds", Bond.CODEC.listOf().encodeStart(NbtOps.INSTANCE, rootedBonds).result().orElseGet(ListTag::new));
        tag.putLong("lastDecay", lastDecayTick);
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        disturbance.clear();
        DOMAIN_MAP.parse(NbtOps.INSTANCE, tag.get("disturbance")).result().ifPresent(disturbance::putAll);
        floor.clear();
        DOMAIN_MAP.parse(NbtOps.INSTANCE, tag.get("floor")).result().ifPresent(floor::putAll);
        rootedBonds.clear();
        Bond.CODEC.listOf().parse(NbtOps.INSTANCE, tag.get("rootedBonds")).result().ifPresent(rootedBonds::addAll);
        lastDecayTick = tag.getLong("lastDecay");
    }
}
