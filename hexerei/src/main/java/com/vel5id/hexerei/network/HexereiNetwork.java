package com.vel5id.hexerei.network;

import com.vel5id.hexerei.soul.Disturbance;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * The mod's network channel — NeoForge 1.21 payloads (the successor to the Forge {@code SimpleChannel}).
 * Payloads register on the mod event bus via {@link #register}; the mod constructor wires the listener.
 */
public final class HexereiNetwork {
    private HexereiNetwork() {}

    private static final String VERSION = "1";

    /** Mod-bus listener: register every payload + its codec + handler. */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(CycleRiteC2SPacket.TYPE, CycleRiteC2SPacket.STREAM_CODEC, CycleRiteC2SPacket::handle);
        registrar.playToClient(TaintSyncS2CPacket.TYPE, TaintSyncS2CPacket.STREAM_CODEC, TaintSyncS2CPacket::handle);
        registrar.playToClient(BloodMoonSyncS2CPacket.TYPE, BloodMoonSyncS2CPacket.STREAM_CODEC, BloodMoonSyncS2CPacket::handle);
    }

    /** Tell every client in {@code level} that the blood moon turned on/off (a whole-level event). */
    public static void sendBloodMoonSync(ServerLevel level, boolean active) {
        PacketDistributor.sendToPlayersInDimension(level, new BloodMoonSyncS2CPacket(active));
    }

    /** Send current taint for a chunk to all players watching it. */
    public static void sendTaintSync(ServerLevel level, ChunkPos pos) {
        float value = Disturbance.total(level, pos);
        PacketDistributor.sendToPlayersTrackingChunk(level, pos, new TaintSyncS2CPacket(pos.toLong(), value));
    }
}
