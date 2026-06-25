package com.vel5id.hexerei;

import com.vel5id.hexerei.item.CharmTickHandler;
import com.vel5id.hexerei.network.HexereiNetwork;
import com.vel5id.hexerei.network.TaintSyncS2CPacket;
import com.vel5id.hexerei.ritual.WorldTaintAura;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;


public final class HexereiLevelEvents {
    private HexereiLevelEvents() {}

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.level instanceof ServerLevel sl)) return;
        long gt = sl.getGameTime();
        if (gt % 200 == 0) {
            WorldTaintAura.pulse(sl);
            WorldTaintAura.punishPlayers(sl);
            com.vel5id.hexerei.ritual.BloodMoonPulse.tick(sl);
        }
        if (gt % 20 == 0) {             // every 1s: drain/recharge/apply carried charms
            CharmTickHandler.tick(sl);
        }
    }

    @SubscribeEvent
    public static void onChunkWatch(ChunkWatchEvent.Watch event) {
        ServerLevel sl = event.getLevel();
        ChunkPos pos = event.getPos();
        float taint = com.vel5id.hexerei.soul.Disturbance.total(sl, pos);
        if (taint > 0f) {
            // Send current taint to the player who just loaded this chunk
            HexereiNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(event::getPlayer),
                    new TaintSyncS2CPacket(pos.toLong(), taint));
        }
    }
}
