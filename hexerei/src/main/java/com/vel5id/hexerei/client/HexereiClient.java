package com.vel5id.hexerei.client;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.client.particle.AshParticle;
import com.vel5id.hexerei.client.particle.WispParticle;
import com.vel5id.hexerei.item.BrewItem;
import com.vel5id.hexerei.registry.HexereiItems;
import com.vel5id.hexerei.registry.HexereiParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only setup (item color tints, particle providers). */
@Mod.EventBusSubscriber(modid = HexereiMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HexereiClient {
    private HexereiClient() {}

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        // Tint the brew's liquid overlay (layer0 / tintindex 0) by its brew color.
        event.register((stack, tintIndex) -> tintIndex == 0 ? 0xFF000000 | BrewItem.color(stack) : 0xFFFFFFFF,
                HexereiItems.BREW.get());
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(HexereiParticles.WISP_LOW.get(),    WispParticle.LowProvider::new);
        event.registerSpriteSet(HexereiParticles.WISP_MEDIUM.get(), WispParticle.MediumProvider::new);
        event.registerSpriteSet(HexereiParticles.WISP_HIGH.get(),   WispParticle.HighProvider::new);
        event.registerSpriteSet(HexereiParticles.ASH.get(),         AshParticle.Provider::new);
    }
}
