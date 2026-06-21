package com.vel5id.hexerei.registry;

import com.vel5id.hexerei.HexereiMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class HexereiCreativeTabs {
    private HexereiCreativeTabs() {}

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HexereiMod.MODID);

    public static final RegistryObject<CreativeModeTab> HEXEREI = TABS.register("hexerei",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.hexerei"))
                    .icon(() -> new ItemStack(HexereiBlocks.ALTAR.get()))
                    .displayItems((params, output) -> {
                        output.accept(HexereiBlocks.ALTAR.get());
                        output.accept(HexereiBlocks.CAULDRON.get());
                        output.accept(HexereiBlocks.RITUAL_CIRCLE.get());
                        output.accept(HexereiBlocks.RITUAL_GLYPH.get());
                        output.accept(HexereiItems.RITUAL_CHALK.get());
                        HexereiCrops.SEED_ITEMS.forEach(s -> output.accept(s.get()));
                        output.accept(HexereiItems.BELLADONNA_FLOWER.get());
                        output.accept(HexereiItems.MANDRAKE_ROOT.get());
                        output.accept(HexereiItems.ARTICHOKE.get());
                        output.accept(HexereiItems.WORMWOOD.get());
                        output.accept(HexereiItems.WOLFSBANE.get());
                        output.accept(HexereiItems.ICY_NEEDLE.get());
                        // one filled brew per starter recipe, for creative access
                        output.accept(com.vel5id.hexerei.item.BrewItem.of(com.vel5id.hexerei.brewing.Brews.SLEEPING_DRAUGHT));
                        output.accept(com.vel5id.hexerei.item.BrewItem.of(com.vel5id.hexerei.brewing.Brews.FRAILTY));
                    })
                    .build());
}
