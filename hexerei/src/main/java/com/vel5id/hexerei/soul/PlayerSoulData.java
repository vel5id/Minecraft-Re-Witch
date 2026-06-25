package com.vel5id.hexerei.soul;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Per-player soul state (Модель §3): the witch's essence, the bonds the world has
 * laid on her, and the amulets she wears. This is what the dream generator reads.
 *
 * <p>It MUST survive death — death does not pay debt (the clone handler copies it
 * on respawn). {@code essence} is the shared resource of the Article III loop;
 * nothing outside this bloodstream may grant power.
 */
public class PlayerSoulData implements INBTSerializable<CompoundTag> {

    private float essence;
    private float totalDebt;
    private final List<Bond> marks = new ArrayList<>();   // free bonds the world laid on the witch
    private final List<UUID> amulets = new ArrayList<>(); // references to sealed bonds in worn items

    public float essence() { return essence; }
    public float totalDebt() { return totalDebt; }
    public List<Bond> marks() { return marks; }
    public List<UUID> amulets() { return amulets; }

    public void addEssence(float amount) { essence = Math.max(0f, essence + amount); }

    /** Spend essence if affordable; returns true and debits, false if short (consume-before-input). */
    public boolean spendEssence(float amount) {
        if (amount <= essence) { essence -= amount; return true; }
        return false;
    }

    public void setTotalDebt(float v) { totalDebt = Math.max(0f, v); }
    public void addMark(Bond bond) { marks.add(bond); }
    public void wearAmulet(UUID bondId) { if (!amulets.contains(bondId)) amulets.add(bondId); }
    public void removeAmulet(UUID bondId) { amulets.remove(bondId); }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("essence", essence);
        tag.putFloat("totalDebt", totalDebt);
        tag.put("marks", Bond.CODEC.listOf().encodeStart(NbtOps.INSTANCE, marks)
                .result().orElseGet(ListTag::new));
        tag.put("amulets", UUIDUtil.CODEC.listOf().encodeStart(NbtOps.INSTANCE, amulets)
                .result().orElseGet(ListTag::new));
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        essence = tag.getFloat("essence");
        totalDebt = tag.getFloat("totalDebt");
        marks.clear();
        Bond.CODEC.listOf().parse(NbtOps.INSTANCE, tag.get("marks"))
                .result().ifPresent(marks::addAll);
        amulets.clear();
        UUIDUtil.CODEC.listOf().parse(NbtOps.INSTANCE, tag.contains("amulets", Tag.TAG_LIST) ? tag.get("amulets") : new ListTag())
                .result().ifPresent(amulets::addAll);
    }

    /** Copy all state from another instance (used on respawn so debt survives death). */
    public void copyFrom(PlayerSoulData other) {
        deserializeNBT(other.serializeNBT());
    }
}
