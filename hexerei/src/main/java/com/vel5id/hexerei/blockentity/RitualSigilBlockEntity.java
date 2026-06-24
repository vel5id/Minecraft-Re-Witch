package com.vel5id.hexerei.blockentity;

import com.vel5id.hexerei.registry.HexereiBlockEntities;
import com.vel5id.hexerei.ritual.CircleSize;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Stores which rite a drawn ritual sigil is bound to. A plain right-click with chalk draws the rune
 * ring and binds the rite here; the bind gates overdraw (you must break the circle before drawing a
 * different rite) and drives the destruction penalty's "was a circle here" check.
 */
public class RitualSigilBlockEntity extends BlockEntity {
    private static final String KEY_RITE = "hexerei:BoundRite";
    private static final String KEY_SIZE = "hexerei:BoundSize";

    private String boundRite = "";
    private CircleSize boundSize = CircleSize.SMALL;

    public RitualSigilBlockEntity(BlockPos pos, BlockState state) {
        super(HexereiBlockEntities.RITUAL_SIGIL.get(), pos, state);
    }

    /** True once a rite has been bound to this sigil (a complete circle was drawn). */
    public boolean isBound() {
        return !boundRite.isEmpty();
    }

    /** The bound rite id (e.g. {@code hexerei:tempest}), or empty when unbound. */
    public String boundRiteId() {
        return boundRite;
    }

    /** The bound circle size; defaults to {@link CircleSize#SMALL} when unbound. */
    public CircleSize boundSize() {
        return boundSize;
    }

    /** Bind this sigil to a rite + size and persist/sync the change. */
    public void bind(String riteId, CircleSize size) {
        this.boundRite = riteId == null ? "" : riteId;
        this.boundSize = size == null ? CircleSize.SMALL : size;
        setChanged();
        sync();
    }

    /** Clear the bind (the circle was broken) and persist/sync the change. */
    public void unbind() {
        this.boundRite = "";
        this.boundSize = CircleSize.SMALL;
        setChanged();
        sync();
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString(KEY_RITE, boundRite);
        tag.putString(KEY_SIZE, boundSize.name());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        boundRite = tag.getString(KEY_RITE);
        boundSize = parseSize(tag.getString(KEY_SIZE));
    }

    private static CircleSize parseSize(String name) {
        if (name == null || name.isEmpty()) {
            return CircleSize.SMALL;
        }
        try {
            return CircleSize.valueOf(name);
        } catch (IllegalArgumentException ex) {
            return CircleSize.SMALL;
        }
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        load(tag);
    }
}
