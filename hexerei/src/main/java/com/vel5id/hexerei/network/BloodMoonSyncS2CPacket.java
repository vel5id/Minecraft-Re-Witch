package com.vel5id.hexerei.network;

import com.vel5id.hexerei.client.ClientBloodMoonCache;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: the level's blood-moon state changed. Mirrors {@link TaintSyncS2CPacket}. */
public record BloodMoonSyncS2CPacket(boolean active) {
    public static void encode(BloodMoonSyncS2CPacket p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.active());
    }

    public static BloodMoonSyncS2CPacket decode(FriendlyByteBuf buf) {
        return new BloodMoonSyncS2CPacket(buf.readBoolean());
    }

    public static void handle(BloodMoonSyncS2CPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> () -> ClientBloodMoonCache.set(pkt.active())));
        ctx.get().setPacketHandled(true);
    }
}
