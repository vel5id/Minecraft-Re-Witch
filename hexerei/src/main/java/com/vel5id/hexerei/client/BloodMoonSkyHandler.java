package com.vel5id.hexerei.client;

import com.vel5id.hexerei.HexereiMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Tints the sky/fog deep red while a blood moon is active (the visible "blood moon" cue). Client-only. */
@Mod.EventBusSubscriber(modid = HexereiMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
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
