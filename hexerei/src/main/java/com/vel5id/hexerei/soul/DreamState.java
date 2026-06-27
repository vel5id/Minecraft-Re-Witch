package com.vel5id.hexerei.soul;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.util.INBTSerializable;

/** Per-player dream session state: where to return, when to wake, and whether currently dreaming. */
public class DreamState implements INBTSerializable<CompoundTag> {
    private ResourceKey<Level> returnDim;   // null until a crossing
    private BlockPos returnPos;             // null until a crossing
    private long wakeTick;
    private boolean dreaming;
    private boolean sealed;                  // true while the waking inventory is held in the dream
    private ListTag invSnapshot = new ListTag();   // the held inventory; empty unless sealed

    public ResourceKey<Level> returnDim() { return returnDim; }
    public BlockPos returnPos() { return returnPos; }
    public long wakeTick() { return wakeTick; }
    public boolean dreaming() { return dreaming; }
    public boolean sealed() { return sealed; }
    public ListTag invSnapshot() { return invSnapshot; }

    public void begin(ResourceKey<Level> dim, BlockPos pos, long wakeAt) {
        this.returnDim = dim; this.returnPos = pos; this.wakeTick = wakeAt; this.dreaming = true;
    }
    public void clear() { this.dreaming = false; }

    /** Hold the waking inventory snapshot for the duration of the dream. */
    public void seal(ListTag snapshot) { this.invSnapshot = snapshot; this.sealed = true; }

    /** Drop the held snapshot and unseal (called once it has been restored). */
    public void unseal() { this.sealed = false; this.invSnapshot = new ListTag(); }

    @Override public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag t = new CompoundTag();
        t.putBoolean("dreaming", dreaming);
        t.putLong("wakeTick", wakeTick);
        if (returnDim != null) t.putString("returnDim", returnDim.location().toString());
        if (returnPos != null) { t.putInt("rx", returnPos.getX()); t.putInt("ry", returnPos.getY()); t.putInt("rz", returnPos.getZ()); }
        t.putBoolean("sealed", sealed);
        if (sealed && !invSnapshot.isEmpty()) t.put("invSnapshot", invSnapshot);
        return t;
    }
    @Override public void deserializeNBT(HolderLookup.Provider provider, CompoundTag t) {
        dreaming = t.getBoolean("dreaming");
        wakeTick = t.getLong("wakeTick");
        returnDim = t.contains("returnDim")
                ? ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(t.getString("returnDim"))) : null;
        returnPos = t.contains("rx") ? new BlockPos(t.getInt("rx"), t.getInt("ry"), t.getInt("rz")) : null;
        sealed = t.getBoolean("sealed");
        invSnapshot = t.contains("invSnapshot") ? t.getList("invSnapshot", Tag.TAG_COMPOUND) : new ListTag();
    }
}
