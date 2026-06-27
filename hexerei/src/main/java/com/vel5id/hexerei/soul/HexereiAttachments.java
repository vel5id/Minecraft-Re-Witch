package com.vel5id.hexerei.soul;

import com.vel5id.hexerei.HexereiMod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * The two soul-data carriers (Модель §3), as NeoForge data attachments — the 1.21 successor to the
 * Forge capabilities this mod used on 1.20.1. Both are server-authoritative.
 *
 * <ul>
 *   <li>{@code PLAYER_SOUL} is attached to the {@link net.minecraft.world.entity.player.Player} and copied
 *       on death ({@code copyOnDeath()}), so debt survives — death does not pay debt (Модель §3).</li>
 *   <li>{@code CHUNK_SOUL} is attached to the chunk and persists with it; mutators must mark the chunk
 *       unsaved ({@code chunk.setUnsaved(true)}) for the change to reach the region file.</li>
 * </ul>
 *
 * <p>Read with {@code holder.getData(ATTACHMENT)} (creates the default), peek with
 * {@code getExistingData(...)}, write with {@code holder.setData(...)} (auto-marks dirty).
 */
public final class HexereiAttachments {
    private HexereiAttachments() {}

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, HexereiMod.MODID);

    public static final Supplier<AttachmentType<PlayerSoulData>> PLAYER_SOUL =
            ATTACHMENT_TYPES.register("player_soul", () ->
                    AttachmentType.serializable(PlayerSoulData::new).copyOnDeath().build());

    public static final Supplier<AttachmentType<ChunkSoulData>> CHUNK_SOUL =
            ATTACHMENT_TYPES.register("chunk_soul", () ->
                    AttachmentType.serializable(ChunkSoulData::new).build());

    public static final Supplier<AttachmentType<DreamState>> DREAM_STATE =
            ATTACHMENT_TYPES.register("dream_state", () ->
                    AttachmentType.serializable(DreamState::new).copyOnDeath().build());

    /** Wire attachment registration onto the mod event bus; call from the mod constructor. */
    public static void register(IEventBus modBus) {
        ATTACHMENT_TYPES.register(modBus);
    }
}
