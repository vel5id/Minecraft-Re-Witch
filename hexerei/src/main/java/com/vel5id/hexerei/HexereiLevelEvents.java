package com.vel5id.hexerei;

import com.vel5id.hexerei.item.AmuletTickHandler;
import com.vel5id.hexerei.network.TaintSyncS2CPacket;
import com.vel5id.hexerei.ritual.WorldTaintAura;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;


public final class HexereiLevelEvents {
    private HexereiLevelEvents() {}

    // NeoForge split the Forge LevelTickEvent into Pre/Post; Post == the old phase==END.
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel sl)) return;
        long gt = sl.getGameTime();
        if (gt % 200 == 0) {
            WorldTaintAura.pulse(sl);
            WorldTaintAura.punishPlayers(sl);
            com.vel5id.hexerei.ritual.BloodMoonPulse.tick(sl);
        }
        if (gt % 20 == 0) {             // every 1s: accrue debt/grind seals/apply effects of worn amulets
            AmuletTickHandler.tick(sl);
            com.vel5id.hexerei.item.CurseTickHandler.tick(sl); // + tick curse bonds on afflicted players
        }
    }

    @SubscribeEvent
    public static void onChunkWatch(ChunkWatchEvent.Watch event) {
        ServerLevel sl = event.getLevel();
        ChunkPos pos = event.getPos();
        float taint = com.vel5id.hexerei.soul.Disturbance.total(sl, pos);
        if (taint > 0f) {
            // Send current taint to the player who just loaded this chunk.
            PacketDistributor.sendToPlayer(event.getPlayer(), new TaintSyncS2CPacket(pos.toLong(), taint));
        }
    }
}
