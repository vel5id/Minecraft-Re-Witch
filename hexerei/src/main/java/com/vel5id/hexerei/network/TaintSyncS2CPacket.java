package com.vel5id.hexerei.network;

import com.vel5id.hexerei.client.ClientTaintCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record TaintSyncS2CPacket(long chunkPos, float taint) {
    public static void encode(TaintSyncS2CPacket p, FriendlyByteBuf buf) {
        buf.writeLong(p.chunkPos()); buf.writeFloat(p.taint());
    }
    public static TaintSyncS2CPacket decode(FriendlyByteBuf buf) {
        return new TaintSyncS2CPacket(buf.readLong(), buf.readFloat());
    }
    public static void handle(TaintSyncS2CPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> ClientTaintCache.set(pkt.chunkPos(), pkt.taint())));
        ctx.get().setPacketHandled(true);
    }
}
