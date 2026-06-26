package com.vel5id.hexerei.network;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.client.ClientBloodMoonCache;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** S2C: the level's blood-moon state changed (a whole-level event). Mirrors {@link TaintSyncS2CPacket}. */
public record BloodMoonSyncS2CPacket(boolean active) implements CustomPacketPayload {

    public static final Type<BloodMoonSyncS2CPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HexereiMod.MODID, "blood_moon_sync"));

    public static final StreamCodec<ByteBuf, BloodMoonSyncS2CPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, BloodMoonSyncS2CPacket::active,
                    BloodMoonSyncS2CPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs only on the physical client (S2C handlers are never invoked server-side). */
    public static void handle(BloodMoonSyncS2CPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientBloodMoonCache.set(pkt.active()));
    }
}
