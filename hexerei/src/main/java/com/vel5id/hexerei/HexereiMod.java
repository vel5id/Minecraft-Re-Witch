package com.vel5id.hexerei;

import com.vel5id.hexerei.registry.HexereiBlockEntities;
import com.vel5id.hexerei.registry.HexereiBlocks;
import com.vel5id.hexerei.registry.HexereiCreativeTabs;
import com.vel5id.hexerei.registry.HexereiItems;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(HexereiMod.MODID)
public final class HexereiMod {
    public static final String MODID = "hexerei";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HexereiMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        com.vel5id.hexerei.registry.HexereiCrops.init(); // create crop block + seed RegistryObjects before events fire
        HexereiBlocks.BLOCKS.register(modBus);
        HexereiItems.ITEMS.register(modBus);
        HexereiBlockEntities.BLOCK_ENTITIES.register(modBus);
        HexereiCreativeTabs.TABS.register(modBus);
        LOGGER.info("Hexerei loading");
    }
}
