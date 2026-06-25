package com.vel5id.hexerei.soul;

import com.vel5id.hexerei.HexereiMod;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Registers and attaches the two soul-data capabilities (Модель §3, §10 legacy-Forge
 * note: {@code AttachmentType} → Capability on 1.20.1). Server-authoritative; the
 * chunk attachment persists with the chunk, the player attachment is copied on death
 * so debt survives.
 */
@Mod.EventBusSubscriber(modid = HexereiMod.MODID)
public final class HexereiCapabilities {
    private HexereiCapabilities() {}

    public static final Capability<PlayerSoulData> PLAYER_SOUL =
            CapabilityManager.get(new CapabilityToken<>() {});
    public static final Capability<ChunkSoulData> CHUNK_SOUL =
            CapabilityManager.get(new CapabilityToken<>() {});

    private static final ResourceLocation PLAYER_ID = new ResourceLocation(HexereiMod.MODID, "player_soul");
    private static final ResourceLocation CHUNK_ID = new ResourceLocation(HexereiMod.MODID, "chunk_soul");

    /** Wire the mod-bus capability registration; call from the mod constructor. */
    public static void register(IEventBus modBus) {
        modBus.addListener(HexereiCapabilities::onRegister);
    }

    private static void onRegister(RegisterCapabilitiesEvent event) {
        event.register(PlayerSoulData.class);
        event.register(ChunkSoulData.class);
    }

    @SubscribeEvent
    public static void attachPlayer(AttachCapabilitiesEvent<net.minecraft.world.entity.Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(PLAYER_ID, new Provider<>(new PlayerSoulData(), PLAYER_SOUL));
        }
    }

    @SubscribeEvent
    public static void attachChunk(AttachCapabilitiesEvent<LevelChunk> event) {
        event.addCapability(CHUNK_ID, new Provider<>(new ChunkSoulData(), CHUNK_SOUL));
    }

    /** Death does not pay debt: copy the witch's soul onto her respawned body (Модель §3). */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        event.getOriginal().reviveCaps();
        event.getOriginal().getCapability(PLAYER_SOUL).ifPresent(oldData ->
                event.getEntity().getCapability(PLAYER_SOUL).ifPresent(newData ->
                        newData.copyFrom(oldData)));
        event.getOriginal().invalidateCaps();
    }

    /** Generic serializable provider over an {@link INBTSerializable} soul-data instance. */
    public static final class Provider<T extends INBTSerializable<CompoundTag>>
            implements ICapabilitySerializable<CompoundTag> {

        private final T instance;
        private final Capability<T> capability;
        private final LazyOptional<T> optional;

        public Provider(T instance, Capability<T> capability) {
            this.instance = instance;
            this.capability = capability;
            this.optional = LazyOptional.of(() -> instance);
        }

        @Override
        public <X> @NotNull LazyOptional<X> getCapability(@NotNull Capability<X> cap, @Nullable Direction side) {
            return capability.orEmpty(cap, optional);
        }

        @Override
        public CompoundTag serializeNBT() {
            return instance.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            instance.deserializeNBT(tag);
        }
    }
}
