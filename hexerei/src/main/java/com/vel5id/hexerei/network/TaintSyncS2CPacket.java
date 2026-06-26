package com.vel5id.hexerei.network;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.client.ClientTaintCache;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** S2C: the disturbance ("taint") level of a chunk, pushed to players tracking it. */
public record TaintSyncS2CPacket(long chunkPos, float taint) implements CustomPacketPayload {

    public static final Type<TaintSyncS2CPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(HexereiMod.MODID, "taint_sync"));

    public static final StreamCodec<ByteBuf, TaintSyncS2CPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, TaintSyncS2CPacket::chunkPos,
                    ByteBufCodecs.FLOAT, TaintSyncS2CPacket::taint,
                    TaintSyncS2CPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs only on the physical client (S2C handlers are never invoked server-side). */
    public static void handle(TaintSyncS2CPacket pkt, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientTaintCache.set(pkt.chunkPos(), pkt.taint()));
    }
}
