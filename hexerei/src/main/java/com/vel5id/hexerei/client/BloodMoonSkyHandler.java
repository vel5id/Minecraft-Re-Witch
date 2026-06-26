package com.vel5id.hexerei.client;

import com.vel5id.hexerei.HexereiMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Tints the sky/fog deep red while a blood moon is active (the visible "blood moon" cue). Client-only. */
@EventBusSubscriber(modid = HexereiMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class BloodMoonSkyHandler {
    private BloodMoonSkyHandler() {}

    private static final float R = 0.45f, G = 0.05f, B = 0.05f;
    private static final float STRENGTH = 0.6f; // lerp the fog this far toward blood-red

    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor e) {
        if (!ClientBloodMoonCache.isActive()) {
            return;
        }
        e.setRed(e.getRed() + (R - e.getRed()) * STRENGTH);
        e.setGreen(e.getGreen() + (G - e.getGreen()) * STRENGTH);
        e.setBlue(e.getBlue() + (B - e.getBlue()) * STRENGTH);
    }
}
