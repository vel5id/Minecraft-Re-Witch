package com.vel5id.hexerei;

import com.vel5id.hexerei.soul.DreamState;
import com.vel5id.hexerei.soul.DreamWorld;
import com.vel5id.hexerei.soul.HexereiAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** The dream wake state machine: timer expiry, death-cancel, and login recovery. */
public final class HexereiDreamEvents {
    private HexereiDreamEvents() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        DreamState st = sp.getData(HexereiAttachments.DREAM_STATE);
        if (!st.dreaming()) return;
        if (sp.level().dimension().equals(DreamWorld.DREAM) && sp.level().getGameTime() >= st.wakeTick()) {
            DreamWorld.wake(sp);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        DreamState st = sp.getData(HexereiAttachments.DREAM_STATE);
        if (st.dreaming() && sp.level().dimension().equals(DreamWorld.DREAM)) {
            event.setCanceled(true);                 // a dream death wakes, it does not kill
            sp.setHealth(DreamWorld.DEATH_WAKE_HEALTH);
            DreamWorld.wake(sp);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        DreamState st = sp.getData(HexereiAttachments.DREAM_STATE);
        if (!st.dreaming()) return;
        if (sp.level().dimension().equals(DreamWorld.DREAM)) {
            DreamWorld.wake(sp);                     // logged out in the dream → wake on return
        } else {
            st.clear();                              // a real death slipped past / stale flag → just clear
        }
    }
}
