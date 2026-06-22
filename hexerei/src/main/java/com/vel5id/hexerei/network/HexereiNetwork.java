package com.vel5id.hexerei.network;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.power.ChunkTaintData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class HexereiNetwork {
    private HexereiNetwork() {}

    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(HexereiMod.MODID, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private static int nextId = 0;

    public static void register() {
        CHANNEL.registerMessage(nextId++, CycleRiteC2SPacket.class,
                CycleRiteC2SPacket::encode, CycleRiteC2SPacket::decode, CycleRiteC2SPacket::handle);
        CHANNEL.registerMessage(nextId++, TaintSyncS2CPacket.class,
                TaintSyncS2CPacket::encode, TaintSyncS2CPacket::decode, TaintSyncS2CPacket::handle);
    }

    /** Send current taint for a chunk to all players watching it. */
    public static void sendTaintSync(ServerLevel level, ChunkPos pos) {
        float value = ChunkTaintData.get(level).getTaint(pos);
        CHANNEL.send(
                PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunk(pos.x, pos.z)),
                new TaintSyncS2CPacket(pos.toLong(), value));
    }
}
