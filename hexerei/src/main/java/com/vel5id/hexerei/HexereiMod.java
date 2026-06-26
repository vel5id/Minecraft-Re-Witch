package com.vel5id.hexerei;

import com.vel5id.hexerei.network.HexereiNetwork;
import com.vel5id.hexerei.registry.HexereiBlockEntities;
import com.vel5id.hexerei.registry.HexereiBlocks;
import com.vel5id.hexerei.registry.HexereiCreativeTabs;
import com.vel5id.hexerei.registry.HexereiDataComponents;
import com.vel5id.hexerei.registry.HexereiItems;
import com.vel5id.hexerei.registry.HexereiMenus;
import com.vel5id.hexerei.registry.HexereiParticles;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(HexereiMod.MODID)
public final class HexereiMod {
    public static final String MODID = "hexerei";
    public static final Logger LOGGER = LogUtils.getLogger();

    // NeoForge injects the mod event bus as a constructor parameter (FMLJavaModLoadingContext is gone).
    public HexereiMod(IEventBus modBus) {
        com.vel5id.hexerei.registry.HexereiCrops.init(); // create crop block + seed holders before events fire
        HexereiBlocks.BLOCKS.register(modBus);
        HexereiItems.ITEMS.register(modBus);
        HexereiBlockEntities.BLOCK_ENTITIES.register(modBus);
        HexereiCreativeTabs.TABS.register(modBus);
        HexereiParticles.PARTICLES.register(modBus);
        HexereiMenus.MENUS.register(modBus);
        HexereiDataComponents.DATA_COMPONENTS.register(modBus);          // item data components (item NBT successor)
        com.vel5id.hexerei.soul.HexereiAttachments.register(modBus);     // soul-data attachments (vector core)
        modBus.addListener(HexereiNetwork::register);                    // payload registration (mod bus)
        NeoForge.EVENT_BUS.register(HexereiLevelEvents.class);           // game-bus world events
        LOGGER.info("Hexerei loading");
    }
}
