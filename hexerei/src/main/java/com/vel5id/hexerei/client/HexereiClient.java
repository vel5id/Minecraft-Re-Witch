package com.vel5id.hexerei.client;

import com.vel5id.hexerei.HexereiMod;
import com.vel5id.hexerei.blockentity.CauldronBlockEntity;
import com.vel5id.hexerei.brewing.BrewColor;
import com.vel5id.hexerei.client.particle.AshParticle;
import com.vel5id.hexerei.client.particle.CauldronBubbleParticle;
import com.vel5id.hexerei.client.particle.CauldronSteamParticle;
import com.vel5id.hexerei.client.particle.WispParticle;
import com.vel5id.hexerei.item.BrewItem;
import com.vel5id.hexerei.network.CycleRiteC2SPacket;
import com.vel5id.hexerei.registry.HexereiBlocks;
import com.vel5id.hexerei.registry.HexereiItems;
import com.vel5id.hexerei.registry.HexereiMenus;
import com.vel5id.hexerei.registry.HexereiParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client-only setup (item color tints, particle providers). */
@EventBusSubscriber(modid = HexereiMod.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HexereiClient {
    private HexereiClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // Per-brew model selection: one item, 6 textures, picked by the catalog index in NBT.
            ItemProperties.register(HexereiItems.BREW.get(),
                    ResourceLocation.fromNamespaceAndPath(HexereiMod.MODID, "brew"),
                    (stack, level, entity, seed) -> BrewItem.modelIndex(stack) / 10.0f);
        });
    }

    // NeoForge 1.21: menu screens register through RegisterMenuScreensEvent, not the now-private MenuScreens.register.
    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(HexereiMenus.CHARM_POUCH.get(), CharmPouchScreen::new);
    }

    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        // Tint the cauldron's interior liquid quad (tintindex 0) by the brewing color the BE computes.
        event.register((state, level, pos, tintIndex) -> {
            if (tintIndex != 0 || level == null || pos == null) {
                return -1;
            }
            return level.getBlockEntity(pos) instanceof CauldronBlockEntity be ? be.getColor() : BrewColor.WATER;
        }, HexereiBlocks.CAULDRON.get());
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(HexereiParticles.WISP_LOW.get(),    WispParticle.LowProvider::new);
        event.registerSpriteSet(HexereiParticles.WISP_MEDIUM.get(), WispParticle.MediumProvider::new);
        event.registerSpriteSet(HexereiParticles.WISP_HIGH.get(),   WispParticle.HighProvider::new);
        event.registerSpriteSet(HexereiParticles.ASH.get(),         AshParticle.Provider::new);
        event.registerSpriteSet(HexereiParticles.CAULDRON_BUBBLE.get(), CauldronBubbleParticle.Provider::new);
        event.registerSpriteSet(HexereiParticles.CAULDRON_STEAM.get(),  CauldronSteamParticle.Provider::new);
    }

    /** Game-bus client events (scroll to cycle rite). */
    @EventBusSubscriber(modid = HexereiMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
    public static final class ForgeClientEvents {
        private ForgeClientEvents() {}

        @SubscribeEvent
        public static void onScroll(InputEvent.MouseScrollingEvent event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || !mc.player.isShiftKeyDown()) return;
            ItemStack main = mc.player.getMainHandItem();
            ItemStack off  = mc.player.getOffhandItem();
            boolean holdingChalk = main.is(HexereiItems.RITUAL_CHALK.get())
                                || off.is(HexereiItems.RITUAL_CHALK.get());
            if (!holdingChalk) return;
            event.setCanceled(true);
            int delta = event.getScrollDeltaY() > 0 ? 1 : -1;
            PacketDistributor.sendToServer(new CycleRiteC2SPacket(delta));
        }
    }
}
